package it.palsoftware.pastiera.inputmethod.statusbar

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Shared host for status bar buttons across different containers.
 * Handles wrapping (badge/flash) and state updates consistently.
 */
class StatusBarButtonHost(
    private val context: Context,
    private val registry: StatusBarButtonRegistry
) {

    data class HostedButton(
        val id: StatusBarButtonId,
        val button: View,
        val container: View
    )

    private val hostedButtons = mutableMapOf<StatusBarButtonId, HostedButton>()

    fun getOrCreateButton(
        id: StatusBarButtonId,
        size: Int,
        callbacks: StatusBarCallbacks,
        width: Int,
        height: Int
    ): HostedButton? {
        val existing = hostedButtons[id]
        if (existing != null) {
            if (id == StatusBarButtonId.Language) {
                registry.getLanguageFactory().refreshLanguageText(context, existing.button)
            }
            prepareForAttach(existing, width, height)
            return existing
        }

        val result = registry.createButton(context, id, size, callbacks) ?: return null
        val button = result.view
        val container = if (result.badgeView != null || result.flashOverlayView != null) {
            createWrappedView(button, result.badgeView, result.flashOverlayView, width, height)
        } else {
            button
        }

        val hosted = HostedButton(id, button, container)
        hostedButtons[id] = hosted
        prepareForAttach(hosted, width, height)
        return hosted
    }

    fun updateButtonLayout(id: StatusBarButtonId, width: Int, height: Int) {
        val hosted = hostedButtons[id] ?: return
        if (hosted.container is FrameLayout) {
            val params = (hosted.button.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(width, height)
            params.width = width
            params.height = height
            hosted.button.layoutParams = params
        }
    }

    fun updateClipboardCount(count: Int) {
        updateButton(StatusBarButtonId.Clipboard, ButtonState.ClipboardState(count))
    }

    fun setMicrophoneActive(isActive: Boolean) {
        val hosted = hostedButtons[StatusBarButtonId.Microphone] ?: return
        registry.getMicrophoneFactory().setActive(hosted.button, isActive)
    }

    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        val hosted = hostedButtons[StatusBarButtonId.Microphone] ?: return
        registry.getMicrophoneFactory().updateAudioLevel(hosted.button, rmsdB)
    }

    fun refreshLanguageText() {
        val hosted = hostedButtons[StatusBarButtonId.Language] ?: return
        registry.getLanguageFactory().refreshLanguageText(context, hosted.button)
    }

    fun setMinimalUiActive(isActive: Boolean) {
        updateButton(StatusBarButtonId.MinimalUi, ButtonState.MinimalUiState(isActive))
    }

    fun detachAll() {
        hostedButtons.keys.forEach { detachButton(it) }
    }

    fun detachButton(id: StatusBarButtonId) {
        val hosted = hostedButtons[id] ?: return
        (hosted.container.parent as? ViewGroup)?.removeView(hosted.container)
        hosted.button.visibility = View.GONE
        hosted.button.alpha = 1f
    }

    fun cleanup() {
        hostedButtons.forEach { (id, hosted) ->
            registry.cleanupButton(id, hosted.button)
        }
        hostedButtons.clear()
    }

    private fun updateButton(id: StatusBarButtonId, state: ButtonState) {
        val hosted = hostedButtons[id] ?: return
        registry.updateButton(id, hosted.button, state)
    }

    private fun prepareForAttach(hosted: HostedButton, width: Int, height: Int) {
        (hosted.container.parent as? ViewGroup)?.removeView(hosted.container)
        hosted.button.visibility = View.VISIBLE
        hosted.button.alpha = 1f
        if (hosted.container is FrameLayout) {
            val params = (hosted.button.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(width, height)
            params.width = width
            params.height = height
            hosted.button.layoutParams = params
        }
    }

    private fun createWrappedView(
        button: View,
        badgeView: View?,
        flashOverlayView: View?,
        width: Int,
        height: Int
    ): View {
        val frame = FrameLayout(context)
        frame.addView(button, FrameLayout.LayoutParams(width, height))

        badgeView?.let { badge ->
            (badge.parent as? ViewGroup)?.removeView(badge)
            frame.addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.TOP
                ).apply {
                    val margin = dpToPx(2f)
                    val offset = dpToPx(2f)
                    setMargins(margin, margin + offset, margin, margin)
                }
            )
        }

        flashOverlayView?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            frame.addView(overlay)
        }

        return frame
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
