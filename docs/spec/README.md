# PhysiBoard behavioral specification

This directory is the only bridge between the 2.x codebase (a GPLv3 derivative of Pastiera) and
the 3.0 clean-room rewrite. See `docs/plans/rebuild-from-scratch.md` for the decision and the
build order.

## Rules for every document here

1. **Behavior, not implementation.** What the user does, what the keyboard does, in what order,
   with what timing. Never the structure of the code that does it.
2. **No source code and no source identifiers.** No snippets, no pseudocode, no class, method,
   or variable names. The only identifiers allowed are interface contracts: preference key
   strings, asset file names, Intent actions, broadcast names, system settings keys, sysfs
   paths, scancodes and keycodes, file paths on disk.
3. **Every number is written down.** Timings, thresholds, limits, defaults, sizes.
4. **Every setting is a row**: preference key, type, default, what it changes, which screen it
   lives on, user-facing label.
5. **Every device fact is numbered** (D1, D2, ...) with the evidence behind it.
6. **Test cases are concrete**: input sequence, expected outcome, in a form a JVM test can encode.
7. **Keep / Drop for 3.0** closes each document. 3.0 is Titan-only and has no on-screen keyboard.
8. **Provenance** is a list of paths read, at the very end, and nowhere else.

During the build phase the legacy branch is checked out nowhere on the build machine. A gap in
a spec is filled from device evidence and added here, never from reading old source.

## Documents

| File | Covers |
|---|---|
| `keys-and-modifiers.md` | Key events, modifier state, Fn/Ctrl/Sym detection, hold and repeat, bounce and accidental-press filters, multi-tap |
| `layers-sym-alt.md` | Sym pages, Alt layer, variations, key mappings, layouts |
| `text-input.md` | Composition, autospace, deferred punctuation, autocapitalization, selection helpers |
| `autocorrect-suggestions.md` | Suggestion engine, autocorrect decision, confidence, user words, substitutions, eval harness |
| `dictionaries-languages.md` | Dictionary formats, hosting, download and install, language switching |
| `status-bar.md` | The candidates strip as status bar, buttons, visibility, insets, the per-app dip |
| `per-app-behavior.md` | Enter behavior, exact typing, WebAPK hosts, per-app lists |
| `dictation.md` | Speech sessions, engines, silence handling, haptics, permissions |
| `trackpad-caret-nav.md` | Screen trackpad, caret badge, nav mode |
| `device-backlight-ring.md` | Keyboard backlight, smart backlight, notification ring, Titan hardware facts |
| `broker-privileged-toolbox.md` | Embedded ADB broker, privileged setup, bloat remover, system tweaks, density |
| `settings-catalog.md` | Every preference key, defaults, migration, baseline, backup |
| `expansion-clipboard-pickers-launcher.md` | Text expansion, clipboard history, emoji and Unicode pickers, launcher shortcuts, quick launcher, commands |
| `app-shell.md` | Onboarding, tutorial, diagnostics, update checker, about, notifications, build and release configuration |
| `test-corpus.md` | Key sequences recorded on the Titan with expected text |
