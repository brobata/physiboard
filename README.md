# PhysiBoard

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android-3ddc84.svg)
![Version](https://img.shields.io/badge/version-1.0.6-f59e0b.svg)

Input method for physical-keyboard Android devices (e.g. Unihertz Titan 2 Elite), designed to make typing faster through shortcuts, gestures, dictation, and customization.

PhysiBoard is a **fork of [Pastiera](https://github.com/palsoftware/pastiera)** by Andrea Palumbo (PalSoftware) and its contributors — the keyboard engine is their work. This fork adds a physical-keyboard-focused feature set (hold-Fn dictation, an always-on-in-the-dark backlight, a terminal-style UI) on top of it.

> If you'd like to support the people who built the engine PhysiBoard runs on, back the original project on **[OpenCollective](https://opencollective.com/pastiera)**. This fork doesn't take donations of its own.

<p align="center">
  <img src="docs/screenshots/home.png" width="300" alt="PhysiBoard home screen">
</p>

## What this fork adds

Full change notes (per GPLv3 §5(a)) are in [PHYSIBOARD_CHANGES.md](PHYSIBOARD_CHANGES.md).

- **Hold Fn to dictate** — hold the Fn key in any text field to start voice typing. Matched by hardware scan code so vendor Fn remapping can't break it. Haptic cues, an adjustable end-of-speech pause (default 2.5s), strong start/stop haptic cues, and a real profanity-masking toggle.
- **Screen trackpad** — hold Space and swipe anywhere on the display to move the cursor; hold Shift while swiping to select. The whole screen is the trackpad, it works in every app including terminals, and it needs no Shizuku or root. Trigger key (Space, either Shift, Sym) and activation (hold, double tap, single tap) are configurable.
- **Always-on keyboard backlight** — lifts the vendor's 30-second keyboard-light cap with a persistent device setting that survives reboots, applied in-app over the device's own wireless debugging (no companion app, no root).
- **One pairing sets up everything** — pair Wireless debugging once and PhysiBoard applies every device-level step itself: the backlight setting and the trackpad's "Display over other apps" permission. No trips to system settings.
- **Update checks** — checks GitHub for new releases daily and from the home screen, with a direct APK download.
- **Terminal UI** — slate + amber, JetBrains Mono throughout, and an action-surface home that only surfaces what needs attention.
- **Sensible defaults + two-step onboarding** — first-letter auto-capitalization, hold-Fn dictation, the screen trackpad and the always-on backlight all on, status bar hidden, Slate Dark theme, and a decluttered settings tree with search.

## Quick overview

- Compact status bar with LED indicators for Shift/Alt and a variants/suggestions bar.
- Multiple layouts (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, etc.), fully configurable with JSON import/export.
- SYM pages usable via touch or physical keys (emoji + symbols), reorderable/disableable, with an integrated editor.
- Clipboard history with pinnable items.
- Dictionary-based suggestions and autocorrection, with swipe-to-accept.
- Hold-Fn voice dictation, a screen trackpad, and an always-on keyboard backlight (this fork).
- Full backup/restore (settings, layouts, variations, dictionaries) and built-in update checks.

## Typing and modifiers

- Long-press a key to input Alt+key or Shift+key (timing configurable).
- Shift/Ctrl/Alt in one-shot or lock mode (double-tap), option to clear Alt on space.
- **Fn Layer** (Pastiera's Nav Mode): double-tap Ctrl outside text fields to turn letter keys into arrows and editing/shortcut actions — fully customizable.
- Standard shortcuts: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, arrow/selection/page shortcuts (all customizable).

## Voice dictation *(fork)*

- Hold the Fn key in any text field to start voice typing; release to stop.
- Adjustable end-of-speech pause (0–10s, default 2.5s), profanity masking toggle, and firm start/stop haptic cues.
- Optional Alt+Ctrl trigger; the microphone is also always available on the variants bar.

## Screen trackpad *(fork)*

- Hold Space and swipe anywhere on the screen to move the cursor; a small on-screen pill shows the mode.
- Hold Shift while swiping to extend the selection. Movement is sent as arrow keys, so it works in every app, including terminals.
- A quick Space tap still types a space; pressing another key while Space is down is treated as a normal chord.
- Settings → Screen trackpad: trigger key (Space, Left/Right/Either Shift, Sym), activation (hold / double tap / single tap — the tap modes stay on until you tap again, press Back, or tap the pill), sensitivity, and the hint.
- Needs the "Display over other apps" permission, which the one-time pairing grants for you (see below); otherwise the settings screen opens the system toggle.

## Keyboard backlight *(fork)*

- Lifts the vendor's 30-second keyboard-light cap with a persistent device setting, so it survives reboots — set it up once and forget it.
- Runs entirely in-app by pairing once with Android's own Wireless debugging — no companion app, no root.
- That single pairing also applies every other device-level step PhysiBoard needs (currently the trackpad's overlay permission), and a one-tap **Reset to stock** in Advanced reverts everything.

## Keyboard layouts

- Included layouts: qwerty, azerty, qwertz, greek, arabic, russian/armenian phonetic translit, plus dedicated Alt maps for Titan.
- JSON import/export directly from the app, with visual preview and list management.
- Layout maps live under `files/keyboard_layouts` and can be edited manually.

## Symbols, emoji, and variations

- Touch-based SYM pages (emoji + symbols): reorderable/enableable, auto-close after input, customizable keycaps.
- In-app SYM editor with emoji grid and Unicode picker.
- A variations bar above the keyboard showing accents/variants of the last typed letter.

## Suggestions and autocorrection

- Dictionary-based suggestions and autocorrection with swipe-to-accept.
- User dictionary with search and edit.
- Per-language auto-substitution editor.
- **Exact typing** *(fork)*: pick apps (terminals, editors) where suggestions, autocorrect, auto-capitalization, and double-space-period are all disabled. Installed web apps count too — they type inside their browser, so excluding one excludes that browser.

## Comfort and extra input

- Double-space → period + space + uppercase.
- Screen trackpad (hold Space + swipe) to move the cursor and select text.
- Compact status bar; with the on-screen keyboard disabled it uses even less space (PhysiBar mode).

## Backup, updates, and data

- UI-based backup/restore in ZIP format (preferences, layouts, variations, SYM/Ctrl maps, dictionaries).
- Built-in GitHub update check: daily in the background and from the home screen, with a direct APK download and a "later" dismissal per release.

## Screenshots

<p align="center">
  <img src="docs/screenshots/voice.png" width="220" alt="Hold-Fn voice dictation">
  <img src="docs/screenshots/backlight-setup.png" width="220" alt="Smart backlight setup">
  <img src="docs/screenshots/onboarding.png" width="220" alt="Two-step onboarding">
  <img src="docs/screenshots/themes.png" width="220" alt="Themes">
</p>

## Installation

1. Install the latest `physiboard-x.y.z.apk` from [Releases](../../releases), or build it yourself (below). Once installed, the app announces new releases on its own.
2. Android Settings → System → Languages & input → On-screen keyboard / Manage keyboards.
3. Enable **PhysiBoard**, then select it from the input selector when typing. (The app's two-step onboarding walks you through this.)

## Requirements

- Android 11 (API 30) or higher.
- A device with a physical keyboard (profiled on the Unihertz Titan 2 Elite; adaptable via JSON). The always-on backlight is Titan 2 Elite-specific; the screen trackpad and everything else work on any hardware-keyboard Android phone.

## Development

Standard Gradle Android project. The physical-keyboard release flavor is `physi` (application id `brobata.physiboard`):

```bash
./gradlew :app:assemblePhysiDebug
```

Run the core + routing + service modifier regression tests:

```bash
./gradlew :app:testStableDebugUnitTest \
  --tests it.palsoftware.pastiera.core.ModifierStateControllerTest \
  --tests it.palsoftware.pastiera.inputmethod.InputEventRouterModifierE2ETest
```

Release signing is read from Gradle properties / environment variables (`PASTIERA_KEYSTORE_PATH`, `PASTIERA_KEYSTORE_PASSWORD`, `PASTIERA_KEY_ALIAS`, `PASTIERA_KEY_PASSWORD`) — no keys are committed. See [CONTRIBUTING.md](CONTRIBUTING.md).

The internal source namespace remains `it.palsoftware.pastiera` on purpose: it keeps the fork lineage honest and merges from upstream possible. Only the user-facing application id and branding are PhysiBoard.

## Continuous Integration

Pushes to `main` and pull requests run `.github/workflows/ci.yml` (inherited from Pastiera), which runs the stable and nightly unit-test suites.

## Based on Pastiera

PhysiBoard is a fork of [Pastiera](https://github.com/palsoftware/pastiera). The entire keyboard engine — layouts, modifier handling, suggestions, virtual-keyboard mode, the settings framework — comes from there. Several PhysiBoard features are intended as upstream pull requests back to Pastiera.

## License

PhysiBoard is licensed under the **GNU General Public License, version 3** — the same as Pastiera. See [LICENSE](LICENSE).

Bundled third-party components (AOSP LatinIME, JetBrains Mono, the vendored Shizuku wireless-ADB code and `libadb.so`, Unicode CLDR data, Leipzig frequency data, CC0 sound samples) retain their own licenses — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

PhysiBoard exists because of **Pastiera** by Andrea Palumbo (PalSoftware) and its contributors. In-app credits (Settings → About) carry the full contributor and upstream-license attributions.
