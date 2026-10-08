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
 */
internal class BottomOverlay(private val service: InputMethodService, private val tag: String) {

    val overlayContext: Context = runCatching {
        service.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(service)

    var view: View? = null
        private set

    val isShown: Boolean get() = view != null

    /**
     * Shows [content] at the bottom of the screen, [heightPx] tall (or wrapping when null), above
     * the keyboard window by [bottomMarginPx]. Returns false when the permission is missing or the
     * window manager refused the window, after a toast the user can act on.
     */
    fun show(content: View, heightPx: Int?, bottomMarginPx: Int = 0, focusable: Boolean = false, widthPx: Int? = null): Boolean {
        if (view != null) return true
        val corners = if (widthPx == null) cornerInsets(bottomMarginPx) else RoundedCornerInsets.NONE
        if (corners != RoundedCornerInsets.NONE) {
            content.setPadding(
                content.paddingLeft + corners.sidePx, content.paddingTop,
                content.paddingRight + corners.sidePx, content.paddingBottom + corners.bottomPx,
            )
        }
        if (OverlayPermission.availability(service) != OverlayAvailability.AVAILABLE) {
            runCatching { Toast.makeText(service, "This panel needs Display over other apps. Enable it in PhysiBoard settings.", Toast.LENGTH_SHORT).show() }
            return false
        }
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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
        val added = runCatching { windowManager.addView(content, params) }.onFailure { Log.e(tag, "overlay refused", it) }.isSuccess
        if (added) view = content
        return added
    }

    fun hide() {
        val current = view ?: return
        view = null
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

    companion object {
        /**
         * `titan2_elite_rounded_corner_insets`, set by the keyboard whenever its settings load.
         * Process-wide because every panel controller owns its own overlay and there is one
         * keyboard service per process.
         */
        @Volatile
        var roundedCornersEnabled: Boolean = true
    }
}
