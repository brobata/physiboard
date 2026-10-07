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
| `per-app-behavior.md` | Enter behavior, Terminal mode (exact typing), WebAPK hosts, per-app lists |
| `dictation.md` | Speech sessions, engines, silence handling, haptics, permissions |
| `trackpad-caret-nav.md` | Screen trackpad, caret badge, nav mode |
| `device-backlight-ring.md` | Keyboard backlight, smart backlight, notification ring, Titan hardware facts |
| `broker-privileged-toolbox.md` | Embedded ADB broker, privileged setup, bloat remover, system tweaks, density |
| `settings-catalog.md` | Every preference key, defaults, migration, baseline, backup |
| `expansion-clipboard-pickers-launcher.md` | Text expansion, clipboard history, emoji and Unicode pickers, launcher shortcuts, quick launcher, commands |
| `app-shell.md` | Onboarding, tutorial, diagnostics, update checker, about, notifications, build and release configuration |
| `test-corpus.md` | Key sequences recorded on the Titan with expected text |

## Amendments

Places where a fix changed behaviour and the spec was updated to match, rather than the other way
around.

| Document | Section(s) | What changed | Commit |
|---|---|---|---|
| `keys-and-modifiers.md` | 2, 5.2, 5.3, 5.6, 21, 22 | The Shift/Alt layer latch dropped its own release-to-release timer; it now follows the same down-side double tap that sets caps lock or the Alt latch. Shift also gained Ctrl's "other key during the hold clears the one-shot" rule. | fcec53b |
| `keys-and-modifiers.md` | 5.4, 5.5, 22 | The Alt+Ctrl dictation chord is removed; Alt or Ctrl down with the other's meta bit is now an ordinary press. | fcec53b |
| `keys-and-modifiers.md` | 7.5 | Ctrl+Space only consumes and switches when another input subtype exists; with one layout, Fn+Space no longer vanishes. | fcec53b |
| `autocorrect-suggestions.md` | 6.1, 7.2, 9, 10 | Primary case repair and the automatic-correction decision's "exact primary case" fact now consult the personal and default word stores and every loaded dictionary, not only the primary list. | fcec53b |
| `layers-sym-alt.md` | 5.4, 5.7 | A long press on an Emoji page grid key whose emoji takes a skin tone opens the skin-tone chooser instead of the customisation screen; a held page key whose emoji takes a tone no longer repeats its letter after the emoji. | 76a37bc |
| `autocorrect-suggestions.md` | 18, 19, 20 | New section 18, the system spell checker; Keep/Drop and Provenance renumbered to 19 and 20. | d4cc06c |
| `dictionaries-languages.md` | 4.1 | Dictionaries, tables and user words are held once per process and shared with the spell checker; the change broadcasts are handled even with no keyboard session running. | d4cc06c |
| `layers-sym-alt.md` | 1, 4.1, 4.3, 4.5, 5.2, 5.10, 12, 14 | The GIF page (page 6, `gif` in `sym_pages_config`, off by default) and the Sym page chooser on a Sym double tap (`sym_double_tap_chooser`); the status-bar direct opens are recorded as 2.x only, and the Emoji-page eviction rule of 4.2 no longer applies. | 139b647e, 7f40c852 |
| `layers-sym-alt.md` | 1, 4.1, 4.6, 5.3, 5.4, 5.10, 7.1, 7.2, 7.4, 8, 12, 14, 15 | 3.0's own accents: a built-in variation table ordered by the keyboard language, the user's lists in `custom_variations` (Customize Variations), the accent chooser that picks only while the letter is held or after Alt (`long_press_variation_chooser`), and the Long press screen for the mode and hold time; the composing-region swap is replaced by delete-and-commit with the caret check kept, and no long press arms in an email field. The user's own Sym pages 7 to 9 (`sym_custom_pages`, `custom1Enabled` to `custom3Enabled`), opened from the cycle and from the chooser on M, N and B; a Sym chord draws from the first switched-on key layer, the user's own included. | 313977bd, 05fc3928, f1fa175b |
| `keys-and-modifiers.md` | 8.2, 8.3, 18, 22 | The long press has one 500 ms default and a screen; Accent mode does not arm in an email field and opens the accent chooser; two new settings rows. | 313977bd, 05fc3928 |
| `dictation.md` | 1 to 9, 13 to 17 | Rewritten for the session model that runs until stopped: Fn again, any key, the silence limit or the cap end it; the engine's own endings are re-listened without a cap; segmented sessions are asked for correctly (the extra's value is a String naming the silence extra); prefer-offline, session-long audio focus, the start cue at the first audio report, the status bar icon; `dictation_end_silence_ms` and `dictation_continuous_session` replaced by `dictation_stop_after_silence_ms`, `dictation_stop_on_typing`, `dictation_prefer_offline`, `dictation_pause_media`. Device facts D14 to D21 from the 2026-10-07 log. | this change |
| `settings-catalog.md` | 2.8, 9.2, 12 | The four dictation rows above; the two removed keys and the importer dropping the 2.x pause. | this change |
| `app-shell.md` | 31.2 | Private mode keeps dictation on the phone. | this change |
