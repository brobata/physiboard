package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.view.View
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks

/**
 * Factory interface for creating status bar buttons.
 * 
 * Each button type (clipboard, microphone, language, emoji) implements this interface
 * to provide a standardized way of creating and updating buttons.
 * 
 * This allows the VariationBarView to work with buttons generically,
 * making it easy to add new button types without modifying VariationBarView.
 * 
 * To add a new button type:
 * 1. Create a new StatusBarButtonId in StatusBarButtonConfig.kt
 * 2. Implement this interface with your button's logic
 * 3. Register the factory in StatusBarButtonRegistry
 * 4. Add the button ID to SettingsManager
 * 5. Add UI for it in StatusBarButtonsScreen
 */
interface StatusBarButtonFactory {
    
    /**
     * Creates a new button view.
     * 
     * The factory should extract the callbacks it needs from StatusBarCallbacks
     * and wire them up to the button's click handlers.
     * 
     * @param context Android context for creating views
     * @param size The size (width and height) for the button in pixels
     * @param callbacks All available callbacks - use only what this button needs
     * @return ButtonCreationResult containing the main view and any auxiliary views
     */
    fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult
    
    /**
     * Updates an existing button with new state.
     * 
     * @param view The button view to update (from create())
     * @param state The new state to apply
     */
    fun update(view: View, state: ButtonState)
    
    /**
     * Called when the button is being removed from the view hierarchy.
     * Use this to clean up any resources (animations, handlers, etc.).
     * 
     * @param view The button view being removed
     */
    fun cleanup(view: View) {
        // Default implementation does nothing
    }
}
