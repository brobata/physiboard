# Settings catalog: the preference store, defaults, migration, backup, and the settings app

This document is the inventory the 3.0 settings importer is written from. It lists every
preference key the 2.x app reads or writes, the value each key holds, where it comes from on a
fresh Titan 2 Elite, how a 1.x store was carried across, how the store is exported and restored,
and the shape of the settings app that edits it. Behavior that belongs to another subsystem is
only pointed at from the key table; the file that owns it is named in the "Feature" column.

Vocabulary used throughout:

- "The store" is the app's main preferences file. "A row" is one key in it.
- "Code default" is the value the app behaves with when the key is absent.
- "Baseline" is the value written by the shipped first-run asset on every Titan 2 Elite.
- "Marker" is a row that is not a setting: it records that a one-time action already ran, or what
  a system value was before the app changed it.
- "Dead" is a row that is written and never read, or read and never written by any screen.

## 1. The preference store

The store is a private SharedPreferences file named `physiboard_prefs`. Before 2.0 the same data
lived in a file named `pastiera_prefs`; that file is the migration source described in section 5
and is deleted by the baseline reset in section 4.

Four smaller stores exist beside it. None of them is a user setting and none is edited from a
screen:

| File | Rows | Purpose | In backups |
|---|---|---|---|
| `embedded_adb` | `physiboard` (the pairing key material) | The wireless-debugging broker's pairing (see broker-privileged-toolbox.md) | Yes: every shared preferences file except the emoji one is exported, so a backup carries the pairing |
| `physiboard_toolbox` | `pending_revert` (JSON object `{id, apply, revert}`), `removal_journal` (JSON array of `{pkg, prev, action, at}`) | The toolbox's undo records for a system change in flight and for removed packages | Yes |
| `app_list_cache_prefs` | `package_change_sequence` (int), `package_change_boot_count` (int) | Invalidation counters for the cached app list (see per-app-behavior.md) | Yes |
| `recent_emojis_prefs` | `recent_emojis` | Recently used emoji | No: explicitly excluded |

Reads and writes are synchronous from the point of view of the caller; most writes are
asynchronous to disk. The exceptions, where the app waits for the disk, are called out where they
matter: the migration and the baseline (because the input method can read a row microseconds
later), the trackpad rows, the swipe-to-delete provider, the ring backlight capture, and every
backup restore.

The input method service and several screens register change listeners on the store so that a
row changed in the settings app takes effect in the running keyboard without a restart. Which
rows are live-applied is a matter for each subsystem's document; nothing in this file depends
on it.

### 1.1 Start-up order

Every process start (the keyboard service and the app share one process) runs these four steps,
in this order, before anything reads a setting:

1. The 1.x migration (section 5). Runs once per install, gated by `prefs_migrated_v2`.
2. The baseline reset (section 4). Runs once per baseline version, gated by
   `settings_baseline_version`.
3. The impact defaults stamp (section 4.3). Runs once per install, gated by
   `impact_defaults_applied`.
4. The Alt+Shift default initialisation (section 5.4). Runs once, gated by
   `alt_shift_default_initialized`.

Step 3 runs after step 2 and writes unconditionally, so on a fresh install the impact defaults
win over the baseline wherever the two disagree. They disagree on exactly one row,
`status_bar_visibility` (baseline `APPS`, impact defaults `ALWAYS`). On an upgrade that triggers
the reset, `impact_defaults_applied` is kept, step 3 does not run, and the baseline value stands.

## 2. Every preference key

Columns: key | type | code default | baseline (only when it differs from the code default; "same"
means the baseline carries the code default; blank means the baseline does not carry the key) |
what it changes | screen and label ("no screen" means nothing in 2.x writes it except a backup
restore or the baseline). Value ranges are clamped on read and on write unless noted.

### 2.1 Typing pipeline (text-input.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `auto_capitalize_first_letter` | boolean | true | same | Capital at the start of a field | Keyboard > Smart Features > Capitalization > "Capitalize at text start" |
| `auto_capitalize_after_period` | boolean | true | | Capital after a sentence end | Smart Features > "Capitalize after sentence end" |
| `auto_capitalize_restricted_fields` | boolean | false | | Auto-shift in fields that ask not to | Smart Features > Advanced > "Shift in all text fields" |
| `double_space_to_period` | boolean | true | | Two spaces become ". " | Smart Features > Spacing & punctuation > "Double Space inserts period" |
| `clear_alt_on_space` | boolean | true | | Space drops an armed or locked Alt | Smart Features > Keyboard behavior > "Release Alt with Space" |
| `auto_show_keyboard` | boolean | true | same | Bring the keyboard up when a field gains focus | Smart Features > "Show keyboard automatically" |
| `physical_keyboard_currency_symbol` | string, one of `€ $ £ ¥ ₹ ₽ ₿ ¤`; anything else reads as `€` | `€` | `$` | The currency key's output | Smart Features > "Currency Symbol" chips |
| `shift_backspace_delete` | boolean | false | | Shift+Backspace deletes forward | Smart Features > Delete > "Shift + Backspace" |
| `alt_backspace_delete` | boolean | false | | Alt+Backspace deletes forward | Smart Features > Delete > "Alt + Backspace" |
| `backspace_at_start_delete` | boolean | false | | Backspace at line start deletes forward | Smart Features > Advanced > "Backspace at line start" |
| `auto_space_punctuation` | string: an ordered subset of the characters `.,;:!?\/")]}` in that canonical order, other characters dropped | `""` | | Which punctuation gets a space put before it | Smart Features > Advanced > "Punctuation spacing", "before" column |
| `space_after_punctuation` | string, same shape | `""` | | Which punctuation gets a space put after it | Same dialog, "after" column |
| `comma_space` | boolean | false | | Space after a comma | Smart Features > Advanced > "Space after comma" |
| `spaced_hyphen_to_en_dash` | boolean | false | | " - " becomes a dash | Smart Features > Advanced > "Hyphen to dash" |
| `spaced_hyphen_dash_style` | string `en_dash` or `em_dash`; anything else reads as `en_dash` | `en_dash` | | Which dash | Same row |
| `mid_word_quote_to_apostrophe` | boolean | false | | A quote inside a word becomes an apostrophe | Smart Features > Advanced > "Quotes inside words" |
| `smart_quotes` | boolean | false | | Straight quotes become typographic | Smart Features > Advanced > "Quotation mark style" |
| `smart_quotes_style` | string, one of `german_guillemets`, `french_guillemets`, `french_guillemets_narrow_spaced`, `german_low_high`, `english_curly`; anything else reads as `german_guillemets` | `german_guillemets` | | Which quote pair | Same row |
| `french_punctuation_spacing` | boolean | false | | Narrow space before `?!:;` | No screen in 2.x |
| `french_punctuation_only_french` | boolean | false | | Apply the above only when the input language is French | No screen in 2.x |
| `swipe_to_delete` | boolean | false | | Swipe left on the keyboard deletes a word | No screen in 2.x (keys-and-modifiers.md) |
| `swipe_to_delete_provider` | string `titan2_keycode` or `native_ime`; written synchronously | `native_ime` | | Which event source the swipe comes from | No screen in 2.x |
| `layout_aware_ctrl_shortcuts` | boolean | false | | Ctrl+letter resolved through the active layout | Keyboard > Fn Layer > "Layout-aware app Ctrl shortcuts" |
| `long_press_threshold` | long ms, 50 to 1000 | 300, but see the quirk: the Alt/Sym layer reads the raw row with a fallback of 500 when it is absent | | Hold time before a key is a long press | Only on the "Key Behaviour & Timing" screen, which no row navigates to in 2.x (dead UI) |
| `swipe_incremental_threshold` | float dp, 3 to 25 | 9.6 | | Distance per cursor step on the retired swipe bar | Dead: written by nothing, read by nothing since 2.0 |

### 2.2 Autocorrect and suggestions (autocorrect-suggestions.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `auto_correct_enabled` | boolean | true | | The text replacement engine | Keyboard > Auto-correction > "Text replacements" |
| `auto_correct_enabled_languages` | string, comma-separated language codes | absent reads as {system language if it is one of it, en, es, fr, de, pl, otherwise en} plus `x-pastiera` | | Which languages' replacement tables apply | Auto-correction > "Manage text replacements" |
| `auto_correct_custom_<code>` (one row per language code) | string, JSON object; the field `__name` holds the display name, every other field is `wrong: right` | none | | User substitutions for that language | Text Replacements > Custom Substitutions > edit screen ("Add Correction") |
| `auto_replace_on_space_enter` | boolean | false | true | Replace the word on Space or Enter | Auto-correction > "Automatic correction" |
| `max_auto_replace_distance` | int 0 to 3 (0 = off) | 1 | 2 | Edit distance allowed for an automatic replacement | Auto-correction > "Maximum correction distance" |
| `suggestions_enabled` | boolean | true | | The suggestion strip | Auto-correction > "Suggestions while typing" |
| `accent_matching_enabled` | boolean | true | | Accent-insensitive matching | Auto-correction > "Accent & spelling marks" |
| `use_keyboard_proximity` | boolean | false | true | Key-distance ranking | Auto-correction > "Keyboard Proximity Ranking" |
| `use_edit_type_ranking` | boolean | false | | Insert > substitute > delete ranking | Auto-correction > "Edit Type Ranking" |
| `user_dictionary_entries` | string, JSON array of objects `{"w": word, "f": frequency, "u": last used ms}` | none | | The personal dictionary | Auto-correction > "Personal dictionary" (User dictionary screen) |
| `suggestion_debug_logging` | boolean | true | | Verbose suggestion logs (stripped from release builds anyway) | No screen |
| `trackpad_gestures_enabled` | boolean; written synchronously | false | | Swipe gestures on the suggestion strip | No screen in 2.x |
| `trackpad_gesture_add_word_enabled` | boolean; synchronous | true | | Gesture may add a word | No screen in 2.x |
| `trackpad_gesture_add_word_full_width_enabled` | boolean; synchronous | true | | Full-width add-word gesture | No screen in 2.x |
| `trackpad_swipe_threshold` | float 120 to 750; synchronous | 500 | | Legacy shared threshold; read only as the fallback for the two rows below | No screen |
| `trackpad_suggestion_swipe_threshold` | float 120 to 750 | falls back to `trackpad_swipe_threshold`, then 500 | | Swipe distance to pick a suggestion | No screen |
| `trackpad_delete_swipe_threshold` | float 120 to 750 | same fallback | | Swipe distance to delete | No screen |
| `trackpad_provider` | string `shizuku` or `native_ime`; synchronous | `native_ime` | | Event source for strip gestures | No screen |

### 2.3 Dictionaries, languages, layouts (dictionaries-languages.md, layers-sym-alt.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `keyboard_layout` | string layout id | `qwerty` | same | The active physical layout | Extras > Input Languages (layout per input style) |
| `keyboard_layout_auto_by_locale` | boolean | true | | Resolve layout from the input style's locale mapping | Input Languages > "Automatic Layout Mapping" |
| `keyboard_layout_auto_mapping_updated` | long, epoch ms | none | | Reload trigger after the locale-to-layout mapping file changes | Written by Input Languages, read by the keyboard |
| `keyboard_layout_list` | string, JSON array of layout ids | absent reads as [current layout] | | The cycle order for the layout-switch chords | No screen writes it in 2.x |
| `alt_shift_layout_switch` | boolean | false on a new install, true on an install that already had rows when the one-time initialisation ran (section 5.4) | true | Alt+Shift cycles layouts | Input Languages > Layout Switch Shortcuts > "Alt+Shift Layout Switch" |
| `alt_shift_default_initialized` | boolean marker | | | Guards the above | |
| `alt_enter_layout_switch` | boolean | false | | Alt+Enter cycles layouts | "Alt+Enter Layout Switch" |
| `ctrl_space_layout_switch` | boolean | true | | Ctrl+Space cycles layouts | "Ctrl+Space Layout Switch" |
| `toast_on_layout_switch` | boolean | true | | Toast on a layout change | No screen in 2.x |
| `additional_ime_subtypes` | string set of `locale:layout` | empty | | Extra input styles registered with Android | Input Languages list |
| `custom_input_styles` | string, entries separated by `;` | absent reads as the app's predefined subtype list | | The user's input style list | Input Languages |
| `input_style_suggestion_locales` | string, JSON object keyed `<locale>:<layout>` (locale with `-`), each value a JSON array of locale tags | none | | Extra suggestion dictionaries per input style | Input Languages > "Suggestion dictionaries" |
| `hidden_system_input_styles` | string, JSON array of `<locale>:<layout>` | none | | System-provided styles the user hid | Input Languages > hide/show system locale |
| `app_language_tag` | string BCP-47; blank means system | none | | The settings app's own UI language | About > "App Language" and Input Languages > "App Language" |
| `physical_keyboard_profile_override` | string `auto`, `key2`, `Q25`, `titan`, `titan2`, `titan2elite_qwerty`, `mp01`, `clicks_razr`, `clicks_pixel`, `clicks_power`; anything else reads as `auto` | `auto` | | Which device profile's key mappings apply | Only on the "Built-in Keyboards" screen, which nothing navigates to in 2.x (dead UI); the picker offers `auto`, `titan2`, `titan2elite_qwerty` |
| `titan2_layout_enabled` | boolean | absent reads as "the phone is a Titan 2 family device" | | Aligns the on-screen keyboard with the physical layout | Same dead screen, "Titan 2 Layout Alignment" |
| `global_variation_layout_override` | string layout id, blank = none | `""` | | Variation ordering layout | Dropped by the 1.x migration, yet still read once by the variation loader: effectively dead |
| `variations_updated` | long, epoch ms | none | | Reload trigger after `variations.json` changes | Written by the variation editors and restore |

### 2.4 Keys and modifiers (keys-and-modifiers.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `shift_tap_latches` | boolean | false | | Tap Shift arms it for one key | No screen in 2.x (the Modifiers screen was deleted) |
| `alt_tap_latches` | boolean | false | | Tap Alt arms it | No screen |
| `ctrl_tap_latches` | boolean | false | | Tap Ctrl arms it | No screen |
| `alt_latch_stays_on_space` | boolean | false | | Armed Alt survives Space | No screen |
| `ctrl_latch_stays_on_space` | boolean | false | | Armed Ctrl survives Space | No screen |
| `long_press_modifier` | string `alt`, `shift`, `variations`, `sym`, `sym_symbols`, `sym_emoji`; anything else reads as `alt` | `alt` | | What holding a letter produces | No screen in 2.x |
| `bounce_keys_enabled` | boolean | false | | Ignore a repeat of the same key inside the delay | No screen (Accessibility screen deleted) |
| `bounce_keys_delay_ms` | long 20 to 500 | 80 | | The delay | No screen |
| `bounce_keys_character_keys_enabled` | boolean | true | | Filter applies to letters | No screen |
| `bounce_keys_modifier_keys_enabled` | boolean | false | | Filter applies to modifiers | No screen |
| `bounce_keys_space_enabled` | boolean | true | | Filter applies to Space | No screen |
| `bounce_keys_enter_enabled` | boolean | true | | Filter applies to Enter | No screen |
| `bounce_keys_backspace_enabled` | boolean | true | | Filter applies to Backspace | No screen |
| `overlapping_keys_enabled` | boolean | false | | Overlapping-press handling | No screen |
| `nav_mode_enabled` | boolean | true | | The Fn layer | Keyboard > Fn Layer > "Enable Fn Layer" |
| `nav_mode_ctrl_hold_enabled` | boolean | false | | Holding Ctrl uses the Fn layer in text fields | Fn Layer > "Ctrl-hold navigation" |
| `nav_mode_default_mappings_version` | int marker | absent reads as 1; current is 3 | | Which default-mapping upgrade of `ctrl_key_mappings.json` has run | |
| `nav_mode_mappings_updated` | long, epoch ms | none | | Reload trigger after the mappings file changes | Written by Fn Layer saves, resets and upgrades |
| `fn_ctrl_prev_captured` | boolean marker | false | | The two rows below are valid | Fn Layer > "Set Fn key to Ctrl" |
| `fn_ctrl_prev_enable` | int; absent reads as the sentinel "unset" (the most negative int) | | | The system's programmable-key enable value before the app changed it | |
| `fn_ctrl_prev_function` | int, same sentinel | | | The system's programmable-key function value before | |
| `fn_speech_scan_code` | int | 251 | | Scan code of the key held for dictation | No screen (dictation.md) |
| `power_shortcuts_enabled` | boolean | true | | Sym+assigned key launches | Extras > PhysiBoard-QuickLauncher > "SYM key shortcuts" |
| `launcher_shortcuts_enabled` | boolean | false | | Assigned keys fire on the home screen | PhysiBoard-QuickLauncher > "Homescreen shortcuts" |
| `sym_edit_shortcuts` | boolean | true | | Sym+C/V/X/A edit chords | SYM customization > "Sym+C/V/X/A: copy, paste, cut, select all" |

### 2.5 Sym pages and the Alt layer (layers-sym-alt.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `sym_mappings_custom` | string, JSON `{"mappings": {"KEYCODE_Q": "text", ...}}` for the 26 letter keys | none | | Custom Sym page 1 (emoji) | SYM customization activity |
| `sym_mappings_page2_custom` | string, same shape | none | | Custom Sym page 2 (symbols) | SYM customization activity |
| `sym_pages_config` | string, JSON object with `deviceEnabled`, `emojiEnabled`, `symbolsEnabled`, `clipboardEnabled`, `emojiPickerEnabled` (booleans), `emojiFirst` (legacy boolean, written for older builds), `symPageOrder` (array of page ids `device`, `emoji`, `symbols`, `clipboard`, `emoji_picker`) | device off, emoji on, symbols on, clipboard off, picker off, order device, emoji, symbols, clipboard, emoji_picker | emoji off, symbols on, clipboard off, picker on, `emojiFirst` false, order emoji_picker, symbols, clipboard, emoji (device is absent from the order and is appended last when read) | Which Sym pages exist and in what order | SYM customization > "Arrange SYM pages order" |
| `alt_character_layer_binding` | string `first`, `emoji`, `symbols`, or `device:<something>`; anything else reads as `device:auto` | `device:auto` | | Which Sym page the Alt layer shows | SYM customization > "Alt character layer" |
| `sym_auto_close` | boolean | true | | Sym page closes after a key | SYM customization > "Auto-Close SYM Layout" |
| `sym_auto_close_on_touch` | boolean | true | | Also after an on-screen Sym key | "Also close after on-screen SYM keys" |
| `emoji_picker_expanded_height` | boolean | true | false | Taller emoji picker | SYM customization > "Larger emoji picker" |
| `restore_sym_page` | int 0, 1 (emoji) or 2 (symbols) | 0 | | Page to reopen after returning from SYM customization | Transient |
| `pending_restore_sym_page` | int | 0 | | Candidate for the above, promoted only when the user presses Back | Transient |
| `current_sym_page` | int | 0 | | The page currently open, written by the keyboard, read when opening SYM customization | Not managed by the settings layer |

### 2.6 Status bar, theme, caret badge (status-bar.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `status_bar_visibility` | string `ALWAYS`, `NEVER`, `APPS` | absent reads through `show_status_bar`: true or absent gives `ALWAYS`, false gives `NEVER` | `APPS` (but a fresh install ends at `ALWAYS`, section 1.1) | Where the strip appears | Keyboard > Status Bar Theme > "Show status bar": Always / Never / Only in these apps |
| `show_status_bar` | boolean | true | same | Legacy mirror; every write of the row above also writes this as (visibility is not NEVER) | Same control |
| `status_bar_apps` | string set of package names | seeded on first read with the 20-app messaging, mail and social list (D8) | same 20 | Apps where the strip shows in `APPS` mode | "Only in these apps" list |
| `status_bar_height_dp` | int, offered 36, 48, 56, 64 | 56 | same | Strip height | Status Bar Theme > "Bar height" |
| `status_bar_slots_left` | string, JSON array of button ids | `["hamburger"]` | `["clipboard"]` | Buttons on the left | Status Bar Theme > Buttons > "Left buttons" |
| `status_bar_slots_right` | string, JSON array | `["emoji","microphone"]` | `["microphone","none"]` | Buttons on the right | "Right buttons" |
| `status_bar_slot_left` | string button id | `hamburger` | `clipboard` | Legacy mirror of element 0 of the left array; the array read falls back to it when the array is absent | Same control |
| `status_bar_slot_right_1` | string | `emoji` | `microphone` | Legacy mirror of right element 0 | |
| `status_bar_slot_right_2` | string | `microphone` | `none` | Legacy mirror of right element 1 | |
| `modifier_indicator_mode` | string | none | `menu_bar` | Nothing: dead. Written by the baseline and the impact defaults, read by no code path since the indicator was pinned to the status bar in 2.0 | |
| `hardware_bar_height_migrated` | boolean marker | false | | The one-time lift of `suggestions_height_scale` from 1.0 to 1.4 has run (section 6.1) | |
| `caret_modifier_badge` | boolean | true | | Modifier glyphs beside the cursor | Status Bar Theme > Modifiers > "Show modifiers at the cursor" |
| `caret_badge_armed_color` | int ARGB | 0xFF2563EB (-14326805) | 0xFF111827 (-15656921) | Colour for a one-shot modifier | "One press colour" |
| `caret_badge_locked_color` | int ARGB | 0xFFDC2626 (-2349530) | | Colour for a locked modifier | "Locked colour" |
| `keyboard_theme_hardware` | string, JSON theme (section 3.1) | Slate Dark with `suggestions_height_scale` 1.4 | a custom theme (section 4.1) | The theme of the strip and hardware-mode chrome | Status Bar Theme (presets, colour rows, "Save and use theme") |
| `keyboard_theme_software` | string, JSON theme | Slate Dark with the software geometry (corner 0.19/0.20, key height 1.5489256, row gap 0.47933885, suggestions 0.8982954, variations 0.95914257, ortholinear) | | Theme of the on-screen keyboard | Reached only with a "software" target extra |
| `keyboard_theme_assignment_mode_hardware`, `..._software` | string `fixed` or `follow_system` | | | Nothing: since 2.0 the mode is always `fixed` and the stored value is ignored | Dead |
| `keyboard_theme_light_hardware`, `..._dark_hardware`, `..._light_software`, `..._dark_software` | string, JSON theme | | | The follow-system slots; only read when the mode is `follow_system`, which never happens. Still touched by the bar-height lift | Dead in effect |
| `keyboard_theme_layout_overrides_hardware`, `..._software` | string, JSON array of `{"locale"?: tag, "layout"?: id, "theme": theme}`; an entry with neither locale nor layout is dropped | `[]` | | A theme per input style or layout; the best match wins (exact locale 16 points, language 8, layout 4) | Status Bar Theme override editor |
| `keyboard_theme_saved_themes` | string, JSON array of `{"name", "theme"}`; names compare case-insensitively; blank name saves as "Custom" | `[]` | | Saved custom themes | Status Bar Theme |
| `keyboard_theme_drafts` | string, JSON array of `{"name", "theme", "populated_fields": [field names]}`; blank name saves as "Untitled theme" | `[]` | | Unfinished custom themes | Status Bar Theme > "Create a custom theme" |
| `keyboard_theme_preview_viewport_scale` | float 1.0 to 1.8 | 1.0 | | Zoom of the preview | Status Bar Theme > "Keyboard UI Preview" |
| `accessibility_live_announcements_enabled` | boolean | false | | TalkBack live region on the strip | No screen (Accessibility screen deleted) |
| `accessibility_read_second_row_enabled` | boolean | false | | TalkBack reads the second row | No screen |
| `accessibility_suggestions_announcement_delay_ms` | long 100 to 2000 | 500 | | Delay before suggestions are announced again | No screen |
| `ime_overlay_debug_logging` | boolean | false | | Inset logging | No screen |
| `titan2_elite_rounded_corner_insets` | boolean | absent reads as "the phone is a Titan 2 Elite" | true | Inset the strip away from the rounded corners | No screen (hidden) |
| `pastierina_mode_active` | boolean | false | | Nothing: read once by the visibility logic, written by nothing, dropped by migration | Dead |
| `pastierina_mode_override` | string | | | Nothing: only a name in the source, never read or written | Dead |

### 2.7 Per-app behavior (per-app-behavior.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `app_raw_mode_packages` | string set of package names | empty | `org.chromium.webapk.a5d49fddf77614419_v2` (D3) | Apps typed into raw | Keyboard > Exact typing |
| `app_keyboard_nudge_packages` | string set | seeded on first read with `com.microsoft.teams` | | Apps that get the strip dip | Keyboard > Text box under the bar |
| `app_enter_behavior_enabled` | boolean | true | | Per-app Enter handling at all | Keyboard > Enter key behaviour > "App-specific Enter behaviour" |
| `app_enter_behavior_preset` | string `app_default`, `enter_send_shift_newline`, `enter_newline_ctrl_send`, `custom`; anything else, including the UI's `enter_newline_only`, reads as `app_default` | `enter_send_shift_newline` | same | Preset for the known messaging apps | Enter key behaviour > "Messaging preset" |
| `app_enter_behavior_overrides` | string, JSON array of `{"packageName", "behavior", "sendStrategy", "additionalSendShortcut"}`; duplicates by package dropped, blank package dropped; `behavior` one of `app_default`, `enter_newline`, `enter_send_shift_newline`, `enter_newline_ctrl_send`; `sendStrategy` one of `auto`, `editor_action`, `ctrl_enter`, `plain_enter`; `additionalSendShortcut` `none` or `sym_enter` | `[]` | four entries: `com.whatsapp`, `com.discord`, `com.google.android.apps.messaging`, `com.instagram.android`, each `enter_send_shift_newline` / `auto` / `none` | Per-app Enter rules | Enter key behaviour > "App overrides", "Add app" |
| `software_keyboard_mode` | string `auto`, `force_hardware`, `force_virtual` | `auto` | same | Whether the on-screen keyboard is forced | Status bar button and the launcher shortcut; writing it clears the runtime override |
| `software_keyboard_mode_runtime_override` | string `force_hardware` or `force_virtual`; `auto` or absent means none | none | | Temporary override until the next explicit choice | Keyboard toggle |
| `software_keyboard_mode_toggle_toasts` | boolean | true | | Toast when the mode flips | No screen |
| `launcher_shortcuts` | string, JSON object keyed by decimal keycode, each value `{"type", "packageName"?, "appName"?, "action"?, "data"?, "commandId"?, "source"?, "kind"?, "title"?, "subtitle"?, "launch"?}`; `type` is `app`, `shortcut`, `quick_launcher` or `command` | `{}`, after which the quick launcher is assigned to Space (keycode 62) on first read unless Space already holds something | `{"62": {type command, appName "Pastiera QuickLauncher", commandId `pastiera.quick_launcher`, source `pastiera`, kind PastieraAction, title "Pastiera QuickLauncher", subtitle "Open Pastiera search", launch {type internal_action, actionId open_quick_launcher}}}` | Key assignments | PhysiBoard-QuickLauncher > "Assigned launcher keys" |
| `quick_launcher_default_assigned` | boolean marker | false | | The Space auto-assignment has been decided | |

### 2.8 Dictation and the assistant (dictation.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `alt_ctrl_speech_shortcut` | boolean | true | false | Alt+Ctrl starts dictation | No screen in 2.x (removed) |
| `fn_long_press_speech` | boolean | false | true | Holding Fn dictates | Keyboard > Voice > Triggers > "Long-press Fn for speech input" |
| `dictation_haptics` | boolean | true | same | Vibrate on start and stop (also gated on the system haptic setting) | Keyboard > Sound & Haptics > "Vibrate on dictation start/stop" |
| `dictation_haptic_strength` | string `light`, `standard`, `strong` | `strong` | | Pulse length | Sound & Haptics > "Vibration strength" (only while the row above is on) |
| `dictation_end_silence_ms` | int 0 to 10000; 0 = engine default | 0 | 2000 | End-of-speech pause | Voice > Transcription > "End-of-speech pause" |
| `dictation_mask_offensive` | boolean | true | false | Engine masks profanity | Voice > "Block offensive words" |
| `dictation_engine` | string: `""` system default, `ondevice`, or a flattened component name | `""` | `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService` (D4) | Which recognizer | Voice > "Speech engine" |
| `dictation_continuous_session` | boolean | true | | One long session timed by the engine | Voice > "Let the engine time the pause" |
| `dictation_auto_punctuation` | boolean | true | | Engine punctuates | Voice > "Automatic punctuation" |
| `sym_long_press_assistant` | boolean | false | | Hold Sym opens the assistant | Voice > Voice assistant > "Hold Sym for the assistant" |
| `side_key_assistant` | boolean | false | true | Orange side key long press opens the assistant | Voice > "Orange key opens the assistant" |
| `assistant_action` | string `auto`, `voice_command`, `hands_free`, `assist` | `auto` | | Which intent opens it | Voice > "How the assistant opens" |
| `side_key_original_captured` | boolean marker | false | | The two rows below are valid; never captured if the original target is the app itself | |
| `side_key_original_package`, `side_key_original_activity` | string | none | | What the side key long press pointed at before | |

### 2.9 Screen trackpad (trackpad-caret-nav.md)

All five rows are written synchronously.

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `screen_trackpad_enabled` | boolean | false | true | The trackpad | T2E Tools > Screen trackpad > "Enable screen trackpad" |
| `screen_trackpad_trigger_key` | string `space`, `shift_left`, `shift_right`, `shift_either`, `sym`; anything else reads as `space` | `space` | | Which key is held | "Trigger key" |
| `screen_trackpad_activation` | string `hold`, `double_tap`, `single_tap`; anything else reads as `hold` | `hold` | | How the trigger engages | "Activate by" |
| `screen_trackpad_step_px` | int 8 to 64 | 32 | same | Pixels per cursor step | "Sensitivity" |
| `screen_trackpad_show_hint` | boolean | true | | On-screen hint | "Show on-screen hint" |

### 2.10 Backlight and notification ring (device-backlight-ring.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `smart_backlight_enabled` | boolean | false | true | Keep the keyboard lit in the dark | T2E Tools > Smart keyboard backlight > "Smart backlight" |
| `smart_backlight_applied` | boolean marker | false | | The persistent vendor value was written at least once | |
| `notification_ring_enabled` | boolean | false | true | The ring | T2E Tools > Notification ring > "Ring on new notifications" |
| `notification_ring_minutes` | int 1 to 60 | 10 | 2 | Screen-on time | "Keep the screen on for" slider |
| `notification_ring_brightness` | string `DIM`, `NORMAL`, `BRIGHT`; anything else reads as `NORMAL` | `NORMAL` | | Screen brightness while ringing (0.05, 0.2, 0.6) | "Ring brightness" |
| `notification_ring_icons` | boolean | false | | App icons under the ring | "Show app icons" |
| `notification_ring_keyboard_dark` | boolean | true | | Keyboard stays unlit while ringing | "Keep the keyboard dark" |
| `notification_ring_default_color` | int ARGB | the ring policy's default (device-backlight-ring.md) | | Colour for apps without one or with one too dark | "Default colour" |
| `notification_ring_app_colors` | string, JSON object package to ARGB int | `{}` | `co.kidcasa.app` 0xFFF474B6 (-757066), `com.google.android.apps.googlevoice` 0xFF34B7F1 (-13318311) | Per-app colours | "App colours" |
| `notification_ring_cx`, `_cy`, `_radius`, `_stroke` | float window px; the override exists only when `_radius` is present | absent = the fitted default | 78.4834, 80.4834, 45.9375, 9.625 (D2) | A hand-fitted ring | "Fit the ring to the lens" |
| `ring_backlight_prev_captured` | boolean marker; written synchronously | false | | A ring has the vendor backlight switch turned off | |
| `ring_backlight_prev` | int | absent reads as 1 | | The switch value to put back | |
| `qs_backlight_prev_captured` | boolean marker | false | | The quick-settings tile captured the vendor global | |
| `qs_backlight_prev` | int; absent reads as the "unset" sentinel | | | The vendor global before the tile flipped it | |

### 2.11 Privileged setup diagnostics (broker-privileged-toolbox.md)

Written by the broker paths, read by the Status screen, the setup card and the debug export.
Never edited by the user.

| Key | Type | What |
|---|---|---|
| `privileged_backlight_ok`, `privileged_overlay_grant_ok`, `privileged_notification_ring_ok`, `privileged_ring_backlight_ok` | boolean | Last outcome of that step |
| `privileged_<step>_reason` | string `ok`, `not_paired`, `wireless_debugging_off`, `shell_failed` | Why |
| `privileged_<step>_at` | long epoch ms; 0 or absent means the step never ran | When |
| `privileged_backlight_device_value`, `..._at` | string, long | The backlight timeout last read back from the device |
| `privileged_broker_status`, `..._at` | string (broker status name), long | The last broker verification |

### 2.12 Text expansion, clipboard, quick launcher (expansion-clipboard-pickers-launcher.md)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `snippets_enabled` | boolean | false | | Snippets | Extras > Text expansion > Snippets > "Enable snippets" |
| `snippets_prefix` | string; must pass the prefix validity rule or reads as `!` | `!` | | Trigger prefix | "Snippet prefix" |
| `snippets_v1` | string, JSON object shortcut (lower-cased, validated) to replacement (blank replacements dropped) | `{}` | | The snippets | "Manage snippets" |
| `snippets_presentation` | string `off`, `floating_popup`, `suggestion_bar`; anything else reads as `floating_popup` | `floating_popup` | | Where matches show | "Show matches in" |
| `snippets_exact_on_space` | boolean | true | | Space expands an exact match | "Expand exact match with Space" |
| `snippets_accept_prefix_with_space` | boolean | false | | Space accepts a prefix match | "Accept prefix match with Space" |
| `snippets_accept_with_tab` | boolean | true | | Tab accepts | "Accept with Tab" |
| `snippets_accept_with_enter` | boolean | false | | Enter accepts | "Accept with Enter" |
| `clipboard_history_enabled` | boolean | true | | Clipboard history | No screen in 2.x |
| `clipboard_retention_time` | long minutes | 5 | | Age at which unpinned entries expire | No screen in 2.x |
| `quick_launcher_behavior` | string `pastiera` (PhysiBoard's own launcher) or `niagara`; anything else reads as `pastiera` | `pastiera` | | Which launcher search opens | PhysiBoard-QuickLauncher > Behaviour > "QuickLauncher behaviour" dropdown ("PhysiBoard QuickLauncher" / "Niagara Launcher Search") |
| `quick_launcher_auto_start_single` | boolean | false | | Open a unique match | Behaviour > "Open unique match automatically" |
| `quick_launcher_limit_results` | boolean | false | | Show only the top results | "Show only top search results" |
| `quick_launcher_respect_keyboard_layout` | boolean | true | | Search letters follow the layout | "Use active keyboard layout" |
| `quick_launcher_typo_tolerant_ranking` | boolean | true | | Fuzzy ranking | "Typo-tolerant search" |
| `quick_launcher_animation_duration_ms` | int 0 to 320, the slider steps by 20 | 120 | | Open/close animation | "Animation duration" |
| `quick_launcher_width_percent` | int 50 to 100 | 100 | | Launcher width | "Appearance" screen, "Launcher width": no row opens this screen in 2.x (section 9.3) |
| `quick_launcher_pill_mode` | boolean | false | | Pill shape | Appearance (unreachable) |
| `quick_launcher_highlight_favorites` | boolean | true | | Favourites tinted | Appearance (unreachable) |
| `quick_launcher_favorite_color` | int ARGB; the most negative int means "dynamic" | dynamic | | Favourite tint | Appearance (unreachable) |
| `quick_launcher_icon_colors` | boolean | false | | Tint entries from icon colours | Appearance (unreachable) |
| `quick_launcher_show_alias_first` | boolean | true | | Alias before name | Appearance (unreachable) |
| `quick_launcher_static_top_highlight` | boolean | false | | Fixed top-match colour | Appearance (unreachable) |
| `quick_launcher_static_top_highlight_color` | int ARGB | 0x7A4285F4 (2051180020) | | That colour | Appearance (unreachable) |
| `command_surface_sources` | string, JSON object keyed by source id (`apps`, `pastiera`, `app_actions`, `device_control`, `nav_actions`), each `{"quick_launcher": boolean}` | apps true, pastiera true, app_actions false, device_control false, nav_actions false | | Which command sources the launcher lists | Appearance > "QuickLauncher entries" (unreachable) |
| `quick_launcher_command_customizations` | string, JSON object keyed by command id, each with only the non-default fields of `favorite` (bool), `hidden` (bool), `custom_search` (string), `favorite_order` (int), `color` (int); an entry with all defaults is removed; entries sorted by id | `{}` | | Favourites, hidden entries, aliases, colours | Appearance > "Customize entries" (unreachable) |

### 2.13 Sound and haptics (this document, section 9.4)

| Key | Type | Code default | Baseline | What it changes | Screen and label |
|---|---|---|---|---|---|
| `typing_sound_mode` | string `off`, `click`, `typewriter`, `custom`; anything else reads as `off` | `off` | | Key sound | Keyboard > Sound & Haptics > "Typing Sounds": Off / Keyboard click / Typewriter / Custom sound pack… |
| `typing_sound_output_mode` | string `media`, `system`, `notification`; anything else reads as `media` | `media` | | Audio stream | Same row: Media volume / System sounds / Notifications |
| `typing_sound_custom_file_name` | string | none | | Directory name of the imported pack under the app's `typing_sounds/` folder (`custom_pack`) | Written by the import |
| `typing_sound_custom_display_name` | string | none | | The zip's display name | Written by the import |
| `typing_sound_updated_at` | long epoch ms | none | | Reload trigger | Written by the import |
| `tap_haptic_use_system` | boolean | true | | Use the system click haptic | Sound & Haptics > "Tap vibration" |
| `tap_haptic_duration_ms` | long 5 to 80 | 25 | | Custom pulse length when the above is off | "Custom vibration: N ms" |

### 2.14 On-screen keyboard (drop for 3.0)

All read by the on-screen keyboard only. The screen that edits them ("Key Behaviour & Timing",
section "On-screen Keyboard") is not reachable from any row in 2.x.

| Key | Type | Code default | What |
|---|---|---|---|
| `software_keyboard_layout_style` | string `compact`, `extended_iso`, `full_ansi`, `full_iso` | `compact` | Key arrangement |
| `software_keyboard_number_row_enabled` | boolean | true | Number row |
| `software_keyboard_nearest_key_touch_enabled` | boolean | true | Nearest-key touch resolution |
| `software_keyboard_left_modifier_key` | string `ctrl` or `alt` | `ctrl` | Left modifier key |
| `software_keyboard_right_modifier_key` | string `ctrl` or `alt` | `alt` | Right modifier key |
| `software_keyboard_long_press_layer_popup_enabled` | boolean | true | Long-press popup |
| `software_keyboard_long_press_layer_popup_below_key` | boolean | true | Popup placement |

### 2.15 App shell markers (app-shell.md)

| Key | Type | Code default | What |
|---|---|---|---|
| `tutorial_completed` | boolean | false | Onboarding finished; setting it also stamps `last_seen_whats_new_version` with the current version |
| `last_seen_whats_new_version` | string | none | The what's-new card was shown for this version |
| `dismissed_releases` | string, comma-separated release tags | none | Update prompts the user dismissed |
| `untested_device_notice_seen` | boolean | false | The "Untested on this phone" dialog was shown (only on a Titan 2 that is not an Elite) |
| `impact_defaults_applied` | boolean marker | false | Section 4.3 ran |
| `prefs_migrated_v2` | boolean marker | false | Section 5 ran |
| `v2_migration_notice_seen` | boolean marker | false | The rename notice was dismissed |
| `settings_baseline_version` | int marker | 0 | Which baseline version has been applied; current is 1 |

### 2.16 Rows that exist only in the restore schema

These names are accepted by a backup restore but nothing in 2.x reads them. A backup made on
1.x can contain them; they land in the store and sit there.

`auto_capitalize_respect_manual_shift_off`, `experimental_suggestions_enabled`,
`status_bar_variations_visible`, `static_variation_bar_mode`, `static_variation_bar_preset`,
`static_variation_bar_base_layer_enabled`, `static_variation_bar_modifier_hold_restoration`,
`quick_launcher_text_field_shortcuts`, and every `clicks_*` row (button modes, bindings,
charging automation, `clicks_power_keyboard_snapshots_v1`, `clicks_power_soc_calibration_*`).
Note that the 1.x migration and the restore translation drop `status_bar_variations_visible`,
`quick_launcher_text_field_shortcuts`, `global_variation_layout_override`, and everything
prefixed `clicks_`, `static_variation_bar_`, `dynamic_variation_bar_` or `pastierina_` before
the schema sees them, so only a backup made by a 2.x build can carry the survivors in.

## 3. Value shapes

### 3.1 The theme object

Every theme row (`keyboard_theme_hardware`, `keyboard_theme_software`, the light/dark slots, the
`theme` member of overrides, saved themes and drafts) is one JSON object with these fields.
Missing fields take the target's default; unknown fields are ignored; a malformed object reads
as the default theme.

| Field | Type | Slate Dark default | Baseline hardware value |
|---|---|---|---|
| `background` | int ARGB | 0xFF000000 | 0xFF111111 |
| `divider` | int | 0xFF2C3136 | 0xFF303030 |
| `normal_key` | int | 0xFF15191D | 0xFF3A3A3C |
| `special_key` | int | 0xFF2B3138 | 0xFF3A3A3C |
| `text_and_icons` | int | 0xFFEFEFEF | 0xFFF8F8F8 |
| `led_inactive` | int | 0xFF303030 | 0xFF303030 |
| `led_active` | int | 0xFF6496FF | 0xFF409CFF |
| `led_locked` | int | 0xFFF76300 | 0xFFFF9F0A |
| `accent` | int | 0xFF6496FF | 0xFF409CFF |
| `cursor_swipe` | int | accent | 0xFF409CFF |
| `key_popup` | int | special key | 0xFF3A3A3C |
| `key_popup_selected` | int | accent | 0xFF409CFF |
| `suggestion` | int | normal key | 0xFF171717 |
| `status_bar_button` | int | special key | 0xFF1C1C1E |
| `key_corner_radius_ratio` | double | 0.10 (software 0.19) | 0.18186983466148376 |
| `chrome_corner_radius_ratio` | double | 0.10 (software 0.20) | 0.3499999940395355 |
| `key_height_scale` | double | 1.0 (software 1.5489256) | 1.2588016986846924 |
| `number_row_height_scale` | double | 0.8 | 0.9711260199546814 |
| `key_width_scale` | double | 1.0 | 0.9414876699447632 |
| `row_gap_scale` | double | 0.0 (software 0.47933885) | 1.0499999523162842 |
| `distribute_horizontal_spacing` | boolean | true | true |
| `ortholinear` | boolean | false (software true) | true |
| `show_leds` | boolean | false | false |
| `suggestions_height_scale` | double | 1.0 as written, 1.4 for the hardware target (software 0.8982954) | 0.8999999761581421 |
| `variations_height_scale` | double | 1.0 (software 0.95914257) | 0.8799999952316284 |
| `key_popup_style` | string `floating` or `classic`; anything else reads as `floating` | `floating` | `floating` |
| `key_popup_attached` | boolean | true | true |
| `key_popup_tail_enabled` | boolean | true | true |
| `key_preview_after_long_press` | boolean | false | false |
| `key_alternates_popup_enabled` | boolean | true | true |

The light system slot, never used since 2.0, would default to background 0xFFF8FAFC, divider
0xFFC7CDD4, normal key 0xFFFFFFFF, special key 0xFFE0E6EE, text 0xFF171A1F, LED inactive
0xFFD1D5DB, LED active and accent 0xFF276EF1, LED locked 0xFFD65A00.

### 3.2 Other JSON rows

Their shapes are given in the key table. Two general rules apply to all of them: a value that
does not parse reads as the default (empty map, empty list, default config) and is logged at
error level, which is the one log level the release build keeps; and every writer rewrites the
whole row, so a row is never partially updated.

The exception to the first rule is `input_style_suggestion_locales`: a stored value that does
not parse makes every write to it refuse rather than replace it with a fresh object, so a corrupt
row is kept rather than silently emptied (2.0.1 changelog, "Nothing is overwritten that could not
be read").

## 4. The factory baseline

### 4.1 The asset

`assets/common/default_settings.json` is the configuration of a known-good Titan 2 Elite (D1).
Its top-level fields are `_comment`, `capturedAt` (`2026-08-29`), `capturedFrom`
(`Titan 2 Elite, PhysiBoard 2.0.0`), and `entries`. Each entry is `{"type": t, "value": v}` with
`t` one of `boolean`, `int`, `long`, `float`, `string`, `string_set` (value a JSON array). This is
exactly the shape a backup's preferences file uses for its entries, so a backup can be used to
author a new baseline. An entry with an unknown type is skipped. The asset carries 47 entries;
they are the "Baseline" column of section 2 and, for reference, the complete list:

`alt_ctrl_speech_shortcut` false, `alt_shift_layout_switch` true, `app_enter_behavior_overrides`
(four apps), `app_enter_behavior_preset` enter_send_shift_newline, `app_raw_mode_packages`
{PersaLink WebAPK}, `auto_capitalize_first_letter` true, `auto_replace_on_space_enter` true,
`auto_show_keyboard` true, `caret_badge_armed_color` -15656921, `dictation_end_silence_ms` 2000,
`dictation_engine` (Google), `dictation_haptics` true, `dictation_mask_offensive` false,
`emoji_picker_expanded_height` false, `fn_long_press_speech` true, `keyboard_layout` qwerty,
`keyboard_theme_hardware` (section 3.1), `launcher_shortcuts` (Space = quick launcher),
`max_auto_replace_distance` 2, `modifier_indicator_mode` menu_bar, `notification_ring_app_colors`
(two apps), `notification_ring_cx` 78.4834, `notification_ring_cy` 80.4834,
`notification_ring_enabled` true, `notification_ring_minutes` 2, `notification_ring_radius`
45.9375, `notification_ring_stroke` 9.625, `physical_keyboard_currency_symbol` $,
`screen_trackpad_enabled` true, `screen_trackpad_step_px` 32, `show_status_bar` true,
`side_key_assistant` true, `smart_backlight_enabled` true, `software_keyboard_mode` auto,
`status_bar_apps` (20 packages), `status_bar_height_dp` 56, `status_bar_slot_left` clipboard,
`status_bar_slot_right_1` microphone, `status_bar_slot_right_2` none, `status_bar_slots_left`
["clipboard"], `status_bar_slots_right` ["microphone","none"], `status_bar_visibility` APPS,
`sym_pages_config` (picker first), `titan2_elite_rounded_corner_insets` true,
`use_keyboard_proximity` true.

Two of these are personal data captured from the maintainer's phone rather than device facts:
the two `notification_ring_app_colors` entries (a childcare app and Google Voice) and the
PersaLink WebAPK id in `app_raw_mode_packages`.

### 4.2 When it runs and what it does

The baseline applies once per baseline version, at every process start, after the 1.x migration.
The version is 1; the marker is `settings_baseline_version`. Raising the version resets every
install once more.

1. If `settings_baseline_version` is at least 1, nothing happens.
2. The asset is read. If it is missing or malformed, the marker is written to 1 anyway (so the
   reset does not retry on every launch), an error is logged, and the store is left as it was.
3. If the store has any rows at all, it is a reset rather than a first run: the entire store is
   written to `settings_before_reset.json` in the app's files directory as one JSON object (string
   sets become arrays). Nothing reads this file; it is the manual way back. A fresh install is not
   snapshotted.
4. The rows in the bookkeeping list are copied aside: `prefs_migrated_v2`,
   `v2_migration_notice_seen`, `tutorial_completed`, `last_seen_whats_new_version`,
   `dismissed_releases`, `hardware_bar_height_migrated`, `impact_defaults_applied`,
   `nav_mode_mappings_updated`, `smart_backlight_applied`, `settings_baseline_version`,
   `side_key_original_package`, `side_key_original_activity`, `side_key_original_captured`,
   `fn_ctrl_captured`, `fn_ctrl_prev_captured`, `fn_ctrl_original_enable`,
   `fn_ctrl_original_function`, `qs_backlight_prev_captured`, `ring_backlight_prev_captured`,
   `ring_backlight_prev`.
5. The store is cleared, the bookkeeping rows are put back, every asset entry is written, and
   the marker is set to 1. The write is synchronous.
6. On a reset (not a first run) two files are also removed: the local `ctrl_key_mappings.json`
   (so the Fn layer falls back to the shipped default on the next read) and the entire
   `pastiera_prefs` file (so the 1.x values can never migrate back, and the migration notice with
   its "Restore my old settings" button stops appearing).
7. One error-level log line records either "seeded a new install with N baseline values" or
   "reset M stored values to the N-value baseline; kept K bookkeeping keys".

Not touched: the personal dictionary, custom layouts under `keyboard_layouts/`, `variations.json`,
`locale_layout_mapping.json`, `user_defaults.json`, and every other store.

The bookkeeping list has a defect. The Fn-to-Ctrl capture is stored under
`fn_ctrl_prev_captured`, `fn_ctrl_prev_enable` and `fn_ctrl_prev_function`, and the tile capture
under `qs_backlight_prev_captured` and `qs_backlight_prev`. The list keeps the two `_captured`
flags but names the values as `fn_ctrl_original_enable`, `fn_ctrl_original_function` and
`fn_ctrl_captured`, which no code writes, and omits `qs_backlight_prev` entirely. After a reset
on a phone that had captured either original, the flag says "captured" and the value reads as the
"unset" sentinel, so Reset to stock restores the sentinel's fallback rather than the true prior
value. The 1.x migration's content list has the same misnaming (section 5.1).

### 4.3 The impact defaults stamp

Immediately after the baseline, a second one-shot writes a fixed list of rows, unconditionally,
guarded by `impact_defaults_applied`. It predates the baseline (it was re-captured from the
maintainer's phone on 2026-08-27 at app 1.2.3, D9) and survives beside it. Its values:

booleans `auto_capitalize_first_letter` true, `fn_long_press_speech` true, `dictation_haptics`
true, `dictation_mask_offensive` false, `smart_backlight_enabled` true,
`titan2_elite_rounded_corner_insets` true, `use_keyboard_proximity` true,
`alt_shift_layout_switch` true, `alt_ctrl_speech_shortcut` false, `auto_show_keyboard` true,
`emoji_picker_expanded_height` false, `screen_trackpad_enabled` true,
`auto_replace_on_space_enter` true, `side_key_assistant` true, `notification_ring_enabled` true;
ints `dictation_end_silence_ms` 2000, `status_bar_height_dp` 56, `screen_trackpad_step_px` 32,
`notification_ring_minutes` 2; strings `status_bar_visibility` ALWAYS, `modifier_indicator_mode`
menu_bar, `status_bar_slot_left` clipboard, `status_bar_slot_right_1` microphone,
`status_bar_slot_right_2` none, `physical_keyboard_currency_symbol` $, `keyboard_layout` qwerty,
`software_keyboard_mode` auto, `app_enter_behavior_preset` enter_send_shift_newline,
`status_bar_slots_left` ["clipboard"], `status_bar_slots_right` ["microphone","none"],
`sym_pages_config` and `app_enter_behavior_overrides` identical to the baseline.

Every value agrees with the baseline except `status_bar_visibility`, and the stamp does not
write `max_auto_replace_distance`, so it never undoes the baseline's 2. The user-facing effect of
the two stamps together is: fresh install gets the baseline with the strip always on; an
upgrade that resets gets the baseline with the strip only in the listed apps.

## 5. The 1.x migration

2.0 renamed the preferences file from `pastiera_prefs` to `physiboard_prefs`. The app's own id
did not change, so the old file sits in the same data directory and is read in place.

### 5.1 What carries over

Runs once, guarded by `prefs_migrated_v2` in the new file, before the first read; the final write
is synchronous. Only rows in the content list are copied, with their type preserved (boolean,
int, long, float, string, string set); everything else falls through to the first-run defaults.
The content list:

`launcher_shortcuts`, `launcher_shortcuts_enabled`, `quick_launcher_command_customizations`,
`sym_mappings_custom`, `sym_pages_config`, `custom_input_styles`, `app_enter_behavior_overrides`,
`app_raw_mode_packages`, `status_bar_apps`, `additional_ime_subtypes`,
`notification_ring_app_colors`, `notification_ring_default_color`, `caret_modifier_badge`,
`caret_badge_armed_color`, `caret_badge_locked_color`, `notification_ring_cx`, `_cy`, `_radius`,
`_stroke`, `keyboard_theme_saved_themes`, `keyboard_theme_drafts`, `keyboard_theme_hardware`,
`keyboard_theme_software`, the four light/dark slots, the two layout-override rows, the two
assignment-mode rows, `side_key_original_package`, `side_key_original_activity`,
`side_key_original_captured`, `fn_ctrl_original_enable`, `fn_ctrl_original_function`,
`fn_ctrl_captured`, `fn_ctrl_prev_captured`, `qs_backlight_prev_captured`, `tutorial_completed`,
`quick_launcher_default_assigned`, `alt_shift_default_initialized`,
`nav_mode_default_mappings_version`, plus the two renamed rows below.

Omissions worth knowing: `sym_mappings_page2_custom`, `user_dictionary_entries`, every
`auto_correct_custom_*` row, `snippets_v1`, `input_style_suggestion_locales`,
`hidden_system_input_styles`, `app_keyboard_nudge_packages`, `fn_ctrl_prev_enable`,
`fn_ctrl_prev_function` and `qs_backlight_prev` are not in the list, so they did not carry
(the 2.0 plan said user dictionary, substitutions and expansions would). `impact_defaults_applied`
is deliberately not carried so the stamp re-runs.

### 5.2 Renames and drops

| From | To |
|---|---|
| `pastierina_status_bar_slots_left` | `status_bar_slots_left` |
| `pastierina_status_bar_slots_right` | `status_bar_slots_right` |

Dropped (never copied, and removed from a restored backup): `status_bar_variations_visible`,
`quick_launcher_text_field_shortcuts`, `quick_launcher_alt_space_in_text_fields`,
`quick_launcher_alt_shortcuts_outside_text_fields`, `global_variation_layout_override`, and every
row starting with `clicks_`, `static_variation_bar_`, `dynamic_variation_bar_` or `pastierina_`
(the two renamed rows are matched first, so they survive their prefix).

One error-level log line records "found N legacy keys, carried M".

### 5.3 The notice and the undo

The old file is left in place. While it exists and `v2_migration_notice_seen` is false, the home
screen shows a dialog titled "You will need to enable PhysiBoard again" explaining the rename,
with "Got it" and "Restore my old settings". Restore replays the old file in full, behaviour rows
included, still applying the renames and drops, then shows "Your previous settings are back" and
hides the restore button. Both buttons mark the notice seen. Because the baseline reset of 2.0.1
deletes the old file on every upgraded install, the notice and the undo are only ever seen by a
phone that upgrades straight from 1.x to 2.0.0.

### 5.4 Alt+Shift default

Once per install (`alt_shift_default_initialized`): if `alt_shift_layout_switch` is absent, it is
set to true when the store already had any row (an existing install) and false otherwise. On a
Titan the baseline writes it true first, so the initialisation never changes anything.

## 6. Other in-place migrations

### 6.1 Hardware bar height lift

Themes saved before the strip's default height was raised carry `suggestions_height_scale` 1.0.
Once per install (`hardware_bar_height_migrated`), the first time any hardware theme is read or
written, every hardware theme row (`keyboard_theme_hardware`, the hardware dark and light slots,
and each `theme` inside the hardware layout overrides) that holds exactly 1.0 is rewritten to 1.4.
Any other value is left alone. An explicit save runs the lift first so the saved value is never
rewritten later.

### 6.2 Fn layer default mappings

`ctrl_key_mappings.json` in the files directory is created from
`assets/common/ctrl/ctrl_key_mappings.json` when absent. If `nav_mode_default_mappings_version` is
below 3, the file is upgraded in place: N, M, U, I get `move_word_left`, `move_word_right`,
`expand_selection_word_left`, `expand_selection_word_right` when they are absent or `none`; B gets
the command `pastiera.toggle_software_keyboard_mode` under the same condition; the marker is set to
3 and `nav_mode_mappings_updated` is stamped.

### 6.3 Legacy fallbacks on read

- `status_bar_visibility` absent: derived from `show_status_bar` (section 2.6).
- `status_bar_slots_left` / `_right` absent or unparsable: derived from the three single-slot
  rows. Unknown button ids in either form read as `none`. Known ids: `none`, `clipboard`,
  `emoji`, `microphone`, `language`, `hamburger`, `software_keyboard_mode`, `settings`,
  `symbols`, `undo`, `redo`.
- `sym_pages_config` without `symPageOrder`: order is emoji, symbols, clipboard (reversed when
  `emojiFirst` is false) followed by emoji_picker.
- `trackpad_suggestion_swipe_threshold` / `trackpad_delete_swipe_threshold` absent: the shared
  `trackpad_swipe_threshold`.
- `launcher_shortcuts` entries without a `launch` object: an `app` entry launches its
  `packageName`; a `quick_launcher` entry launches the internal open-quick-launcher action.
- `launcher_shortcuts` on first read with `quick_launcher_default_assigned` false: if any entry is
  already the quick launcher, only the marker is set; else if Space (62) is free, the quick
  launcher is assigned to Space and the marker set; else nothing changes and the QuickLauncher
  screen shows the "Space already has a shortcut" hint until the user assigns it somewhere.

## 7. Backup and restore

### 7.1 The archive

"Backup now" opens the system file picker for a new document of type `application/zip` named
`physiboard-backup-yyyyMMdd-HHmm.zip` (local time). The archive contains:

- `backup_meta.json`: `{"versionCode": int, "versionName": string, "timestamp": ISO-8601 with UTC
  offset, "components": [paths]}`, pretty-printed with two-space indent.
- `prefs/<file>.json`, one per shared preferences file except `recent_emojis_prefs`:
  `{"name": file, "entries": {key: {"type": boolean|int|long|float|string|string_set, "value": v}}}`.
  A string set is an array; a double is stored as float; a null value is JSON null; an entry of
  any other type is skipped.
- `files/<relative path>` for `ctrl_key_mappings.json`, `variations.json`, `user_defaults.json`,
  `locale_layout_mapping.json`, and everything under `keyboard_layouts/`.

The archive is assembled in a temporary directory under the cache and deleted afterwards. The
result is a snackbar: "Backup completed" or "Backup failed: <reason>", where the reason is
"Unable to open target destination" when the picker's target cannot be opened for writing, or the
underlying error's message.

### 7.2 Restore

"Restore from file" opens the picker for one existing `application/zip` document. Steps:

1. Unzip into a temporary cache directory. Any entry whose resolved path escapes the target
   directory aborts the restore ("Refusing to unzip entry outside target dir").
2. Read `backup_meta.json`. Missing or unreadable: the restore fails with "Not a PhysiBoard
   backup: backup_meta.json is missing or unreadable" and nothing is applied.
3. Read every `prefs/*.json`. A file that does not parse is recorded as unreadable and skipped.
   A file named `pastiera_prefs.json` is treated as `physiboard_prefs` after applying the
   section 5.2 renames and drops to its entries.
4. Restore files first. For each file under `files/`: paths outside the allowed set are skipped;
   a `.json` file that is not valid JSON (object or array) is skipped; an existing target is
   copied to a `.bak` in the cache before being overwritten; `variations.json` is merged (the
   backup's top-level fields are written over the current file's, or over the shipped
   `assets/common/variations/variations.json` when there is no current file) rather than copied;
   everything else is copied over. If any step throws, every overwritten file is put back from
   its `.bak` and the restore fails. Afterwards the layouts directory and the Fn mapping file are
   ensured to exist, and if `variations.json` was restored `variations_updated` is stamped.
5. Restore preferences, file by file. For each entry: if the key already exists in that store on
   this phone it is accepted; otherwise it must be in the restore schema for that store (only
   `physiboard_prefs` has one; its fixed key list is section 2 minus the rows called out below,
   plus the prefixes `auto_correct_custom_` and `clicks_power_soc_calibration_`). Unknown keys are
   skipped. The value is coerced to the schema's type when it has one: strings parse to boolean
   ("true"/"false" only), int, long or float; numbers narrow or widen; anything becomes a string;
   a collection or a single string becomes a set. A value that cannot be coerced is skipped. Each
   store is committed synchronously; a failed commit moves that store's entries to skipped.
6. If `physiboard_prefs:user_dictionary_entries` was applied, or a restored file is named
   `user_defaults.json` at any depth, the broadcast
   `brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED` is sent to the app's own package so the
   running keyboard reloads the dictionary.
7. Snackbar: "Restore completed", or "Restored, but one item could not be applied" / "Restored,
   but N items could not be applied" where N counts skipped keys, skipped files and unreadable
   preference files, or "Restore failed: <reason>".

The restore schema was never updated for most rows added after 1.x. Not in it, and therefore
restored only when the key already exists on the receiving phone: `keyboard_theme_hardware`,
`keyboard_theme_software`, `keyboard_theme_saved_themes`, `keyboard_theme_drafts`,
`keyboard_theme_preview_viewport_scale`, `status_bar_visibility`, `status_bar_apps`,
`status_bar_height_dp`, `caret_modifier_badge`, both caret colours, every `notification_ring_*`,
`smart_backlight_enabled`, every `dictation_*`, `fn_long_press_speech`, `fn_speech_scan_code`,
`sym_long_press_assistant`, `side_key_assistant`, `assistant_action`, every
`app_enter_behavior_*`, `app_raw_mode_packages`, `app_keyboard_nudge_packages`,
`alt_character_layer_binding`, `sym_edit_shortcuts`, `keyboard_layout_auto_by_locale`,
`nav_mode_ctrl_hold_enabled`, `typing_sound_*`, `alt_latch_stays_on_space` is in but
`software_keyboard_mode_runtime_override` is not, `quick_launcher_default_assigned`, and every
marker except `tutorial_completed`, `dismissed_releases`, `variations_updated`,
`nav_mode_mappings_updated`. On a Titan most of these exist because the baseline seeded them; the
ones the baseline does not carry (saved themes, drafts, ring brightness and icons, dictation
punctuation and continuous session, the assistant rows, the nudge list before its first read)
are silently counted as "could not be applied" on a fresh phone. The same rule means a
`embedded_adb` pairing in a backup overwrites this phone's pairing if one exists, and is
ignored if none does.

### 7.3 Serialization summary

| Thing | Where | Shape |
|---|---|---|
| Per-app lists (`status_bar_apps`, `app_raw_mode_packages`, `app_keyboard_nudge_packages`, `additional_ime_subtypes`) | preference string set | `{"type": "string_set", "value": [..]}` |
| Per-app Enter overrides | preference string | JSON array (section 2.7) inside a `string` entry |
| User dictionary | preference string `user_dictionary_entries` | JSON array of `{"w","f","u"}` inside a `string` entry; plus the legacy file `user_defaults.json` if present |
| Substitutions | one preference string per language `auto_correct_custom_<code>` | JSON object with `__name` |
| Expansions (snippets) | preference string `snippets_v1` | JSON object shortcut to text |
| Key mappings, variations, custom layouts, locale mapping | files under `files/` | verbatim copies |

## 8. Settings search

A static catalog of 36 entries drives the search field at the top of Settings and of both hubs
(T2E Tools, Keyboard). Each entry is a title, the screen it lives on, a target, and a keyword
string. A query matches when the trimmed query is a case-insensitive substring of the title, of
the screen title, or of the keywords. Results are listed in catalog order; an empty result shows
"No settings match “<query>”". Picking a result clears the field and navigates; on Settings the
row's description reads "In <screen>" when the screen differs from the title, on a hub the
description is the screen title.

| Title | Screen | Target | Keywords |
|---|---|---|---|
| Screen trackpad | Screen trackpad | Screen trackpad | trackpad cursor swipe screen spacebar hold arrow select |
| Smart Features | Smart Features | Smart Features | typing punctuation spaces |
| Auto-correction | Auto-correction | Auto-correction | autocorrect spell dictionary suggestions typo |
| More Customization | More Customization | Customization (an empty page, section 9.3) | customize variations shortcuts |
| Advanced | Advanced | Advanced | trackpad clipboard backup restore export import debug |
| Reset device settings to stock | Advanced | Advanced | reset stock uninstall undo revert fn ctrl backlight system default factory |
| About | About | About | version credits license update |
| Input Languages | Input Languages | Input languages | language layout azerty qwertz input style |
| App Language | App Language | App language | language locale translate |
| Fn Layer | Fn Layer | Fn Layer | navigation arrows cursor dpad scroll |
| Status Bar Theme | Status Bar Theme | Status Bar Theme | theme dark light color appearance keyboard |
| Status Bar | Status Bar Theme | Status Bar Theme (buttons) | microphone mic emoji hamburger bottom bar status slots |
| PhysiBoard-QuickLauncher | PhysiBoard-QuickLauncher | QuickLauncher | quick launcher apps shortcut launch |
| Enter key behaviour | Enter key behaviour | Enter behaviour | enter send newline whatsapp per app |
| T2E Tools | T2E Tools | Toolbox | device toolbox titan unihertz system tools |
| Remove bloat | T2E Tools | Remove bloat | bloat bloatware debloat uninstall remove disable unihertz vendor apps agui |
| Screen density | T2E Tools | Toolbox | density dpi screen size scale smaller bigger fit more zoom |
| System tweaks | T2E Tools | Toolbox | animation speed faster notification history one handed pixel tweaks |
| Key mapping | T2E Tools | Toolbox | key mapping keys remap fn sym orange side button shortcut bindings |
| Voice | Voice | Voice | voice dictation microphone speech talk transcribe |
| Long-press Fn for speech input | Voice | Voice | voice dictation microphone speech fn hold |
| Vibrate on dictation start/stop | Voice | Voice | vibrate vibration haptic dictation voice |
| Vibration strength | Voice | Voice | vibration strength stronger firmer haptic dictation voice |
| End-of-speech pause | Voice | Voice | pause silence timeout cutoff dictation voice |
| Block offensive words | Voice | Voice | profanity censor swear offensive f*** |
| Hold Sym for the assistant | Voice | Voice | assistant gemini sym hold long press voice ask siri |
| Orange key opens the assistant | Voice | Voice | assistant gemini orange side key func1 shortcut voice ask |
| How the assistant opens | Voice | Voice | assistant action intent listening gemini voice command hands free assist |
| Speech engine | Voice | Voice | engine recognizer speech service google on-device offline dictation voice |
| Let the engine time the pause | Voice | Voice | continuous segmented session pause cutoff dictation voice |
| Automatic punctuation | Voice | Voice | punctuation comma period capitalization formatting dictation voice |
| Capitalize at text start | Smart Features | Smart Features | capital uppercase sentence autocap |
| Double Space inserts period | Smart Features | Smart Features | period full stop double space |
| Text expansion | Smart Features | Smart Features (not Extras, where the page actually lives) | snippet abbreviation expand shortcut |
| Exact typing | Exact typing | Exact typing | terminal termux disable smart per app raw exceptions |
| Text box under the bar | Text box under the bar | Text box under the bar | teams hidden covered text box compose field under bar inset blink |

Target resolution differs by where the search was started:

| Target | From Settings | From a hub |
|---|---|---|
| Advanced | Diagnostics | Settings (Main) |
| Remove bloat | T2E Tools hub | Remove bloat screen |
| Customization | More Customization (empty) | More Customization (empty) |
| Text Input for "Text expansion" | Smart Features screen | Smart Features screen |
| Every other target | the named screen | the named screen |

The two "Vibrate" entries point at Voice, where those rows have not lived since 2.0 (they are on
Sound & Haptics).

## 9. The settings app

### 9.1 Entry points

The home screen (app-shell.md) has six tiles, in order: "T2E Tools", "Keyboard", "Status Bar
Theme", "Status" (with subtitle "all good" or "needs setup"), "Extras", "Settings". Each opens the
settings activity with a destination extra; the keyboard's status bar can also open it. Deep
links and the back stack they build:

| Destination extra | Stack (bottom to top) |
|---|---|
| none, or Settings tile | Settings |
| `customization` (with optional customization extra `variations`, `launcher_shortcuts`, `app_enter_behavior`, `status_bar_buttons`, `keyboard_theme`, `sounds`) | Settings, Customization (Settings is omitted when a customization extra is given) |
| `keyboard_theme_destination` | Settings, Customization at Status Bar Theme |
| `device_sym_layer_editor` | Device SYM layer editor stub only |
| `smart_backlight` | Settings, Smart keyboard backlight |
| `toolbox_destination` | T2E Tools |
| `keyboard_hub_destination` | Keyboard |
| `extras_destination` | Extras |
| `status_destination` | Status only (back returns to the home screen) |
| `remove_bloat_destination` | T2E Tools, Remove bloat |
| `screen_trackpad_destination` | Settings, Screen trackpad |
| `input_languages` | Settings, Input Languages |
| `smart_features_destination` | Settings, Smart Features |
| `auto_correct_destination` | Settings, Auto-correction |
| `voice_destination` | Settings, Voice |
| `raw_mode_destination` | Settings, Exact typing |
| `fn_layer_destination` | Settings, Fn Layer |

Navigation is a push/pop stack inside one activity; push slides the new screen in from the right
over 250 ms, pop slides it out to the right. Each screen keeps its scroll position while it is
below another. Back at the bottom of the stack finishes the activity. Pushing the destination
already on top is a no-op. Opening Settings runs the GitHub update check once (app-shell.md).

### 9.2 The map

Labels are the English resource strings. "switch", "chips", "slider", "picker" describe the
control; ">" means the row navigates.

- **Settings** (title "Settings")
  - search field "Search settings…"
  - "Status" > Status: "Check PhysiBoard is set up correctly"
  - "Backup now": "Export all settings and custom layouts to a ZIP" (section 7.1)
  - "Restore from file": "Import a PhysiBoard backup ZIP" (section 7.2)
  - "Diagnostics" > Diagnostics: "Physical key-event logger and debug export" (app-shell.md)
  - "Reset device settings to stock" (section 9.5)
  - divider "About"
  - "About" > About: "Version, licence, and credits"
  - "Updates" ("Checking for updates…" while busy): "Check the latest release on GitHub."; shown
    only when GitHub update checks apply to this build; results are toasts "Unable to reach
    GitHub." / "App is up to date." or the update dialog
- **Status** (title "Status"): rows "PhysiBoard enabled", "Active keyboard", "Input language",
  "Smart backlight" (On/Off), "App version", plus the privileged step outcomes (app-shell.md)
- **T2E Tools** (title "T2E Tools", intro "Titan-specific tools. These change the phone itself
  rather than the keyboard, so anything here that outlives an uninstall can be undone with Reset
  device settings to stock.")
  - search field
  - device setup card (pairing state; broker-privileged-toolbox.md)
  - "Smart keyboard backlight" > "Keep the keyboard lit in the dark, past the 30s limit"
    - pairing status, "Set up" / "Check again" / "Reapply", switch "Smart backlight"
  - "Remove bloat" > "Disable or uninstall the Unihertz apps Android won't let you remove"
    - preset cards ("In testing" badge on the Android Auto one), tiers, per-app Disable /
      Uninstall / Restore, "Restore all"
  - "Screen density" > "Fit more on screen, or make everything bigger": slider, "Apply", "Back to
    stock", keep/revert confirmation
  - "System tweaks" > "Settings Android supports that this phone never shows you": "Animation
    speed" chips Off / Fast / Normal, "Notification history" switch, "One-handed mode" switch,
    "Put all of these back to stock"
  - "Notification ring" > "A glow around the camera hole while the screen is off"
    - "Ring on new notifications" switch; permission rows "Notification access", "Show over the
      lock screen", "PhysiBoard notifications" with grant buttons; "Keep the screen on for" slider
      1 to 60 min; "Ring brightness" chips Dim / Normal / Bright; "Show app icons" switch; "Keep
      the keyboard dark" switch; "Default colour" picker; "App colours" list with add/remove and a
      picker; "Fit the ring to the lens"; "Try it"
  - "Screen trackpad" > "Hold a key and swipe anywhere on the screen to move the cursor"
    - "Enable screen trackpad" switch; "Display over other apps" permission row; "Trigger key"
      chips Space / Left Shift / Right Shift / Either Shift / Sym; "Activate by" chips Hold /
      Double tap / Single tap; "Sensitivity" slider 8 to 64; "Show on-screen hint" switch
  - "Key mapping" > "Every physical key and what it currently does": an inventory; each row
    routes to the screen that owns the key (Fn to Fn Layer, Sym and the orange key to Voice,
    Space to Screen trackpad)
- **Keyboard** (title "Keyboard", intro "Everything about how the keyboard behaves when you type.")
  - search field
  - "Smart Features" > "Auto-capitalization, spacing and text expansion"
    - header "Capitalization": "Capitalize at text start", "Capitalize after sentence end"
    - header "Spacing & punctuation": "Double Space inserts period"
    - header "Keyboard behavior": "Release Alt with Space", "Show keyboard automatically"
    - header "Currency Symbol": chips € $ £ ¥ ₹ ₽ ₿ ¤
    - header "Delete": description, checkboxes "Shift + Backspace", "Alt + Backspace", hint,
      "Open Fn Layer settings" > Fn Layer, selection hint
    - collapsed "Advanced": "Shift in all text fields", "Punctuation spacing" (a before/after
      table dialog with "Reset" and a help dialog), "Space after comma", "Hyphen to dash" (with
      en/em choice), "Quotes inside words", "Quotation mark style" (five styles), "Backspace at
      line start"
  - "Auto-correction" > "Corrections, suggestions and the personal dictionary"
    - "Text replacements" switch; "Manage text replacements" > Text Replacements (system
      language, other languages, "Custom Substitutions" > Edit with "Add Correction"); "Automatic
      correction" switch; "Maximum correction distance" slider 0 to 3 ("Off" at 0); "Personal
      dictionary" > User dictionary (search, add, edit, delete); "Suggestions while typing";
      "Accent & spelling marks"; "Keyboard Proximity Ranking"; "Edit Type Ranking"
  - "Voice" > "Hold Fn to dictate, and the assistant triggers"
    - intro; header "Triggers": "Long-press Fn for speech input"
    - header "Transcription": "Speech engine" picker (system default, on-device, each installed
      service); "Automatic punctuation"; "Block offensive words"; "End-of-speech pause" slider
      ("System default" at 0, else "N s"); "Let the engine time the pause"
    - header "Voice assistant": "Orange key opens the assistant"; "Hold Sym for the assistant";
      "How the assistant opens" (Auto / Voice command / Hands free / Assist)
  - "Status Bar Theme" > "Colours, LEDs, and which buttons sit on the bar"
    - "Choose a preset" (presets incl. Synthwave, Vapourwave, Hazard, Blueprint, Forest Floor,
      Rose Gold, Ink and Paper); "Keyboard UI Preview" (zoomable, "Editing draft" badge);
      "Customize colors" ("N required values missing", "Create a custom theme", colour rows
      Background, Dividers, Normal keys, Special keys, Text and icons, Accent, Suggestions,
      Status bar buttons, Cursor swipe, Key popup, Key popup selected, LED inactive, LED active,
      LED locked, geometry rows, "Save and use theme"); saved theme list with delete, duplicate,
      export (copyable string), import (paste, "invalid" error); per-layout override editor
    - divider "Status Bar"; description; divider "Buttons": "Left buttons", "Right buttons" (add /
      remove slots from the eleven button ids); divider "Show status bar": Always / Never / Only
      in these apps (app list with add/remove), "Bar height" 36 / 48 / 56 / 64; divider
      "Modifiers": "Show modifiers at the cursor" (with an overlay-permission prompt), "One press
      colour", "Locked colour"; "Reset"
    - "Show LEDs" switch
  - "Sound & Haptics" > "Typing sounds and vibration" (section 9.4)
  - "Exact typing" > "For terminals, SSH and code: what you type is what goes in, nothing
    corrected or capitalised": description, WebAPK note, app list with search
  - "Text box under the bar" > "For apps like Teams that leave the text box hidden under the bar
    until the keyboard moves": description, app list
  - "Enter key behaviour" > "Configure app-specific Enter and newline handling"
    - "App-specific Enter behaviour" switch; "Messaging preset" (Send with Enter, Shift+Enter
      newline / Newline with Enter, Ctrl+Enter sends / Newline only / Custom / App default); "App
      overrides" list with "Add app" (search), per-app desired behaviour, send strategy,
      additional send shortcut, remove, and a show/hide manual override
- **Extras** (title "Extras")
  - "PhysiBoard-QuickLauncher" > "Quick launching, SYM shortcuts, and launcher key assignments"
    - intro; hint when Space is taken; "Homescreen shortcuts" switch; "SYM key shortcuts" switch;
      "Behaviour" > ; "Assigned launcher keys" > (a row per key; tap to assign or replace a
      command; description names the key that opens the quick launcher)
    - **Behaviour**: "QuickLauncher behaviour" dropdown; "Open unique match automatically";
      "Show only top search results"; "Use active keyboard layout"; "Typo-tolerant search";
      ranking info button and dialog; "Animation duration" slider
    - **Appearance** (unreachable, section 9.3): "Launcher width" slider; "Pill mode"; "Entry
      appearance" with "Highlight favorites in list" (colour swatch + switch), "Tint entries from
      app icon colors", "Use static top-match highlight color" (swatch + switch), "Show search
      alias before entry name"; "QuickLauncher entries" per-source switches and "Customize
      entries" dialog (search, All / Favorites filters, star, reorder, hide, alias, colour)
  - "Input Languages" > "Input languages and layouts"
    - "Installed dictionaries"; the input style list (system badge, hide/show, edit, delete);
      "Add Input Style" (locale picker, custom locale, layout picker with "No dictionary" warning);
      "Automatic Layout Mapping" switch; header "Layout Switch Shortcuts": "Alt+Shift Layout
      Switch", "Alt+Enter Layout Switch", "Ctrl+Space Layout Switch"; "App Language" dropdown
      ("System default" plus the ten locales); "Suggestion dictionaries" per style
  - "Text expansion" > "Type a short trigger and have it expand into whatever you saved."
    - header "Snippets": "Enable snippets"; "Snippet prefix" (validated, error text);
      "Show matches in" Off / Floating popup / Suggestion bar; "Accept with Tab"; "Accept with
      Enter"; "Manage snippets" (list, add, edit, delete); "Expand exact match with Space";
      "Accept prefix match with Space"
- **About** (title "About"): build info "PhysiBoard IME - Palsoftware 2026", "Device: X;
  Keyboard: Y", "App Language" > App Language, "Show Tutorial", credits, "Report a problem"
  (app-shell.md)
- **App Language** (title "App Language"): "System default" plus one row per locale
- **Diagnostics** (title "Diagnostics"): test field, key-event recorder with "Record" / "Stop",
  "Clear", "View", "Share", include switches for suggestions, raw trackpad, autocorrections, the
  last key event (action, keycode, scancode, unicode, output, modifiers) (app-shell.md)
- **SYM customization** (a separate activity reached from the keyboard, title "Customize SYM
  Keyboard"): "Arrange SYM pages order" (five pages, move up/down, edit layer), "Alt character
  layer", header "SYM behaviour and display": "Sym+C/V/X/A: copy, paste, cut, select all",
  "Auto-Close SYM Layout", "Also close after on-screen SYM keys", "Larger emoji picker", "Reset
  to Default" with confirmation (layers-sym-alt.md)
- **Fn Layer** (title "Fn Layer", reached from Smart Features, Key mapping, search and the
  `fn_layer_destination` deep link): "Fn Layer guide"; "Set Fn key to Ctrl" card with warning,
  "already set" state, apply and reset buttons and status lines; "Enable Fn Layer" switch;
  "Ctrl-hold navigation" switch; "Layout-aware app Ctrl shortcuts" with info dialog; the 26-key
  grid with a per-key editor (type: keycode / action / native Ctrl / command / none; "Use
  default"); "Revert to Default" (keys-and-modifiers.md)

Screens that exist in the build but that no row, tile, search entry or deep link reaches:
"Key Behaviour & Timing" (long press slider and the on-screen keyboard section), "Built-in
Keyboards" (profile override and Titan 2 layout alignment), "Languages" (a legacy activity),
"More Customization" (reachable, but empty), the QuickLauncher "Appearance" screen, and the
IME test and layout viewer developer screens (the viewer is reachable from the layout editor
inside Input Languages).

### 9.3 Customization

"More Customization" is the container that hosts the QuickLauncher, Enter key behaviour,
Status Bar Theme and Sound & Haptics screens. Its own page is a title with an empty body: every
row it used to hold moved to a hub in 2.0, and the search entry "More Customization" and the
`customization` deep link without a sub-destination land on that empty page. Back from any hosted
screen returns to whichever screen pushed it, not to the empty page, because a deep-linked
sub-destination replaces the container's root.

The QuickLauncher page passes an "open appearance" action to its body, but the body draws no row
for it. The Appearance screen and everything on it (section 2.12) are unreachable in 2.x; the
rows keep their stored values and the launcher still honours them.

### 9.4 Sound & Haptics

Title "Sound & Haptics". Rows in order:

1. "Typing Sounds": a chooser with Off, Keyboard click, Typewriter, Custom sound pack…, and an
   output chooser Media volume / System sounds / Notifications. Choosing the custom pack opens a
   file picker for a `.zip`. The zip is extracted into a staging directory; only entries whose
   path contains one of the group folders `normal`, `space`, `backspace`, `enter`, `modifier`
   and whose extension is `ogg`, `wav`, `mp3` or `m4a` are kept, renamed `001.ext`, `002.ext` in
   arrival order; `__MACOSX/` entries are ignored; a pack over 96 files, a file over 2 MiB, or a
   pack over 16 MiB aborts; an entry escaping the target aborts; a pack with no `normal` file is
   rejected. Only then is the previous pack moved aside and replaced; failure restores the old
   pack. Success writes the three `typing_sound_custom_*` rows, sets the mode to `custom`, and
   toasts "import success"; failure toasts "import failed" and leaves the mode alone. A pack that
   is a single file rather than a directory is served as the `normal` group.
2. "Tap vibration" switch (`tap_haptic_use_system`).
3. "Custom vibration: N ms" slider 5 to 80, shown when tap vibration does not use the system.
4. "Vibrate on dictation start/stop" switch.
5. "Vibration strength" chips Light / Standard / Strong, shown only while row 4 is on; picking a
   chip plays that cue immediately.

### 9.5 Reset device settings to stock

The row on Settings reads "Reset device settings to stock" with the description "Undo the
system-wide changes PhysiBoard made (the Fn key mapping and keyboard backlight), restoring your
device to stock. Do this BEFORE uninstalling; uninstalling alone won't undo them." Tapping it
shows a dialog "Reset device settings to stock?" with the text "This restores the Fn key mapping
and keyboard backlight to your device's stock settings. Your PhysiBoard preferences are kept. You
can re-apply these features anytime." and buttons "Reset to stock" / "Cancel". While running the
row shows a spinner and cannot be tapped again. The result snackbar is one of "Device settings
restored to stock.", "Grant PhysiBoard "Modify system settings", or pair wireless debugging, then
try again.", or "Some settings were restored. A reboot may be needed for changes to fully apply."
What is reverted, and how the captured originals (section 2.4, 2.8, 2.10) are used, is in
broker-privileged-toolbox.md. The preference store is not touched by this action; the only
settings reset in 2.x is the baseline reset of section 4, which the user cannot trigger.

The "Advanced" screen that held these rows before 2.0 no longer exists. A collapsible "Advanced"
section still appears inside Smart Features; it opens collapsed and remembers its state only
while the screen is alive.

## 10. Titan-specific facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The baseline was captured from a Titan 2 Elite running PhysiBoard 2.0.0 on 2026-08-29 | the asset's `capturedAt` and `capturedFrom` fields |
| D2 | The fitted ring on that phone is centre (78.4834, 80.4834) px, radius 45.9375 px, stroke 9.625 px in window pixels; panels differ by a few pixels so a refit screen exists | baseline; 1.2.3 changelog "Fit the ring yourself again" |
| D3 | PersaLink installs as the Chrome WebAPK `org.chromium.webapk.a5d49fddf77614419_v2` and is typed raw by default | baseline `app_raw_mode_packages` |
| D4 | The Google recognition service component `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService` is present and is the dictation engine on the maintainer's phone | baseline `dictation_engine` |
| D5 | The Fn key's scan code is 251 regardless of the keycode the vendor remaps it to | the `fn_speech_scan_code` default and its source comment |
| D6 | The Titan screen is 1080 by 1200 at density 300, that is 574 by 640 dp, which is why the colour picker's height is derived from the window | 2.0 settings walkthrough, colour wheel item |
| D7 | The ROM does not surface app logs for a non-debuggable build, so the migration's error-level line never reached logcat in the rehearsal | 2.0 overhaul plan, pre-flight 5 |
| D8 | The stock fallback keyboard is `com.iqqijni.bbkeyboard`, already enabled beside PhysiBoard, so the 2.0 rename could not leave the phone unable to type | 2.0 overhaul plan, pre-flight 2 |
| D9 | The impact defaults were re-captured from the maintainer's phone on 2026-08-27 at app 1.2.3; the earlier 1.0.4 snapshot had drifted (`show_status_bar` false, silence 2500 vs 2000) | source comment on the stamp; overhaul plan pre-flight 1 |
| D10 | The wireless-debugging pairing lives in its own `embedded_adb` file, so the 2.0 file rename did not touch it | overhaul plan, pre-flight 3 |
| D11 | Every Titan 2 Elite is the same hardware on the same firmware, which is the justification for a whole-store reset to one configuration | baseline source comment; 2.0.1 changelog |

## 11. Edge cases, quirks and known bugs

| Situation | Behavior | Why |
|---|---|---|
| Fresh install on a Titan | Store ends with the baseline except `status_bar_visibility` = `ALWAYS` | The impact defaults stamp runs after the baseline and overwrites it (section 1.1) |
| Upgrade from a 2.0.0 install | Whole store replaced by the baseline, user themes and ring fit included; snapshot written; `ctrl_key_mappings.json` and `pastiera_prefs` deleted; onboarding not repeated | Baseline version 1 (section 4.2) |
| Upgrade from 1.x that had captured the Fn original or the tile original | After the reset the `_captured` flags survive but the values do not | Bookkeeping list names the value rows wrongly (section 4.2) |
| The baseline asset fails to parse | Marker set, store untouched, error logged | Avoids retrying the reset on every launch |
| `modifier_indicator_mode` | Written by both stamps, read by nothing | Indicator pinned to the status bar in 2.0; the writers were not updated |
| `long_press_threshold` absent | The layer engine waits 500 ms, every other reader 300 ms | Two readers with two fallbacks |
| `global_variation_layout_override` present in a 1.x store | Dropped by the migration, but still honoured if written by a restore of a 2.x backup | Dropped list and reader disagree |
| Restoring a 2.x backup onto a fresh phone | Rows not in the restore schema and not yet present are skipped and counted | Schema never updated past 1.x (section 7.2) |
| Restoring a backup that contains `embedded_adb` | Pairing overwritten if the phone has one, ignored if not | "already present" acceptance rule |
| Backup zip with a path traversal entry | Restore fails before anything is applied | Canonical path check |
| Backup without `backup_meta.json` | "Restore failed: Not a PhysiBoard backup: backup_meta.json is missing or unreadable" | 2.0.1 "Failures say so" |
| A `prefs/*.json` file that does not parse | Counted once in "could not be applied"; the rest restores | Unreadable files are reported, not fatal |
| `variations.json` in a backup | Merged over the current or shipped file | Older backups lack newer top-level fields |
| Restore applies `user_dictionary_entries` or restores `user_defaults.json` | One dictionary refresh broadcast, even if both match | Post-restore rule is deduplicated |
| Search "Advanced" from Settings vs from a hub | Diagnostics vs Settings | Two resolvers |
| Search "Remove bloat" from Settings | Lands on T2E Tools, not the bloat screen | Settings resolver maps it to the hub |
| Search "More Customization" | An empty titled page | Container root has no rows (section 9.3) |
| Search "Text expansion" | Smart Features, where it is not | Catalog entry not repointed at Extras |
| Search "Vibrate on dictation start/stop" | Voice, where the row is not | Rows moved to Sound & Haptics in 2.0 |
| QuickLauncher "Appearance" | Cannot be opened | No row calls the action (section 9.3) |
| QuickLauncher assignment on a phone whose Space already had a shortcut | Hint shown; nothing auto-assigned until the user assigns the quick launcher somewhere | Space is not stolen |
| `app_enter_behavior_preset` set to "Newline only" in the UI | Stored as `enter_newline_only`, read back as `app_default` | Normalizer does not know the value the UI offers |
| Status bar slots: an array present but a legacy single row stale | The array wins; writing the array rewrites the singles | Arrays are authoritative |
| `notification_ring_radius` absent but `_cx` present | No override; fitted default used | Presence of `_radius` decides |
| Saving a theme named "custom" when "Custom" exists | Replaces it | Names compare case-insensitively |
| Typing sound pack with no `normal` files | Rejected, previous pack kept | Section 9.4 |
| Store rows written by the settings app while the keyboard is up | Most take effect live; a few need the keyboard to restart | Per-subsystem listeners |
| The "Untested on this phone" dialog | Shown once on a Titan 2 that is not an Elite, never on an Elite | `untested_device_notice_seen` |
| The 1.x migration notice | Only seen by a phone going 1.x to 2.0.0; 2.0.1's reset deletes its source | Section 5.3 |
| The impact defaults' comment says it only sets rows the user has not chosen | It sets every row unconditionally | The guard is the marker, not per-row presence |

## 12. Test cases

Each is a JVM test over an in-memory preference store and the asset text.

1. Fresh store, apply the baseline: the store contains `auto_capitalize_first_letter`,
   `status_bar_height_dp` = 56, `keyboard_theme_hardware`, `sym_pages_config`,
   `notification_ring_cx`, `notification_ring_radius`, `launcher_shortcuts`, `status_bar_apps`;
   it does not contain `tutorial_completed`, `last_seen_whats_new_version`,
   `side_key_original_package`, `side_key_original_activity`, `side_key_original_captured`;
   no snapshot file exists; `settings_baseline_version` = 1.
2. Store holding `status_bar_height_dp` 36, `auto_capitalize_first_letter` false,
   `keyboard_theme_hardware` `{"background":-1}`, `notification_ring_cx` 12.5,
   `some_stale_1x_key` "junk": after the baseline, height 56, capitalize true, theme not
   `{"background":-1}`, cx not 12.5, stale key absent, snapshot file exists and mentions
   `status_bar_height_dp`.
3. Store holding `tutorial_completed` true, `last_seen_whats_new_version` "2.0.0",
   `prefs_migrated_v2` true, `side_key_original_package` "com.example.assistant",
   `side_key_original_activity` "com.example.assistant.Main", `side_key_original_captured` true,
   `qs_backlight_prev_captured` true: all survive the baseline unchanged.
4. Store with one row and a local `ctrl_key_mappings.json`: after the baseline the file is gone.
   Empty store with the same file: the file remains.
5. Store with one row and a non-empty `pastiera_prefs`: after the baseline `pastiera_prefs` is
   empty and the migration notice condition is false.
6. Apply the baseline, then set `status_bar_height_dp` 64, then apply twice more: still 64.
7. Baseline then impact defaults on an empty store: `status_bar_visibility` = `ALWAYS`,
   `max_auto_replace_distance` = 2, `impact_defaults_applied` true. Baseline on a store that
   already holds `impact_defaults_applied` true, then the stamp: `status_bar_visibility` = `APPS`.
8. Legacy file with `launcher_shortcuts`, `sym_mappings_custom`, `keyboard_theme_hardware`,
   `notification_ring_radius` 45.9375, string sets `status_bar_apps` {com.whatsapp, com.Slack}
   and `additional_ime_subtypes` {en_US:qwerty}: after migration all present in the new file with
   the same types and values.
9. Legacy `pastierina_status_bar_slots_left` `["clipboard"]`: new file has
   `status_bar_slots_left` `["clipboard"]` and no `pastierina_status_bar_slots_left`.
10. Legacy `show_status_bar` false, `auto_capitalize_first_letter` false, `status_bar_height_dp`
    36: none present after migration.
11. Legacy `status_bar_variations_visible`, `static_variation_bar_preset`,
    `dynamic_variation_bar_slot_count`, `clicks_button_mode`, `pastierina_mode_override`: none
    present after migration.
12. Legacy `tutorial_completed` true, `quick_launcher_default_assigned` true,
    `alt_shift_default_initialized` true, `nav_mode_default_mappings_version` 3,
    `side_key_original_captured` true, `side_key_original_package` "com.google.android.apps.bard":
    all present after migration. Legacy `impact_defaults_applied` true: absent after migration.
13. Migrate, change `launcher_shortcuts` in the new file, migrate again: the change stands. The
    legacy file still holds its rows. Empty legacy file: the new file holds only
    `prefs_migrated_v2`.
14. Restore-legacy after migration with legacy `show_status_bar` false, `status_bar_height_dp`
    36, `status_bar_variations_visible` true, `pastierina_status_bar_slots_left` `["clipboard"]`:
    show false, height 36, variations row absent, slots-left `["clipboard"]`.
15. A backup entry set for `pastiera_prefs` containing `pastierina_status_bar_slots_right`,
    `status_bar_variations_visible`, `clicks_button_mode` and `keyboard_layout`: translated to
    `status_bar_slots_right` and `keyboard_layout` only.
16. Hardware theme JSON with `suggestions_height_scale` 1.0 in the fixed row, both slots and an
    override: after the first read all four hold 1.4 and `hardware_bar_height_migrated` is true;
    a row holding 1.2 is untouched; a second read changes nothing.
17. `status_bar_visibility` absent and `show_status_bar` absent: ALWAYS. Absent and false: NEVER.
    Present `APPS` with `show_status_bar` false: APPS. Writing NEVER writes `show_status_bar`
    false; writing APPS writes it true. `APPS` with apps {a} and package a: shown; package b: not;
    null package: not.
18. `status_bar_apps` absent: first read returns the 20-package set and writes it.
19. `status_bar_slots_left` absent, `status_bar_slot_left` `emoji`: read gives `["emoji"]`.
    `status_bar_slots_right` `["bogus","undo"]`: read gives `["none","undo"]`. Writing
    `["clipboard","undo"]` to the right writes `status_bar_slot_right_1` clipboard and
    `status_bar_slot_right_2` undo; writing `[]` writes both singles `none`.
20. `sym_pages_config` `{"emojiFirst": false}`: order symbols, clipboard, emoji, then
    emoji_picker, then device appended. The baseline string: enabled pages emoji_picker and
    symbols only.
21. `launcher_shortcuts` `{}` and marker false: first read assigns keycode 62 to the quick
    launcher and sets the marker. `{"62": {"type":"app","packageName":"x"}}` and marker false:
    nothing assigned, "blocked" is true. `{"70": {"type":"quick_launcher"}}`: marker set, no
    Space entry added.
22. `app_enter_behavior_overrides` with two entries for the same package and one blank package:
    reads as the first entry only. Preset `enter_newline_only`: reads as `app_default`.
23. Backup restore with meta present, `prefs/physiboard_prefs.json` holding `keyboard_layout`
    "azerty" (string), `bounce_keys_delay_ms` "120" (string), `additional_ime_subtypes` "a:b"
    (string), `totally_unknown` true: layout applied as string, delay applied as long 120,
    subtypes applied as the set {a:b}, unknown key skipped and counted; result "Restored, but one
    item could not be applied".
24. Backup with no `backup_meta.json`: failure "Not a PhysiBoard backup: backup_meta.json is
    missing or unreadable", store unchanged.
25. Backup whose `prefs/physiboard_prefs.json` is not JSON: success with one skipped item.
26. Backup with `prefs/physiboard_prefs.json` holding `user_dictionary_entries`: exactly one
    dictionary refresh broadcast. Backup with only `files/user_defaults.json`: one broadcast.
    Backup with neither: none. Applied key `pastiera_prefs:keyboard_layout` alone: none.
27. Backup with `files/variations.json` `{"variations": {"a": ["à"]}}` on a phone whose file has
    `emailVariations`: the merged file keeps `emailVariations` and takes the backup's
    `variations`; `variations_updated` is stamped.
28. Zip entry named `../evil.json`: restore fails, nothing applied.
29. Search "mic" from Settings: results include "Status Bar" (In Status Bar Theme), "Voice",
    "Long-press Fn for speech input"; search "zzz": the no-match text with the query.
30. Typing sound zip with `normal/a.ogg`, `space/b.wav`, `__MACOSX/normal/c.ogg`, `x/d.mp3`:
    imported files `normal/001.ogg`, `space/002.wav`; mode becomes `custom`. Zip with only
    `enter/a.ogg`: rejected.
31. Currency symbol written "£": reads "£"; written "₩": reads "€". Dash style "em_dash": kept;
    "dash": `en_dash`. Quote style "welsh": `german_guillemets`.
32. `notification_ring_minutes` written 2: read 2 (no clamp on the store; the slider clamps 1 to
    60). `screen_trackpad_step_px` written 200: read 64. `quick_launcher_width_percent` written
    10: read 50. `dictation_end_silence_ms` written 20000: stored 10000.

## 13. Keep / Drop for 3.0

| Item | Verdict | Reason |
|---|---|---|
| One preference file with typed rows, plus a typed JSON export identical in shape to the baseline asset | Keep | The asset-equals-backup symmetry is the only reason the baseline was cheap to author |
| Baseline asset applied once per version, with snapshot and bookkeeping keep-list | Keep, fix the misnamed keep-list rows | It is the mechanism that made every Titan the same; the bug is a typo |
| Impact defaults stamp | Drop | A second first-run stamp that fights the baseline on one row; fold its intent into the asset |
| 1.x migration, legacy file, migration notice and "Restore my old settings" | Drop | 3.0 is a new app; 2.x users import a backup instead |
| Alt+Shift "existing install" initialisation | Drop | Only meaningful for pre-1.x stores |
| Hardware bar height lift | Drop | Only 1.x themes carried 1.0; the importer can lift on the way in |
| Fn mapping file version upgrade | Undecided | Depends on whether 3.0 keeps a user-editable mapping file (keys-and-modifiers.md) |
| Backup: files list, zip layout, meta, validity checks, rollback, dictionary refresh | Keep | Proven; the failure messages were audited in 2.0.1 |
| Backup: the fixed restore schema | Drop, replace with "every key the importer knows" | It silently skips most 2.x rows |
| Backing up `embedded_adb` and `physiboard_toolbox` | Drop from the archive | A pairing secret and undo journals are not settings; restoring them onto another phone is wrong |
| Legacy mirror rows `show_status_bar`, `status_bar_slot_*` | Drop; importer maps them into the visibility and slot arrays | Only existed for pre-1.2.2 readers |
| `modifier_indicator_mode`, `pastierina_*`, `swipe_incremental_threshold`, `global_variation_layout_override`, assignment-mode and light/dark theme slots, `keyboard_theme_software`, every `software_keyboard_*` row, `titan2_layout_enabled`, `physical_keyboard_profile_override`, every `clicks_*` and `static_variation_bar_*` row | Drop | Dead, on-screen-keyboard only, or other-device only |
| Long-press modifier and threshold (`long_press_modifier`, `long_press_threshold`) | Keep, with a screen: Keyboard > "Long Press behaviour", a hold-time slider (50 to 1000 ms) and a picker for Alt / Shift / Variations / Sym | keys-and-modifiers.md section 8 keeps every mode, and `shift` is what BlackBerry 10 users expect from a held letter (brobata/physiboard#9); the 2.x screen went with the Modifiers page, so the row has been a constant nobody chose |
| Keyboard-surface swipe rows (`trackpad_gestures_enabled`, `trackpad_gesture_add_word_enabled`, `trackpad_suggestion_swipe_threshold`) | Keep, with a screen, if trackpad-caret-nav.md keeps the native provider: Keyboard > "Keyboard swipe", an enable switch, an add-word switch and a distance slider | Since 1.0.2 nothing reachable turns the gesture on (trackpad-caret-nav.md section 3.2), so no 2.x user has exercised the native provider on the Elite; brobata/physiboard#11 tracks the capture that decision needs |
| Rows with no screen in 2.x: bounce keys, overlapping keys, tap latches, accessibility rows, French spacing, swipe-to-delete, `trackpad_provider`, clipboard rows, `toast_on_layout_switch`, `alt_ctrl_speech_shortcut`, `fn_speech_scan_code`, `keyboard_layout_list`, `software_keyboard_mode_toggle_toasts`, debug logging rows | Undecided per row; drop the row unless its subsystem document keeps the behavior | A setting without a screen is a constant; keep the behavior, not the row |
| Settings search catalog | Keep, regenerate from the screen map so titles and targets cannot drift | Four entries already point at the wrong place |
| Hub structure: home tiles, T2E Tools, Keyboard, Extras, Settings with maintenance rows and About | Keep | The 2.0 walkthrough settled it with the maintainer |
| "More Customization" container | Drop | An empty page whose only job is hosting |
| QuickLauncher Appearance screen and its ten rows | Undecided | Unreachable in 2.x, so nobody has used it; decide with expansion-clipboard-pickers-launcher.md |
| Sound & Haptics: typing sounds, custom packs, tap haptic, dictation haptic and strength | Keep the dictation cue and strength; undecided on typing sounds and packs | The cue is the only feedback dictation has; packs are an upstream soft-keyboard feature |
| Reset device settings to stock row and dialog | Keep | The only way to undo system writes that outlive an uninstall |
| Diagnostics row | Keep | app-shell.md |
| Updates row | Keep | app-shell.md |
| `settings_before_reset.json` snapshot | Keep | Costs nothing, is the way back |
| Personal data in the baseline (two ring colours, the PersaLink WebAPK id) | Drop from the asset | Device facts belong in the asset, one person's contacts do not |

## 14. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsCatalog.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsBaseline.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsMigration.kt
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/default_settings.json
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsHubScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsAdvancedSection.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsSectionDivider.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/MaintenanceSettingsRows.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/CustomizationSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ExtrasScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/PhysiBoardApplication.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/MainActivity.kt (home tiles, migration and untested-device notices)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SymPagesConfig.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyboardThemeModel.kt (default geometry constants)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/Punctuation.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingBrightness.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/commands/CommandModel.kt (source ids)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/expansion/TextExpansionModels.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PrivilegedDiagnostics.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AltSymManager.kt (long-press fallback)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt (the `current_sym_page` read)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/UserDictionaryStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AppListHelper.kt, toolbox/RevertibleChange.kt, toolbox/RemovalJournal.kt, data/emoji/RecentEmojiManager.kt, /home/disdiqqq/projects/pastiera/app/src/main/java/moe/shizuku/manager/adb/EmbeddedAdbInit.kt (the other stores)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/RestoreManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/ZipHelper.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AppBroadcastActions.kt
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml (labels)
- Screen files read for row order and labels: TextInputSettingsScreen.kt, AutoCorrectionCategoryScreen.kt, AutoCorrectSettingsScreen.kt, AutoCorrectEditScreen.kt, VoiceSettingsScreen.kt, KeyboardTimingSettingsScreen.kt, ScreenTrackpadSettingsScreen.kt, TextExpansionSettingsScreen.kt, AppLanguageSettingsScreen.kt, NavModeSettingsScreen.kt, HardwareKeyboardSettingsScreen.kt, KeyboardLayoutSettingsScreen.kt, TypingSoundSettingsRow.kt, KeyboardThemeSettingsScreen.kt, StatusBarButtonsScreen.kt, CustomInputStylesScreen.kt, StatusScreen.kt, AboutScreen.kt, DiagnosticsScreen.kt, KeyMappingScreen.kt, SmartBacklightScreen.kt, NotificationRingScreen.kt, AppRawModeScreen.kt, AppKeyboardNudgeScreen.kt, DeviceSetupCard.kt, LauncherShortcutsScreen.kt, AppEnterBehaviorScreen.kt, SymCustomizationScreen.kt, LanguagesScreen.kt, SystemTweaksScreen.kt, DisplayDensityScreen.kt, BloatRemoverScreen.kt (all under /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/)
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/SettingsBaselineTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/SettingsMigrationTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/backup/RestoreManagerAndBackupContractTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/backup/RestoreManagerIntegrationTest.kt
- /home/disdiqqq/projects/pastiera/docs/plans/2.0-settings-walkthrough.md
- /home/disdiqqq/projects/pastiera/docs/plans/2.0-overhaul.md
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md
