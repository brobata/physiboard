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
- **Sym pages**: the pages the Sym key cycles through. Three are *key layers* that remap the 26
  letter keys (Device, Emoji, Symbols; 3.0 drops Device and adds the user's own three, "My
  page 1" to 3, section 4.6); the rest are *panels* (Clipboard, Emoji Picker, and 3.0's GIF
  page) that are content, not key maps.
- **Page chooser** (3.0): a small transient panel, opened by a double tap of Sym, that lists
  every page with a letter and opens the one whose letter is pressed (section 5.10).
- **Variations**: accented or related characters for a base character (a to à á ä ...), reached
  by long press when the long-press action is set to Variations ("Accent / variation" in 3.0).
- **Accent chooser** (3.0): a transient bar a long press in Accent mode opens when the letter
  has more than one variation; it numbers them and swaps the one just typed (section 8.4).
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
| `gif` | 6 | panel | GIF search (3.0; section 4.5) |
| `custom1`, `custom2`, `custom3` | 7, 8, 9 | key layer | the user's own pages, "My page 1" to 3 (3.0; section 4.6) |
| `fill` | 10 | panel | one-time codes and a password manager's suggestions, in the cycle only while it has something (3.0; section 4.7) |

Page number 0 means no page is open. Page number 5 stays reserved for the dropped Device page.

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
| `emojiEnabled` | boolean | true (3.0: false) | Emoji page (the letter-key layer, "Emoji keys" in 3.0) is in the cycle |
| `symbolsEnabled` | boolean | true | Symbols page is in the cycle |
| `clipboardEnabled` | boolean | false | Clipboard panel is in the cycle |
| `emojiPickerEnabled` | boolean | false (3.0: true) | Emoji picker panel ("Emoji" in 3.0) is in the cycle |
| `gifEnabled` | boolean | true | GIF page is in the cycle (3.0). On by the maintainer's choice (2026-10-07); it is the only page that sends anything off the phone, and only while it is open |
| `custom1Enabled`, `custom2Enabled`, `custom3Enabled` | boolean | false | the user's own pages 7, 8 and 9 are in the cycle (3.0, section 4.6) |
| `fillEnabled` | boolean | true | the Fill page may join the cycle; it joins only while it has something (3.0, section 4.7) |
| `symPageOrder` | array of page ids | `["device","emoji","symbols","clipboard","emoji_picker","gif","custom1","custom2","custom3","fill"]` | cycle order |
| `emojiFirst` | boolean | true | legacy; written for old builds as "emoji comes before symbols in the order" |

Reading is tolerant: unknown ids in `symPageOrder` are dropped, duplicates collapse to the
first occurrence, whitespace is trimmed, and every known id missing from the order is appended
in default order. If `symPageOrder` is absent the order is rebuilt from the legacy flag: emoji,
symbols, clipboard (reversed to clipboard, symbols, emoji when `emojiFirst` is false), then
emoji_picker, then device appended by normalisation. A malformed value yields the defaults.

3.0: a config written before the GIF page existed has no `gif` in `symPageOrder` and no
`gifEnabled`; it reads with `gif` appended last and `gifEnabled` false (its owner never chose it, whatever a fresh install's default), every other field as
stored. Likewise a config written before the user's own pages existed reads with `custom1`,
`custom2` and `custom3` appended last, in that order, and all three switched off. A config
written before the Fill page reads with `fill` appended last and `fillEnabled` true: unlike the
GIF page it sends nothing anywhere and shows only when it has something for the field. 3.0 never writes `deviceEnabled` or `emojiFirst`, and drops `device` from the order when
reading.

The factory baseline shipped in `common/default_settings.json` (applied once to every install)
sets `{"emojiEnabled":false,"symbolsEnabled":true,"clipboardEnabled":false,
"emojiPickerEnabled":true,"emojiFirst":false,"symPageOrder":["emoji_picker","symbols",
"clipboard","emoji"]}`. On a fresh Titan the cycle is therefore: no page, Emoji Picker,
Symbols, no page. `device` is absent from that order and is appended last, disabled.

3.0 (baseline 8, 2026-10-07): a fresh install, and once every existing 3.0 install through
the settings baseline (not a store the 2.x importer filled in the same start, whose pages and
order are the user's own), has Emoji (the picker), Symbols and GIFs on, in that order, and every other
page off: `symPageOrder` `["emoji_picker","symbols","gif","clipboard","emoji","custom1",
"custom2","custom3"]`. Sym therefore steps Emoji, Symbols, GIFs, closed. The letter-key Emoji
page stays in the list, off: it is still the one-press way to an emoji on each key and the
first key layer a Sym chord can draw from when the user turns it on.

### 4.2 The cycle

The cycle is the ordered list of enabled pages with "no page" (0) prepended. Tapping Sym moves
one step forward and wraps. 3.0: the Fill page (4.7) is in the list only while it has something,
and moves to the front when what it has is for this field; whether it is, is decided at each
Sym press. If the current page is not in the cycle, the next step is the first
enabled page. If no page is enabled, Sym never opens anything.

Consistency rule applied on every read of the current page: if the current page is the Emoji
page (1) and it is not in the enabled cycle, it is replaced by the first enabled page, or by
"no page" when nothing is enabled. Pages 2, 3, 4 and 5 are allowed to stay open even when
disabled in the cycle, because they can be opened directly (section 4.3). Only the Emoji page
gets evicted this way. 3.0 no longer applies this rule: a page opened from the chooser stays
open whether or not it is in the cycle, the Emoji page included.

### 4.3 Direct opens

2.x: four status bar buttons opened a specific page regardless of whether it was enabled in the
cycle, and each one toggled: pressing it while its page was open closed the page.

| Button | Page |
|---|---|
| clipboard | 3 |
| emoji picker | 4 |
| emoji layer | 1 |
| symbols | 2 |

The Minimal Phone emoji key (keycode 666) also toggles page 4; irrelevant on a Titan.

3.0: the status bar is gone for good, and its buttons with it. The one direct way to a page is
the page chooser (section 5.10): it opens any page, enabled in the cycle or not, and does not
toggle (choosing the page that is already open leaves it open). The chooser is also a command,
`physiboard.sym_page_chooser`, so it can sit on an assigned launcher key or on the Fn layer
(expansion-clipboard-pickers-launcher.md 8.2).

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

### 4.5 The GIF page (3.0)

Page 6, a panel. In the cycle by default, third after Emoji and Symbols (`gifEnabled` true
since baseline 8; before, it was off and only the chooser reached it, which the maintainer
never found); the user moves or switches it off under Customize SYM Keyboard (5.9), and the
chooser opens it with G (5.10). It is
the only Sym page that sends anything off the phone, and only while it is open.

When it opens while `gifEnabled` is on, it asks for trending 300 ms later (the Sym double-tap
window), so a page a double tap only flashes past asks for nothing. When it is off and the
chooser opened it, it asks for nothing at all until the user types a search, presses Enter or
taps a quick search; it shows the local sections and "Type to search KLIPY, or press Enter for
what's trending." meanwhile.

**Provider.** KLIPY (https://klipy.com, documentation at docs.klipy.com), the Tenor-compatible
service started after Google shut the Tenor API on 2026-06-30. Two endpoints are used, both
`GET` on `https://api.klipy.com/api/v1/{app_key}/gifs/...`:

| Use | Path | Parameters sent |
|---|---|---|
| query empty | `trending` | `page`, `per_page` 24, `locale` (the phone's country, two lowercase letters, left out when unknown), `content_filter` `medium`, `format_filter` `gif,webp` |
| query typed | `search` | the same plus `q`, the trimmed query |

No `customer_id` or any other identifier is sent. KLIPY's share trigger (`POST
.../gifs/share/{slug}`) is optional in its documentation and only feeds its personalisation, so
it is never sent. A list body over 1,000,000 bytes is refused.

A response is `{"result":true,"data":{"data":[items],"current_page","per_page","has_next"}}`.
Each item's `file` holds renditions `hd`, `md`, `sm`, `xs`, each with `gif` and `webp` objects
of `url`, `width`, `height`. The preview is the first present of xs.webp, xs.gif, sm.webp,
sm.gif, md.webp, md.gif; the GIF sent is the first present of md.gif, sm.gif, hd.gif, xs.gif.
Only `https://` URLs count. An item with neither is skipped; every other item keeps the place
KLIPY gave it (KLIPY's integration rules forbid reordering or filtering results, and require
URLs to be used exactly as returned). `result` false or another shape reads as a failure.

**The API key** is a build input, never committed: Gradle property `klipy.apiKey`
(`-Pklipy.apiKey=...` or `~/.gradle/gradle.properties`), else the line `klipy.apiKey=...` in
the untracked `local.properties`. With no key the page draws as usual and says "GIF search isn't
set up in this build: it has no KLIPY API key." in place of results; nothing is requested.

**Layout**, 260 dp tall, above the keyboard window like the picker:

1. A search field (hint "Search KLIPY") and a close button (36 by 32 dp).
2. A row of quick searches, in order: LOL, Love, Sad, Wow, Yes, No, Bye. Tapping one replaces
   the query with it and searches at once.
3. The grid: cells 88 dp tall, as many columns of at least 104 dp as fit (2 to 5), each preview
   cropped to fill. With an empty query: "★ Favourites" (when any), "Recently sent" (when any),
   then "Trending" from KLIPY. With a query: KLIPY's results alone. The next page of the same
   list is fetched when the grid is scrolled to within two rows of its end and `has_next` was
   true; a later page that fails ends that for the list (no retry on every scroll), and a body
without `current_page` counts as the page asked for. A message (no key, private mode, "Couldn't reach KLIPY. Check the connection and try
   again.", "No GIFs found") shows along the bottom of the grid; the local sections stay above
   it.
4. "Powered by KLIPY" in small type at the bottom end (KLIPY asks for its branding).

**Search from the keys.** The page opens with capture on: hardware keys type into the search
field under exactly the picker's capture rules (expansion-clipboard-pickers-launcher.md 4.5:
Back, Sym, pure modifiers and Alt or Meta combinations are not captured; Ctrl+A/C/X/V edit the
field). Enter searches at once; otherwise the query is searched 400 ms after the last change.
Tapping the field turns capture off and on; the app's caret moving turns it off. A response for
a query no longer on screen is dropped.

**Previews** are fetched through the network gate (app-shell.md 31.2, purpose "GIF download")
four at a time, at most 2,000,000 bytes each, decoded off the main thread by the platform
(animated WebP or GIF), scaled to the cell width, and animate. They are kept in memory only, up
to 8 MB for the life of the keyboard; there is no disk cache, because KLIPY's integration rules
forbid keeping copies of its media.

**Sending.** Tapping a cell sends it (with `sym_auto_close` and `sym_auto_close_on_touch` on,
the page closes first, as for every page tap, 5.5):

- When the field declares, in `EditorInfo.contentMimeTypes`, a type matching `image/gif`
  (`image/gif`, `image/*` or `*/*`, ignoring case), the GIF is fetched through the gate (at
  most 8,000,000 bytes), written under a unique name to the cache path `gif-share/` (on every
  send and when the keyboard service ends, files older than 10 minutes and all but the four
  newest are deleted), and committed with `InputConnection.commitContent`, MIME type
  `image/gif`, a read grant on a content URI from the authority `<package>.gifshare`, and the
  GIF's address as the link. If the field changed meanwhile (the keyboard left the field or
  attached to another one, even another chat in the same app, or the package or field id
  differ), it is not sent: toast "The text field changed, so the GIF was not sent." A failed download
  toasts "Couldn't download the GIF."
- Otherwise, or when the app refuses the content, the GIF's address (the URL above) is typed
  as plain text. That needs no request at all. A cancelled list or preview request (the query
  changed, the page closed) is cut off at once rather than left to time out.

A GIF that went in moves to the front of "Recently sent" (24 at most, no duplicates).

**Favourites.** A long press on a cell stars it ("Added to favourites") or unstars it
("Removed from favourites"), and that cell's ★ in the top corner appears or goes at once (the
grid is not redrawn, so no request is made; the Favourites section follows on the next open or
search); and favourites (48 at most,
newest first) lead the empty-query grid. Both lists live only on the phone, in the
`gif_prefs` file (`gif_favourites`, `gif_recents`, each a JSON array of the GIF's slug, title,
preview and send URLs and sizes; never the image), and are not part of a backup. Reading is
tolerant: a malformed value is an empty list, an entry without its slug or `https://` URLs is
skipped, duplicates collapse to the first.

**Privacy** (app-shell.md 31). Every request (lists, previews, the GIF sent) goes through the
network gate. In private mode nothing is requested and the page shows the gate's sentence
("Private mode is on, so PhysiBoard makes no network requests."); until the keyboard has read
the setting it shows the gate's other sentence ("PhysiBoard could not read its settings, so it
makes no network requests."); a link can still be typed into a field that takes no images, since that sends
nothing. While learning is off (private mode, or a field asking for no personalised learning),
a sent GIF is not added to recents and a star is refused (toast "Private: favourites are not
changed"); removing a star still works.

### 4.6 The user's own pages (3.0)

Three key layers of the user's own, pages 7, 8 and 9 (`custom1` to `custom3`), so a user who
wants more characters at hand need not give up the Emoji or Symbols page for them. Each has a
name and, for any of the 26 letter keys, a text to type: a character, a symbol, an emoji, a
character from another script, or a short word. They are stored together in the preference
`sym_custom_pages`:

```
{"pages": [{"name": "Polski", "mappings": {"KEYCODE_A": "ą", "KEYCODE_S": "ś"}},
           {"name": "", "mappings": {}}, {"name": "", "mappings": {}}]}
```

Reading is tolerant: there are always exactly three pages; a missing or malformed page reads
empty; extra pages are ignored; a name is trimmed and cut to 24 characters; a blank name shows
as "My page 1", "My page 2" or "My page 3"; a key name other than `KEYCODE_A` to `KEYCODE_Z`
or an empty text is skipped. A page with no key has no characters (every key types as usual
while it is open). Like the custom Emoji and Symbols pages (4.4) the pages have no uppercase
map, and the default skin tone (expansion-clipboard-pickers-launcher.md 4.7) tones their emoji.

Each page has a switch and a place in the cycle in `sym_pages_config` (4.1), off by default.
A page behaves like the Emoji and Symbols pages in every way: it is drawn as the grid of 5.7
(a word shrinks to fit its key, down to 14 px), a key on it types its text (5.4) and closes it
under `sym_auto_close` (5.5), a Sym chord draws from it when it is the open page or the first
switched-on key layer (5.3), its pencil and a long press on a key open its editor (5.8; the
keyboard passes page number 7, 8 or 9), and a page restored after the editor follows 5.8.
The page chooser opens one with M, N or B (5.10).

**Editing** (Customize SYM Keyboard, 5.9): the three pages are rows in "Arrange SYM pages
order", each with a switch, the arrows and a pencil, and the kind label "Key layer · your own ·
chooser letter M" (N, B). The pencil opens "Edit <name>": a "Page name" field, the grid of 5.9,
and a red "Clear page" button that asks "Remove every key from this page? Its name stays. This
cannot be undone." Tapping a grid key opens the character dialog (expansion-clipboard-pickers-
launcher.md 5.2): any text in its custom field, or a character from its grid; its "Clear this
key" choice removes the key. Every change is written at once and travels in backups.

### 4.7 The Fill page (3.0)

Page 10, a panel: one-time codes from notifications and a password manager's suggestions,
where a phone with an on-screen keyboard would show them in a bar. There is no bar (the
suggestion bar is gone for good): nothing pops up by itself, and the page is reached with Sym
like every other page.

**When it is in the cycle.** Unlike every other page, the Fill page joins the cycle only while
it has something, and its place depends on what:

| The page has | Its place |
|---|---|
| nothing (no code waiting, no suggestions) | not in the cycle; the chooser (5.10, F) still opens it, showing why it is empty |
| a code, and the field is not a code's field (text-input.md 3.1) | at its own place in the order (last by default, so after GIFs) |
| a code, in a code's field | first: the first Sym press opens it, ahead of Emoji, Symbols and GIFs |
| a password manager's suggestions for this field | first |

`fillEnabled` off keeps it out of the cycle whatever it has. What the page has is decided at
every Sym press (codes come and go with the clock) and at every field start; a Fill page that
followed to the next field of the same app (5.2) closes there if it has nothing for it.

**The cue.** When the page would be first and no page is open, the caret badge
(trackpad-caret-nav.md 4.2) adds a faint `FILL` after its other glyphs, in the one-shot colour
at the faint alpha. The badge was chosen over a toast, a vibration or a strip button because it
already sits at the caret, already comes and goes with state, and costs nothing when the user
does not care; with `caret_modifier_badge` off there is no cue, and Sym still opens the page
first.

**The page.** A panel at the bottom like the other panels (5.7), its height its content's:

1. A header, "Fill: press a code's key, or tap", and the close button.
2. A password manager's suggestions, when there are any: one row of chips the manager draws
   itself (a login, "Autofill with ...", its own icon). Chips it marks pinned sit at the start
   and never scroll; the rest scroll sideways. A tap on a chip lets the manager fill the field;
   PhysiBoard never sees the login or the password.
3. The codes, newest first, each a row: the key that types it, the code in large monospace,
   and "Code from <app> · <age>" ("just now" under a minute, then "N min ago", redrawn every
   30 s while the page is up).
4. With neither, one line saying why: one-time codes are switched off; or "give PhysiBoard
   notification access in its settings (Customize SYM Keyboard)"; or that codes show here for
   10 minutes.

**Typing a code.** The key printed with 1 types the first code, 2 the second, 3 the third: the
letter keys whose device-layer character (3.2) is that digit, W, E and R on the Titan 2 Elite,
labelled with that letter on the row, as the accent chooser labels its picks (8.4). Bare or with
Alt (an armed Alt one-shot is spent on the pick); never with Sym held (that is a Sym chord) or
Ctrl in any form (a Ctrl shortcut), which go on as usual. The key's release and auto-repeats are
consumed with it. A key types the code the page shows on its row, even if another arrived since. A tap on a row types it too. Any
other key behaves as on any panel page (5.4): it types with the page open, Back closes it.
The code goes in as one finished commit (no composing, no auto-space); in a Terminal mode field
(per-app-behavior.md) each character is sent as its key press, the way that field gets every
character. A typed code leaves the list and is not taken again from its notification while it
would still be valid (a messaging app posts its conversation again on every change). With
`sym_auto_close` on the page closes first (for a tap, with `sym_auto_close_on_touch`).

**Where codes come from.** A notification listener, "PhysiBoard one-time codes", its own entry
in Android's notification access list (separate from the notification ring's, so each is its
own choice; the privileged setup pass grants only the ring's). It works only after the user
allows it; then, while `otp_from_notifications` is on and private mode (app-shell.md 31) is
off, it reads each posted notification's title and text (a conversation's messages newest
first, the expanded text, the text, an inbox's lines), ignoring PhysiBoard's own notifications
and group summaries, and keeps a code when the extractor below finds one in a text posted less
than 10 minutes ago. When it connects, and when reading is switched back on, it reads the
notifications already showing the same way.

**The extractor.** A code is only ever taken next to a word that says it is one: "code",
"OTP", "one-time", "passcode", "password", "verification", "PIN", "TAN", "2FA", "sign in"
and the like in English, Spanish, Portuguese, French, German, Dutch, the Scandinavian
languages, Italian, Polish, Czech, Turkish, Indonesian, Vietnamese, Russian, Ukrainian, Greek,
Hebrew, Arabic, Persian, Hindi, Chinese, Japanese and Korean. "Promo code", "zip code",
"country code", "code review" and their kind are not that word, and a word from the title (the
sender or the subject) counts only for a body of at most 60 characters. Near it, at most 60
characters away, it takes 4 to 8 digits; 9 or 10 digits, or a 4-digit year (1900 to 2099),
only right beside the word ("code: 1234567890", "2024 is your code"); two groups of 3 or 4
digits written apart ("482 913", "482-913") joined; a 1 to 3 letter prefix and digits
("G-482913" gives "482913"); or 4 to 10 capitals and digits mixing both ("F7K2QX"). It never
takes a phone number (international, North American, four groups, or after "call", "text",
"reply" and the like), a date or time, an amount (a currency sign or code before or after,
thousands separators, a decimal part, a percentage, or after "payment of", "balance", "total"
and the like), an IP address, a masked card or account number ("****1234", "XX1234", "ending in
1234"), a number named as an order, invoice, tracking, booking, reference, account, ticket and
the like, a quantity with a unit, or anything in a link or an e-mail address. Of several
candidates the nearest wins, one right beside the word ("code is 123456", "123456 is your
code") first, six digits over other lengths, digits over letters and digits. Digits in any
script (Arabic-Indic, full-width) are read as ASCII digits; codes are typed in ASCII.

**Kept in memory only.** At most 3 codes, newest first; a code is dropped 10 minutes after its
notification was posted, when it is typed, when the screen turns off, when private mode comes
on, when `otp_from_notifications` goes off, and when notification access is withdrawn; a code
received before such a clear is not taken again when its notification is posted again (a
conversation re-posted after the screen comes back). The page and the caret cue are redrawn
when the oldest code expires. Nothing
is written to disk, put in the settings or a backup, logged, traced, put in a diagnostics
report or sent anywhere; a process restart forgets them. The phone-testing log line that
quotes the text before the caret after an outside change says "(hidden)" in a code's field and
for 60 s after a code is typed. A failure while reading is logged by
its kind alone, never with any text.

**Password manager suggestions (experimental, `fill_inline_suggestions`, off).** Android 11
and later lets a keyboard show an autofill service's suggestions inline. On the Titan this
needs two things the keyboard otherwise never does, which is why it ships off: Android's
autofill sends the suggestions only while the keyboard's input view is up (the AOSP autofill
session sends its response only after the input method's input view starts; D15), and a
keyboard that asks for inline suggestions takes over from the manager's own drop-down list.
With it on, when Android asks (as a field that autofill can fill starts), PhysiBoard answers
with chips of 40 dp, its theme's colours, at most 6 suggestions, and asks for its input view,
an empty view of no height, for that field, so the response arrives. A response replaces the
field's suggestions; an empty one clears them; the field finishing clears them and lets the
input view go. Suggestions whose autofill hints name a one-time code (`smsOTPCode`) make the
field a code's field too. With it off PhysiBoard answers nothing and the password manager's
drop-down works as before.

## 5. The Sym key session

### 5.1 Key identity

The Sym key arrives as keycode 63 (`KEYCODE_SYM`) with scancode 253 (D1). Unlike Fn, it
delivers a normal key down and key up (D10).

### 5.2 Tap: toggle on release

With an editable field focused:

1. Sym key down (first event, not a repeat): the keyboard notes "a toggle is pending" and
   "no chord used yet", and arms the assistant hold timer if that feature is on (section 5.6).
   3.0: it also notes whether this press is the second tap of a double tap (section 5.10).
   The key down is consumed; nothing visible happens yet.
2. Any non-modifier key pressed while Sym is down marks the press as a chord (section 5.3).
3. Sym key up: if a toggle is pending and no chord was used, the page cycles one step
   (section 4.2) and the strip redraws; 3.0: unless this was the second tap of a double tap,
   which opens the page chooser instead (section 5.10). The pending and chord flags are
   cleared. The key up is consumed.

Without an editable field, Sym down and up do not touch the pages at all: the down goes to the
launcher-shortcut logic (power shortcuts toggle, out of scope) or to the system, and the up
just clears the flags. 3.0 makes two exceptions:

- While a page is open, Sym steps it (section 4.2) whether or not a field is there; Sym is never
  the launcher key with a page on screen.
- **A text box that went away.** When the app's editable field goes (the field finishes, or a
  start or restart replaces it with a field that is not editable), the keyboard notes the app,
  the time and the page that was open; a restart into a field that is not editable also closes
  the page, which has nothing left to type into; the window hiding counts the same, since the
  system may hide it before it finishes the field. When a page was open as the box went, for 15
  seconds after, in that same app, a Sym press with no editable field does not arm the launcher
  shortcuts: it is consumed and the toast "Tap the text box, then Sym" shows. A box left with no
  page open (Back to an app's list) leaves Sym the launcher key there at once. When an editable field of that app starts again
  within the 15 seconds, the noted page reopens. Found on the Titan (2026-10-07): with the emoji
  page open in Messages, a screenshot took window focus, Messages came back with no focused
  text box (Android logs `HIDE_SAME_WINDOW_FOCUSED_WITHOUT_EDITOR`), the page closed, and the
  next Sym toasted "Press shortcut key to launch". Another app (the home screen) or a press
  after the 15 seconds gets the launcher shortcuts as before. Moving between two fields of one
  app goes through a finish, so an open page follows to the next field of the same app.

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
   uppercase entry is used when it exists, else the normal entry. 3.0: the user's own pages
   (4.6) are key layers too, so the preferred text page is the open key layer, else the first
   switched-on key layer of Emoji, Symbols and the user's own pages in the configured order.
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
| a letter with a character on the current key layer (pages 1, 2, 5; 3.0: 1, 2, 7, 8, 9) | the character is committed (with French punctuation spacing applied when enabled and the character is one of `?!;:`); if `sym_auto_close` is on the page closes; the key is consumed |
| a letter with no entry, Space, digits, or any key on a panel page (3, 4) | not handled here; normal typing proceeds with the page still open |

Auto-space replacement (turning "word " plus punctuation into "word, ") is *not* applied to
page characters, only French spacing is. Alt and long-press characters do get auto-space
replacement (sections 6 and 7); this asymmetry is as shipped.

With Ctrl active the page is bypassed entirely and Ctrl shortcuts run.

The emoji picker page (4) captures ordinary typing into its search field; that behaviour is in
the pickers document.

3.0: a page key (or a Sym chord) that commits an emoji taking a skin tone arms a hold; its
auto-repeats are consumed, and still holding it past the long-press threshold opens the
skin-tone chooser (expansion-clipboard-pickers-launcher.md 4.7). The page characters themselves
come out in the default skin tone, `emoji_default_skin_tone`.

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
  (section 5.8). 3.0: a key whose emoji takes a skin tone opens the skin-tone chooser instead
  (expansion-clipboard-pickers-launcher.md 4.7); the pencil still opens the editor.
- A close button (36 dp by 32 dp, bottom right, close icon on a translucent red 95/255 alpha
  background unless themed) is visible on pages 1, 2 and 5; the clipboard and emoji picker
  panels carry their own chrome.
- 3.0: the Symbols page (2) puts a search button (🔍) in row 2's spare cell after L. It opens
  the emoji picker (4) in Unicode symbols mode with its search field up and capturing, the way
  into every Unicode symbol now that symbols are no longer one of the picker's mode-button
  modes (expansion-clipboard-pickers-launcher.md 4.3).
- 3.0: every page is a panel of its own at the bottom of the screen (key layers, picker,
  clipboard, GIFs, the chooser); the strip collapses while one is open, so no strip button
  shows under a page (status-bar.md 3.4). With `titan2_elite_rounded_corner_insets` on, a
  panel whose bottom sits within the display's corner radius of the screen bottom pads its sides
  and bottom by r times (1 - 1/sqrt 2), plus 2 dp, in its own background, and grows by the
  bottom padding: on the Titan 2 Elite, which reports r = 100 px, 33 px. The radius is the
  display's reported bottom-corner radius, else 24 dp. The key-layer grid takes its key width
  from the width left between the paddings.
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

3.0: the first section is **Sym pages**: a line reading the result back in the primary colour
("Sym: Emoji → Symbols → GIFs → closed"), the explanation collapsed under "About the Sym pages"
("Each Sym press opens the next page that is switched on, in this order; after the last one Sym
closes. ..."), then one row per page: its name numbered with the press
that reaches it while on ("1. Emoji"), what it holds and its chooser letter, a pencil where the
page can be edited, up and down arrows, and its switch. The picker is called "Emoji" and the
letter-key layer "Emoji keys". The Fill page (4.7) has a row like the others ("One-time codes
and saved logins; joins only when it has one, first in a code or login box · chooser letter F")
with its switch and arrows, but no press number and no place in the read-back line, since it
joins the cycle only when it has something. Under the list, a section "Fill page: one-time
codes": "One-time codes from notifications" (`otp_from_notifications`), "Notification access"
(the state, and a button that opens Android's page for the listener, 4.7), and "Password
manager suggestions (experimental)" (`fill_inline_suggestions`). Then "Kaomoji on the Emoji page"
(`emoji_picker_kaomoji`, expansion-clipboard-pickers-launcher.md 4.3). There is no drag handle;
the arrows reorder. The 2.x screen, for the record:

1. **Arrange SYM pages order** ("Drag or use the arrows to set the cycle order. The switch only
   controls whether an item appears in the cycle."): one row per page in normalised order with
   a drag handle (long-press then drag, one slot per 56 dp of vertical travel), an up arrow
   (disabled on the first row), a down arrow (disabled on the last), a kind label ("Key layer"
   or "Panel"), an edit pencil for Device, Emoji and Symbols, an "under construction" badge on
   Device, and an enable switch. Every change writes `sym_pages_config` immediately.
2. *(Removed 2026-10-08.)* 2.x had an **Alt character layer** row here ("Choose which SYM layer
   Alt uses in Modifier settings.") that opened a destination with no screen, so it led nowhere.
   3.x drops the dead row; the preference it described (section 6.3) has no settings row.
3. **SYM behaviour and display**: the three switches `sym_edit_shortcuts`, `sym_auto_close`,
   `sym_auto_close_on_touch` (the last greyed out while auto-close is off).
4. **Larger emoji picker** (`emoji_picker_expanded_height`).

Pressing a pencil on Emoji or Symbols replaces the screen content with the editable grid for
that page (title "Edit Emoji Layer" or "Edit Symbols Layer"), rendered with the same geometry
and Titan alignment as the live grid on a black background, followed by a red "Reset to
Default" button. Back returns to the list.

### 5.10 The page chooser (3.0)

With the status bar gone, cycling was the only way to a page; the chooser opens any page in two
taps and a letter.

**Trigger: a double tap of Sym.** Before 3.0 a Sym double tap had no meaning of its own: it was
two cycle steps. Nothing else claims it: chords need a key while Sym is held, the assistant
needs a 600 ms hold, and the edit shortcuts and launcher keys are chords. The one conflict is
with fast cycling, where two quick taps used to reach the second page; that page is now Sym Sym
and its letter, and `sym_double_tap_chooser` off brings the old behaviour back. So the double
tap is kept rather than moved to another trigger.

The rule, in an editable field with `sym_double_tap_chooser` on (default on):

- A plain Sym tap (one that cycled a page) remembers when it came up and which page was open
  before it.
- The next Sym down, if it comes no more than 300 ms after that release and no other key went
  down in between (Shift, Ctrl and Alt included), is the second tap. Its release, if no chord was used, puts back the page that
  was open before the first tap and opens the chooser over it. The first tap's page shows for
  the moment between the taps.
- A chord on the second press (an edit shortcut, a launcher key, a Sym symbol) works as always
  and opens neither the chooser nor a page. A hold that fires the assistant (5.6) wins too.
- The tap that opened the chooser ends the streak. With the chooser open a further Sym tap
  closes it (table below); after that, the next tap is a plain first tap.
- Without an editable field Sym taps never open the chooser.
- The window is 300 ms, shorter than Shift and Alt's 500 ms double tap, so two deliberate taps
  a little apart still step two pages.
- With Sym as the screen trackpad trigger in `double_tap` mode the trackpad takes the second
  tap first (it runs ahead of everything, trackpad-caret-nav.md 2.2), so the chooser is only
  reachable through its command there; in `single_tap` mode Sym never reaches the pages at all;
  in `hold` mode a quick tap is replayed and double taps work.

The same chooser opens from the command `physiboard.sym_page_chooser` (expansion-clipboard-
pickers-launcher.md 8.2), offered for assigned launcher keys and nav mode / the Fn layer; it
needs an editable field (otherwise "No input context").

**The panel** sits at the bottom above the keyboard window like the other panels: a title
"Open a Sym page: press its letter", a close button, and two columns of rows, one per page, each
showing its letter and name. Rows follow the cycle order; the picker's own modes follow the
picker. A page that is off in the cycle is drawn at 60% opacity but opens all the same. The
picker's row is named "Emoji" and the letter-key layer's "Emoji keys". The K row exists only
with `emoji_picker_kaomoji` on; without it K closes the chooser and types like any unlisted
letter.

| Key | Opens |
|---|---|
| E | Emoji page (1) |
| S | Symbols page (2) |
| C | Clipboard (3) |
| P | Emoji picker (4) in Emoji mode |
| K | Emoji picker (4) in Kaomoji mode |
| U | Emoji picker (4) in Symbols (Unicode) mode |
| G | GIF page (6) |
| M | the user's own page 1 (7), under its name (3.0, 4.6) |
| N | the user's own page 2 (8), under its name |
| B | the user's own page 3 (9), under its name |
| F | the Fill page (10, 4.7), whatever it has; dimmed while it has nothing for this field |

The letter is the one printed on the physical key (the QWERTY position), whatever the layout.
One of the user's own pages has a row only when it is set up: switched on in the cycle, or
holding at least one key. Its letter does nothing in the chooser otherwise (it closes the
chooser and types, like any letter without a row), so three empty "My page" rows never crowd
it. M stands for "My page"; N and B are the keys beside it, all free of the other letters.
There is no Device row: the Device page is dropped in 3.0.

While the chooser is open:

| Key | Result |
|---|---|
| a letter in the table | the chooser closes and that page opens; key and release consumed |
| Shift | ignored, the chooser stays (so Shift+letter picks too); consumed |
| Back, Sym | the chooser closes; consumed |
| an auto-repeat of a key the chooser consumed | consumed (any other key's repeat counts as that key, so Fn closes it) |
| any other key (another letter, Space, Enter, Alt, digits) | the chooser closes and the key does what it always does |

Tapping a row opens its page; the close button closes it. The chooser draws nothing that
stays: it closes after a pick, after a dismissal, after 10 seconds without a key (a Shift press
starts the 10 seconds over), when the field finishes, and when the keyboard window hides.

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

3.0: one default, 500 ms, for the screen and the timer alike. The row is "Hold time" on the
Keyboard > Long press screen ("How long to hold a key before it counts as a long press."), a
slider from 50 to 1000 ms in 50 ms steps showing "N ms"; a stored value outside the range is
clamped.

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

3.0: the modes are chosen on Keyboard > Long press, under "Long press types", one radio row
each, with the stored values unchanged:

| Value | 3.0 label | Description shown |
|---|---|---|
| `alt` (default) | Alt symbol | The symbol printed on the key, as with Alt. The default. |
| `shift` | Capital letter | Hold a for A. |
| `variations` | Accent / variation | Hold a for its first accent, ą in Polish or ä in German, in your keyboard language's order. |
| `sym_symbols` | Sym symbol | The key's character on the Symbols page. |
| `sym_emoji` | Sym emoji | The key's emoji on the Emoji page. |
| `sym` | First Sym page | The key's character on the Emoji or Symbols page, whichever comes first in your Sym page order. |

The same screen holds "Hold time" (7.1), "Show every accent" (8.4; greyed out unless the mode
is `variations`) and a row to "Customize Variations" (8.3). Search finds it under "Long press",
"Long press types", "Hold time", "Show every accent" and words such as accents, diacritics,
variations and hold.

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

3.0, `variations`: the replacement is the same delete-one-and-commit as every other mode (one
batch edit), not a composing-region swap: a web terminal is on record dropping composed text
(per-app-behavior.md D7), and in 3.0 no input can come between the letter and the timer (another
letter re-arms the long press for itself, an Alt or Ctrl key cancels it), so the anchor has
nothing to protect. What is kept is the check: the letter is replaced only while the field's
text before the caret still ends with it; an unreadable field, or one that reads back empty
(a web field answers "" after every letter), is trusted. In a terminal-mode
app (per-app-behavior.md 4.6) the check is skipped, since the terminal empties its text box
after every key, and the accent reaches the app like any other character (a commit, or key
presses for a character a key produces). The long press does not arm at all in a field that
takes no accents (text-input.md 3, the "Variations" column: an email address); the letter
types and auto-repeats as usual there. When the letter has more than one variation, the accent
chooser opens (8.4).

## 8. Variations

3.0 replaces the 2.x data files with one built-in table written for PhysiBoard 3.0 (no list is
taken from another keyboard), the user's own lists stored as a setting, and an accent chooser
that needs no bar. Each subsection gives 3.0 first and keeps the 2.x record after it.

### 8.1 Data

**3.0.** A built-in table of the Latin letters that take accents, lower and upper case, at most
ten entries each (one for each digit printed on the keys, 8.4). Accented forms come first, in
the order grave, acute, circumflex, diaeresis, tilde, ring, macron, ogonek, breve and the rest,
then related letters (ø œ æ ł đ ß þ ı), then a currency sign on its letter's name:

| Base | Variations (neutral order) |
|---|---|
| a | à á â ä ã å ā ą ă æ |
| c | ç ć č ¢ |
| d | ď đ |
| e | è é ê ë ē ę ě ė € |
| g | ğ ģ |
| i | ì í î ï ī į ı |
| l | ł ľ ĺ ļ £ |
| n | ñ ń ň ņ |
| o | ò ó ô ö õ ø ō ő œ ơ |
| r | ř ŕ |
| s | ß ś š ş ș $ |
| t | ť ț ţ þ |
| u | ù ú û ü ū ů ű ų ư |
| y | ý ÿ ¥ |
| z | ź ž ż |

The capital key's list is the same list in capitals, in the same order, with three exceptions:
ß becomes the capital sharp s ẞ, Turkish dotless ı becomes the dotted capital İ (the letter a
Turkish capital I lacks), and currency signs stay as they are. Outside Latin: Cyrillic е gives
ё є (Е gives Ё Є), г gives ґ (Г: Ґ), і gives ї (І: Ї), Р gives ₽, Armenian Դ gives ֏. Every
other character (h, p, digits, punctuation) has no variations by default.

**Language order.** When the active input style's language (its subtype locale, for example
`pl_PL`; Norwegian `nb` and `nn` count as `no`) has letters of its own, those letters move to
the front of their base letter's list, in the order below; every other entry keeps its place
behind them, and nothing is dropped:

| Language | Letters first (per base letter) |
|---|---|
| pl | ą ć ę ł ń ó ś ź ż |
| fr | é è ê ë, à â æ, ç, î ï, ô œ, ù û ü, ÿ |
| de | ä ö ü ß |
| es | á é í ó ú ü ñ |
| pt | ã á â à, õ ó ô, é ê, í, ú, ç |
| it | à, è é, ì í, ò ó, ù ú |
| ca | à, è é, í ï, ò ó, ú ü, ç |
| cs | á č ď é ě í ň ó ř š ť ú ů ý ž |
| sk | á ä č ď é í ĺ ľ ň ó ô ŕ š ť ú ý ž |
| ro | ă â î ș ț |
| tr | ç ğ ı ö ş ü |
| nl | é ë è, ï, ó ö, á, ü |
| sv | å ä, ö, é |
| da | å æ, ø, é |
| no | å æ, ø ô ò ó, é è ê |
| hu | á é í ó ö ő ú ü ű |
| gd | à è ì ò ù |
| vi | ă â, đ, ê, ô ơ, ư |

Any other language (English included) gets the neutral order. Spanish ¿ and ¡ are not on a
letter: they are on the Symbols page (¡ on B) and in the Unicode picker.

**2.x record.** Shipped file `common/variations/variations.json` with these top-level fields:

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
picker dialog that no screen opens any more. 3.0 orders by language rather than by layout, so a
German typist on `qwerty` gets ä first too, and drops `p` → `%`.

### 8.2 Effective list

**3.0.** The table a long press reads is the built-in table in the active language's order,
with each character the user customised (8.3) replaced by the user's own list for it, exactly
as saved; an empty saved list means that character has no variations. It is rebuilt whenever
the settings change and on every input style switch. A character with a list of one entry
types it on a long press and opens no chooser.

**2.x record.**

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

### 8.3 Writing: Customize Variations

**3.0.** The preference `custom_variations` holds the user's lists as one JSON object from a
single character to an array of strings, `{"a": ["ą", "à"], "E": []}`. Reading is tolerant: a
key that is not exactly one character, a value that is not an array and a member that is not a
string are skipped; an unparseable value reads as empty. Every list is cleaned on use: blanks
dropped, an entry cut to 16 characters, duplicates removed keeping the first, at most ten kept.

The screen "Customize Variations" (Keyboard > Long press > Customize Variations) says: "With
Long press set to Accent / variation, holding a letter types the first accent in its list. Tap a
letter to choose, order or add its accents. Your changes apply in every language." Then:

- "Order shown for": a dropdown of "No particular language" and the languages of 8.1, starting
  on the phone's language when it is one of them. It only chooses the order this screen
  previews untouched letters in; the keyboard uses its own language.
- One row per letter a to z: the letter (with "· changed" when either case is customised) and
  its accents, or "No accents".
- "Reset every letter to default" (red, greyed when nothing is customised) removes every list.

Tapping a letter opens "Accents for a": a section for the small letter and one for the capital
("a (changed)" when customised). Each entry is a row with its number (1 to 9, then 0; the first
is marked "Typed by a long press"), up and down arrows and a remove button. "Add" (greyed as
"Full (10)" at ten) opens the character dialog (expansion-clipboard-pickers-launcher.md 5.2),
which takes any text, a character from another script included; its "Cancel" adds nothing.
"Reset to default" (greyed unless customised) removes that case's list. The first change to a
case stores the whole list as shown (in the previewed order) and it is used as stored in every
language from then on. Every change is written at once and travels in backups. Search finds
the screen as "Customize Variations" and by words such as accents, diacritics, variations and
the accented letters.

**2.x record.** Only the Dev's Choice onboarding preset writes `files/variations.json` today
(it saves the currently effective lists unchanged), and "reset" copies `defaultvariations.json`
over it. The picker dialog ("Select variation for X", with a "Custom character" text field,
"Clear (Empty)" and a grid of 48 dp cells at least 4 per row) exists but is unreachable.
Backups include `variations.json` and merge it with the shipped defaults on restore rather than
overwriting.

### 8.4 Where variations show: the accent chooser

**3.0.** The suggestion bar is gone for good, so the accents show in a transient bar of their
own, the same row the skin-tone chooser uses (expansion-clipboard-pickers-launcher.md 4.7).

When a long press in `variations` mode replaces a letter with the first of two or more
variations, and `long_press_variation_chooser` is on (default on), the bar opens above the
keyboard with every variation of that letter, in order, each labelled with a digit and the
letter key that carries the digit on the device layer: the first is 1, then 2 to 9, then 0 for
the tenth; on the Titan 2 Elite that is 1 · W, 2 · E, 3 · R, 4 · S, 5 · D, 6 · F, 7 · Z,
8 · X, 9 · C, 0 · Q. A close button sits at its end.

The pick keys are ordinary letters, and typing straight on after an accent is common ("będę":
hold e for ę, then d), so a bare letter picks only while the long-pressed key is still held:

| Key | Effect |
|---|---|
| A key carrying a listed digit (or a digit key), while the bar is open, the long-pressed key held or not (amended 2026-10-07: the maintainer lets go first, then presses the pick key; typing straight on with a pick key while the bar is up now picks) | the accent just typed is replaced by that variation; the bar closes; the key and its release are consumed |
| The same key after Alt was pressed with the bar open, or with Alt held | the same pick |
| Alt | consumed with its release; the bar stays and the next pick key picks (so Alt then W picks the first, and no Alt one-shot is left armed) |
| The long-pressed key's own auto-repeat | consumed; the bar stays |
| The long-pressed letter tapped again once it is up (no Alt) | the next accent replaces the current one, wrapping round; the bar stays; consumed. The Titan reports a second key only after the held one is up (measured 2026-10-07), so this, not a bare pick key, is how a hand goes on |
| An auto-repeat of any other key held from before (Shift, Fn) | goes on as usual; the bar stays |
| Back | closes the bar without a change; consumed |
| Any other key (a letter once the held key is up, a letter with no listed digit, Space, Enter, Backspace, Shift, Sym, Ctrl) | closes the bar, then does exactly what it would have done |

A tap on a variation in the bar picks it the same way. A pick replaces the variation the long
press typed only while the field's text before the caret still ends with it, an unreadable or
empty read being trusted; otherwise nothing changes. A terminal-mode app skips that check
and gets the character the way it gets every other (per-app-behavior.md 4.6). Picking the
variation already typed changes nothing. The pick goes through the same path as the long press
itself, so the word being tracked follows. The bar also closes after 10 seconds without a key,
when the field finishes, when the keyboard window hides and when the keyboard service ends. It
never opens on its own, and it intercepts Space, Enter, Shift and Backspace only by closing.

**2.x record.** Only through the long-press `variations` mode. The accent row under the
suggestions was removed with the strip's second row; the per-keystroke cursor read that fed it
is gone, and the code path that would insert a tapped variation (delete one character, commit
the variation; commit without delete for punctuation and brackets) has no button left to call
it.

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
| D15 | Android's autofill hands an input method its inline suggestions only while the input method's input view is started, and a keyboard that returns an inline suggestions request takes the place of the autofill drop-down. PhysiBoard has no input view (no soft keyboard), so without raising one it would get no suggestions and the user would lose the drop-down: the reason `fill_inline_suggestions` ships off. Not yet seen on the Titan. | AOSP `services/autofill/.../AutofillInlineSuggestionsRequestSession.java` (`maybeUpdateResponseToImeLocked` sends only when `mImeInputViewStarted`; `onInlineSuggestionsResponseLocked` returns true, keeping the drop-down away, once the keyboard sent a request) and `core/java/android/inputmethodservice/InlineSuggestionSessionController.java`, android14-release, read 2026-10-08 |
| D14 | The "Long Press Mappings" help text in the tutorial still lists the non-Elite Titan 2 legend (Q 0 ... O ', P :, A @, G *, H #, J +, K ", L ', Z !, B ., N ', M ?), which is wrong for the Elite. | `res/values/strings.xml` long press mapping lines |

## 12. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `current_sym_page` | int | 0 | page currently open (0, 1..5); internal | none | none |
| `sym_pages_config` | string (JSON, 4.1) | see 4.1; baseline in D12; 3.0 baseline 8: Emoji, Symbols, GIFs | which pages are in the cycle and in what order | Customize SYM Keyboard | Sym pages |
| `emoji_picker_kaomoji` | boolean | false | 3.0: kaomoji is a mode of the Emoji page and has a chooser row (expansion-clipboard-pickers-launcher.md 4.3) | Customize SYM Keyboard | Kaomoji on the Emoji page |
| `otp_from_notifications` | boolean | true | 3.0: one-time codes are read from notifications for the Fill page (4.7); inert until notification access is given; off clears every code | Customize SYM Keyboard | One-time codes from notifications |
| `fill_inline_suggestions` | boolean | false | 3.0, experimental: a password manager's inline suggestions on the Fill page (4.7) | Customize SYM Keyboard | Password manager suggestions (experimental) |
| `sym_mappings_custom` | string (JSON, 3.1) | unset | Emoji page characters | Customize SYM Keyboard, Edit Emoji Layer | (grid) |
| `sym_mappings_page2_custom` | string (JSON, 3.1) | unset | Symbols page characters | Customize SYM Keyboard, Edit Symbols Layer | (grid) |
| `sym_custom_pages` | string (JSON, 4.6) | three empty pages | 3.0: the user's own pages 7 to 9, their names and characters | Customize SYM Keyboard, Edit <name> | Page name, (grid), Clear page |
| `sym_auto_close` | boolean | true | page closes after a character, Alt or Enter | Customize SYM Keyboard | Auto-Close SYM Layout |
| `sym_auto_close_on_touch` | boolean | true | page closes after an on-screen key tap (needs the one above) | Customize SYM Keyboard | Also close after on-screen SYM keys |
| `sym_edit_shortcuts` | boolean | true | Sym+C/V/X/A copy, paste, cut, select all | Customize SYM Keyboard | Sym+C/V/X/A: copy, paste, cut, select all |
| `sym_double_tap_chooser` | boolean | true | 3.0: a Sym double tap opens the page chooser (5.10); off, two quick taps step two pages | Customize SYM Keyboard | Double-tap Sym for the page chooser |
| `emoji_picker_expanded_height` | boolean | true (baseline false) | page 4 height is 1.5 times compact | Customize SYM Keyboard | Larger emoji picker |
| `restore_sym_page` | int | 0 | page to reopen at next input start; internal | none | none |
| `pending_restore_sym_page` | int | 0 | page noted when leaving for customisation; internal | none | none |
| `sym_long_press_assistant` | boolean | false | holding Sym 600 ms opens the assistant listening | Voice settings | Hold Sym for the assistant |
| `alt_character_layer_binding` | string | `device:auto` | which map Alt types from (6.3) | none (orphaned) | Alt character layer |
| `alt_tap_latches` | boolean | false | a single Alt tap locks instead of one-shot | none found | Single-tap locks Alt |
| `clear_alt_on_space` | boolean | true | Space/Enter end Alt one-shot and lock | Text input | Release Alt with Space |
| `alt_latch_stays_on_space` | boolean | false | a locked Alt survives Space | none found | Keep locked Alt after Space |
| `long_press_modifier` | string | `alt` | long-press action (7.2) | 2.x: onboarding tutorial; 3.0: Keyboard > Long press | 2.x: Long Press Modifier; 3.0: Long press types |
| `long_press_threshold` | long ms | 2.x: 300 shown, 500 used when unset (7.1); 3.0: 500, clamped 50 to 1000 | hold time before the alternate | 2.x: Keyboard timing; 3.0: Keyboard > Long press | 2.x: Long Press; 3.0: Hold time |
| `long_press_variation_chooser` | boolean | true | 3.0: a long press in `variations` mode on a letter with several variations opens the accent chooser (8.4) | Keyboard > Long press | Show every accent |
| `custom_variations` | string (JSON, 8.3) | `{}` | 3.0: the user's own variation lists, per character | Keyboard > Long press > Customize Variations | (per-letter lists) |
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

3.0 keeps no variations file: `custom_variations` replaces `files/variations.json`, and
`variations_updated` and `global_variation_layout_override` are not read. Backups carry
`custom_variations` and `sym_custom_pages` like every other preference.

Files, not preferences (2.x): `files/variations.json`, `files/keyboard_layouts/*.json`,
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
| "Alt character layer" row on the SYM screen | removed (it opened a destination with no screen) | dead row, dropped 2026-10-08 |
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

Page chooser (3.0, section 5.10), default config (cycle 0, 1, 2), times in ms:

42. Sym down 0, up 60: page 1, no chooser.
43. Sym tap 0/60, then Sym down 300, up 350: chooser opens; page back to 0.
44. Page 2 open; Sym tap 0/50 (page 0); Sym down 120, up 170: chooser opens over page 2.
45. Sym tap 0/60, then Sym tap 361/400: page 2, no chooser (the gap is over 300).
46. State machine alone: taps at 0/40, 100/140 (chooser), 200/240: the third tap is a plain
    first tap and cycles to page 1. In the running keyboard the chooser is open by then, so
    that Sym tap closes it instead (5.10 key table).
47. Sym tap 0/40, A down at 80, Sym tap 120/160: page 2, no chooser. The same with Shift, Ctrl
    or Alt pressed at 80.
48. Sym tap 0/40; Sym down 100; a chord; Sym up 200: no chooser, page stays 1.
49. `sym_double_tap_chooser` off: taps 0/40 and 100/140 give page 2.
50. Assistant hold on: tap 0/40; Sym down 100, the assistant fires at 700; Sym up 800: no
    chooser.
51. Baseline config (picker, symbols, clipboard, emoji; GIF off): rows in order P, K, U, S, C,
    E, G; the first four in the cycle, the last three dimmed.
52. Chooser open: E, S, C, P, K, U, G each open their target; Shift is ignored; Back and Sym
    close it; an auto-repeat of E is consumed.
53. Chooser open: X, Space, Alt or Fn (whose first event on the Titan is already a repeat)
    close it and pass through.

GIF page (3.0, section 4.5):

54. Key `KEY123`, query " thumbs up & more ", country US: the search URL is
    `https://api.klipy.com/api/v1/KEY123/gifs/search?page=1&per_page=24&q=thumbs%20up%20%26%20more&locale=us&content_filter=medium&format_filter=gif%2Cwebp`;
    no `customer_id` ever; a country that is not two letters is left out.
55. A response with an item carrying xs.webp, xs.gif, sm.gif, md.gif, md.webp and hd.gif, an
    item with only sm.gif, and an item with only a jpg: two items, in order; the first previews
    xs.webp (URL kept with its query string) and sends md.gif; the second previews and sends
    sm.gif; numbers sent as strings still read.
56. `{"result":false,...}`, an HTML body, or `data` not an object: no result.
57. No key: the page says it is not set up; nothing is requested, in or out of private mode.
58. Private mode: nothing is requested; the gate's sentence is shown.
59. Empty query: trending; "Wow": search with `q=Wow`.
60. The gate refuses at request time: its reason is shown; a failure or unreadable body:
    "Couldn't reach KLIPY..."; an empty first page: "No GIFs found".
61. Content types `image/gif`, `IMAGE/*`, `*/*` take the GIF; none, or `image/png, video/*,
    text/plain`, get the link.
62. Recents: sending b over [a, b] gives [b, a]; sending c gives [c, a, b]; with learning off
    the list is unchanged; 24 at most. Favourites: a star adds first, a second star removes
    (even with learning off), a new star with learning off is refused. A stored list
    round-trips; a malformed value is empty; an entry without slug or with an `http://` URL is
    skipped; a duplicate slug keeps the first.

The user's own pages (3.0, section 4.6), default config unless stated:

63. The user's own pages are numbers 7, 8 and 9, key layers, off by default: the cycle is still
    0, 1, 2; with `custom2Enabled` true it is 0, 1, 2, 8.
64. `custom1Enabled` true, names {page 1: "Polski", page 3: "  "}: the chooser lists M
    "Polski" (in the cycle) and B "My page 3" (dimmed), no N.
65. Chooser listing Emoji and page 1 only: M opens page 7; N closes the chooser and types.
66. Emoji off, Symbols on, page 1 on, order [custom1, symbols], page 1 Q = "ą": Sym held + Q
    commits "ą".
67. Page 8 open, A = "你好": A commits "你好" and the page closes (`sym_auto_close`).

Variations (3.0, section 8):

68. Language `pl_PL`: a, c, e, l, n, o, s start with ą, ć, ę, ł, ń, ó, ś; z starts ź ż; A
    starts Ą; Z starts Ź Ż.
69. `fr_FR` e starts é; `de_DE` a ä, s ß; `es_ES` n ñ; `pt_PT` a ã; `cs` r ř; `ro` s ș;
    `tr` i ı and I İ; `nb_NO` a å, o ø. Every language's lists hold the same entries as the
    neutral lists.
70. `en_US`, `xx`, null and "" give the neutral order; a is à á â ä ã å ā ą ă æ.
71. No list is longer than ten, in any language; each capital list is as long as its small
    one; S is ẞ Ś Š Ş Ș $; E ends with €.
72. `pl_PL` → `pl`, `pt-BR` → `pt`, `nb` and `nn_NO` → `no`.
73. `pl_PL` with saved {a: [à, ą, 中], q: [¿]}: a gives à ą 中, q gives ¿, e still starts ę,
    A still starts Ą.
74. Saved {a: []}: a has no variations, e still does.
75. Cleaning [a, " ", "", a, 40 x's, 1..20]: a, then 16 x's, ten entries in all. Stored keys
    "ab" and "" are skipped.
76. One variation opens no chooser; two do.
77. Choices are labelled 1 2 3 4 5 6 7 8 9 0; digit 1 picks the first, 0 the tenth; device
    text "1" is digit 1, "@" none.
78. Chooser for A held with [ą, à, á]: W (digit 1) picks ą; after A comes up, W closes the
    bar and types; Alt held + E picks à; Alt pressed (consumed), then R picks á; S (digit 4)
    closes the bar (no fourth choice).
79. Chooser open: A's auto-repeat is consumed; a Shift repeat passes on with the bar open;
    Back closes it, consumed; Space and H close it and pass on.
80. A pick of ą → à: allowed when the text before the caret is "zażółć ą", refused for
    "zażółć a", allowed when the field cannot be read or reads back empty, allowed in a
    terminal.

The Fill page (3.0, section 4.7):

81. Pages Emoji, Symbols, GIFs on: with the Fill page having nothing, Sym steps Emoji, Symbols,
    GIFs, closed; with a code for another field, Emoji, Symbols, GIFs, Fill, closed; with a code
    in a code's field or a password manager's suggestions, Fill, Emoji, Symbols, GIFs, closed;
    with `fillEnabled` off, never Fill.
82. The extractor's table (its own test): "Your verification code is 482913" gives 482913;
    "G-602144 is your Google verification code." 602144; "Your code is 482 913" 482913;
    "Rs. 5,000 debited from a/c XX1234 on 12-05-24. OTP 778899 for txn. Call 1800-123-4567"
    778899; "【Acme】您的验证码是482913，5分钟内有效。" 482913; "Ваш код: ٤٨٢٩١٣" 482913;
    "Your 2FA code is F7K2QX" F7K2QX. Nothing from "Use promo code SAVE2025 for 20% off",
    "Your order 123456 has shipped", "Meeting moved to 3:30, room 4412", "Call me at
    555-123-4567", "You paid $1,250.00", "Confirm your payment of 2500 to Acme", "New login from
    192.168.10.20 on 2025-05-06", "Code review: 3 comments on PR 4821".
83. Codes: at most 3, newest first; one is gone 10 minutes after it was posted; the same code
    from the same app is listed once; a typed code is not taken again from its notification
    until it would have expired; "Code from Messages · 2 min ago".
84. Fill page open with codes 482913 and 551204: W types 482913 and the page closes
    (`sym_auto_close`); E types 551204; R (no third code) types as on any panel page.
85. A Terminal mode field: the code reaches the app as key presses.
86. Private mode on, or `otp_from_notifications` off: no notification is read and every code
    held is dropped. Screen off: every code is dropped.
87. `fill_inline_suggestions` off: the keyboard answers Android's inline request with nothing.

## 15. Keep / Drop for 3.0

| Item | Verdict | Reasoning |
|---|---|---|
| Sym tap toggles on release; chords never open a page | keep | the core of the Titan Sym key |
| Emoji and Symbols key layers with shipped maps | keep | the two pages people use |
| Device page (5) | drop | duplicates Alt, off by default, marked under construction |
| Clipboard and emoji picker as Sym pages | keep | overlays are in the 3.0 scope; cycling into them is how the Titan reaches them |
| Page order and enable switches, `sym_pages_config` shape | keep | user content that survives migration; keep the JSON contract |
| Legacy `emojiFirst` field | drop | 3.0 writes and reads `symPageOrder` only |
| The Fill page (one-time codes from notifications, a password manager's suggestions) | new in 3.0 | the bar other keyboards show them in is gone for good; Sym reaches them instead. Inline suggestions ship off until proven on the Titan (D15) |
| GIF page (6), KLIPY, off by default (4.5) | new in 3.0 | maintainer's request; the only page that goes online, so it stays off until switched on and passes the network gate |
| Page chooser on a Sym double tap (5.10) | new in 3.0 | the status bar's direct-open buttons are gone for good; the chooser is the transient replacement |
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
| Long-press modes `sym`, `sym_symbols`, `sym_emoji` | keep | a user asked to choose between capital, accent and symbol (2026-10); cheap with the pages kept |
| Long-press threshold with one default | keep, done | 500 ms for the screen and the timer (7.1) |
| A screen for the long-press mode and threshold | new in 3.0 | Keyboard > Long press (7.2) |
| Anchored composing-region replacement for variations | drop, keep the check | delete-and-commit like every other mode, guarded by "the text still ends with the letter" (7.4); composed text is dropped by a web terminal |
| Variations data and layout overrides | replace | one built-in table ordered by language, not layout (8.1, 8.2) |
| `files/variations.json` | replace | `custom_variations`, edited on Customize Variations (8.3) |
| Accent chooser (8.4) | new in 3.0 | the variation row lived in the suggestion bar, which is gone for good; a transient bar that picks only while the letter is held, or after Alt |
| The user's own Sym pages 7 to 9 (4.6) | new in 3.0 | more characters at hand without giving up Emoji or Symbols |
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
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/autofill/java/com/android/server/autofill/AutofillInlineSuggestionsRequestSession.java (3.0, section 4.7, D15)
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/inputmethodservice/InlineSuggestionSessionController.java (3.0, section 4.7, D15)
