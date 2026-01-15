# Hamburger Menu Overlay (Status Bar)

This document explains how to configure the status bar hamburger menu overlay, change the number of buttons, reorder them, and adjust implementation details.

## Overview

The hamburger menu overlay is an in-status-bar view that replaces the variations row while keeping the LED strip visible. It is opened by a configurable status bar button and shows a fixed row of quick-action buttons.

Key goals:
- Same height as the status bar row (no extra vertical space).
- Does not cover the LED strip (LEDs remain visible).
- Button actions do not auto-close the menu; Close and outside tap are the explicit exit paths.
- No open/close animations (instant visibility toggle).

## Architecture Summary

- `HamburgerButtonFactory` creates the configurable status bar button (hamburger icon) and triggers `onHamburgerMenuRequested`.
- `StatusBarController` owns a `HamburgerMenuView`, attaches it to the `VariationBarView` wrapper, and toggles visibility.
- `HamburgerMenuView` builds the overlay row with a Close button and a fixed list of button IDs created via `StatusBarButtonRegistry`.
- `StatusBarButtonHost` wraps button results (badge/flash overlays) and keeps state updates consistent across containers.
- `StatusBarButtonStyles` centralizes status bar button colors and corner radius.
- The overlay lives inside the variation bar wrapper, so the LED strip (which is a separate view below) is not covered.

## Entry Points and Key Files

- Button factory: `app/src/main/java/it/palsoftware/pastiera/inputmethod/statusbar/button/HamburgerButtonFactory.kt`
- Overlay view: `app/src/main/java/it/palsoftware/pastiera/inputmethod/ui/HamburgerMenuView.kt`
- Wiring + callbacks: `app/src/main/java/it/palsoftware/pastiera/inputmethod/StatusBarController.kt`
- Button registry/IDs: `app/src/main/java/it/palsoftware/pastiera/inputmethod/statusbar/StatusBarButtonRegistry.kt`
- Shared button host: `app/src/main/java/it/palsoftware/pastiera/inputmethod/statusbar/StatusBarButtonHost.kt`
- Button styles: `app/src/main/java/it/palsoftware/pastiera/inputmethod/statusbar/StatusBarButtonStyles.kt`
- Settings list: `app/src/main/java/it/palsoftware/pastiera/SettingsManager.kt`
- Settings UI: `app/src/main/java/it/palsoftware/pastiera/StatusBarButtonsScreen.kt`
- Icons: `app/src/main/res/drawable/ic_menu_24.xml`, `app/src/main/res/drawable/ic_close_24.xml`, `app/src/main/res/drawable/ic_minimal_ui_24.xml`

## How to Change the Available Buttons

The quick-action buttons inside the overlay are defined in `HamburgerMenuView`.

Current list:
```
private val menuButtonIds = listOf(
    StatusBarButtonId.Symbols,
    StatusBarButtonId.Emoji,
    StatusBarButtonId.Microphone,
    StatusBarButtonId.Clipboard,
    StatusBarButtonId.Language,
    StatusBarButtonId.Settings,
    StatusBarButtonId.MinimalUi
)
```

To change the available buttons:
1. Edit `menuButtonIds` in `HamburgerMenuView`.
2. Use button IDs already registered in `StatusBarButtonRegistry`.
3. If you add a new ID, make sure it is registered and has a factory.

Notes:
- The Close button is always inserted at the far left and is not part of `menuButtonIds`.
- Each button uses the same factory as the status bar buttons, so styling and behavior stay consistent.
- The overlay uses `StatusBarButtonHost` to ensure badges/flash overlays and state updates behave like the standard status bar.

## How to Reorder Buttons

Reorder the `menuButtonIds` list in `HamburgerMenuView`. Buttons are rendered in the exact order listed, from left to right, after the Close button.

Example (custom order):
```
private val menuButtonIds = listOf(
    StatusBarButtonId.Settings,
    StatusBarButtonId.Symbols,
    StatusBarButtonId.Emoji,
    StatusBarButtonId.Microphone,
    StatusBarButtonId.Clipboard,
    StatusBarButtonId.Language
)
```

## How to Change the Number of Buttons

The number of slots equals `menuButtonIds.size + 1` (the +1 is the Close button).

If you increase or decrease the count:
- Update `menuButtonIds` accordingly.
- Button width is computed dynamically as `availableWidth / totalButtons`, so layout will adapt automatically.
- The overlay height remains the same as the status bar row, so ensure icons remain legible.

## Close Button Behavior

The Close button is created directly in `HamburgerMenuView` using the same visual style as other status bar buttons.

Its behavior:
- Triggers haptic feedback.
- Closes the overlay without executing any action.

Close button icon: `ic_close_24.xml`.

## Haptic Feedback

All button clicks trigger haptic feedback via `StatusBarCallbacks.onHapticFeedback`. The overlay forwards that callback to each button factory and also uses it for the Close button.

## Click-Outside to Close

The overlay root view is clickable and closes the menu on any click outside the buttons. This is handled in `HamburgerMenuView.show()` by setting a click listener on the root frame.

## Layout and Sizing Rules

- The overlay is attached to the `VariationBarView` wrapper (FrameLayout) so it shares the same height as the status bar row.
- The LED strip remains visible because it is placed below the variation wrapper in `StatusBarController`.
- Button width is computed at runtime based on total slots and available width.
- Vertical padding is applied to keep the row consistent with the existing status bar button height.

## Adding a New Button Type

1. Add a new `StatusBarButtonId` in `StatusBarButtonConfig.kt`.
2. Implement a new `StatusBarButtonFactory` in `inputmethod/statusbar/button/`.
3. Register the factory in `StatusBarButtonRegistry.kt`.
4. Add a constant in `SettingsManager.kt` and include it in `getAvailableStatusBarButtons()`.
5. Add name/description/icon entries in `StatusBarButtonsScreen.kt` and strings.
6. Optionally add it to the hamburger menu `menuButtonIds`.

## Technical Notes

- The overlay does not use animations; visibility is toggled directly.
- The overlay is automatically hidden when SYM/clipboard overlays are active or when minimal UI is forced.
- The overlay rebuilds its row on show to pick up any updated button factories or callbacks.
- Overlay buttons do not auto-close the menu; actions like SYM/clipboard/emoji will hide it indirectly by switching the status bar state.
- The minimal UI (pastierina) button toggles the same forced-minimal state used by the system "hide on-screen keyboard" mode.

## Quick Checklist After Changes

- Menu opens from the hamburger button in any slot (LEFT/RIGHT1/RIGHT2).
- LEDs remain visible with the menu open.
- Close button works and does not trigger any action.
- All quick-action buttons execute their actions without auto-closing.
- The menu closes on outside tap.
