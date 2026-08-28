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

    /**
     * Latched only when the window manager actually rejects the view, so a broken overlay is not
     * retried on every keystroke. A missing permission is NOT latched here: it is re-checked each
     * time, so granting it takes effect without restarting the keyboard.
     */
    private var overlayRejected = false

    private var currentLabel: String? = null

    /**
     * The last caret position the editor reported, so pressing a modifier can put the badge on
     * screen straight away instead of waiting a round trip for a fresh one.
     */
    private var lastCaret: PointF? = null

    /**
     * True while the feature is on, i.e. while the editor should be publishing cursor updates.
     *
     * Monitoring deliberately stays on for the whole editing session rather than being switched on
     * when a modifier is pressed: the caret moves as you type, so a position cached from before the
     * last few keystrokes would put the badge in the wrong place, and waiting for a fresh one would
     * show it a frame or two late on every capital letter.
     */
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
        if (label == null) {
            hide()
        } else {
            lastCaret?.let { show(label, it.x.toInt(), it.y.toInt()) }
        }
        if (enabled == wantsCursorUpdates) return false
        wantsCursorUpdates = enabled
        if (!enabled) lastCaret = null
        return true
    }

    /** Positions the badge from the caret the editor just reported. */
    fun onCursorAnchorInfo(info: CursorAnchorInfo?) {
        val caret = caretTopLeft(info)
        lastCaret = caret
        val label = currentLabel ?: return
        if (caret == null) hide() else show(label, caret.x.toInt(), caret.y.toInt())
    }

    /** Called when the editor goes away - the cached caret belongs to it, not to the next one. */
    fun onEditorGone() {
        lastCaret = null
        hide()
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
        lastCaret = null
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
        if (overlayRejected || !canDrawOverlays()) return
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
            Log.e(TAG, "caret badge overlay rejected", e)
            overlayRejected = true
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
         * Every way a modifier can be on, including a plain physical hold. This is a read-out of
         * what the keyboard is actually doing, so it reports a held Shift exactly like a latched
         * one - if it is on, it is shown.
         */
        fun labelFor(snapshot: StatusBarController.StatusSnapshot): String? {
            val parts = mutableListOf<String>()
            when {
                snapshot.capsLockEnabled -> parts += "CAPS"
                snapshot.shiftOneShot || snapshot.shiftPhysicallyPressed -> parts += "SHIFT"
            }
            when {
                snapshot.altLatchActive -> parts += "ALT LOCK"
                snapshot.altOneShot || snapshot.altPhysicallyPressed -> parts += "ALT"
            }
            // The one exclusion: nav mode holds Ctrl latched for as long as it is on, so reporting
            // that would pin CTRL LOCK beside the cursor permanently rather than report a keypress.
            when {
                snapshot.ctrlLatchActive && !snapshot.ctrlLatchFromNavMode -> parts += "CTRL LOCK"
                snapshot.ctrlOneShot || snapshot.ctrlPhysicallyPressed -> parts += "CTRL"
            }
            if (snapshot.symPage != 0) parts += "SYM"
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }
    }
}
