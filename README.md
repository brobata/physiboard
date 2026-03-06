
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)
# Pastiera

Input method for physical keyboards android devices (e.g. Unihertz Titan 2), designed to make typing faster through shortcuts, gestures, and customization.

## Quick overview
- Compact status bar with LED indicators for Shift/SYM/Ctrl/Alt, variants/suggestions bar, and swipe-pad gestures to move the cursor.
- Multiple layouts (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, etc.) fully configurable; JSON import/export directly from the app. A web frontend for editing layouts is available at https://pastierakeyedit.vercel.app/
- SYM pages usable via touch or physical keys (emoji + symbols), reorderable/disableable, with an integrated layout editor.
- Clipboard support with multiple entries and pinnable items.
- Support for dictionary based suggestions/Autocorrections + swipe gestures to accept a suggestion (requires Shizuku)
- Full backup/restore (settings, layouts, variations, dictionaries), UI translated into multiple languages, and built-in GitHub update checks.

## Typing and modifiers
- Long press on a key can input Alt+key or Shift+Key (uppercase) timing configurable.
- Shift/Ctrl/Alt in one-shot or lock mode (double tap), option to clear Alt on space.
- Current behavior note: `Ctrl` used as a physically held shortcut modifier (e.g. hold `Ctrl` + `A`) intentionally follows the app shortcut path and is not the same flow as Nav Mode (`Ctrl` double-tap latch outside text fields). Nav Mode remains a separate implementation/state.
- Multi-tap support for keys with layout-defined variants (e.g. Cyrillic)
- Standard shortcuts: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, Ctrl+E/D/S/F or I/J/K/L for arrows, Ctrl+W/R for selection, Ctrl+T for Tab, Ctrl+Y/H for Page Up/Down, Ctrl+Q for Esc (all customizable in the Customize Nav screen).

## QOL features
- **Nav Mode**: double tap Ctrl outside text fields to use ESDF or IJKL as arrows, and many more useful mappings (everything is customizable in Customize Nav Mode settings)
- **Variations bar as swipe pad**: drag to move the cursor, with adjustable threshold.
- **Launcher shortcuts**: in the launcher, press a letter to open/assign an app.
- **Power shortcuts**: press SYM (5s timeout) then a letter to use the same shortcuts anywhere, even outside the launcher.
- Change language with a tap on language code in the status bar, longpress to enter pastiera settings

## Keyboard layouts
- Included layouts: qwerty, azerty, qwertz, greek, arabic, russian/armenian phonetic translit, plus dedicated Alt maps for Titan 2.
- Layout switching: select from the enabled layouts list (configurable).
- Multi-tap support and mapping for complex characters.
- JSON import/export directly from the app, with visual preview and list management (enable/disable, delete).
- Layout maps are stored in `files/keyboard_layouts` and can also be edited manually. A web frontend for editing layouts is available at https://pastierakeyedit.vercel.app/

## Symbols, emoji, and variations
- Two touch-based SYM pages (emoji + symbols): reorderable/enableable, auto-close after input, customizable keycaps.
- In-app SYM editor with emoji grid and Unicode picker.
- Variations bar above the keyboard: shows accents/variants of the last typed letter or static sets (utility/email) when needed.
- Dedicated variations editor to replace/add variants via JSON or Unicode picker; optional static bar.

## Suggestions and autocorrection

- Experimental support for dictionary based autocorrection/suggestions
- User dictionary with search and edit abilities.
- Per-language auto substituion editor, quick search, and a global “Pastiera Recipes” set shared across all languages.
- Change language/keymap with a tap on the language code button or ctrl+space



## Comfort and extra input
- Double space → period + space + uppercase; 
- Swipe left on the keyboard to delete a word (Titan 2).
- Optional Alt+Ctrl shortcut to start Google Voice Typing; microphone always available on the variants bar.
- Compact status bar to minimize vertical space. With on-screen keyboard disabled from the IME selector, it uses even less space (aka Pastierina mode)
- Translated UI (it/en/de/es/fr/pl/ru/hy) and onboarding tutorial.

## Backup, updates, and data
- UI-based backup/restore in ZIP format: includes preferences, custom layouts, variations, SYM/Ctrl maps, and user dictionaries.
- Restore merges saved variations with defaults to avoid losing newly added keys.
- Built-in GitHub update check when opening settings (with option to ignore a release).
- Customizable files in `files/`: `variations.json`, `ctrl_key_mappings.json`, `sym_key_mappings*.json`, `keyboard_layouts/*.json`, user dictionaries.
- Android autobackup function 

## Installation
1. Build the APK or install an existing build.
2. Android Settings → System → Languages & input → Virtual keyboard → Manage keyboards.
3. Enable “Pastiera” and select it from the input selector when typing.

## Requirements
- Android 10 (API 29) or higher.
- Device with a physical keyboard (profiled on Unihertz Titan 2, adaptable via JSON).

## Development / Tests
- Run core + routing + service modifier regression tests:
  - `./gradlew :app:testDebugUnitTest --tests it.palsoftware.pastiera.core.ModifierStateControllerTest --tests it.palsoftware.pastiera.inputmethod.InputEventRouterModifierE2ETest --tests it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodServiceDeviceBehaviorTest`
- Service-level (device-near) modifier behavior regressions:
  - `./gradlew :app:testDebugUnitTest --tests it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodServiceDeviceBehaviorTest`
- Router-level input pipeline modifier/SYM tests:
  - `./gradlew :app:testDebugUnitTest --tests it.palsoftware.pastiera.inputmethod.InputEventRouterModifierE2ETest`
- Core modifier state machine tests:
  - `./gradlew :app:testDebugUnitTest --tests it.palsoftware.pastiera.core.ModifierStateControllerTest`
- Build debug APK:
  - `./gradlew :app:assembleDebug`

## Manual release CI
- The repository includes a manually triggered GitHub Actions workflow at `.github/workflows/release.yml`.
- Required GitHub Actions secrets:
  - `PASTIERA_KEYSTORE_B64`
  - `PASTIERA_KEYSTORE_PASSWORD`
  - `PASTIERA_KEY_ALIAS`
  - `PASTIERA_KEY_PASSWORD`
- The workflow:
  - runs stable flavor unit tests
  - builds a signed stable release APK
  - verifies APK signing
  - uploads the APK and its SHA256 checksum as artifacts
  - optionally creates a GitHub Release
- Release versioning is injected via Gradle properties:
  - `-PPASTIERA_VERSION_CODE=...`
  - `-PPASTIERA_VERSION_NAME=...`
- Local release builds can use the same mechanism:
  - `./gradlew :app:assembleStableRelease -PPASTIERA_VERSION_CODE=85 -PPASTIERA_VERSION_NAME=0.85`

## Manual nightly CI
- The repository includes a manually triggered nightly workflow at `.github/workflows/debug.yml`.
- Required GitHub Actions secrets:
  - `PASTIERA_NIGHTLY_KEYSTORE_B64`
  - `PASTIERA_NIGHTLY_KEYSTORE_PASSWORD`
  - `PASTIERA_NIGHTLY_KEY_ALIAS`
  - `PASTIERA_NIGHTLY_KEY_PASSWORD`
- The workflow:
  - runs nightly flavor release-unit tests
  - builds a nightly release APK signed with the shared nightly key
  - computes a SHA256 checksum
  - uploads the APK and checksum as workflow artifacts
  - automatically turns a base version like `0.85` into a unique nightly version like `0.85-nightly.20260306.195412`
  - optionally publishes a GitHub pre-release under the `nightly/v*` tag scheme using that full nightly version
- The nightly flavor uses a separate application ID so it installs alongside the stable release.
- The nightly flavor is signed with a shared nightly key so local and CI nightly builds remain upgrade-compatible.
- Nightly pre-release disclaimer text is maintained in `.github/release-templates/debug-prerelease.md`.
- The same versioning can be generated locally:
  - `./scripts/nightly-version.sh 0.85`
  - `./gradlew :app:assembleNightlyRelease -PPASTIERA_VERSION_NAME=0.85 -PPASTIERA_NIGHTLY_VERSION_SUFFIX=-nightly.$(./scripts/nightly-version.sh 0.85 | awk -F= '/^timestamp=/{print $2}')`
- Local wrappers are available:
  - `./scripts/build-nightly.sh 0.85`
  - `./scripts/build-nightly.sh 0.85 --publish`
  - `./scripts/build-release.sh 0.85 85`
  - `./scripts/build-release.sh 0.85 85 --publish`
