package brobata.physiboard.inputmethod

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
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

    private var currentLabel: Badge? = null

    /**
     * What to draw. [held] is true only while every active modifier is a plain physical hold, which
     * is drawn muted - a hold ends when the user lets go, so it does not need to shout. Anything
     * armed or locked stays on after the key is released and is drawn solid.
     */
    private data class Badge(val text: String, val held: Boolean)

    /**
     * The last caret position the editor reported, so pressing a modifier can put the badge on
     * screen straight away instead of waiting a round trip for a fresh one.
     */
    private var lastCaret: Caret? = null

    /** The caret the editor reported, in screen pixels. */
    private data class Caret(val x: Float, val top: Float, val bottom: Float)

    /**
     * Recomputes the label from the modifier state.
     *
     * @return true if the need for cursor updates changed, so the caller can re-issue the request.
     */
    fun onSnapshot(snapshot: StatusBarController.StatusSnapshot, enabled: Boolean) {
        val badge = if (enabled) badgeFor(snapshot) else null
        if (badge == currentLabel) return
        currentLabel = badge
        if (badge == null) hide() else lastCaret?.let { show(badge, it) }
    }

    /** Positions the badge from the caret the editor just reported. */
    fun onCursorAnchorInfo(info: CursorAnchorInfo?) {
        val caret = caretRect(info)
        lastCaret = caret
        val badge = currentLabel ?: return
        if (caret == null) hide() else show(badge, caret)
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
    }

    /**
     * The caret in screen coordinates, or null when the editor is not reporting one or has scrolled
     * it out of sight. The whole insertion marker is kept, not just its top, so the badge can be
     * centred on the line the way a subscript glyph sits beside a cursor.
     */
    private fun caretRect(info: CursorAnchorInfo?): Caret? {
        if (info == null) return null
        val horizontal = info.insertionMarkerHorizontal
        val top = info.insertionMarkerTop
        val bottom = info.insertionMarkerBottom
        if (horizontal.isNaN() || top.isNaN() || bottom.isNaN()) return null
        // The editor tells us when the caret is scrolled out of its own viewport; a badge floating
        // over the app where the cursor is not actually visible is worse than no badge.
        val flags = info.insertionMarkerFlags
        if (flags and CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION != 0 &&
            flags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION == 0
        ) {
            return null
        }
        val points = floatArrayOf(horizontal, top, horizontal, bottom)
        info.matrix.mapPoints(points)
        return Caret(x = points[0], top = points[1], bottom = points[3])
    }

    private fun show(badge: Badge, caret: Caret) {
        if (overlayRejected || !canDrawOverlays()) return
        val view = badgeView ?: createBadge().also { badgeView = it }
        view.text = badge.text
        (view.background as? GradientDrawable)?.setColor(
            if (badge.held) BADGE_HELD else BADGE_LATCHED
        )

        val metrics = context.resources.displayMetrics
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = view.measuredWidth
        val height = view.measuredHeight
        // Above the caret rather than beside it. Beside reads better in the abstract, but the
        // caret is not always at the end of a line - put it in a field with text after the cursor
        // and a badge to its right sits straight on top of that text.
        val gap = dp(2)
        var x = (caret.x - dp(2)).toInt()
        var y = (caret.top - height - gap).toInt()
        // At the top of the screen there is no room above, so it drops below the line instead.
        if (y < 0) y = (caret.bottom + gap).toInt()
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

    // It has to carry its own contrast: it floats over an app whose background could be any colour,
    // so a near-black pill disappears on a dark theme and a pale one disappears on a light theme.
    // White bold text on a saturated fill reads on both.
    private fun createBadge(): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        includeFontPadding = false
        letterSpacing = 0.06f
        setPadding(dp(6), dp(3), dp(6), dp(3))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(BADGE_LATCHED)
        }
    }

    private fun canDrawOverlays(): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "CaretBadge"

        /** Armed or locked: it outlives the keypress, so it is drawn at full strength. */
        const val BADGE_LATCHED = 0xF03B82F6.toInt()

        /** A plain hold, which ends the moment the key is released. Present, but muted. */
        const val BADGE_HELD = 0xC064748B.toInt()

        /**
         * Every way a modifier can be on, including a plain physical hold. This is a read-out of
         * what the keyboard is actually doing, so it reports a held Shift exactly like a latched
         * one - if it is on, it is shown.
         */
        fun badgeFor(snapshot: StatusBarController.StatusSnapshot): Badge? {
            val parts = mutableListOf<String>()
            // Tracked separately from the text: the style says whether this outlives the keypress,
            // which is the part that actually changes what the next letter will do.
            var anyLatched = false
            when {
                snapshot.capsLockEnabled -> { parts += "CAPS"; anyLatched = true }
                snapshot.shiftOneShot -> { parts += "SHIFT"; anyLatched = true }
                snapshot.shiftPhysicallyPressed -> parts += "SHIFT"
            }
            when {
                snapshot.altLatchActive -> { parts += "ALT"; anyLatched = true }
                snapshot.altOneShot -> { parts += "ALT"; anyLatched = true }
                snapshot.altPhysicallyPressed -> parts += "ALT"
            }
            // The one exclusion: nav mode holds Ctrl latched for as long as it is on, so reporting
            // that would pin a Ctrl badge beside the cursor permanently rather than report a press.
            when {
                snapshot.ctrlLatchActive && !snapshot.ctrlLatchFromNavMode -> {
                    parts += "CTRL"; anyLatched = true
                }
                snapshot.ctrlOneShot -> { parts += "CTRL"; anyLatched = true }
                snapshot.ctrlPhysicallyPressed -> parts += "CTRL"
            }
            if (snapshot.symPage != 0) { parts += "SYM"; anyLatched = true }
            if (parts.isEmpty()) return null
            return Badge(text = parts.joinToString(" "), held = !anyLatched)
        }
    }
}
