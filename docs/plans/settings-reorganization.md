# Settings reorganization (3.1)

The maintainer asked for the settings to be modernised, for everything that no longer does
anything to go, and for the rest to be grouped so it is easy to find. This is the audit behind
the change and the structure it produced.

## How the audit was done

Every row on every settings screen was traced to the stored setting it writes and then to the
keyboard code that reads that setting. A row is **live** when something the user can see or feel
changes when it is flipped. A row is **dead** when nothing reads it any more, or when the only
thing that reads it draws the suggestion strip, which has been switched off for good since
c61c240 (the keyboard forces the strip's visibility to "never" and no longer reads the stored
choice).

Removing a row from a screen never removes its stored key. The settings codec still reads and
writes every key, so a backup made before this change restores in full and round-trips unchanged.

## What the old structure got wrong

- **Two front doors.** Home had six tiles, and "Settings" behind one of them repeated four of the
  tiles as rows, plus its own search. "Theme" was reachable from Home and from the Keyboard hub.
- **Names that said nothing.** "Extras" held languages, the launcher, text expansion and the
  clipboard. "Smart Features" held capitals, punctuation and Backspace. "T2E Tools" held the
  screen trackpad and key mapping as well as the phone tools.
- **Dead rows.** The Theme screen and its colour editor still offered the strip's buttons, its
  height, its LED colours and its corner sizes, under a note saying they changed nothing.
  Auto-correction still offered "Suggestions while typing", which only fed the strip.
- **Duplicates.** The app's own language was a dropdown on Input Languages and a screen under
  About. "Automatic Layout Mapping" was on Input Languages and on Keyboard Layout.
- **A hazard.** Fn Layer's "Revert to Default" reset every key setting (long press, the user's
  own accent lists, the bounce filter) with no confirmation.
- **A fake control.** Privacy showed "Fields that ask for privacy" as a switch that could never be
  turned off.

## The new structure

Home is the status card, the search field and a category index. Each index row shows an icon, the
category's name and a one-line summary of where its settings stand, computed from the stored
settings so it is never out of date. A category with one screen opens it directly; the others open
a short screen that gathers theirs.

| Index row | Summary example | Opens | What is in it |
|---|---|---|---|
| Typing | Auto-capitals on · double-space period on | Typing | Capitals, double-space full stop, More punctuation (comma space, dashes, quotes, spaces around punctuation), Backspace forward-delete, Space or Enter releases Alt, Text expansion |
| Autocorrect & words | Autocorrect on · mix-ups off | Autocorrect & words | Fix typos, fix mixed-up words, add apostrophes and accents; Personal dictionary, Text replacements (its on/off switch now lives on its own screen), System spell checker; Fine-tuning (correction reach, nearby keys) collapsed |
| Languages & layouts | QWERTY · follows the language | Languages & layouts | Keyboard layout (with "Follow the language"), Languages you type in, Dictionaries, the three switch-language chords |
| Long press & accents | Alt symbol · 500 ms | Long press & accents | unchanged contents |
| Sym pages | Emoji → Symbols | Sym pages | Pages and their order; Emoji (kaomoji, larger picker, skin tone); Fill page (codes, notification access, password manager); Sym key (double-tap chooser, Sym+C/V/X/A, auto-close); Clipboard history |
| Voice | Hold Fn · stops after 2.5 s | Voice | unchanged contents |
| Keys & shortcuts | Fn layer on · trackpad off | Keys & shortcuts | Key mapping; Fn layer, Screen trackpad; Quick launcher |
| Apps | No terminal apps · Enter sends | Apps | Terminal mode, Enter key |
| Look & feel | Slate Light · silent keys | Look & feel | Theme, Sound & haptics, the modifier badge at the cursor and its two colours, App language |
| Privacy | Private mode off · clean links on | Privacy | Private mode, clean links, notification access for one-time codes |
| Titan tools | Backlight, notification ring, screen | Titan tools | Pairing card, smart backlight, remove bloat, screen density, system tweaks, notification ring |
| Backup & restore | | Backup & restore | Back up, restore; Reset device settings to stock, Reset all settings (in the error colour, each confirmed) |
| Help | | Help | Status check, test field, diagnostics, the tutorial, check for updates |
| About | Version 3.1.0 | About | Version, report a problem, support, licences and credits (collapsed) |

The Keyboard and Extras hubs, the old Settings screen and the placeholder screen are deleted. The
search field lives on Home only; every search entry was regenerated to point at the new screens
and names, and a unit test checks that each one opens a registered screen.

## Every setting, and what happened to it

Keep = stays where it was. Move = same control, new place. Collapsed = behind an expander on its
screen. Remove from UI = no screen offers it; the stored key stays for backups.

| Setting (stored key) | Was on | Decision | Why |
|---|---|---|---|
| `auto_capitalize_first_letter`, `auto_capitalize_after_period` | Smart Features | Move to Typing > Capitals | Typing is what they change |
| `auto_capitalize_restricted_fields` | Smart Features > Advanced | Move to Typing > Capitals, shown while the first row is on | the spec's own condition; it was hidden in Advanced as "Shift in all text fields" |
| `double_space_to_period` | Smart Features | Move to Typing > Punctuation | |
| `comma_space`, `spaced_hyphen_to_en_dash` (+ dash style), `smart_quotes` (+ style) | Smart Features > Advanced | Collapsed under Typing > More punctuation | rarely changed |
| `auto_space_punctuation`, `space_after_punctuation` | Punctuation spacing | Keep; reached from More punctuation as "Spaces around punctuation" | |
| `shift_backspace_delete`, `alt_backspace_delete`, `backspace_at_start_delete` | Smart Features (one in Advanced) | Move to Typing > Backspace | the three belonged together |
| `clear_alt_on_space` | Smart Features | Move to Typing > Alt key, relabelled "Space or Enter releases Alt" | the old label left out Enter |
| `auto_correct_enabled` (Text replacements) | Auto-correction | Move onto the Text replacements screen; the row on Autocorrect & words shows On/Off | a switch next to the list it switches |
| `auto_replace_on_space_enter` | Auto-correction | Keep, relabelled "Fix typos" | |
| `fix_word_mixups` | Auto-correction | Keep | |
| `accent_matching_enabled` | Auto-correction | Keep, relabelled "Add missing apostrophes and accents" | |
| `max_auto_replace_distance`, `use_keyboard_proximity` | Auto-correction | Collapsed under Fine-tuning, relabelled | jargon, rarely changed |
| `suggestions_enabled` | Auto-correction | **Remove from UI** | only filled the suggestion strip; autocorrect never reads it |
| `keyboard_layout`, `layout_auto_by_locale` | Input Languages and Keyboard Layout | Keep on Keyboard layout only ("Follow the language"); Languages & layouts shows it as the row's value | was on two screens |
| `alt_shift_layout_switch`, `alt_enter_layout_switch`, `ctrl_space_layout_switch` | Input Languages | Keep, under "Switch language with" | |
| `app_language_tag` | Input Languages dropdown and About > App Language | Move to Look & feel (the one App language screen) | was on two screens |
| long press mode, threshold, variation chooser, custom variations | Long press | Keep | |
| Sym pages config, custom pages, auto-close, double-tap chooser, kaomoji, larger picker, skin tone, OTP, inline suggestions, `sym_edit_shortcuts` | Customize SYM Keyboard | Keep, regrouped into Pages / Emoji / Fill page / Sym key | kaomoji sat under "Fill page" |
| `clipboard_history_enabled`, retention | Extras > Clipboard history | Move under Sym pages > Clipboard | the clipboard is a Sym page |
| dictation rows | Voice | Keep | |
| dictation haptics and strength | Sound & Haptics | Keep, under a "Dictation" label | |
| typing sound, tap vibration | Sound & Haptics | Keep | |
| nav mode, Ctrl-hold navigation, layout-aware Ctrl shortcuts, Fn mappings | Fn Layer (via Smart Features and Key mapping) | Move under Keys & shortcuts | |
| Fn Layer "Revert to Default" | Fn Layer | **Fixed**: resets only the three switches on that screen | reset every key setting before |
| screen trackpad rows | T2E Tools | Move to Keys & shortcuts | about keys, not the phone |
| key mapping | T2E Tools | Move to Keys & shortcuts | |
| quick launcher rows, assigned keys, entries | Extras | Move to Keys & shortcuts | |
| exact typing packages (Terminal mode) | Keyboard hub | Move to Apps | |
| Enter behaviour, preset, overrides | Keyboard hub | Move to Apps | |
| theme colours (background, keys, buttons, outlines, text, accent), saved themes, per-language themes | Theme, Customize colors | Keep, under Look & feel > Theme | live: they paint the Sym pages and panels |
| caret badge on/off and its two colours | Theme > Modifiers | Move to Look & feel > Modifier badge | not part of the theme |
| `status_bar_left_buttons`, `status_bar_right_buttons`, `status_bar_height_dp`, the strip-buttons Reset | Theme > Status strip | **Remove from UI** | strip only |
| theme `show_leds`, LED inactive/active/locked colours | Theme, Customize colors, layout override editor | **Remove from UI** | strip only; kept in the stored theme and in each override |
| theme key/chrome corner ratios, `suggestions_height_scale` | Customize colors > Status strip | **Remove from UI** | strip only |
| `status_bar_apps`, `app_keyboard_nudge_packages` | unreachable app pickers | **Remove from UI** (pickers deleted) | strip only; no screen linked to them |
| snippets on/off, prefix, presentation, accept keys, snippets list | Extras > Text expansion | Move under Typing | the "Suggestion bar" choice stays hidden unless a restored backup has it, so it can be changed away |
| private mode, clean links | Privacy | Keep | |
| "Fields that ask for privacy" | Privacy | Replaced by a note | it was a switch that could never be turned off |
| notification access (one-time codes) | Customize SYM Keyboard | Keep there, and also offered on Privacy | it is the one thing PhysiBoard reads outside the keyboard |
| smart backlight, ring, bloat, density, tweaks, pairing | T2E Tools | Keep, screen renamed Titan tools | |
| backup, restore, reset to defaults, reset device to stock | Settings | Move to Backup & restore | |
| status, diagnostics, test field, updates, tutorial | Settings and About | Move to Help | |

Settings that are live but have never had a row (bounce keys, overlapping keys, keyboard swipe
gestures, French punctuation spacing, the layout-switch toast, the typing-sound output stream,
the Sym panels' rounded corner insets, the diagnostic overlay log) are unchanged by this work:
each would need its own decision about whether to offer it.

## Look

The shared building blocks changed rather than each screen:

- Every screen has a collapsing large-title bar: the title starts large under the back arrow and
  folds into the bar as the content scrolls, the pattern the maintainer's other apps use.
- Rows that open a screen show the current value at the right ("On", "Slate Light", "3 apps").
- The index rows carry a tinted icon per category, each hue checked for 3:1 against its tile.
- Expanders animate open and closed, and the screen underneath drifts back as a new one slides in.
- Destructive rows are in the error colour, without a chevron, each behind a confirmation.
- Intros sit above the cards as plain text instead of in a card of their own.
