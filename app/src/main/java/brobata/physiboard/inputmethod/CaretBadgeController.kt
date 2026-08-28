package brobata.physiboard.inputmethod

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.widget.TextView

/**
 * Shows the active modifier as a small label beside the text cursor, the way a hardware-keyboard
 * phone used to.
 *
 * This replaces having to look down at an LED strip on the keyboard: the state is reported where the
 * user is already looking. It draws into an overlay window because an IME cannot paint inside the
 * app it is typing into, and it is positioned from [CursorAnchorInfo], which the editor publishes
 * only while we ask for it - so monitoring is switched on when a modifier is armed and back off the
 * moment none are, rather than running for every keystroke.
 *
 * Editors are not obliged to report a cursor position. When one does not, there is nothing to anchor
 * to and the badge simply stays hidden; the status bar and the optional LED strip still show state.
 */
class CaretBadgeController(private val service: InputMethodService) {

    private val context: Context = service
    private var windowManager: WindowManager? = null
    private var badgeView: TextView? = null
    private var attached = false

    /** One failure to add the overlay is enough - do not thrash the window manager on every key. */
    private var overlayUnavailable = false

    private var currentLabel: String? = null

    /** True while a modifier is armed, i.e. while the editor should be publishing cursor updates. */
    var wantsCursorUpdates: Boolean = false
        private set

    /**
     * Recomputes the label from the modifier state.
     *
     * @return true if the need for cursor updates changed, so the caller can re-issue the request.
     */
    fun onSnapshot(snapshot: StatusBarController.StatusSnapshot, enabled: Boolean): Boolean {
        val label = if (enabled) labelFor(snapshot) else null
        currentLabel = label
        if (label == null) hide()
        val want = label != null
        if (want == wantsCursorUpdates) return false
        wantsCursorUpdates = want
        return true
    }

    /** Positions the badge from the caret the editor just reported. */
    fun onCursorAnchorInfo(info: CursorAnchorInfo?) {
        val label = currentLabel ?: return
        val caret = caretTopLeft(info) ?: run { hide(); return }
        show(label, caret.x.toInt(), caret.y.toInt())
    }

    fun hide() {
        val view = badgeView ?: return
        if (!attached) return
        runCatching { windowManager?.removeViewImmediate(view) }
        attached = false
    }

    fun destroy() {
        hide()
        badgeView = null
        windowManager = null
        currentLabel = null
        wantsCursorUpdates = false
    }

    /**
     * The caret in screen coordinates, or null when the editor is not reporting one or has scrolled
     * it out of sight. Anchored to the top of the insertion marker so the badge can sit above the
     * line rather than over the text being typed.
     */
    private fun caretTopLeft(info: CursorAnchorInfo?): PointF? {
        if (info == null) return null
        val horizontal = info.insertionMarkerHorizontal
        val top = info.insertionMarkerTop
        if (horizontal.isNaN() || top.isNaN()) return null
        // The editor tells us when the caret is scrolled out of its own viewport; a badge floating
        // over the app where the cursor is not actually visible is worse than no badge.
        val flags = info.insertionMarkerFlags
        if (flags and CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION != 0 &&
            flags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION == 0
        ) {
            return null
        }
        val point = floatArrayOf(horizontal, top)
        info.matrix.mapPoints(point)
        return PointF(point[0], point[1])
    }

    private fun show(label: String, caretX: Int, caretTopY: Int) {
        if (overlayUnavailable) return
        if (!canDrawOverlays()) {
            overlayUnavailable = true
            return
        }
        val view = badgeView ?: createBadge().also { badgeView = it }
        view.text = label

        val metrics = context.resources.displayMetrics
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = view.measuredWidth
        val height = view.measuredHeight
        // Sit above the caret and slightly right of it, then keep it on screen: near the top of a
        // field there is no room above, so it drops below the line instead of being clipped away.
        val gap = dp(4)
        var x = caretX + dp(2)
        var y = caretTopY - height - gap
        if (y < 0) y = caretTopY + dp(20) + gap
        x = x.coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
        y = y.coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))

        val params = layoutParams(x, y)
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        try {
            if (attached) wm.updateViewLayout(view, params) else wm.addView(view, params)
            attached = true
        } catch (e: Exception) {
            Log.e(TAG, "caret badge overlay unavailable", e)
            overlayUnavailable = true
            attached = false
        }
    }

    private fun layoutParams(x: Int, y: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never focusable and never touchable: this is a read-out, and it must not steal a tap that
        // was meant for the text underneath it.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun createBadge(): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        includeFontPadding = false
        letterSpacing = 0.08f
        setPadding(dp(6), dp(3), dp(6), dp(3))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(BADGE_BACKGROUND)
        }
    }

    private fun canDrawOverlays(): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "CaretBadge"

        /** Dark enough to read white text on any app background, light enough not to be a block. */
        const val BADGE_BACKGROUND = 0xE0202124.toInt()

        /**
         * Mirrors the LED strip: only armed one-shots and locks are reported. A plain physical hold
         * is left alone, or the badge would flash beside the cursor on every capital letter.
         */
        fun labelFor(snapshot: StatusBarController.StatusSnapshot): String? {
            val parts = mutableListOf<String>()
            when {
                snapshot.capsLockEnabled -> parts += "CAPS"
                snapshot.shiftOneShot -> parts += "SHIFT"
            }
            when {
                snapshot.altLatchActive -> parts += "ALT LOCK"
                snapshot.altOneShot -> parts += "ALT"
            }
            // Nav mode drives its own Ctrl latch and has its own on-screen story; reporting it here
            // would leave CTRL pinned beside the cursor for as long as nav mode is on.
            when {
                snapshot.ctrlLatchActive && !snapshot.ctrlLatchFromNavMode -> parts += "CTRL LOCK"
                snapshot.ctrlOneShot -> parts += "CTRL"
            }
            if (snapshot.symPage != 0) parts += "SYM"
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }
    }
}
