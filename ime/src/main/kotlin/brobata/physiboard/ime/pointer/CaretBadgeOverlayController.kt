package brobata.physiboard.ime.pointer

import android.content.Context
import android.graphics.PixelFormat
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.WindowManager
import brobata.physiboard.core.pointer.OverlayAvailability
import brobata.physiboard.core.pointer.caret.BadgeSize
import brobata.physiboard.core.pointer.caret.CaretBadge
import brobata.physiboard.core.pointer.caret.CaretBadgeSettings
import brobata.physiboard.core.pointer.caret.CaretBadgePlacement
import brobata.physiboard.core.pointer.caret.CaretGeometry
import brobata.physiboard.core.pointer.caret.ModifierGlyphInput
import brobata.physiboard.core.pointer.caret.ScreenGeometry

/**
 * Owns the caret badge's real overlay window.
 *
 * spec: trackpad-caret-nav.md SS4.5, the fix this task calls out explicitly: "The badge window must
 * be created from a context typed for an overlay. The keyboard service's own context is typed as an
 * input method, and adding an overlay through it makes the system log a window-type mismatch on
 * every layout pass... PhysiBoard creates a window context of type `TYPE_APPLICATION_OVERLAY`... and
 * falls back to the service context only if that fails." [overlayContext] is created exactly once,
 * from `Context.createWindowContext`, and reused for every show; `:ime`'s own `minSdk` is 31, so the
 * "falls back... only if that fails" branch is the only one this build can ever take (SS4.5 names
 * Android 12 = API 31 as the cutover), kept because a failing `createWindowContext` call is still
 * possible in principle and must degrade rather than crash.
 *
 * NEEDS A REAL DEVICE: whether a badge window the window manager actually rejects should latch off
 * until the service restarts (SS4.6) is modelled here as [rejected], but that branch has never been
 * exercised against a real `WindowManager`; whether the window-type mismatch log line is really
 * gone with this fix needs `adb logcat` on the Titan (SS4.5's own changelog entry is what this is
 * reproducing).
 */
internal class CaretBadgeOverlayController(private val service: InputMethodService) {

    private val overlayContext: Context = runCatching {
        service.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(service)

    private var badgeView: CaretBadgeOverlayView? = null
    private var rejected = false

    /** spec SS4.8's three rows; the colours reach a live badge at once, the switch on the next [update]. */
    var settings: CaretBadgeSettings = CaretBadgeSettings()
        set(value) {
            field = value
            badgeView?.armedColorArgb = value.armedColorArgb
            badgeView?.lockedColorArgb = value.lockedColorArgb
        }

    /**
     * Recomputes and shows, moves or hides the badge. spec SS4.6: "recomputes its items on every
     * strip refresh... If it became empty the badge hides... a caret is unusable when...".
     */
    fun update(modifierInput: ModifierGlyphInput, caret: CaretGeometry?, screenWidthPx: Float, pxPerDp: Float) {
        if (rejected) return
        // spec SS4.8: `caret_modifier_badge` is "whether the badge exists".
        if (!settings.enabled) {
            hide()
            return
        }
        if (OverlayPermission.availability(service) != OverlayAvailability.AVAILABLE) {
            hide()
            return
        }
        val items = CaretBadge.items(modifierInput)
        if (items.isEmpty() || caret == null) {
            hide()
            return
        }
        val view = badgeView ?: createView() ?: return
        // Private mode keeps the badge up for every keystroke (app-shell.md SS31.4), so a refresh
        // that changes neither the glyphs nor the place costs no relayout.
        val itemsChanged = view.items != items
        if (itemsChanged) view.items = items
        view.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        val size = BadgeSize(view.measuredWidth.toFloat(), view.measuredHeight.toFloat(), view.baselineOffsetPx)
        val position = CaretBadgePlacement.place(caret, size, ScreenGeometry(screenWidthPx), pxPerDp)
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (!itemsChanged && params.x == position.xPx && params.y == position.yPx) return
        params.x = position.xPx
        params.y = position.yPx
        runCatching { windowManager().updateViewLayout(view, params) }
    }

    /** spec SS4.6: "The remembered caret is forgotten and the badge hidden when the editor finishes...". */
    fun hide() {
        val view = badgeView ?: return
        badgeView = null
        runCatching { windowManager().removeView(view) }
    }

    private fun createView(): CaretBadgeOverlayView? {
        val view = CaretBadgeOverlayView(overlayContext)
        view.armedColorArgb = settings.armedColorArgb
        view.lockedColorArgb = settings.lockedColorArgb
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        val added = runCatching { windowManager().addView(view, params) }.isSuccess
        if (!added) {
            rejected = true
            return null
        }
        badgeView = view
        return view
    }

    private fun windowManager(): WindowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}
