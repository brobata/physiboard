package it.palsoftware.pastiera.inputmethod.statusbar

import android.content.Context
import android.view.View
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.statusbar.button.ClipboardButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.EmojiButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.HamburgerButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.LanguageButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.MinimalUiButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.MicrophoneButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.SettingsButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.SymbolsButtonFactory
import it.palsoftware.pastiera.inputmethod.statusbar.button.StatusBarButtonFactory

/**
 * Central registry for status bar button factories.
 * 
 * Manages the creation and configuration of status bar buttons.
 * Built-in buttons (clipboard, microphone, language) are registered by default.
 * Custom buttons can be registered dynamically.
 * 
 * Usage:
 * ```
 * val registry = StatusBarButtonRegistry()
 * val enabledButtons = registry.getEnabledButtons(context)
 * 
 * for (config in enabledButtons) {
 *     val result = registry.createButton(context, config.id, buttonSize, callbacks)
 *     // Add result.view to layout
 * }
 * ```
 */
class StatusBarButtonRegistry {
    
    private val factories = mutableMapOf<StatusBarButtonId, StatusBarButtonFactory>()
    
    // Keep references to specific factories for direct access when needed
    private val clipboardFactory = ClipboardButtonFactory()
    private val microphoneFactory = MicrophoneButtonFactory()
    private val languageFactory = LanguageButtonFactory()
    private val emojiFactory = EmojiButtonFactory()
    private val hamburgerFactory = HamburgerButtonFactory()
    private val minimalUiFactory = MinimalUiButtonFactory()
    private val settingsFactory = SettingsButtonFactory()
    private val symbolsFactory = SymbolsButtonFactory()
    
    init {
        // Register built-in button factories
        factories[StatusBarButtonId.Clipboard] = clipboardFactory
        factories[StatusBarButtonId.Microphone] = microphoneFactory
        factories[StatusBarButtonId.Language] = languageFactory
        factories[StatusBarButtonId.Emoji] = emojiFactory
        factories[StatusBarButtonId.Hamburger] = hamburgerFactory
        factories[StatusBarButtonId.MinimalUi] = minimalUiFactory
        factories[StatusBarButtonId.Settings] = settingsFactory
        factories[StatusBarButtonId.Symbols] = symbolsFactory
    }
    
    /**
     * Registers a custom button factory.
     * 
     * @param id The unique identifier for this button
     * @param factory The factory to create the button
     */
    fun register(id: StatusBarButtonId, factory: StatusBarButtonFactory) {
        factories[id] = factory
    }
    
    /**
     * Unregisters a button factory.
     * 
     * Note: Built-in buttons cannot be unregistered, only disabled via configuration.
     * 
     * @param id The identifier of the button to unregister
     * @return true if the factory was removed, false if it wasn't registered or is built-in
     */
    fun unregister(id: StatusBarButtonId): Boolean {
        if (id in listOf(StatusBarButtonId.Clipboard, StatusBarButtonId.Microphone, StatusBarButtonId.Language, StatusBarButtonId.Emoji, StatusBarButtonId.Hamburger, StatusBarButtonId.MinimalUi, StatusBarButtonId.Settings, StatusBarButtonId.Symbols)) {
            return false // Cannot unregister built-in buttons
        }
        return factories.remove(id) != null
    }
    
    /**
     * Gets the factory for a specific button ID.
     * 
     * @param id The button identifier
     * @return The factory, or null if not registered
     */
    fun getFactory(id: StatusBarButtonId): StatusBarButtonFactory? {
        return factories[id]
    }
    
    /**
     * Creates a button using the registered factory.
     * 
     * The factory will extract only the callbacks it needs from StatusBarCallbacks.
     * This allows VariationBarView to pass all callbacks without knowing which buttons
     * will use which callbacks.
     * 
     * @param context Android context
     * @param id The button identifier
     * @param size Button size in pixels
     * @param callbacks All available callbacks - each factory uses only what it needs
     * @return ButtonCreationResult, or null if no factory is registered for this ID
     */
    fun createButton(
        context: Context,
        id: StatusBarButtonId,
        size: Int,
        callbacks: StatusBarCallbacks
    ): ButtonCreationResult? {
        return factories[id]?.create(context, size, callbacks)
    }
    
    /**
     * Updates a button's state.
     * 
     * @param id The button identifier
     * @param view The button view to update
     * @param state The new state
     */
    fun updateButton(id: StatusBarButtonId, view: View, state: ButtonState) {
        factories[id]?.update(view, state)
    }
    
    /**
     * Cleans up resources for a button being removed.
     * 
     * @param id The button identifier
     * @param view The button view being removed
     */
    fun cleanupButton(id: StatusBarButtonId, view: View) {
        factories[id]?.cleanup(view)
    }
    
    /**
     * Gets the list of enabled buttons based on slot configuration.
     * 
     * Reads from SettingsManager to determine which buttons are in each slot.
     * Layout: [Left Slot] [---variations---] [Right Slot 1] [Right Slot 2]
     * 
     * @param context Android context (for reading settings)
     * @return List of enabled button configurations, sorted for display
     */
    fun getEnabledButtons(context: Context): List<StatusBarButtonConfig> {
        val enabledButtons = mutableListOf<StatusBarButtonConfig>()
        
        // Left slot
        val leftButton = SettingsManager.getStatusBarSlotLeft(context)
        buttonIdFromString(leftButton)?.let { id ->
            enabledButtons.add(StatusBarButtonConfig(
                id = id,
                position = StatusBarButtonPosition.LEFT,
                enabled = true,
                order = 0
            ))
        }
        
        // Right slot 1
        val rightButton1 = SettingsManager.getStatusBarSlotRight1(context)
        buttonIdFromString(rightButton1)?.let { id ->
            enabledButtons.add(StatusBarButtonConfig(
                id = id,
                position = StatusBarButtonPosition.RIGHT,
                enabled = true,
                order = 0
            ))
        }
        
        // Right slot 2
        val rightButton2 = SettingsManager.getStatusBarSlotRight2(context)
        buttonIdFromString(rightButton2)?.let { id ->
            enabledButtons.add(StatusBarButtonConfig(
                id = id,
                position = StatusBarButtonPosition.RIGHT,
                enabled = true,
                order = 1
            ))
        }
        
        return enabledButtons
    }
    
    /**
     * Converts a button string ID to a StatusBarButtonId.
     * Returns null for "none" or unknown IDs.
     */
    private fun buttonIdFromString(buttonString: String): StatusBarButtonId? {
        return when (buttonString) {
            SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> StatusBarButtonId.Clipboard
            SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> StatusBarButtonId.Microphone
            SettingsManager.STATUS_BAR_BUTTON_EMOJI -> StatusBarButtonId.Emoji
            SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> StatusBarButtonId.Language
            SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> StatusBarButtonId.Hamburger
            SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> StatusBarButtonId.Settings
            SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> StatusBarButtonId.Symbols
            SettingsManager.STATUS_BAR_BUTTON_NONE -> null
            else -> null
        }
    }
    
    /**
     * Gets the default button configuration.
     * 
     * Default layout: [Clipboard] [---variations---] [Emoji] [Language]
     */
    fun getDefaultConfiguration(): List<StatusBarButtonConfig> {
        return listOf(
            StatusBarButtonConfig(
                id = StatusBarButtonId.Clipboard,
                position = StatusBarButtonPosition.LEFT,
                enabled = true,
                order = 0
            ),
            StatusBarButtonConfig(
                id = StatusBarButtonId.Emoji,
                position = StatusBarButtonPosition.RIGHT,
                enabled = true,
                order = 0
            ),
            StatusBarButtonConfig(
                id = StatusBarButtonId.Language,
                position = StatusBarButtonPosition.RIGHT,
                enabled = true,
                order = 1
            )
        )
    }
    
    /**
     * Direct access to the microphone factory for audio level updates.
     * This is needed because audio level updates happen frequently and
     * we want to avoid the overhead of looking up the factory each time.
     */
    fun getMicrophoneFactory(): MicrophoneButtonFactory = microphoneFactory
    
    /**
     * Direct access to the language factory for text updates.
     */
    fun getLanguageFactory(): LanguageButtonFactory = languageFactory
    
    /**
     * Direct access to the clipboard factory for badge updates.
     */
    fun getClipboardFactory(): ClipboardButtonFactory = clipboardFactory
    
    /**
     * Direct access to the emoji factory.
     */
    fun getEmojiFactory(): EmojiButtonFactory = emojiFactory
}
