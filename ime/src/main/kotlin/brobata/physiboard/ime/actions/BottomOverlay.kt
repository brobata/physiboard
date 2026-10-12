package brobata.physiboard.ime.actions

import android.content.Context
import android.graphics.PixelFormat
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.Gravity
import android.view.RoundedCorner
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import brobata.physiboard.core.pointer.OverlayAvailability
import brobata.physiboard.core.strip.RoundedCornerInsets
import brobata.physiboard.core.strip.StripGeometry
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.ime.pointer.OverlayPermission

/**
 * One bottom-anchored overlay window: the surface every panel this feature puts on screen shares
 * (the clipboard panel, the emoji picker, the expansion popup and the quick launcher sheet).
 * PhysiBoard has no input view (no soft keyboard, rebuild-from-scratch.md), so the Sym panels of
 * expansion-clipboard-pickers-launcher.md SS3.5 and SS4.3 cannot live inside the keyboard's own
 * window as they did in 2.x; they are `TYPE_APPLICATION_OVERLAY` windows like the screen trackpad
 * (trackpad-caret-nav.md SS2.4) and share its "Display over other apps" permission, re-checked on
 * every show and never cached (SS4.6).
 *
 * The window is created from a context typed for an overlay (SS4.5's fix) so the system does not
 * log a window-type mismatch on every layout pass. [focusable] windows take hardware keys
 * themselves (the quick launcher types into its own query); the others stay `FLAG_NOT_FOCUSABLE`
 * so the editor underneath keeps its connection and the keyboard keeps seeing keys.
 *
 * A full-width window that sits on the bottom edge pads its content away from the display's
 * rounded corners ([cornerInsets], status-bar.md SS4, layers-sym-alt.md SS5.7), in the content's
 * own background, and grows by the bottom padding so the content keeps its size.
 *
 * Panels open on the design system's spring and close on its short drop and fade
 * (docs/design/design-system.md, "Motion"); with animations off they appear and go in place. A
 * closing panel stops taking touches at once and its window goes when the fade ends, so [isShown]
 * is false, and a new panel can open, the moment [hide] is called.
 *
 * A panel that opens often (the Sym pages, layers-sym-alt.md SS5.12) keeps its window: [hide] with
 * `keep` leaves the window added with its content invisible (a window whose root is not visible
 * has no surface and takes no touches), and the next [show] of the same content only makes it
 * visible again. Adding a window and inflating its views cost a Sym press 25 to 60 ms on the
 * main thread (2026-10-11); a reveal costs well under one. [prepare] adds such a window ahead of
 * time, still invisible, so even the first open is a reveal. [release] removes it for good.
 */
internal class BottomOverlay(private val service: InputMethodService, private val tag: String) {

    val overlayContext: Context = runCatching {
        service.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(service)

    var view: View? = null
        private set

    val isShown: Boolean get() = view != null

    /** The window's content while a window is added, shown or kept invisible. */
    private var attached: View? = null
    private var attachedParams: Geometry? = null

    /** What the window was added with; a show that asks for anything else adds it again. */
    private data class Geometry(val heightPx: Int?, val bottomMarginPx: Int, val focusable: Boolean, val widthPx: Int?)

    /** The padding a view had before the corner insets were added to it, so a re-add never adds them twice. */
    private val basePadding = java.util.WeakHashMap<View, IntArray>()

    /**
     * Shows [content] at the bottom of the screen, [heightPx] tall (or wrapping when null), above
     * the keyboard window by [bottomMarginPx]. Returns false when the permission is missing or the
     * window manager refused the window, after a toast the user can act on.
     */
    fun show(
        content: View,
        heightPx: Int?,
        bottomMarginPx: Int = 0,
        focusable: Boolean = false,
        widthPx: Int? = null,
        enter: DesignMotion.Enter = DesignMotion.Enter.SPRING,
    ): Boolean {
        if (view != null) return true
        val geometry = Geometry(heightPx, bottomMarginPx, focusable, widthPx)
        if (attached === content && attachedParams == geometry && windowAlive(content)) {
            if (OverlayPermission.availability(service) != OverlayAvailability.AVAILABLE) {
                release()
                brobata.physiboard.ime.pointer.OverlayPermission.explainAndOpenSettings(service, "This panel")
                return false
            }
            reveal(content, enter)
            return true
        }
        if (!add(content, geometry, explain = true)) return false
        view = content
        DesignMotion.enter(content, enter)
        return true
    }

    /**
     * Adds [content]'s window now, invisible, so a later [show] of it is only a reveal. Quiet: no
     * toast when the permission is missing (the user did not ask for a panel). Returns whether
     * the window is there.
     */
    fun prepare(content: View, heightPx: Int?, bottomMarginPx: Int = 0): Boolean {
        val geometry = Geometry(heightPx, bottomMarginPx, focusable = false, widthPx = null)
        if (attached === content && attachedParams == geometry && windowAlive(content)) return true
        if (view != null) return false
        // Drawn once, untouchable and with the WINDOW fully transparent, so the glyphs it shows are
        // ready on the GPU before the first open (the first draw of a page of emoji cost about
        // 200 ms on the emulator), then hidden until a show. Window alpha 0, not view alpha:
        // Android 12+ lets touches through another app's untouchable overlay only when the window
        // itself is at most 0.8 opaque, so a view-alpha warm-up would eat the app's taps.
        content.visibility = View.VISIBLE
        if (!add(content, geometry, explain = false, touchable = false, windowAlpha = 0f)) return false
        val warm = object : android.view.ViewTreeObserver.OnDrawListener {
            var done = false
            override fun onDraw() {
                if (done) return
                done = true
                content.post {
                    runCatching { content.viewTreeObserver.removeOnDrawListener(this) }
                    if (view !== content && attached === content) {
                        content.visibility = View.INVISIBLE
                        setTouchable(content, true)
                    }
                }
            }
        }
        content.viewTreeObserver.addOnDrawListener(warm)
        return true
    }

    /**
     * Whether [content]'s window is still there: added and not removed. [View.isAttachedToWindow]
     * only turns true after the first layout pass, so a window added this frame counts by its
     * parent (the window's root, set by the add itself).
     */
    private fun windowAlive(content: View): Boolean = content.isAttachedToWindow || content.parent != null

    /**
     * Adds or clears `FLAG_NOT_TOUCHABLE` on [content]'s window, only when it changes. A touchable
     * window is always fully opaque again (the warm-up of [prepare] draws at window alpha 0).
     */
    private fun setTouchable(content: View, touchable: Boolean) {
        val params = content.layoutParams as? WindowManager.LayoutParams ?: return
        val notTouchable = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
        if (notTouchable == !touchable && (!touchable || params.alpha == 1f)) return
        if (touchable) params.alpha = 1f
        params.flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { windowManager?.updateViewLayout(content, params) }
    }

    private fun reveal(content: View, enter: DesignMotion.Enter) {
        content.animate().cancel()
        // Normally already touchable again (the close cleared it once the panel was hidden); a
        // show during the closing motion or the first warm-up draw clears it here.
        setTouchable(content, true)
        content.visibility = View.VISIBLE
        view = content
        DesignMotion.enter(content, enter)
    }

    private fun add(content: View, geometry: Geometry, explain: Boolean, touchable: Boolean = true, windowAlpha: Float = 1f): Boolean {
        if (attached != null) release()
        val heightPx = geometry.heightPx
        val bottomMarginPx = geometry.bottomMarginPx
        val widthPx = geometry.widthPx
        val base = basePadding[content]
        if (base != null) content.setPadding(base[0], base[1], base[2], base[3])
        val corners = if (widthPx == null) cornerInsets(bottomMarginPx) else RoundedCornerInsets.NONE
        if (corners != RoundedCornerInsets.NONE) {
            basePadding[content] = intArrayOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
            content.setPadding(
                content.paddingLeft + corners.sidePx, content.paddingTop,
                content.paddingRight + corners.sidePx, content.paddingBottom + corners.bottomPx,
            )
        }
        if (OverlayPermission.availability(service) != OverlayAvailability.AVAILABLE) {
            if (explain) brobata.physiboard.ime.pointer.OverlayPermission.explainAndOpenSettings(service, "This panel")
            return false
        }
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!geometry.focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            widthPx ?: WindowManager.LayoutParams.MATCH_PARENT,
            heightPx?.let { it + corners.bottomPx } ?: WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = bottomMarginPx
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        params.alpha = windowAlpha
        val added = runCatching { windowManager.addView(content, params) }.onFailure { Log.e(tag, "overlay refused", it) }.isSuccess
        if (added) {
            attached = content
            attachedParams = geometry
        }
        return added
    }

    /**
     * Takes the panel away: with [animate], on the closing motion (the window ignores touches
     * from now on and is removed when the motion ends); without, at once, as a panel replaced in
     * place or a caller running its own motion needs.
     */
    fun hide(animate: Boolean = true, keep: Boolean = false) {
        val current = view ?: return
        view = null
        if (keep && !teardown && attached === current) {
            conceal(current, animate)
            return
        }
        if (attached === current) {
            attached = null
            attachedParams = null
        }
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        // Always removed, attached or not: a window added this frame is not "attached" until its
        // first layout, and skipping it then would leave it on screen with nothing holding it.
        val remove = {
            runCatching { windowManager.removeView(current) }.onFailure { Log.e(tag, "overlay removal crashed", it) }
            Unit
        }
        if (!animate || teardown) {
            current.animate().cancel()
            remove()
            return
        }
        (current.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager.updateViewLayout(current, params) }
        }
        DesignMotion.exit(current, remove)
    }

    /**
     * Takes a kept panel off screen and leaves its window: on the closing motion (no touches from
     * now on) or at once. The content stays laid out but invisible, ready for the next [show].
     */
    private fun conceal(current: View, animate: Boolean) {
        val windowManager = overlayContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val settle = {
            // A show of this same panel during the motion cancels it; this never runs then.
            if (view !== current) {
                current.visibility = View.INVISIBLE
                current.alpha = 1f
                current.translationY = 0f
                // Hidden now, so touchable again: the next show needs no window update.
                if (attached === current) setTouchable(current, true)
            }
            Unit
        }
        if (!animate) {
            current.animate().cancel()
            settle()
            return
        }
        (current.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { windowManager?.updateViewLayout(current, params) }
        }
        DesignMotion.exit(current, settle)
    }

    /** Removes the window for good, shown or kept. Called when a panel is rebuilt and when the keyboard service goes. */
    fun release() {
        val current = attached ?: view ?: return
        attached = null
        attachedParams = null
        view = null
        current.animate().cancel()
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { windowManager.removeView(current) }.onFailure { Log.e(tag, "overlay removal crashed", it) }
    }

    /**
     * What a full-width panel whose bottom sits [bottomMarginPx] above the screen's gives the
     * rounded corners. The radius is the display's own (100 px on the Titan 2 Elite), else the
     * strip's 24 dp fallback (status-bar.md SS2).
     */
    fun cornerInsets(bottomMarginPx: Int = 0): RoundedCornerInsets {
        val density = service.resources.displayMetrics.density
        val reported = runCatching {
            val insets = service.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.windowInsets
            maxOf(
                insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
                insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
            )
        }.getOrDefault(0)
        val radius = reported.takeIf { it > 0 } ?: (StripGeometry.BOTTOM_CORNER_FALLBACK_DP * density).toInt()
        return RoundedCornerInsets.forPanel(roundedCornersEnabled, radius, bottomMarginPx, density)
    }

    fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
    fun dp(value: Double): Int = (value * service.resources.displayMetrics.density).toInt()

    /** The whole screen's height, the frame a bottom-anchored overlay's margin counts from. */
    fun screenHeightPx(): Int = runCatching {
        service.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds?.height()
    }.getOrNull() ?: service.resources.displayMetrics.heightPixels

    /** The status bar's height, the top edge nothing placed by the caret goes above. */
    fun statusBarHeightPx(): Int = runCatching {
        service.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.windowInsets
            ?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars())?.top
    }.getOrNull() ?: 0

    companion object {
        /**
         * `titan2_elite_rounded_corner_insets`, set by the keyboard whenever its settings load.
         * Process-wide because every panel controller owns its own overlay and there is one
         * keyboard service per process.
         */
        @Volatile
        var roundedCornersEnabled: Boolean = true

        /**
         * True while the keyboard service tears its panels down: every hide is immediate then, so
         * no window outlives the service on a closing animation. Main thread only.
         */
        var teardown: Boolean = false
    }
}
