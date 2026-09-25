# Changelog

This file starts with 3.0, a clean-room rewrite; it does not carry the 2.x line's history. See
`legacy-2.x` for that.

## 3.0.0 (unreleased)

A from-scratch physical-keyboard IME for the Unihertz Titan 2 Elite, built from a written
behavioral specification rather than the old code.

- Hardware key pipeline: Fn, Sym, Alt, Ctrl and Shift, including sticky and locked modifiers.
- Sym and Alt layers with pages, and a nav mode for cursor and selection movement from the
  keyboard.
- Autocorrect and word suggestions, backed by 19 bundled dictionaries.
- The candidates strip doubling as a status bar, with configurable buttons and a per-app dip.
- Per-app Enter behavior, exact (uncorrected) typing, and WebAPK host expansion.
- Text expansion (snippets), clipboard history, and emoji and Unicode pickers as overlays.
- Dictation with its own silence timer and re-listen handling.
- The screen trackpad and caret badge for cursor and selection control on the touchscreen.
- Keyboard backlight, a smart backlight mode, and a notification ring, driven through an
  embedded ADB broker paired over Wireless Debugging.
- The T2E toolbox: a bloat remover, system tweaks and display density controls for the Titan.
- Backup and restore, an update checker that reads GitHub releases, diagnostics, and first-run
  onboarding with a one-shot importer from a 2.x install.

Not carried forward from the line this succeeds: the AOSP-derived soft keyboard view and its
theming system, custom input styles, the multi-device layout tree, and anything that existed only
for a device this project does not ship to.
