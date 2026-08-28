package brobata.physiboard.inputmethod

import android.content.Context
import android.graphics.PixelFormat
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo

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
    private var badgeView: CaretBadgeView? = null
    private var attached = false

    /**
     * Latched only when the window manager actually rejects the view, so a broken overlay is not
     * retried on every keystroke. A missing permission is NOT latched here: it is re-checked each
     * time, so granting it takes effect without restarting the keyboard.
     */
    private var overlayRejected = false

    private var currentItems: List<CaretBadgeView.Item> = emptyList()

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
        val items = if (enabled) itemsFor(snapshot) else emptyList()
        if (items == currentItems) return
        currentItems = items
        if (items.isEmpty()) hide() else lastCaret?.let { show(items, it) }
    }

    /** Positions the badge from the caret the editor just reported. */
    fun onCursorAnchorInfo(info: CursorAnchorInfo?) {
        val caret = caretRect(info)
        lastCaret = caret
        if (currentItems.isEmpty()) return
        if (caret == null) hide() else show(currentItems, caret)
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
        currentItems = emptyList()
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

    private fun show(items: List<CaretBadgeView.Item>, caret: Caret) {
        if (overlayRejected || !canDrawOverlays()) return
        val view = badgeView ?: CaretBadgeView(context).also { badgeView = it }
        view.items = items

        val metrics = context.resources.displayMetrics
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = view.measuredWidth
        val height = view.measuredHeight
        // Centred over the caret, with the glyphs' feet just inside the top of the line box.
        //
        // One placement, always. Sitting beside the caret reads better when the space there happens
        // to be empty, but it is not empty in an empty field - the editor draws its hint exactly
        // there - so that rule needed a second placement, and the badge then jumped between the two
        // as the field filled and emptied. A position that never collides never has to move.
        //
        // Measured from the foot of the ink rather than the bottom of the view, which also carries
        // the font's descent and room for a lock bar.
        val lineHeight = caret.bottom - caret.top
        var x = (caret.x - width / 2f).toInt()
        var y = (caret.top + lineHeight * LINE_OVERLAP - view.glyphBottomOffset).toInt()
        // No room above at the top of the screen, so it drops below the line instead.
        if (y < 0) y = (caret.bottom - lineHeight * LINE_OVERLAP).toInt()
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

    private fun canDrawOverlays(): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "CaretBadge"

        /** How far the glyphs' feet reach into the top of the line box, as a fraction of it. */
        const val LINE_OVERLAP = 0.18f

        /**
         * Every way a modifier can be on, including a plain physical hold. This is a read-out of
         * what the keyboard is actually doing, so it reports a held Shift exactly like a latched
         * one - if it is on, it is shown.
         */
        fun itemsFor(snapshot: StatusBarController.StatusSnapshot): List<CaretBadgeView.Item> {
            val items = mutableListOf<CaretBadgeView.Item>()

            fun add(shape: CaretBadgeView.Shape, label: String?, locked: Boolean, faint: Boolean) =
                items.add(CaretBadgeView.Item(shape, label, locked, faint))

            val arrow = CaretBadgeView.Shape.ARROW
            val alt = CaretBadgeView.Shape.ALT
            val word = CaretBadgeView.Shape.TEXT

            when {
                snapshot.capsLockEnabled -> add(arrow, null, locked = true, faint = false)
                snapshot.shiftOneShot -> add(arrow, null, locked = false, faint = false)
                snapshot.shiftPhysicallyPressed -> add(arrow, null, locked = false, faint = true)
            }
            when {
                snapshot.altLatchActive -> add(alt, null, locked = true, faint = false)
                snapshot.altOneShot -> add(alt, null, locked = false, faint = false)
                snapshot.altPhysicallyPressed -> add(alt, null, locked = false, faint = true)
            }
            // The one exclusion: nav mode holds Ctrl latched for as long as it is on, so reporting
            // that would pin a Ctrl badge beside the cursor permanently rather than report a press.
            when {
                snapshot.ctrlLatchActive && !snapshot.ctrlLatchFromNavMode ->
                    add(word, "CTRL", locked = true, faint = false)
                snapshot.ctrlOneShot -> add(word, "CTRL", locked = false, faint = false)
                snapshot.ctrlPhysicallyPressed -> add(word, "CTRL", locked = false, faint = true)
            }
            if (snapshot.symPage != 0) add(word, "SYM", locked = false, faint = false)
            return items
        }
    }
}
