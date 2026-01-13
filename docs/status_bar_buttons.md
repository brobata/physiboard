# Adding New Status Bar Buttons

This guide explains how to add new buttons to the IME status bar. The status bar uses a modular factory pattern that makes it easy to add new button types.

## Architecture Overview

The status bar button system consists of:

- **`StatusBarButtonId`**: Sealed class defining unique button identifiers
- **`StatusBarButtonFactory`**: Interface for creating and managing button views
- **`StatusBarButtonRegistry`**: Central registry managing all button factories
- **`StatusBarCallbacks`**: Container for all button interaction callbacks
- **`ButtonState`**: Sealed class for button-specific state updates
- **`StatusBarButtonHost`**: Shared wrapper that applies badge/flash overlays and routes state updates consistently
- **`StatusBarButtonStyles`**: Centralized colors + corner radius for status bar buttons
- **`StatusBarButtonsScreen`**: Compose UI for button customization

## Step-by-Step Guide

### 1. Define the Button ID

Add a new button identifier in `StatusBarButtonConfig.kt`:

```kotlin
sealed class StatusBarButtonId(val key: String) {
    object Clipboard : StatusBarButtonId("clipboard")
    object Microphone : StatusBarButtonId("microphone")
    object Language : StatusBarButtonId("language")
    object Emoji : StatusBarButtonId("emoji")
    object YourNewButton : StatusBarButtonId("your_new_button")  // Add your button
    data class Custom(val customKey: String) : StatusBarButtonId(customKey)
    // ...
}
```

### 2. Create the Button Factory

Create a new factory class implementing `StatusBarButtonFactory` in the `statusbar/button/` package:

```kotlin
package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating your custom button.
 */
class YourButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        
        // Wire up click listener using the appropriate callback
        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            // Add your custom callback here (see step 3)
        }
        
        return ButtonCreationResult(view = button)
    }
    
    override fun update(view: View, state: ButtonState) {
        // If your button needs state updates, handle them here
        // You may need to add a new ButtonState subclass
    }
    
    override fun cleanup(view: View) {
        // Clean up any resources (animations, handlers, etc.)
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_your_icon_24)  // Your icon resource
            setColorFilter(Color.WHITE)
            background = StatusBarButtonStyles.createButtonDrawable()
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
}
```

**Notes:**
- Use `ImageView` for icon-based buttons, or `TextView` for text-based buttons (see `LanguageButtonFactory` for an example)
- The button size is provided as a parameter - do not set layout params in the factory
- Return auxiliary views (badge, flash overlay) in `ButtonCreationResult` if needed (see `ClipboardButtonFactory` for an example)
- Use `callbacks.onHapticFeedback?.invoke()` for haptic feedback on click

### 3. Add Callback (if needed)

If your button needs a specific callback, add it to `StatusBarCallbacks` in `StatusBarButtonConfig.kt`:

```kotlin
data class StatusBarCallbacks(
    val onClipboardRequested: (() -> Unit)? = null,
    val onSpeechRecognitionRequested: (() -> Unit)? = null,
    val onEmojiPickerRequested: (() -> Unit)? = null,
    val onLanguageSwitchRequested: (() -> Unit)? = null,
    val onOpenSettings: (() -> Unit)? = null,
    val onHapticFeedback: (() -> Unit)? = null,
    val onYourButtonAction: (() -> Unit)? = null  // Add your callback
)
```

Then wire it up in `VariationBarView.kt` where `statusBarCallbacks` is created (around line 459):

```kotlin
val statusBarCallbacks = StatusBarCallbacks(
    // ... existing callbacks ...
    onYourButtonAction = { /* your action */ }
)
```

### 4. Register the Factory

Register your factory in `StatusBarButtonRegistry.kt`:

```kotlin
class StatusBarButtonRegistry {
    
    private val factories = mutableMapOf<StatusBarButtonId, StatusBarButtonFactory>()
    
    private val yourButtonFactory = YourButtonFactory()  // Add factory instance
    
    init {
        factories[StatusBarButtonId.Clipboard] = clipboardFactory
        factories[StatusBarButtonId.Microphone] = microphoneFactory
        factories[StatusBarButtonId.Language] = languageFactory
        factories[StatusBarButtonId.Emoji] = emojiFactory
        factories[StatusBarButtonId.YourNewButton] = yourButtonFactory  // Register
    }
    
    // Optionally add direct access method if needed for frequent updates
    fun getYourButtonFactory(): YourButtonFactory = yourButtonFactory
}
```

Also update `buttonIdFromString()` method to support your button ID (around line 178):

```kotlin
private fun buttonIdFromString(buttonString: String): StatusBarButtonId? {
    return when (buttonString) {
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> StatusBarButtonId.Clipboard
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> StatusBarButtonId.Microphone
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> StatusBarButtonId.Emoji
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> StatusBarButtonId.Language
        SettingsManager.STATUS_BAR_BUTTON_YOUR_NEW_BUTTON -> StatusBarButtonId.YourNewButton  // Add
        SettingsManager.STATUS_BAR_BUTTON_NONE -> null
        else -> null
    }
}
```

### 5. Add Settings Manager Support

Add constants and methods in `SettingsManager.kt`:

**Add button string constant** (around line 62):

```kotlin
// Public constants for button IDs
const val STATUS_BAR_BUTTON_NONE = "none"
const val STATUS_BAR_BUTTON_CLIPBOARD = "clipboard"
const val STATUS_BAR_BUTTON_MICROPHONE = "microphone"
const val STATUS_BAR_BUTTON_EMOJI = "emoji"
const val STATUS_BAR_BUTTON_LANGUAGE = "language"
const val STATUS_BAR_BUTTON_YOUR_NEW_BUTTON = "your_new_button"  // Add
```

**Add to available buttons list** (around line 1798):

```kotlin
fun getAvailableStatusBarButtons(): List<String> {
    return listOf(
        STATUS_BAR_BUTTON_NONE,
        STATUS_BAR_BUTTON_CLIPBOARD,
        STATUS_BAR_BUTTON_EMOJI,
        STATUS_BAR_BUTTON_MICROPHONE,
        STATUS_BAR_BUTTON_LANGUAGE,
        STATUS_BAR_BUTTON_YOUR_NEW_BUTTON  // Add
    )
}
```

### 6. Add UI Support

Update `StatusBarButtonsScreen.kt` to support your button in the customization UI:

**Add to button name mapping** (around line 403):

```kotlin
private fun getButtonName(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji)
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> stringResource(R.string.status_bar_button_language)
        SettingsManager.STATUS_BAR_BUTTON_YOUR_NEW_BUTTON -> stringResource(R.string.status_bar_button_your_new_button)  // Add
        else -> buttonId
    }
}
```

**Add to button description mapping** (around line 415):

```kotlin
private fun getButtonDescription(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none_description)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard_description)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone_description)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji_description)
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> stringResource(R.string.status_bar_button_language_description)
        SettingsManager.STATUS_BAR_BUTTON_YOUR_NEW_BUTTON -> stringResource(R.string.status_bar_button_your_new_button_description)  // Add
        else -> ""
    }
}
```

**Add icon resource mapping** (around line 428):

```kotlin
private fun getButtonIconRes(buttonId: String): Int {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> R.drawable.ic_content_paste_24
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> R.drawable.ic_baseline_mic_24
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> R.drawable.ic_emoji_emotions_24
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> R.drawable.ic_globe_24
        SettingsManager.STATUS_BAR_BUTTON_YOUR_NEW_BUTTON -> R.drawable.ic_your_icon_24  // Add
        else -> R.drawable.ic_settings_24 // Fallback
    }
}
```

### 7. Add String Resources

Add localized strings in `values/strings.xml` and `values-it/strings.xml`:

```xml
<!-- In values/strings.xml -->
<string name="status_bar_button_your_new_button">Your Button</string>
<string name="status_bar_button_your_new_button_description">Description of your button</string>

<!-- In values-it/strings.xml -->
<string name="status_bar_button_your_new_button">Il Tuo Bottone</string>
<string name="status_bar_button_your_new_button_description">Descrizione del tuo bottone</string>
```

### 8. Create Icon Drawable

Create a vector drawable for your button icon in `res/drawable/`. See existing icons like `ic_content_paste_24.xml`, `ic_emoji_emotions_24.xml` as examples.

The icon should be:
- 24dp x 24dp viewport
- White color (will be filtered white in code)
- Material Design style

### 9. Add State Support (if needed)

If your button needs dynamic state updates, add a new state class in `StatusBarButtonConfig.kt`:

```kotlin
sealed class ButtonState {
    data class ClipboardState(val itemCount: Int) : ButtonState()
    data class MicrophoneState(val isActive: Boolean, val audioLevelDb: Float = -10f) : ButtonState()
    data class LanguageState(val languageCode: String) : ButtonState()
    data class YourButtonState(val someProperty: String) : ButtonState()  // Add
}
```

Then update the button using the shared host so all containers stay consistent (status bar + hamburger menu).
Add a method in `StatusBarButtonHost` for your state and call it from the controller that owns the UI.

```kotlin
// Example extension inside StatusBarButtonHost
fun updateYourButtonState(value: String) {
    updateButton(StatusBarButtonId.YourNewButton, ButtonState.YourButtonState(value))
}
```

## Examples

### Simple Button (No State)

See `EmojiButtonFactory.kt` for a simple button that only responds to clicks.

### Button with State Updates

See `ClipboardButtonFactory.kt` for a button with:
- Badge overlay
- Flash animation
- State updates (badge count)

See `MicrophoneButtonFactory.kt` for a button with:
- Visual feedback (active/inactive state)
- Audio level visualization

### Text-Based Button

See `LanguageButtonFactory.kt` for a button that displays text instead of an icon.

## Testing Checklist

- [ ] Button appears in the customization screen
- [ ] Button can be selected and placed in any slot
- [ ] Button appears correctly in the IME status bar
- [ ] Button click triggers the expected action
- [ ] Button has correct icon/visual appearance
- [ ] Button respects layout constraints (same size as other buttons)
- [ ] Button state updates work (if applicable)
- [ ] Button cleanup works correctly (if applicable)
- [ ] Localized strings are displayed correctly

## Notes

- All buttons must have the same fixed width (enforced by `VariationBarView`)
- Buttons use consistent margins for spacing
- The variation row automatically adjusts its width based on enabled buttons
- Button factories should not set layout params - `VariationBarView` handles this
- Use `StatusBarButtonHost` to wrap badge/flash overlays and keep state updates aligned across containers
- Variation buttons (suggestions) use `VariationButtonStyles` inside `VariationBarView`
- Use `StatusBarCallbacks` to access IME functionality rather than direct dependencies
