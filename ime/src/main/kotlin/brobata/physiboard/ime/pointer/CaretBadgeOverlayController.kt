package brobata.physiboard.ime.pointer

import android.content.Context
import android.graphics.PixelFormat
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
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

    /** Where the attached window was last put, so an unchanged position costs no window update. */
    private var placedX = Int.MIN_VALUE
    private var placedY = Int.MIN_VALUE

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
     *
     * This runs on every keystroke, so it stays off the window manager whenever it can: the window
     * is added once and then only shown and hidden, its position is written only when it moved,
     * and the overlay permission is re-checked when the badge comes back into view (SS4.6, "on
     * every show"). Adding and removing the window for every Shift or Alt press cost the phone
     * 26 to 37 ms per modifier key (2026-10-06).
     */
    fun update(modifierInput: ModifierGlyphInput, caret: CaretGeometry?, screenWidthPx: Float, pxPerDp: Float) {
        if (rejected) return
        // spec SS4.8: `caret_modifier_badge` is "whether the badge exists".
        if (!settings.enabled) {
            hide()
            return
        }
        val items = CaretBadge.items(modifierInput)
        if (items.isEmpty() || caret == null) {
            badgeView?.visibility = View.GONE
            return
        }
        val existing = badgeView
        val showing = existing != null && existing.visibility == View.VISIBLE
        if (!showing && OverlayPermission.availability(service) != OverlayAvailability.AVAILABLE) {
            hide()
            return
        }
        val view = existing ?: createView() ?: return
        // Private mode keeps the badge up for every keystroke (app-shell.md SS31.4), so a refresh
        // that changes neither the glyphs nor the place costs no relayout.
        if (view.items != items) view.items = items
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val size = BadgeSize(view.measuredWidth.toFloat(), view.measuredHeight.toFloat(), view.baselineOffsetPx)
        val position = CaretBadgePlacement.place(caret, size, ScreenGeometry(screenWidthPx), pxPerDp)
        view.visibility = View.VISIBLE
        if (position.xPx == placedX && position.yPx == placedY) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = position.xPx
        params.y = position.yPx
        if (runCatching { windowManager.updateViewLayout(view, params) }.isSuccess) {
            placedX = position.xPx
            placedY = position.yPx
        }
    }

    /** spec SS4.6: "The remembered caret is forgotten and the badge hidden when the editor finishes...". */
    fun hide() {
        val view = badgeView ?: return
        badgeView = null
        placedX = Int.MIN_VALUE
        placedY = Int.MIN_VALUE
        runCatching { windowManager.removeView(view) }
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
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            rejected = true
            return null
        }
        badgeView = view
        return view
    }

    /**
     * The overlay context's own window manager. The service's is typed for an input method, and
     * adding or moving an overlay through it made StrictMode log a window-type mismatch, with a
     * stack trace, on every keystroke (SS4.5).
     */
    private val windowManager: WindowManager by lazy {
        overlayContext.getSystemService(WindowManager::class.java) ?: service.getSystemService(WindowManager::class.java)
    }
}
