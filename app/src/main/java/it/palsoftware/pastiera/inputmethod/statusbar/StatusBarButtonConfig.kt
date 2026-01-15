package it.palsoftware.pastiera.inputmethod.statusbar

import android.view.View

/**
 * Unique identifier for status bar buttons.
 * Sealed class allows type-safe handling of all button types.
 */
sealed class StatusBarButtonId(val key: String) {
    object Clipboard : StatusBarButtonId("clipboard")
    object Microphone : StatusBarButtonId("microphone")
    object Language : StatusBarButtonId("language")
    object Emoji : StatusBarButtonId("emoji")
    object Hamburger : StatusBarButtonId("hamburger")
    object MinimalUi : StatusBarButtonId("minimal_ui")
    object Settings : StatusBarButtonId("settings")
    object Symbols : StatusBarButtonId("symbols")
    data class Custom(val customKey: String) : StatusBarButtonId(customKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StatusBarButtonId) return false
        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

/**
 * Position of a button in the status bar.
 */
enum class StatusBarButtonPosition {
    LEFT,
    RIGHT
}

/**
 * Configuration for a single status bar button.
 * 
 * @param id Unique identifier for the button
 * @param position Where the button should be placed (LEFT or RIGHT)
 * @param enabled Whether the button is currently enabled
 * @param order Relative order within its position (lower = earlier)
 */
data class StatusBarButtonConfig(
    val id: StatusBarButtonId,
    val position: StatusBarButtonPosition,
    val enabled: Boolean = true,
    val order: Int = 0
)

/**
 * Callbacks for button interactions.
 * All callbacks are optional - pass null for unused ones.
 */
data class ButtonCallbacks(
    val onClick: (() -> Unit)? = null,
    val onLongClick: (() -> Boolean)? = null
)

/**
 * Container for all status bar callbacks.
 * Each button factory will use only the callbacks it needs.
 * This allows VariationBarView to pass a single callbacks object
 * without knowing which specific buttons will use which callbacks.
 */
data class StatusBarCallbacks(
    /** Called when clipboard button is clicked */
    val onClipboardRequested: (() -> Unit)? = null,
    
    /** Called when microphone button is clicked */
    val onSpeechRecognitionRequested: (() -> Unit)? = null,
    
    /** Called when emoji button is clicked */
    val onEmojiPickerRequested: (() -> Unit)? = null,
    
    /** Called when language button is clicked */
    val onLanguageSwitchRequested: (() -> Unit)? = null,

    /** Called when hamburger menu button is clicked */
    val onHamburgerMenuRequested: (() -> Unit)? = null,

    /** Called when minimal UI (pastierina) mode is requested */
    val onMinimalUiToggleRequested: (() -> Unit)? = null,
    
    /** Called when language button is long-pressed (opens settings) */
    val onOpenSettings: (() -> Unit)? = null,
    
    /** Called when symbols button is clicked */
    val onSymbolsPageRequested: (() -> Unit)? = null,
    
    /** Called to trigger haptic feedback */
    val onHapticFeedback: (() -> Unit)? = null
)

/**
 * State passed to buttons for updates.
 * Each button type can define its own state requirements.
 */
sealed class ButtonState {
    /**
     * State for clipboard button - badge count.
     */
    data class ClipboardState(val itemCount: Int) : ButtonState()

    /**
     * State for microphone button - active/inactive and audio level.
     */
    data class MicrophoneState(
        val isActive: Boolean,
        val audioLevelDb: Float = -10f
    ) : ButtonState()

    /**
     * State for language button - current language code.
     */
    data class LanguageState(val languageCode: String) : ButtonState()

    /**
     * State for minimal UI button - active/inactive.
     */
    data class MinimalUiState(val isActive: Boolean) : ButtonState()
}

/**
 * Result of button creation containing the view and optional auxiliary views.
 */
data class ButtonCreationResult(
    val view: View,
    val badgeView: View? = null,
    val flashOverlayView: View? = null
)
