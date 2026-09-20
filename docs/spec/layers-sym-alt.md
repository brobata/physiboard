# Layers: Sym pages, the Alt layer, variations, and layouts

This document describes every character layer that sits above the base letters of the Titan 2
Elite keyboard: the Sym pages the Sym key opens, the Alt (device) layer printed on the keycaps,
long-press alternates and accent variations, and the layout files that decide which letter a
physical key produces in the first place. It covers what the user does, what the keyboard does,
in what order and with what timing. It does not describe the code.

Companion documents: `keys-and-modifiers.md` owns modifier state (held, one-shot, locked) and
hold detection; `text-input.md` owns autospace, deferred punctuation and French spacing;
`expansion-clipboard-pickers-launcher.md` owns the contents of the clipboard and emoji picker
pages and the launcher shortcuts that share the Sym key; `status-bar.md` owns the strip that
hosts the Sym pages; `dictionaries-languages.md` owns language (subtype) switching.

## 1. Vocabulary

- **Base layout**: the letter each physical letter key produces without any modifier. Comes
  from a layout file (section 8). Default is `qwerty`, which is the identity mapping.
- **Device layer** (also called the Alt layer, "Device SYM Layer" in the UI): the secondary
  character printed on each keycap, reached with the physical Alt key. Comes from a per-device
  asset file (section 3).
- **Sym pages**: up to five pages the Sym key cycles through. Three are *key layers* that
  remap the 26 letter keys (Device, Emoji, Symbols); two are *panels* (Clipboard, Emoji Picker)
  that are content, not key maps.
- **Variations**: accented or related characters for a base character (a to à á ä ...), reached
  by long press when the long-press action is set to Variations.
- **Long-press action**: what holding a letter key past the long-press threshold does. One of:
  device layer character, uppercase, first variation, or a Sym page character.

Sym page numbers are a data contract; they are stored in preferences and appear in intent
extras:

| Page id (in `sym_pages_config`) | Stored page number | Kind | Contents |
|---|---|---|---|
| `device` | 5 | key layer | the device layer (same map as Alt) |
| `emoji` | 1 | key layer | 26 emoji, one per letter key |
| `symbols` | 2 | key layer | 26 typographic symbols, one per letter key |
| `clipboard` | 3 | panel | clipboard history |
| `emoji_picker` | 4 | panel | searchable emoji picker |

Page number 0 means no page is open.

## 2. The layers in one picture

For a letter key press in an editable field, the keyboard decides in this order:

1. Numeric field: the device layer character is committed for every key press, Alt or not,
   unless Ctrl is active in any form.
2. A Sym page is open and Ctrl is not active: the page decides (section 5.4).
3. Alt is active (held, one-shot, or locked): the device layer character is committed
   (section 6).
4. Ctrl is active: Ctrl shortcuts (out of scope here).
5. Base layout, multi-tap, shift and caps handling produce the base character, and a long press
   timer may be armed (section 7).

Sym held as a chord modifier is resolved earlier than all of these, on key down, before the
key reaches step 1 (section 5.3).

## 3. Mapping files and their format

### 3.1 Per-key string maps

The device layer, the Emoji page and the Symbols page all use the same file shape: a JSON
object with one `mappings` object whose keys are Android key names and whose values are the
string to insert.

```
{ "mappings": { "KEYCODE_Q": "0", "KEYCODE_W": "1", ... } }
```

Recognised key names: `KEYCODE_A` to `KEYCODE_Z`, `KEYCODE_0` to `KEYCODE_9`, `KEYCODE_GRAVE`,
`KEYCODE_MINUS`, `KEYCODE_EQUALS`, `KEYCODE_LEFT_BRACKET`, `KEYCODE_RIGHT_BRACKET`,
`KEYCODE_BACKSLASH`, `KEYCODE_SEMICOLON`, `KEYCODE_APOSTROPHE`, `KEYCODE_COMMA`,
`KEYCODE_PERIOD`, `KEYCODE_SLASH`, `KEYCODE_CTRL_LEFT`, plus two Minimal Phone custom keys
`KEYCODE_EM` (666) and `KEYCODE_MIC` (667). Unknown names are skipped. A value may be more than
one character (an emoji with a variation selector is two code units).

For the two Sym page files only, a value may instead be an object `{ "lowercase": "x",
"uppercase": "X" }`. The lowercase entry is the page character; the uppercase entry, when
present, is used when Shift is active during a Sym chord or a Sym long press. The shipped Sym
files use the plain string form only, so the shipped uppercase maps are empty. Custom Sym
mappings (section 4.4) are always plain strings and clear the uppercase map for that page.

Values of the form `__DPAD_UP__`, `__DPAD_DOWN__`, `__DPAD_LEFT__`, `__DPAD_RIGHT__` in a device
layer file are not typed; the matching arrow key event is sent to the app instead. No Titan
file uses them (they exist for Clicks keyboards).

### 3.2 Device layer assets

| Asset path | Used when |
|---|---|
| `devices/titan2elite_qwerty/alt_key_mappings.json` | detected or overridden profile is `titan2elite_qwerty` |
| `devices/titan2/alt_key_mappings.json` | detected or overridden profile is `titan2`; also the fallback when the profile is `unknown` or the profile's file is missing |
| `common/alt/virtual_alt_key_mappings.json` | the on-screen (software) keyboard's Alt layer; identical in content to the `titan2` file |

Both device files map exactly the 26 letter keys. `titan2elite_qwerty`:

| Row | Keys and device-layer characters |
|---|---|
| 1 | Q 0, W 1, E 2, R 3, T (, Y ), U _, I -, O +, P @ |
| 2 | A *, S 4, D 5, F 6, G /, H :, J #, K ', L " |
| 3 | Z 7, X 8, C 9, V ?, B !, N ,, M . |

`titan2` (the non-Elite Titan 2 legend), which differs on 13 keys:

| Row | Keys and device-layer characters |
|---|---|
| 1 | Q 0, W 1, E 2, R 3, T (, Y ), U -, I _, O /, P : |
| 2 | A @, S 4, D 5, F 6, G *, H #, J +, K ", L ' |
| 3 | Z !, X 7, C 8, V 9, B ., N ,, M ? |

After loading a device file, if it contains `KEYCODE_GRAVE`, that entry is replaced by the
configured currency symbol (`physical_keyboard_currency_symbol`). Neither Titan file contains
`KEYCODE_GRAVE`, so the currency setting has no effect on a Titan (D7).

The device profile comes from `physical_keyboard_profile_override` when it is not `auto`;
otherwise from the build fingerprint: a Unihertz/Titan fingerprint containing
`titan2elite_qwerty`, `titan2elite-qwerty` or `titan2eliteqwerty`, or whose display string
contains `elite` or whose board contains `g72`, is `titan2elite_qwerty`; a Unihertz/Titan
fingerprint containing `titan 2` or `titan2` is `titan2`; anything else is `unknown` and falls
back to the `titan2` file.

The device layer map is cached and reloaded when the profile override or the Alt binding
preference changes (a preference-change trigger, not a per-keystroke read).

### 3.3 Sym page assets

`common/sym/sym_key_mappings.json` (Emoji page, page 1):

| Row | Keys and emoji |
|---|---|
| 1 | Q 😀, W 😂, E 😍, R 😊, T 😎, Y 👍, U ❤️, I 😘, O 😡, P 😉 |
| 2 | A 😢, S 😭, D 😱, F 😰, G 😴, H 🤔, J 🤢, K 🙄, L 😌 |
| 3 | Z 😔, X 🥳, C 😅, V 🤗, B 🥰, N 👎, M 😳 |

`common/sym/sym_key_mappings_page2.json` (Symbols page, page 2):

| Row | Keys and symbols |
|---|---|
| 1 | Q ~, W `, E {, R }, T [, Y ], U <, I >, O °, P % |
| 2 | A =, S ;, D ±, F – (en dash), G \, H \|, J „, K “, L ” |
| 3 | Z », X «, C &, V ^, B ¡, N §, M $ |

The Device page (page 5) has no file of its own; it shows the device layer map of section 3.2.

## 4. Sym pages configuration

### 4.1 Storage

Preference `sym_pages_config` holds one JSON object:

| Field | Type | Default | Meaning |
|---|---|---|---|
| `deviceEnabled` | boolean | false | Device page is in the cycle |
| `emojiEnabled` | boolean | true | Emoji page is in the cycle |
| `symbolsEnabled` | boolean | true | Symbols page is in the cycle |
| `clipboardEnabled` | boolean | false | Clipboard panel is in the cycle |
| `emojiPickerEnabled` | boolean | false | Emoji picker panel is in the cycle |
| `symPageOrder` | array of page ids | `["device","emoji","symbols","clipboard","emoji_picker"]` | cycle order |
| `emojiFirst` | boolean | true | legacy; written for old builds as "emoji comes before symbols in the order" |

Reading is tolerant: unknown ids in `symPageOrder` are dropped, duplicates collapse to the
first occurrence, whitespace is trimmed, and every known id missing from the order is appended
in default order. If `symPageOrder` is absent the order is rebuilt from the legacy flag: emoji,
symbols, clipboard (reversed to clipboard, symbols, emoji when `emojiFirst` is false), then
emoji_picker, then device appended by normalisation. A malformed value yields the defaults.

The factory baseline shipped in `common/default_settings.json` (applied once to every install)
sets `{"emojiEnabled":false,"symbolsEnabled":true,"clipboardEnabled":false,
"emojiPickerEnabled":true,"emojiFirst":false,"symPageOrder":["emoji_picker","symbols",
"clipboard","emoji"]}`. On a fresh Titan the cycle is therefore: no page, Emoji Picker,
Symbols, no page. `device` is absent from that order and is appended last, disabled.

### 4.2 The cycle

The cycle is the ordered list of enabled pages with "no page" (0) prepended. Tapping Sym moves
one step forward and wraps. If the current page is not in the cycle, the next step is the first
enabled page. If no page is enabled, Sym never opens anything.

Consistency rule applied on every read of the current page: if the current page is the Emoji
page (1) and it is not in the enabled cycle, it is replaced by the first enabled page, or by
"no page" when nothing is enabled. Pages 2, 3, 4 and 5 are allowed to stay open even when
disabled in the cycle, because they can be opened directly (section 4.3). Only the Emoji page
gets evicted this way.

### 4.3 Direct opens

Four status bar buttons open a specific page regardless of whether it is enabled in the cycle,
and each one toggles: pressing it while its page is open closes the page.

| Button | Page |
|---|---|
| clipboard | 3 |
| emoji picker | 4 |
| emoji layer | 1 |
| symbols | 2 |

The Minimal Phone emoji key (keycode 666) also toggles page 4; irrelevant on a Titan.

### 4.4 Custom Emoji and Symbols pages

Preferences `sym_mappings_custom` (Emoji page) and `sym_mappings_page2_custom` (Symbols page)
each hold a JSON object in the per-key string shape of section 3.1, letter keys only
(`KEYCODE_A` to `KEYCODE_Z`). When the preference exists and parses to at least one entry, it
replaces the shipped page entirely (keys absent from the custom map have no character on that
page) and the page's uppercase map is emptied. An unparseable value is treated as absent.
Removing the preference restores the shipped file. Both preferences travel in backups.

Editing (the "Customize SYM Keyboard" screen, section 5.7): tapping a key on the editable grid
opens the emoji picker (page 1) or the Unicode character picker (page 2) for that letter.
Choosing writes the whole page map back immediately; a failed write shows the toast "Failed to
save settings". On page 2, picking the empty choice restores that key's shipped character (or
removes the key if the shipped file has none). "Reset to Default" asks for confirmation
("Are you sure you want to reset all SYM mappings to default? This action cannot be undone.")
and then deletes the preference for that page.

The IME reloads the page maps when either preference changes.

## 5. The Sym key session

### 5.1 Key identity

The Sym key arrives as keycode 63 (`KEYCODE_SYM`) with scancode 253 (D1). Unlike Fn, it
delivers a normal key down and key up (D10).

### 5.2 Tap: toggle on release

With an editable field focused:

1. Sym key down (first event, not a repeat): the keyboard notes "a toggle is pending" and
   "no chord used yet", and arms the assistant hold timer if that feature is on (section 5.6).
   The key down is consumed; nothing visible happens yet.
2. Any non-modifier key pressed while Sym is down marks the press as a chord (section 5.3).
3. Sym key up: if a toggle is pending and no chord was used, the page cycles one step
   (section 4.2) and the strip redraws. The pending and chord flags are cleared. The key up is
   consumed.

Without an editable field, Sym down and up do not touch the pages at all: the down goes to the
launcher-shortcut logic (power shortcuts toggle, out of scope) or to the system, and the up
just clears the flags.

Key repeats of Sym while held are ignored for toggling purposes.

The current page number is written to the preference `current_sym_page` every time it changes,
so a page survives the IME being restarted. A page is reset to 0 when the IME resets its
modifiers (field change, hide), and re-read from the preference on start.

### 5.3 Chords: Sym held plus another key

While Sym is held (or the system reports the Sym meta flag on the event, D8) and an editable
field is focused, a key down with repeat count 0 is tried in this order. The first match
consumes the key, and in every case the press counts as a chord so the later Sym release does
not open a page.

1. **Edit shortcuts** (`sym_edit_shortcuts`, default on, and Alt not held): C copies, V pastes,
   X cuts, A selects all, via the editor's own context-menu actions. Nothing is typed.
2. **Launcher shortcut** (power shortcuts enabled and the key has a launcher shortcut
   configured): the shortcut runs (see the launcher document).
3. **Sym chord symbol**: the character for the key on the *preferred text page* is committed
   as plain text (no autospace or French spacing adjustments), without opening any page. The
   preferred text page is the open page if it is Device, Emoji or Symbols; otherwise the first
   enabled key layer (Device, Emoji or Symbols) in the configured order; if none is enabled,
   nothing is committed. With Shift held, one-shot Shift or Caps Lock active, the page's
   uppercase entry is used when it exists, else the normal entry.
4. No match: the key falls through to normal handling, but the press still counts as a chord.

Pure modifier keys (Shift, Ctrl, Alt, Sym itself) pressed while Sym is held do not count as
chords. Sym+Enter is separately claimable by a per-app "additional send shortcut" (see the
per-app document); when that fires it also counts as a chord.

The Sym key is also the Android language-switch partner of Alt: when Sym is pressed while Alt
is physically held, any Alt one-shot or lock is cleared first so the system switch does not
leave Alt armed.

### 5.4 Keys while a page is open

When a page is open and Ctrl is not active (physically, locked, or one-shot), a key down with
an editable field is handled by the page:

| Key | Behaviour |
|---|---|
| Back | closes the page; the key is consumed |
| Enter | if `sym_auto_close` is on: closes the page and the Enter goes on to the app as usual; if off: falls through (Enter behaves normally, page stays) |
| Alt | closes the page, then Alt is processed normally (it may arm one-shot or lock) |
| a letter with a character on the current key layer (pages 1, 2, 5) | the character is committed (with French punctuation spacing applied when enabled and the character is one of `?!;:`); if `sym_auto_close` is on the page closes; the key is consumed |
| a letter with no entry, Space, digits, or any key on a panel page (3, 4) | not handled here; normal typing proceeds with the page still open |

Auto-space replacement (turning "word " plus punctuation into "word, ") is *not* applied to
page characters, only French spacing is. Alt and long-press characters do get auto-space
replacement (sections 6 and 7); this asymmetry is as shipped.

With Ctrl active the page is bypassed entirely and Ctrl shortcuts run.

The emoji picker page (4) captures ordinary typing into its search field; that behaviour is in
the pickers document.

### 5.5 Sticky versus one-shot

There is no separate sticky mode. `sym_auto_close` (default on) makes a key layer one-shot:
the page closes after one character, after Enter, or after Alt. With it off the page is sticky
and closes only on Back, Alt, a Sym tap that cycles past it, a direct-open button toggle, the
close button, or an IME reset. `sym_auto_close_on_touch` (default on, only effective when
`sym_auto_close` is on) applies the same one-shot rule to taps on the on-screen grid: the page
closes first and the character is committed on the next UI turn.

### 5.6 Hold Sym for the assistant

When `sym_long_press_assistant` is on and Sym is not the screen trackpad trigger key:

- Sym down (repeat 0) starts a 600 ms timer.
- Any non-modifier key down before it fires cancels the timer (the press is a chord).
- Sym up before it fires cancels the timer; the release then toggles normally.
- When it fires: the pending toggle is cancelled, and the assistant is launched already
  listening. If no assistant can be launched, the toast "No voice assistant is set up on this
  device." is shown; the toggle stays cancelled either way. The following Sym up is swallowed.
- The timer flags are cleared on every new Sym down so a release lost to the assistant taking
  focus cannot eat the next tap.

When Sym is the screen trackpad trigger, the trackpad owns the hold (see the trackpad document)
and this timer is never armed.

### 5.7 What the strip shows during a session

The Sym pages render inside the same chrome as the status bar strip. Rules from the strip's
side that matter here:

- A hidden status bar ("Show status bar" off, or off for this app) still shows an open Sym
  page; the strip collapses again to zero height when the page closes (D12).
- While a page is open a text indicator reading `SYM` is added to the modifier indicators; it
  is drawn in the "locked" colour when the Symbols page (2) is open and in the "held" colour
  for any other page. (That the colour keys off page 2 specifically is as shipped.)
- Nav mode hides the whole strip, pages included.

The key-layer surface (pages 1, 2, 5) is a three-row grid of the 26 letter keys drawn over a
background in the theme's background colour:

- Rows: Q W E R T Y U I O P; A S D F G H J K L; Z X C V B N M. Each key shows the letter as a
  small label and the page character large: emoji at 0.75 of the key height, page 2 characters
  at 0.5 of the key height, bold, in the theme text colour. Key corners 6 dp, 1 dp divider
  stroke, background the theme's normal-key colour.
- Key height 56 dp, spacing 4 dp, key width = (screen width in px minus 16 dp) minus 9 gaps of
  4 dp, divided by 10.
- With `titan2_layout_enabled` (default: on for the Titan family, D6) the rows are
  left-aligned to mirror the physical ortholinear grid: row 2 gets one blank cell at the end;
  row 3 is Z X C V, a pencil button (opens the customisation screen for this page), a globe
  button (opens the system input-method picker), B N M, and a blank cell. Without it, rows are
  centred, row 3 has on its left a button that switches to the other key page (symbols icon on
  the Emoji page, smiley on the Symbols page) and on its right a pencil.
- Tapping a key commits its character through the current editor connection (section 5.5 for
  the close-first rule). Keys with no character are not tappable.
- Long-pressing a key opens the customisation screen directly on that letter's picker, and
  when the picker closes the screen finishes and the keyboard returns with the same page open
  (section 5.8).
- A close button (36 dp by 32 dp, bottom right, close icon on a translucent red 95/255 alpha
  background unless themed) is visible on pages 1, 2 and 5; the clipboard and emoji picker
  panels carry their own chrome.
- Surface height: the measured grid height, or 600 dp until measured. Page 4 uses the emoji
  picker's own height, which is 1.5 times its compact height when
  `emoji_picker_expanded_height` is on (default on; the factory baseline sets it off).
- The strip also computes a text "emoji map" (each row as `Q:😀  W:😂 ...` joined with two
  spaces, rows on separate lines) whenever page 1 is open; the view that would show it is kept
  hidden. It is a leftover.

### 5.8 Leaving for the customisation screen and coming back

Opening the customisation screen from the grid (pencil or key long-press) records the current
page in `pending_restore_sym_page` when a page is open. The customisation screen converts that
into `restore_sym_page` when it finishes normally (back arrow, system back, or the auto-return
after a direct picker edit). If the screen is destroyed without finishing (the user went to
another app), the pending value is discarded and nothing is restored.

When the IME next starts input and `restore_sym_page` is greater than 0: the page is reopened
if it is in the enabled cycle, otherwise the first enabled page is opened, otherwise none; the
preference is then cleared and the strip redraws on the next UI turn.

The customisation screen itself accepts intent extras `brobata.physiboard.extra.INITIAL_SYM_PAGE`
(1 or 2 selects the layer editor tab), `brobata.physiboard.extra.INITIAL_SYM_KEY_CODE`,
`brobata.physiboard.extra.OPEN_SYM_PICKER` and `brobata.physiboard.extra.RETURN_AFTER_PICKER`.
With the last two true and a key code present, the picker opens immediately and the screen
finishes as soon as the picker closes (chosen or dismissed). Opening the Device page's editor
routes to the Device SYM Layer Editor instead (section 6.5).

### 5.9 The customisation screen

Title "Customize SYM Keyboard". Sections, top to bottom:

1. **Arrange SYM pages order** ("Drag or use the arrows to set the cycle order. The switch only
   controls whether an item appears in the cycle."): one row per page in normalised order with
   a drag handle (long-press then drag, one slot per 56 dp of vertical travel), an up arrow
   (disabled on the first row), a down arrow (disabled on the last), a kind label ("Key layer"
   or "Panel"), an edit pencil for Device, Emoji and Symbols, an "under construction" badge on
   Device, and an enable switch. Every change writes `sym_pages_config` immediately.
2. **Alt character layer** row ("Choose which SYM layer Alt uses in Modifier settings."): opens
   the settings activity at the `modifiers` destination. That destination has no screen since
   the 2.0 settings rework, so the row leads nowhere useful; the preference it describes is in
   section 6.3.
3. **SYM behaviour and display**: the three switches `sym_edit_shortcuts`, `sym_auto_close`,
   `sym_auto_close_on_touch` (the last greyed out while auto-close is off).
4. **Larger emoji picker** (`emoji_picker_expanded_height`).

Pressing a pencil on Emoji or Symbols replaces the screen content with the editable grid for
that page (title "Edit Emoji Layer" or "Edit Symbols Layer"), rendered with the same geometry
and Titan alignment as the live grid on a black background, followed by a red "Reset to
Default" button. Back returns to the list.

## 6. The Alt layer

### 6.1 The physical Alt key

Alt is scancode 56, reported as `KEYCODE_ALT_LEFT` (D2). Its state machine lives in the
modifiers document; the parts that shape the character layer are:

- Alt held with a key: device layer character.
- Alt tapped (down and up with no other key): one-shot; the next key gets the device layer
  character and the one-shot ends. With `alt_tap_latches` on (default off) a single tap locks
  instead.
- Two taps within 500 ms: locked; every key gets the device layer character until Alt is tapped
  again. A tap while locked unlocks.
- `clear_alt_on_space` (default on): Space or Enter ends one-shot and lock, unless
  `alt_latch_stays_on_space` (default off) keeps a lock through Space (one-shot still ends).
- Any Alt key down while a Sym page is open closes the page first.
- Alt + Ctrl held together (when the Alt+Ctrl speech shortcut is on) starts dictation and is
  not an Alt session.

### 6.2 What Alt plus a key does

With Alt active in any form and an editable field, on key down:

1. Any pending long press for that key is cancelled, and an Alt one-shot is consumed.
2. Back passes through to the system.
3. Space commits a single space (this also blocks Android's own Alt+Space symbol picker).
4. If the layer value for the key is a `__DPAD_*__` sentinel, that arrow key event is sent.
5. If the key has a layer character:
   - the deferred-punctuation rule is given the chance to prepare (text-input document);
   - if French spacing applies and the character is one of `?!;:`, a narrow no-break space
     (U+202F) plus the character is committed, replacing any plain or no-break spaces already
     before the caret, provided the text before them is not empty or whitespace;
   - else if the character is in the configured auto-space punctuation set and an auto-space
     is pending, the pending space is replaced by the character plus a space;
   - else the character is committed as-is and any pending auto-space is forgotten.
   The key is consumed and the strip redraws.
6. Keys with no layer character (Shift, Enter, arrows, ...) go to the default handler.

In a numeric field every key press goes through step 4 and 5 without Alt (Ctrl active
excepted), and the strip redraw is delayed by the cursor-update delay.

### 6.3 Which map Alt uses: the Alt binding

Preference `alt_character_layer_binding` (default `device:auto`) chooses the map behind Alt:

| Value | Map |
|---|---|
| `device:auto` | the device layer file for the detected or overridden profile (section 3.2) |
| `device:<profile>` | that profile's device layer file (`titan2`, `titan2elite_qwerty`, ...) |
| `emoji` | the Emoji page (custom map if any, else the shipped file) |
| `symbols` | the Symbols page (custom map if any, else the shipped file) |
| `first` | whichever of Device, Emoji or Symbols comes first in `sym_pages_config` order, enabled or not (Device when the order has none) |

Any other stored value behaves as `device:auto`. When the binding resolves to a device profile
and the key comes from the on-screen keyboard, the virtual file is used instead. No screen
writes this preference any more (section 5.9, item 2); it is a leftover of the deleted
Modifiers screen and can only be changed by backup restore or manual editing.

### 6.4 Device layer as a Sym page

Page 5 shows and types the same map as `device:auto` and ignores the Alt binding. It is off by
default and marked "under construction" in the page list.

### 6.5 Device SYM Layer Editor

The screen titled "Device SYM Layer Editor" ("Custom hardware profiles created here will be
selectable alongside curated profiles.") is a stub: it lists planned rows (New blank profile,
Clone curated profile, Name and device matching, Device SYM and key mappings, Import and
export), each with an "under construction" badge, and does nothing. It is reached from the
Device row's pencil in the customisation screen and from the pencil on the Device page grid.

### 6.6 The profile screen

"Built-in Keyboards" shows the detected device name and profile, a dropdown "Physical Keyboard
Profile" with Auto, "Unihertz Titan 2" (`titan2`) and "Titan 2 Elite (QWERTY)"
(`titan2elite_qwerty`), a description ("Auto detect from device fingerprint (current: X)." or
"Manual override for device-specific key mappings."), an empty "custom profiles" section, and
the "Titan 2 Layout Alignment" switch ("Align on-screen keyboard with Titan 2 physical keys").

## 7. Long press

### 7.1 Threshold

`long_press_threshold` is read as milliseconds and clamped to 50 to 1000. The settings screen
(Keyboard timing, row "Long Press", value shown as "N ms") offers a slider from 50 to 1000 with
18 intermediate steps (50 ms increments) and shows 300 when the key is unset. The key handler
that arms the timer, however, reads the same preference with its own fallback of 500 ms when
the key is unset. The factory baseline does not set the key, so a fresh install holds for
500 ms while the slider claims 300 until the slider is touched once. The Dev's Choice onboarding
preset writes 200.

### 7.2 Modes

`long_press_modifier` (set from the onboarding tutorial's "Long Press Modifier" dropdown; no
other screen exposes it):

| Value | Label | Held key produces |
|---|---|---|
| `alt` (default) | Device SYM Layer (formerly Alt) | the device layer character |
| `shift` | Shift | the layout's uppercase for the key |
| `variations` | Variations | the first variation of the committed character |
| `sym` | First Emoji or Symbols Layer | the Emoji page entry if `emoji` precedes `symbols` in the configured order (order only, enabled state ignored; Emoji if neither is in the order, Symbols if only Symbols is), else the Symbols page entry |
| `sym_symbols` | Symbols Layer | the Symbols page entry |
| `sym_emoji` | Emoji Layer | the Emoji page entry |

Unknown stored values read as `alt`.

### 7.3 Sequence

A letter key down (editable field, no Sym page consuming it, no Alt or Ctrl) first checks
whether the key *has* long-press support in the current mode: `alt` needs a device layer
entry; `shift` needs a letter from the system character map; `variations` needs a variation
list for the character the key would produce (with the effective Shift state); the `sym*`
modes need an entry on the chosen page (uppercase entry counts when Shift is effective). Long
press can also be suppressed by the caller for keys in special roles (trackpad trigger and the
like).

If supported:

1. The base character is committed immediately (layout character with Shift, Caps Lock and
   one-shot Shift applied; for keys outside the layout, the event's character, uppercased for
   one-shot Shift or Caps Lock, lowercased for Caps Lock plus Shift). Its position is
   remembered as an anchor: the committed text must sit just before a collapsed selection in
   the editor's extracted text, and the whole prefix up to it is recorded.
2. A timer is started for the threshold. While it is pending, key repeats for that key are
   swallowed, so holding a letter never auto-repeats it.
3. Key up before the timer: the timer is cancelled, the character stays, and the suggestion
   engine is told the character was typed. The key up is consumed.
4. Timer fires with the key still down: the mode's replacement happens (7.4), the key is marked
   as long-press-activated so its release does not count as a normal character, and the
   suggestion engine is told about the replacement character.

Multi-tap layouts (Turkish, Norwegian, ...) commit through their own tap logic; after each
multi-tap commit the same timer is armed on the committed text, with "shifted" inferred from
whether the committed character is uppercase, so long press still works on multi-tap keys.

All timers and anchors are dropped on IME reset.

### 7.4 Replacement per mode

- `alt`, `sym`, `sym_symbols`, `sym_emoji`: the committed character is deleted (one character
  before the caret) and the layer character is committed with the same French-spacing and
  auto-space rules as an Alt chord (section 6.2 step 5). For `sym*` modes the uppercase page
  entry is used when the press was shifted and the entry exists.
- `shift`: the committed character is deleted and the layout's uppercase for the key is
  committed; for keys outside the layout, the committed character uppercased (letters only).
- `variations`: the lookup character is the committed character with its case corrected to the
  press: shifted and lowercase becomes uppercase; unshifted and uppercase becomes lowercase
  (so Caps Lock without Shift looks up the lowercase list and replaces `U` with `ü`, as
  shipped). The first entry of the variation list replaces the committed text using a
  composing-region replacement rather than a delete: the editor's extracted text is read; the
  anchored range must still contain the expected character with the same prefix, the
  selection must be collapsed and at or after the anchor; the range is set as composing,
  the variation committed, composing finished, and the selection moved by the length
  difference (a caret inside the range lands after the replacement). If extracted text is
  unavailable when the anchor was taken, the fallback is "the text immediately before the
  caret equals the committed character". If the check fails nothing is replaced and the
  original stays. Later input typed after the character (before the timer fires) is preserved:
  "u" then "3" then timer gives "ü3".

## 8. Variations

### 8.1 Data

Shipped file `common/variations/variations.json` with these top-level fields:

- `variations`: object from a single base character to an ordered list of strings. Shipped
  base characters: a e i o u l c n s z y d g r p t (lowercase and uppercase), plus Cyrillic
  `е` (ё є), `Е` (Ё), `Р` (₽) and Armenian `Դ` (֏). Examples: `a` gives à á ä â ã å ą; `e` gives
  è é ê ë ę ě €; `s` gives ß š ś ș ş ŝ $; `p` gives %.
- `layoutVariationOverrides`: object from layout name to the same shape, listing entries that
  should come *first* for that layout. Shipped: `qwertz` (a ä, A Ä, o ö, O Ö, u ü, U Ü, s ß,
  S ẞ ß, p %, P %, e €, E €), `german_multitap_qwertz` (same without p/P), and
  `norwegian_multitap_qwerty` (a å æ, A Å Æ, o ø, O Ø).
- `staticVariations`, `staticVariationsShift`, `staticVariationsAlt`, `emailVariations`:
  leftovers of the retired variation bar; still parsed and preserved but nothing displays them.

`common/variations/defaultvariations.json` is the copy used by "reset variations"; it lacks the
`german_multitap_qwertz` override. `common/variations/AllVariations.json` is a much larger
per-letter catalogue (A to Z, each with several dozen forms, base letter last) intended for a
picker dialog that no screen opens any more.

### 8.2 Effective list

1. Source text: `files/variations.json` in the app's private files directory if it exists,
   else the shipped file.
2. The shipped file's `layoutVariationOverrides` are merged into the source's: per layout, per
   character, the source wins when it has the key, otherwise the shipped entry is added.
3. The layout whose overrides apply is `global_variation_layout_override` when non-empty (the
   2.0 migration drops this key, so it is normally empty), else the active layout name, else
   `keyboard_layout`.
4. For every base character: the layout's priority list followed by the base list, duplicates
   removed keeping the first. Characters that only appear in the layout's overrides get the
   override list alone.

The list is rebuilt when the layout changes and when the preference `variations_updated` is
touched (any write to the variations file does that). A parse failure yields no variations at
all.

### 8.3 Writing

Only the Dev's Choice onboarding preset writes `files/variations.json` today (it saves the
currently effective lists unchanged), and "reset" copies `defaultvariations.json` over it. The
picker dialog ("Select variation for X", with a "Custom character" text field, "Clear (Empty)"
and a grid of 48 dp cells at least 4 per row) exists but is unreachable. Backups include
`variations.json` and merge it with the shipped defaults on restore rather than overwriting.

### 8.4 Where variations show

Only through the long-press `variations` mode. The accent row under the suggestions was removed
with the strip's second row; the per-keystroke cursor read that fed it is gone, and the code
path that would insert a tapped variation (delete one character, commit the variation; commit
without delete for punctuation and brackets) has no button left to call it.

## 9. Layouts

### 9.1 File format

A layout file is JSON with optional `name` and `description` strings and a `mappings` object.
Each entry is keyed by a key name from the same list as section 3.1 (letters, digits and the
punctuation keys; not `KEYCODE_CTRL_LEFT` or the Minimal Phone keys) and holds:

```
{ "lowercase": "e", "uppercase": "E",
  "multiTapEnabled": true,
  "taps": [ { "lowercase": "e", "uppercase": "E" }, { "lowercase": "ё", "uppercase": "Ё" } ] }
```

Rules: entries missing `lowercase` or `uppercase` are dropped; `taps` entries with both strings
empty are dropped; multi-tap is honoured only when `multiTapEnabled` is true and at least two
taps survive, otherwise the entry is single-character. Files with no parsable `mappings` are
rejected. When a layout cannot be loaded, the base layout is the identity map (a to z, A to Z
on the 26 letter keys).

The character for a key is `uppercase` when one-shot Shift is active, or Caps Lock is on
without Shift, or Shift is held; otherwise `lowercase`. With multi-tap, the tap index selects
the entry (wrapping). The system character map is used only for keys the layout does not
define.

### 9.2 Bundled layouts

`common/layouts/<name>.json`, all defining exactly the 26 letter keys:

| Name | Display name | Multi-tap keys | Note |
|---|---|---|---|
| qwerty | QWERTY | 0 | identity; listed as "Standard" |
| qwertz | QWERTZ \| Deutsch | 0 | Y and Z swapped |
| azerty | AZERTY | 0 | French positions |
| german_multitap_qwertz | QWERTZ Multi-Tap | 5 | umlauts and ß by repeated taps |
| turkish_multitap | Turkish (multi-tap) | 7 | |
| norwegian_multitap_qwerty | Norwegian (multi-tap) | 2 | |
| arabic | Arabic | 10 | |
| armenian_phonetic | Armenian phonetic (multi-tap) | 9 | |
| bulgarian_phonetic | Bulgarian Phonetic | 0 | |
| bulgarian_phonetic_traditional | Bulgarian Phonetic (Traditional) | 4 | |
| Cyrillic_Translite | Cyrillic Translite | 7 | |
| greek | Greek | 7 | |
| russian_jcuken | Russian (JCUKEN compact) | 7 | |
| russian_standard | Russian (ЙЦУКЕН) | 4 | |
| russian_translit | Russian (multi-tap) | 6 | |
| serbian_cyrillic | Serbian Cyrillic | 4 | |
| ukrainian | Ukrainian | 6 | |
| vietnamese_telex_qwerty | Tiếng Việt (TELEX, QWERTY) | 0 | enables the Telex composer |

Alt, Sym and Ctrl maps stay keyed by physical position whatever the layout ("ALT, SYM and Ctrl
mappings remain based on physical key position").

### 9.3 Custom layouts

Directory `files/keyboard_layouts/` in the app's private storage; one `<name>.json` per layout.
A custom file with the same name as a bundled layout takes precedence. The available list is the
union of custom names and bundled names, sorted. Ways in:

- **Import from file**: `android.intent.action.OPEN_DOCUMENT` with type `application/json`;
  the name is the file's `name` field, else `imported_<epoch ms>`; the raw text is saved after a
  successful parse. Snackbars: "Layout imported successfully", "Failed to import layout", or
  the error message.
- **Download from cloud**: a manifest at
  `https://palsoftware.github.io/pastiera-dict/layouts-manifest.json` (fields `schemaVersion`,
  `generatedAt`, `releaseTag`, `items[]` with `id`, `filename`, `url`, `bytes`, `sha256`,
  `updatedAt`, `name`, `shortDescription`, `languageTag`); each file is downloaded to
  `cache/layout_downloads/<filename>.tmp` in 8192-byte chunks, SHA-256 checked against the
  manifest (mismatch discards), parsed (invalid discards), then moved into the layouts
  directory replacing any existing file. This is the upstream author's host.
- **Delete**: only custom layouts (never `qwerty`); confirmation dialog; if the deleted layout
  was selected the selection falls back to `qwerty`.

A link "Keyboard Layout Editor" opens `https://pastierakeyedit.vercel.app/` in the browser.

### 9.4 Locale to layout

`common/locale_layout_mapping.json` maps locale strings to layout names: `en_US`, `it_IT`,
`pl_PL`, `da`, `da_DK`, `es_ES`, `pt_PT` to `qwerty`; `fr_FR` to `azerty`; `de`, `de_DE`,
`de_AT`, `de_CH`, `de_LU` to `qwertz`; `tr`, `tr_TR`, `tr_CY` to `turkish_multitap`; `no`,
`no_NO`, `nb`, `nb_NO`, `nn`, `nn_NO` to `norwegian_multitap_qwerty`; `vi_VN` to
`vietnamese_telex_qwerty`; `ru_RU` to `russian_translit`; `sr_RS` to `serbian_cyrillic`;
`uk_UA` to `ukrainian`.

Resolution for a locale: a user file `files/locale_layout_mapping.json` is consulted first
(exact locale, then the language part before `_` or `-`); then the shipped file (exact, then
language); then `qwerty`.

The active layout at input start:

1. If `keyboard_layout_auto_by_locale` (default true) is off: `keyboard_layout` everywhere.
2. Else if the current input subtype declares a layout (custom input styles): that layout.
3. Else the locale-to-layout result for the subtype's locale (`en_US` when blank).
4. With no subtype available: `keyboard_layout`, to avoid jumps.

Loading a layout also rebuilds the variation lists and tells the suggestion engine the layout
(for proximity ranking).

### 9.5 Layout list and switching

`keyboard_layout_list` is a JSON array of layout names used for cycling; unset means the single
current layout. Cycling always wraps (a one-entry list cycles to itself), skips names that are
neither bundled nor custom, and writes the result to `keyboard_layout`. The chords Alt+Shift
(`alt_shift_layout_switch`, default off, baseline on), Alt+Enter (`alt_enter_layout_switch`,
default off) and Ctrl+Space (`ctrl_space_layout_switch`, default on) cycle the *input language*
(subtype), clearing Alt (and Shift or Ctrl) state first and showing a toast when
`toast_on_layout_switch` (default on) is on; they are specified in the languages document.

### 9.6 Layout settings screen

Title "Keyboard Layout - <locale display name>". Contents: the editor link; a "Standard" row
(`qwerty`, "Standard QWERTY layout (no conversion)") with a view icon and radio; then every
other layout (bundled and custom, sorted by name, except that for German locales `qwertz` then
`german_multitap_qwertz` are listed first) as a row with its display name, a "multitap" badge
when any key is multi-tap, its description, a delete icon for custom layouts, a view icon and a
radio. The top bar has an add menu ("Import from file", "Download from cloud") and, outside
picker mode, a save icon that writes `keyboard_layout_auto_by_locale` and, when automatic is
off, `keyboard_layout`, then reports the choice to the caller. In picker mode (choosing a
layout for an input style) back returns the selection without writing preferences. The list
refreshes on every resume.

### 9.7 Layout viewer

"Keyboard map" with subtitle "Layout: <name>". One row per key sorted by keycode: a chip with
the key name (`KEYCODE_Q`), `lower/upper`, a badge "multitap" or "single", and one chip per tap
level (`lower/upper`, or just `lower` when the tap has no uppercase). "Unable to load this
layout mapping." when the file fails to load; "No key mappings found for this layout." when it
is empty.

## 10. Language and layout specific differences

- **French spacing** (`french_punctuation_spacing`, default off; `french_punctuation_only_french_layouts`,
  default off, restricts it to a French input language): affects Alt characters, Sym page
  characters and long-press characters that are `?`, `!`, `;` or `:`.
- **QWERTZ and Norwegian**: variation lists reorder so ä/ö/ü/ß/€ (German) or å/æ/ø (Norwegian)
  come first; the layout names in the overrides are the exact file names.
- **Cyrillic and Armenian**: variations exist for `е`, `Е`, `Р`, `Դ` only.
- **Vietnamese Telex**: the layout name `vietnamese_telex_qwerty` switches on a live composer
  (out of scope, and dropped for 3.0).
- **Currency symbol** (`physical_keyboard_currency_symbol`, one of € $ £ ¥ ₹ ₽ ₿ ¤, default €,
  baseline $): only replaces a device layer entry for `KEYCODE_GRAVE`, which no Titan profile
  has.
- **Titan 2 versus Titan 2 Elite**: different device layer files (section 3.2); the Elite is
  the tested device and its file matches the printed keycaps (D3).

## 11. Titan-specific facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The Sym key is scancode 253 (`AGUI_SYM` in the vendor key layout) and reaches the IME as keycode 63. | `docs/titan2elite/DEVICE.md` scancode table; `docs/titan2elite/TitanKey.kl` line `key 253 AGUI_SYM`; the service's constant and the trackpad trigger comment "Titan 2 delivers SYM as raw key code 63" |
| D2 | The Alt key is scancode 56, `ALT_LEFT`. | `docs/titan2elite/TitanKey.kl` |
| D3 | The Elite keycap legends for Alt are exactly the vendor character map's `alt` column, and that column equals the `titan2elite_qwerty` asset key for key (Q 0, W 1, E 2, R 3, T (, Y ), U _, I -, O +, P @, A *, S 4, D 5, F 6, G /, H :, J #, K ', L ", Z 7, X 8, C 9, V ?, B !, N ,, M .). | `docs/titan2elite/TitanKey.kcm` compared with `devices/titan2elite_qwerty/alt_key_mappings.json` |
| D4 | The non-Elite Titan 2 legend differs on 13 keys and has its own asset; it is detected by fingerprint tokens `titan 2`/`titan2` without the Elite markers (`elite` in the display string or board `g72`). | device detection in the IME; commit e4b5974 "Default Titan 2 SYM layout toggle to enabled on Titan 2 devices" |
| D5 | The Titan has no Ctrl key; Fn is scancode 251 and is delivered as Ctrl without a key-up. This is why Sym+C/V/X/A were given to editing: copy and paste on Ctrl were unreachable until Fn was remapped. | `docs/titan2elite/DEVICE.md`; `PHYSIBOARD_CHANGES.md` 1.2.3; commit 54b5fdc |
| D6 | The on-screen Sym grid is drawn ortholinear and left-aligned by default on the Titan family, with the pencil and globe buttons where the space bar is and a right-side gap for the keyboard cutout. | commit 55f2a74 "implement ortholinear Titan 2 layout for SYM keyboard"; commit e4b5974 |
| D7 | The Titan has no dedicated currency key (no `KEYCODE_GRAVE` in the key layout), so the currency symbol setting is inert on it. | `docs/titan2elite/TitanKey.kl` has no grave scancode; both Titan device layer files lack `KEYCODE_GRAVE` |
| D8 | Whether chorded key events carry Android's Sym meta flag on the Titan is not established; the IME tracks Sym-held itself and only additionally accepts the flag. The vendor package `com.agui.keyboard` is a translation layer for `FUNC3`/`AGUI_SYM`. | `docs/titan2elite/DEVICE.md` vendor packages section; needs device evidence |
| D9 | The window is about 574 dp by 640 dp; the grid key width formula (section 5.7) is what fits ten keys across it. | `docs/titan2elite/DEVICE.md` display section |
| D10 | Sym delivers a clean down and up, unlike Fn, so a plain timer is enough for the assistant hold. | service comment; `PHYSIBOARD_CHANGES.md` 1.0.7 |
| D11 | Sym pages still open while the status bar is hidden, because they render in the same chrome the toggle collapses. | `PHYSIBOARD_CHANGES.md` 1.0.6; commit 80bbe0b |
| D12 | Fresh Titan installs start with Emoji off, Emoji Picker on, Symbols on, order picker, symbols, clipboard, emoji, currency `$`, larger emoji picker off, Alt+Shift switch on, screen trackpad on. | `common/default_settings.json`, captured from a Titan 2 Elite on 2026-08-29 |
| D13 | The maintainer's onboarding preset (Dev's Choice) selects `qwertz` as the only layout with automatic mapping off, long press = Variations at 200 ms. | onboarding preset in the tutorial |
| D14 | The "Long Press Mappings" help text in the tutorial still lists the non-Elite Titan 2 legend (Q 0 ... O ', P :, A @, G *, H #, J +, K ", L ', Z !, B ., N ', M ?), which is wrong for the Elite. | `res/values/strings.xml` long press mapping lines |

## 12. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `current_sym_page` | int | 0 | page currently open (0, 1..5); internal | none | none |
| `sym_pages_config` | string (JSON, 4.1) | see 4.1; baseline in D12 | which pages are in the cycle and in what order | Customize SYM Keyboard | Arrange SYM pages order |
| `sym_mappings_custom` | string (JSON, 3.1) | unset | Emoji page characters | Customize SYM Keyboard, Edit Emoji Layer | (grid) |
| `sym_mappings_page2_custom` | string (JSON, 3.1) | unset | Symbols page characters | Customize SYM Keyboard, Edit Symbols Layer | (grid) |
| `sym_auto_close` | boolean | true | page closes after a character, Alt or Enter | Customize SYM Keyboard | Auto-Close SYM Layout |
| `sym_auto_close_on_touch` | boolean | true | page closes after an on-screen key tap (needs the one above) | Customize SYM Keyboard | Also close after on-screen SYM keys |
| `sym_edit_shortcuts` | boolean | true | Sym+C/V/X/A copy, paste, cut, select all | Customize SYM Keyboard | Sym+C/V/X/A: copy, paste, cut, select all |
| `emoji_picker_expanded_height` | boolean | true (baseline false) | page 4 height is 1.5 times compact | Customize SYM Keyboard | Larger emoji picker |
| `restore_sym_page` | int | 0 | page to reopen at next input start; internal | none | none |
| `pending_restore_sym_page` | int | 0 | page noted when leaving for customisation; internal | none | none |
| `sym_long_press_assistant` | boolean | false | holding Sym 600 ms opens the assistant listening | Voice settings | Hold Sym for the assistant |
| `alt_character_layer_binding` | string | `device:auto` | which map Alt types from (6.3) | none (orphaned) | Alt character layer |
| `alt_tap_latches` | boolean | false | a single Alt tap locks instead of one-shot | none found | Single-tap locks Alt |
| `clear_alt_on_space` | boolean | true | Space/Enter end Alt one-shot and lock | Text input | Release Alt with Space |
| `alt_latch_stays_on_space` | boolean | false | a locked Alt survives Space | none found | Keep locked Alt after Space |
| `long_press_modifier` | string | `alt` | long-press action (7.2) | onboarding tutorial | Long Press Modifier |
| `long_press_threshold` | long ms | 300 shown, 500 used when unset (7.1) | hold time before the alternate | Keyboard timing | Long Press |
| `physical_keyboard_profile_override` | string | `auto` | device layer profile | Built-in Keyboards | Physical Keyboard Profile |
| `titan2_layout_enabled` | boolean | unset (reads as true on the Titan family) | ortholinear on-screen Sym grid | Built-in Keyboards | Titan 2 Layout Alignment |
| `physical_keyboard_currency_symbol` | string | `€` (baseline `$`) | replaces a `KEYCODE_GRAVE` device entry; inert on Titan | Text input | Currency Symbol |
| `keyboard_layout` | string | `qwerty` | manual layout, and the cycle's current layout | Keyboard Layout | (radio list) |
| `keyboard_layout_auto_by_locale` | boolean | true | layout follows the input language | Keyboard Layout | Automatic Layout Mapping |
| `keyboard_layout_list` | string (JSON array) | unset | layouts to cycle through | Input Languages | (list) |
| `keyboard_layout_auto_mapping_updated` | long | unset | timestamp touched to make the IME re-resolve the layout | none | none |
| `alt_shift_layout_switch` | boolean | false (baseline true) | Alt+Shift cycles languages | Input Languages | Alt+Shift Layout Switch |
| `alt_enter_layout_switch` | boolean | false | Alt+Enter cycles languages | Input Languages | Alt+Enter Layout Switch |
| `ctrl_space_layout_switch` | boolean | true | Ctrl+Space cycles languages | Input Languages | Ctrl+Space Layout Switch |
| `toast_on_layout_switch` | boolean | true | toast on language switch | Input Languages | Layout Switch Toast |
| `variations_updated` | long | unset | timestamp touched to reload variations | none | none |
| `global_variation_layout_override` | string | empty; dropped by the 2.0 migration | force one layout's variation order everywhere | none | Global Variation Mapping |
| `french_punctuation_spacing` | boolean | false | narrow no-break space before `?!;:` | Text input | (see text-input document) |
| `french_punctuation_only_french_layouts` | boolean | false | restrict the above to French | Text input | (see text-input document) |

Files, not preferences: `files/variations.json`, `files/keyboard_layouts/*.json`,
`files/locale_layout_mapping.json`, `cache/layout_downloads/*.tmp`. Backups carry
`sym_mappings_custom`, `sym_pages_config`, `variations.json` (merged on restore),
`locale_layout_mapping.json` and the `keyboard_layouts` directory. The one-time settings
baseline reset clears every preference above except the backup-independent files.

## 13. Edge cases, quirks and known bugs

| Situation | Behaviour | Why |
|---|---|---|
| Sym tapped with all pages disabled | nothing opens; the release is still consumed | the cycle has only "no page" |
| Emoji page open, then Emoji disabled in settings | on the next key the page becomes the first enabled page (or closes) | consistency rule evicts page 1 only |
| Symbols page open, then Symbols disabled | page stays open | pages 2..5 are exempt so direct opens keep working |
| Sym held, letter pressed with no entry on the preferred page | letter types normally, but Sym release does not open a page | the press is marked as a chord before the lookup |
| Sym held, Shift held, chord | uppercase page entry if the file uses object form, else the normal entry | shipped files are string-only |
| Sym+C with `sym_edit_shortcuts` off | C's page-1 emoji (😅) or a launcher shortcut | order of section 5.3 |
| Sym+C with Alt held | not an edit shortcut; falls to the other chord rules | Alt excluded explicitly |
| Sym down in a non-editable screen | no page; the down is handed to launcher logic | pages need an editor |
| Sym held over 600 ms with the assistant hold on | assistant launches, no page opens, release swallowed | section 5.6 |
| Sym is the screen trackpad trigger | assistant hold never arms | two holds on one key |
| Ctrl active while a page is open | Ctrl shortcuts run, page ignored and left open | Ctrl bypass |
| Page open, Space pressed | space typed, page stays | no entry for Space |
| Page open, Enter, auto-close off | Enter to the app, page stays | only auto-close handles Enter |
| Page character `?` with French spacing on and text before the caret | U+202F then `?` replaces trailing spaces | French rule |
| Page character `,` with a pending auto-space | comma committed after the space (no replacement) | page commits skip auto-space replacement |
| Tapped on-screen key with auto-close on | page closes first, character committed on the next UI turn | avoids mutating the view during its own touch |
| Long-press on an on-screen key while a page is open | customisation screen opens on that key's picker; returns to the keyboard with the page restored after the picker closes | section 5.8 |
| Customisation screen killed by going to another app | no page restored on return | pending value discarded on destroy without finish |
| `restore_sym_page` names a page no longer enabled | first enabled page opens instead | section 5.8 |
| Alt key while a page is open | page closes, then Alt state changes as a normal tap/hold | section 5.4 |
| Alt pressed with Sym (system language switch) | Alt one-shot/lock cleared before the system handles it | section 5.3 |
| Alt+Space | a plain space; Android's symbol picker is never shown | consumed explicitly |
| Numeric field, plain letter key | device layer character (Q types 0) | numeric rule |
| `alt_character_layer_binding` set to `emoji` | Alt types emoji; the Device Sym page still types the device layer | page 5 ignores the binding |
| `alt_character_layer_binding` needs changing | no screen; only backup restore or manual edit | Modifiers screen deleted in 2.0 |
| Custom Sym map saved | Shift no longer selects uppercase entries on that page | custom maps clear the uppercase map |
| Long-press key held: auto-repeat | none; repeats swallowed while the timer is pending | section 7.3 |
| Long press with `alt` on a key with no device entry | key behaves as a plain key, repeats normally | no long-press support |
| Long press in `variations`, Caps Lock on, Shift not held | committed `U` is looked up as `u`, replaced by `ü` (lowercase) | case correction follows the Shift flag, not the text |
| Long press in `variations`, editor gives no extracted text | replacement only if the character is immediately before the caret; later input blocks it | anchor fallback |
| Long press in `variations`, the committed character was edited by the app | nothing replaced | anchor check fails |
| `long_press_threshold` unset | slider shows 300 ms, keys hold for 500 ms | two defaults for one key |
| `long_press_modifier` is `sym` and neither emoji nor symbols is enabled | still resolves to Emoji or Symbols by order | mode looks at order, not enablement |
| Currency symbol changed on a Titan | nothing changes | no `KEYCODE_GRAVE` in the device file |
| Unknown device fingerprint | `titan2` device layer file is used | fallback |
| Layout file entry missing `uppercase` | key falls back to the identity letter? No: the key is absent from the layout and the system character map is used | entries need both strings |
| Multi-tap entry with a single tap | treated as single character | needs two taps |
| Custom layout named like a bundled one | custom wins | file store consulted first |
| Downloaded layout hash mismatch | file deleted, "hash mismatch" result | integrity check |
| German locale on the layout screen | `qwertz` and `german_multitap_qwertz` listed first | locale-specific sort |
| Locale `de-AT` | `qwertz` | language-only fallback after exact miss |
| `variations.json` unparsable | no variations anywhere; long press `variations` mode never arms | parse failure yields an empty table |
| Variation picker dialog | unreachable | no caller since the strip's second row was removed |
| "Alt character layer" row on the SYM screen | opens settings at a destination with no screen | orphaned deep link |
| Emoji map text for page 1 | computed every strip update, never shown | leftover view kept hidden |
| `SYM` indicator colour | locked colour only on the Symbols page | as shipped |
| Tutorial "Long Press Mappings" text | shows the non-Elite legend | stale string (D14) |

## 14. Test cases

Encodable on the JVM with a fake editor that records commits, deletions, composing regions and
selection, and a fake clock for timers.

Page cycle and configuration:

1. Config default, page 0: Sym tap sequence gives 1, 2, 0 (Device, Clipboard, Emoji Picker
   disabled by default).
2. Factory baseline config: taps give 4, 2, 0.
3. Config `{"symPageOrder":["symbols","emoji"],"emojiEnabled":true,"symbolsEnabled":true}`:
   taps give 2, 1, 0; normalised order is symbols, emoji, device, clipboard, emoji_picker.
4. Config with `symPageOrder` absent and `emojiFirst:false`: order is clipboard, symbols,
   emoji, emoji_picker, device.
5. Page 1 open, then config sets `emojiEnabled:false` with symbols enabled: reading the current
   page yields 2. Page 2 open with `symbolsEnabled:false`: still 2.
6. Direct open of clipboard while page 3 is open: page becomes 0; while page 1: page becomes 3.
7. `restore_sym_page` = 3 with clipboard disabled and cycle [1,2]: restored page is 1, the
   preference is cleared.

Chords and session:

8. Editable field; Sym down; C down (repeat 0), `sym_edit_shortcuts` on: editor receives the
   copy action, no text; Sym up: page stays 0.
9. Same with `sym_edit_shortcuts` off and no launcher shortcut, default config: `😅` committed
   (Emoji is the first enabled key layer); Sym up: page stays 0.
10. Default config with symbols first in the order: Sym+C commits `&`.
11. Sym down, Shift down, Sym+C with a page file mapping `KEYCODE_C` to
    `{"lowercase":"&","uppercase":"§"}`: `§` committed.
12. Sym down, Sym up with no other key: page 1 open. Sym down, Q down: `😀` committed, page
    closes (auto-close on). With auto-close off: page stays 1.
13. Page 1 open, Enter with auto-close on: page 0 and Enter reaches the app. With auto-close
    off: page 1 and Enter reaches the app.
14. Page 2 open, Ctrl held, C: Ctrl shortcut path taken, page still 2.
15. Page 2 open, Back: page 0, key consumed.
16. Page 1 open, Alt down: page 0, Alt one-shot armed on release.
17. Assistant hold on, Sym down, 600 ms elapse: assistant launched; Sym up: page stays 0.
    Assistant hold on, Sym down, Q down at 200 ms: timer cancelled, chord committed; Sym up:
    page 0. Assistant hold on, Sym down, up at 300 ms: page 1.
18. Key events with no editable field: Sym down and up leave the page at 0.

Alt:

19. Profile `titan2elite_qwerty`: Alt held + U commits `_`; profile `titan2`: `-`; profile
    `unknown`: `-`.
20. Alt one-shot then Space with `clear_alt_on_space` on: space committed, one-shot cleared.
21. Numeric field, Q without Alt: `0` committed; with Ctrl one-shot: not.
22. Binding `emoji`: Alt+Q commits `😀`; binding `first` with order device, emoji, symbols:
    Alt+Q commits `0`; binding `first` with order symbols, emoji: `~`.
23. Alt+Q with auto-space punctuation containing `.` and a pending auto-space after "word ":
    Alt+M on `titan2elite_qwerty` turns "word " into "word. ".

Long press:

24. Mode `variations`, threshold 50 ms, variations u to [ü], U to [Ü]: U key down commits "u";
    after 60 ms the text is "ü", replaced via composing region (0,1), no delete calls.
25. Same, but "3" is typed after the "u" before the timer: result "ü3", selection at 2.
26. Same with one-shot Shift: "Ü3".
27. Same, but the app rewrote the "u" into "x" before the timer: text stays "x3", no
    composing region set.
28. Same with extracted text unavailable at commit time and "3" typed: text stays "u3".
29. Mode `alt`, profile `titan2elite_qwerty`, threshold 300 ms: Q down commits "q"; key up at
    100 ms leaves "q" and cancels the timer; Q down held 300 ms yields "0" (one delete, one
    commit).
30. Mode `shift`, layout qwertz: Y key held past threshold yields "Z".
31. Mode `sym` with emoji before symbols: Q held yields `😀`; mode `sym_symbols`: `~`.
32. Threshold preference 2000 is clamped to 1000; 10 to 50.
33. Key down repeat (repeat count 1) while the long-press timer is pending: consumed, nothing
    committed.

Layouts and locales:

34. Locale `de` gives `qwertz`; `de_DE` gives `qwertz`; `de-AT` gives `qwertz`; `xx_YY` gives
    `qwerty`; with a user mapping file `{"xx":"azerty"}`, `xx_YY` gives `azerty`.
35. Layout `azerty`: key `KEYCODE_Q` lowercase is `a`, uppercase `A`; `KEYCODE_A` is `q`/`Q`.
36. Parsing `{"mappings":{"KEYCODE_Q":{"lowercase":"q"}}}` yields no entry for Q; parsing an
    entry with `multiTapEnabled:true` and one tap yields a single-character entry.
37. Layout cycle with list [`qwerty`,`missing`,`qwertz`] and current `qwerty`: next is
    `qwertz`; from `qwertz`: `qwerty`. List [`qwertz`] and current `qwertz`: next is `qwertz`.
38. Shipped Symbols page: S is `;`, F is `–`, J is `„`, K is `“`, C is `&`, O is `°`, V is `^`,
    Z is `»`, X is `«`.
39. Custom Emoji page `{"mappings":{"KEYCODE_Q":"🙂"}}`: Q gives `🙂`, W gives nothing.

Variations:

40. Active layout `qwertz`: `a` list starts with `ä` then `à á â ã å ą` (no duplicate `ä`);
    `S` list starts `ẞ ß Š ...`. Active layout `norwegian_multitap_qwerty`: `a` starts `å æ`.
41. User file without `layoutVariationOverrides`: the shipped overrides still apply. User file
    with `qwertz.a = ["â"]`: `a` starts with `â`, and `qwertz.o` from the shipped file still
    applies.

## 15. Keep / Drop for 3.0

| Item | Verdict | Reasoning |
|---|---|---|
| Sym tap toggles on release; chords never open a page | keep | the core of the Titan Sym key |
| Emoji and Symbols key layers with shipped maps | keep | the two pages people use |
| Device page (5) | drop | duplicates Alt, off by default, marked under construction |
| Clipboard and emoji picker as Sym pages | keep | overlays are in the 3.0 scope; cycling into them is how the Titan reaches them |
| Page order and enable switches, `sym_pages_config` shape | keep | user content that survives migration; keep the JSON contract |
| Legacy `emojiFirst` field | drop | 3.0 writes and reads `symPageOrder` only |
| Custom Emoji/Symbols maps, `sym_mappings_*` shape | keep | user content in backups |
| Object-form `{lowercase, uppercase}` Sym entries | undecided | shipped files never use it; custom maps erase it; keep only if a Shift layer for symbols is wanted |
| Sym+C/V/X/A edit shortcuts | keep | Titan has no Ctrl key (D5) |
| Auto-close and auto-close-on-touch | keep | one-shot versus sticky is a real preference |
| Hold Sym for the assistant (600 ms) | keep | Titan-only feature people asked for |
| `SYM` indicator colour tied to page 2 | drop | make the indicator reflect open/locked properly |
| Hidden emoji map text | drop | dead view |
| On-screen Sym grid with Titan alignment | keep | it is the page's visible face; drop the non-Titan centred variant |
| Globe button on the grid | undecided | opens the system IME picker; cheap, rarely used |
| Alt layer from `titan2elite_qwerty` file | keep | the keycaps |
| `titan2` file and fingerprint fallback | undecided | the non-Elite Titan 2 is untested; keep the file if the rewrite wants to stay installable there |
| Virtual Alt file | drop | soft keyboard only |
| `__DPAD_*__` sentinels | drop | Clicks-only |
| Currency symbol setting | drop | inert on Titan (D7) |
| Alt binding (`alt_character_layer_binding`) | drop | orphaned setting; Alt should be the keycaps |
| Device SYM Layer Editor stub | drop | never built |
| Physical keyboard profile override | drop | 3.0 is Titan-only |
| Long-press modes `alt`, `variations`, `shift` | keep | all useful on a hardware keyboard |
| Long-press modes `sym`, `sym_symbols`, `sym_emoji` | undecided | niche; cheap to keep if pages stay |
| Long-press threshold with one default | keep | fix the 300/500 split |
| Anchored composing-region replacement for variations | keep | it is what keeps fast typing intact |
| Variations data and layout overrides | keep | but expose an editor or drop the custom file |
| `AllVariations.json` and the picker dialog | drop | unreachable |
| Static/email variation lists | drop | the row they fed is gone |
| Layout files and the identity default | keep | needed for qwertz/azerty users |
| 18 bundled layouts | undecided | keep qwerty, qwertz, azerty and the multi-tap Latin ones; the Cyrillic, Greek, Arabic and Armenian sets serve nobody known on a Titan |
| Vietnamese Telex layout | drop | listed as dropped in the rebuild plan |
| Custom layout import from file | keep | small, user content in backups |
| Cloud layout download from the upstream host | drop | depends on the upstream author's site; dictionaries moved to our own host, layouts should not depend on theirs |
| Online layout editor link | drop | third-party site |
| Layout viewer | keep | cheap and useful for multi-tap layouts |
| Locale to layout mapping and user override file | keep | needed for automatic layout by language |
| Layout list cycling | undecided | overlaps with language switching; decide with the languages document |
| Layout switch chords and toast | see languages document | not owned here |
| French spacing on layer characters | keep | consistent with text-input |
| Auto-space replacement missing on Sym page commits | fix in 3.0 | make all layer commits go through one commit path |

## 16. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/docs/plans/rebuild-from-scratch.md
- /home/disdiqqq/projects/pastiera/docs/titan2elite/DEVICE.md
- /home/disdiqqq/projects/pastiera/docs/titan2elite/TitanKey.kl
- /home/disdiqqq/projects/pastiera/docs/titan2elite/TitanKey.kcm
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/SymLayoutController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AltSymManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SymPagesConfig.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SymCustomizationScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SymCustomizationActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/DeviceSymLayerEditorStubScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/VariationStateController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/VariationButtonHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/VariationPickerDialog.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/SymEditShortcuts.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/LayoutMapping.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/JsonLayoutLoader.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/LayoutMappingRepository.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/LayoutRepositoryManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/layout/LayoutFileStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/mappings/KeyMappingLoader.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/data/variation/VariationRepository.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyboardLayoutSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyboardLayoutViewerScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/HardwareKeyboardSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyboardTimingSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/TextInputSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/CustomInputStylesScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/TutorialActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsBaseline.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsMigration.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/KeyboardVisibilityController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ScreenTrackpadController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ModifierKeyHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/ui/EmojiPickerView.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/subtype/AdditionalSubtypeUtils.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/ModifierStateController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/Punctuation.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/AutoSpaceTracker.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/sym/sym_key_mappings.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/sym/sym_key_mappings_page2.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/alt/virtual_alt_key_mappings.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/devices/titan2/alt_key_mappings.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/devices/titan2elite_qwerty/alt_key_mappings.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/variations/variations.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/variations/defaultvariations.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/variations/AllVariations.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/layouts/ (all 18 files)
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/locale_layout_mapping.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/default_settings.json
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/AltSymManagerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/SymEditShortcutsTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/data/mappings/KeyMappingLoaderTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/StatusBarControllerSoftwareSymTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/subtype/AdditionalSubtypeUtilsLayoutTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/SettingsManagerLayoutSwitchTest.kt
- git log (commits 54b5fdc, 0286208, 80bbe0b, 96c2d73, 55f2a74, e4b5974, ef222dc)
