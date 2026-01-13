package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonHost
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonId
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Overlay menu that replaces the status bar row with 7 fixed buttons.
 */
class HamburgerMenuView(
    private val context: Context,
    private val buttonRegistry: StatusBarButtonRegistry
) {

    private val menuButtonIds = listOf(
        StatusBarButtonId.Symbols,
        StatusBarButtonId.Emoji,
        StatusBarButtonId.Microphone,
        StatusBarButtonId.Clipboard,
        StatusBarButtonId.Language,
        StatusBarButtonId.Settings
    )

    private var root: FrameLayout? = null
    private var row: LinearLayout? = null
    private val buttonHost = StatusBarButtonHost(context, buttonRegistry)
    private var currentButtons: List<StatusBarButtonHost.HostedButton> = emptyList()
    private var lastClipboardCount: Int? = null
    private var lastMicrophoneActive: Boolean? = null
    private var lastMicrophoneRms: Float? = null

    fun attachTo(parent: FrameLayout) {
        val view = ensureView()
        if (view.parent !== parent) {
            (view.parent as? ViewGroup)?.removeView(view)
            parent.addView(view)
        }
    }

    fun show(callbacks: StatusBarCallbacks, onClose: () -> Unit) {
        val view = ensureView()
        view.visibility = View.VISIBLE
        view.bringToFront()
        view.setOnClickListener { onClose() }
        view.post {
            buildButtons(callbacks, onClose)
        }
    }

    fun hide() {
        root?.visibility = View.GONE
    }

    fun isVisible(): Boolean = root?.visibility == View.VISIBLE

    fun updateClipboardCount(count: Int) {
        lastClipboardCount = count
        buttonHost.updateClipboardCount(count)
    }

    fun setMicrophoneActive(isActive: Boolean) {
        lastMicrophoneActive = isActive
        buttonHost.setMicrophoneActive(isActive)
    }

    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        lastMicrophoneRms = rmsdB
        buttonHost.updateMicrophoneAudioLevel(rmsdB)
    }

    fun refreshLanguageText() {
        buttonHost.refreshLanguageText()
    }

    private fun ensureView(): FrameLayout {
        if (root != null) {
            return root!!
        }
        val verticalPadding = dpToPx(8f)
        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(0, verticalPadding, 0, verticalPadding)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateButtonSizes(this)
            }
        }
        row = rowView
        root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            addView(rowView)
        }
        return root!!
    }

    private fun buildButtons(callbacks: StatusBarCallbacks, onClose: () -> Unit) {
        val rowView = row ?: return
        clearButtons()

        val menuCallbacks = StatusBarCallbacks(
            onClipboardRequested = {
                callbacks.onClipboardRequested?.invoke()
            },
            onSpeechRecognitionRequested = {
                callbacks.onSpeechRecognitionRequested?.invoke()
            },
            onEmojiPickerRequested = {
                callbacks.onEmojiPickerRequested?.invoke()
            },
            onLanguageSwitchRequested = {
                callbacks.onHapticFeedback?.invoke()
                callbacks.onLanguageSwitchRequested?.invoke()
            },
            onHamburgerMenuRequested = null,
            onOpenSettings = {
                callbacks.onOpenSettings?.invoke()
            },
            onSymbolsPageRequested = {
                callbacks.onSymbolsPageRequested?.invoke()
            },
            onHapticFeedback = callbacks.onHapticFeedback
        )

        val buttonHeight = resolveButtonHeight(rowView)
        val closeButton = createCloseButton(menuCallbacks.onHapticFeedback, onClose, buttonHeight)
        rowView.addView(closeButton)
        val fallbackWidth = buttonHeight
        val hostedButtons = mutableListOf<StatusBarButtonHost.HostedButton>()
        menuButtonIds.forEach { id ->
            val hosted = buttonHost.getOrCreateButton(
                id,
                buttonHeight,
                menuCallbacks,
                fallbackWidth,
                buttonHeight
            ) ?: return@forEach
            hostedButtons.add(hosted)
            rowView.addView(hosted.container)
        }
        currentButtons = hostedButtons

        updateButtonSizes(rowView)
        applyStoredStates()
    }

    private fun clearButtons() {
        buttonHost.detachAll()
        currentButtons = emptyList()
        row?.removeAllViews()
    }

    private fun updateButtonSizes(rowView: LinearLayout) {
        val expectedButtons = menuButtonIds.size + 1
        val totalButtons = rowView.childCount
        if (totalButtons != expectedButtons) {
            return
        }
        val spacing = dpToPx(3f)
        val availableWidth = rowView.width - rowView.paddingLeft - rowView.paddingRight
        if (availableWidth <= 0) {
            return
        }
        val buttonWidth = ((availableWidth - spacing * (totalButtons - 1)) / totalButtons).coerceAtLeast(1)
        val buttonHeight = resolveButtonHeight(rowView)
        for (index in 0 until totalButtons) {
            val child = rowView.getChildAt(index)
            val params = (child.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(buttonWidth, buttonHeight)
            params.width = buttonWidth
            params.height = buttonHeight
            params.marginEnd = if (index == totalButtons - 1) 0 else spacing
            child.layoutParams = params
        }
        currentButtons.forEach { hosted ->
            buttonHost.updateButtonLayout(hosted.id, buttonWidth, buttonHeight)
        }
    }

    private fun resolveButtonHeight(rowView: LinearLayout): Int {
        val height = rowView.height - rowView.paddingTop - rowView.paddingBottom
        return if (height > 0) height else dpToPx(39f)
    }

    private fun applyStoredStates() {
        lastClipboardCount?.let { buttonHost.updateClipboardCount(it) }
        lastMicrophoneActive?.let { buttonHost.setMicrophoneActive(it) }
        lastMicrophoneRms?.let { buttonHost.updateMicrophoneAudioLevel(it) }
        buttonHost.refreshLanguageText()
    }

    private fun createCloseButton(
        onHapticFeedback: (() -> Unit)?,
        onClose: () -> Unit,
        heightPx: Int
    ): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            setColorFilter(Color.WHITE)
            background = StatusBarButtonStyles.createButtonDrawable(heightPx)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onHapticFeedback?.invoke()
                onClose()
            }
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
