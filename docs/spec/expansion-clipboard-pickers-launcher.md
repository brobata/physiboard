# Expansion, clipboard, pickers, launcher: everything that inserts or launches something other than the typed character

This document describes the parts of PhysiBoard that put something other than the pressed key's
character into the text, or that leave the text field entirely to launch something: text
expansion (snippets), clipboard history, the searchable emoji picker, the two pickers used in
settings to choose an emoji or a Unicode character for a Sym key, the launcher shortcuts that
turn keys into app launchers, the quick launcher, the command catalog those two share, and the
typing sounds and tap vibration that accompany key presses.

Companion documents: `layers-sym-alt.md` owns the Sym pages themselves (which page number is
which, the cycle, direct opens, auto-close, the customisation screen); `status-bar.md` owns the
strip that hosts the clipboard and emoji picker panels and the clipboard and emoji buttons on
it, including the count badge and its flash; `per-app-behavior.md` owns exact typing and the
Sym+Enter extra send; `dictation.md` owns the voice assistant triggers, including the "Voice
assistant" command; `trackpad-caret-nav.md` owns nav mode, where commands can also be bound.
Those are referenced here, not repeated.

Everything below is the 2.x behavior on the Titan 2 Elite unless a row says otherwise.

## 1. Vocabulary

- **Snippet**: a user-defined shortcut (a short word) and the text it expands to.
- **Prefix**: the single symbol typed before a snippet shortcut to announce it, `!` by default.
- **Token**: the prefix plus whatever has been typed after it, up to the caret.
- **Match**: a snippet whose shortcut starts with the typed shortcut. **Exact match**: the one
  match whose shortcut equals the typed shortcut (case-insensitive); there is no exact match
  when two sources would give the same shortcut, which cannot happen with snippets alone.
- **Clip**: one entry of the clipboard history, a piece of text with a timestamp and a pinned
  flag.
- **Panel**: the clipboard history (Sym page 3) and the emoji picker (Sym page 4), the two Sym
  pages that show a scrolling surface instead of a key grid.
- **Assigned key**: one of the 29 keys (26 letters, Backspace, Space, Enter) that can carry a
  launcher shortcut.
- **Command**: anything an assigned key, the quick launcher, or a nav mode key can run: an app,
  an intent into another app, a PhysiBoard action, a device control, or a navigation action.
- **Power shortcut**: an assigned key fired by first pressing Sym (or holding it).
- **Quick launcher**: PhysiBoard's own search-and-launch sheet, opened by an assigned key
  (Sym+Space out of the box).

## 2. Text expansion (snippets)

### 2.1 What a snippet is and how it is stored

A snippet maps a shortcut to a replacement text. Both live in one preference, `snippets_v1`,
which holds a JSON object whose keys are shortcuts and whose values are replacement strings.
Example: `{"sig":"Regards,\nJeremiah","addr":"1516 E Flying Owl Dr"}`.

Rules enforced on every save and every load:

| Rule | Value |
|---|---|
| Shortcut characters | ASCII letters, digits, underscore only |
| Shortcut length | 1 to 40 characters |
| Shortcut case | stored and compared lowercase; the editor lowercases and trims on save |
| Replacement | must not be blank; otherwise kept exactly, including leading and trailing spaces and newlines |
| Entries that fail the rules on load | dropped silently |
| Unparseable preference | treated as no snippets; an error is logged and nothing is rewritten |
| Duplicate shortcut on save | the later value wins (the editor replaces in place; renaming a shortcut removes the old key) |

The prefix is one character in `snippets_prefix` (default `!`). A valid prefix is exactly one
character that is not whitespace, not a letter or digit, and not a colon. A stored value that
fails this is ignored and `!` is used. The colon is reserved because the emoji and symbol
`:shortcode:` triggers used to share this machinery; those were removed in 2.0 (changelog:
"Emoji and symbol shortcodes are gone. Snippets stay, and moved to Extras"). The trigger
detector still knows the colon form (`:id` open, `:id:` closed, never starting inside a URL or a
time such as `12:30`), but no source is connected to it, so typing `:smile:` does nothing.

Backups carry all eight `snippets_*` keys (section 2.9).

### 2.2 Detecting a trigger

After every key the keyboard looks at the last 256 characters before the caret and searches for
the last occurrence of the prefix. A trigger exists when:

1. the prefix is at the very start of that text, or the character before it is whitespace or
   one of `(`, `[`, `{`, `"`, `'` (so `mail!sig` is not a trigger, `Hello !sig` is);
2. everything after the prefix up to the caret is either empty or a valid shortcut (letters,
   digits, underscore, at most 40). A space or any other character after the prefix ends the
   candidacy: `!sig ` is no longer a trigger.

The typed shortcut is lowercased for matching. With only the prefix typed (`!`), the typed
shortcut is empty and every snippet matches, which is how the whole list can be browsed.

### 2.3 Matching

All snippets whose shortcut starts with the typed shortcut are collected, sorted by shortcut
length then alphabetically, and cut to 10. Each match is displayed as
`shortcut → first line of replacement` (an arrow with spaces around it; only the first line of a
multi-line replacement is shown). The exact match, when there is one, is the match whose
shortcut equals the typed shortcut.

The highlighted match starts at the first entry and moves with Up and Down (section 2.5). The
highlight resets to the first entry whenever the typed shortcut changes.

### 2.4 When the lookup runs

- Scheduled, coalesced to one run 24 ms after the last request: after every hardware key
  release that is not a pure modifier; after every text committed from the on-screen keyboard;
  after every selection change that leaves a collapsed caret.
- Immediately, before the key is acted on, when Space, Tab, Enter or the d-pad center key goes
  down, so a fast typist who presses Space before the 24 ms run still gets the expansion.
- Never when: snippets are disabled; the field is not really editable; the field is restricted
  (password, URI, e-mail, filter fields, and any app on the exact-typing list, see
  per-app-behavior.md); there is a non-collapsed selection; or there is no connection to the
  editor. In those cases any open matches are cleared.

Matches are cleared, and the popup or bar restored, whenever the editor changes: on every start
of input and input view, on finishing either, on a configuration change, on the keyboard's
surface transition (window hidden or re-shown), and when the selection stops being collapsed.
A committed expansion also clears them.

### 2.5 Presentation

`snippets_presentation` chooses how matches are shown. Any unknown stored value falls back to
the floating popup.

**Off** (`off`): nothing is drawn. Matches still exist invisibly: Space on an exact match, and
Tab or Enter on an exact match (when those are enabled) still expand. Up/Down and Escape do
nothing because nothing is visible.

**Floating popup** (`floating_popup`, default): a dark rounded panel (background rgb 35,35,38,
12 dp corners, 8 dp elevation) 300 dp wide, positioned at the top center of the keyboard's
window and offset upward by its own height, so it sits just above the keyboard chrome. It lists
up to 10 rows of 44 dp each, white 14 sp text, single line with an ellipsis, 14 dp horizontal
and 10 dp vertical padding, and shows at most 5 rows at a time (the rest scroll, scrollbar always
visible). The highlighted row has background rgb 65,83,125. A row tap commits that match with no
trailing space. The popup does not take focus and is not dismissed by outside touches; it
disappears when matches clear. Up and Down move the highlight and scroll it into view.

**Suggestion bar** (`suggestion_bar`): the first three matches replace the three suggestion
slots of the strip (status-bar.md, section 5), forcing the suggestion row to show even where
suggestions are otherwise off or the app is on a no-suggestions list; the add-word candidate is
hidden while they are up. Tapping a slot commits that match with no trailing space. Up/Down move
the highlight within the three visible entries only. When the bar is cleared the slots go back
to ordinary suggestions on the next refresh.

Escape clears the matches (consumed) when any are visible.

### 2.6 Accepting a match

Keys are checked only when a text field is active and no modifier is active in any form (Ctrl,
Alt, Shift or Meta held, latched, one-shot, or reported by the event). Fn arrives as Ctrl on the
Titan, so Fn plus a key never expands.

| Key | Setting | Behaviour |
|---|---|---|
| Space | `snippets_exact_on_space` (default on) | If there is an exact match: replace the token with the replacement followed by one space; consumed. |
| Space | `snippets_accept_prefix_with_space` (default off) | If there is no exact match, matches are visible and this is on: replace the token with the highlighted match's replacement followed by one space; consumed. |
| Space | neither applies | Not consumed; the space goes on to normal typing (autospace, double-space period, the suggestion engine's boundary handling). |
| Tab | `snippets_accept_with_tab` (default on) | Commit the highlighted match if matches are visible, else the exact match if any, with no trailing space; consumed. Otherwise not consumed. |
| Enter, d-pad center | `snippets_accept_with_enter` (default off) | Same rule as Tab. When off, Enter is never touched by expansion. |
| Up, Down | | Move the highlight when matches are visible; consumed. |
| Escape | | Clear when matches are visible; consumed. |

Commit mechanics: the text before the caret is re-read and must still end with the token
(otherwise the matches are just cleared and nothing is typed); then, in one batch edit, any
composition is finished, the token's characters are deleted backwards, and the replacement plus
suffix is committed as finished text. Afterwards the suggestion engine's context is reset and
re-read from the editor, the strip is refreshed, and the next selection update is skipped as a
self-inflicted one.

Consequences for the rest of typing:

- The Space that expands never reaches autocorrect or autospace, so the shortcut is never
  "corrected" and no double-space-to-period or auto-space replacement runs on it.
- The replacement is committed, not composed, so it is never a candidate for autocorrect or
  suggestions; the engine starts fresh after it.
- On the exact-typing list the whole feature is off (section 2.4), so terminals and SSH clients
  never see an expansion.
- Auto-capitalisation is not applied to the replacement; it is inserted verbatim.

### 2.7 The settings screen

Settings > Extras > "Text expansion" ("Type a short trigger and have it expand into whatever
you saved."). The screen is titled "Text expansion" with a "Snippets" section:

1. "Enable snippets" switch ("Expand global text snippets after a shared prefix.").
2. "Snippet prefix" text field ("One printable symbol. A colon is reserved for emoji and symbol
   shortcodes."): only the last typed character is kept; an invalid one shows the error "Choose
   one non-whitespace symbol other than a colon." and is not saved; a valid one is saved as you
   type.
3. "Show matches in" dropdown: Off, Floating popup, Suggestion bar.
4. "Accept with Tab" switch ("Use Tab to accept the highlighted or unique exact match.").
5. "Accept with Enter" switch (same wording with Enter).
6. "Manage snippets" row ("Add global shortcuts and multiline replacement text.") opening the
   list.
7. "Expand exact match with Space" switch ("Press Space to expand a unique exact shortcut and
   keep the trailing space. Prefix matches are ignored.").
8. "Accept prefix match with Space" switch ("Press Space to accept the highlighted match when
   the typed shortcut is not an exact match.").

The "Manage snippets" list shows each shortcut in medium weight with its replacement below
(newlines rendered as ` ↵ `, at most two lines) and a delete icon; tapping a row edits it; a plus
icon adds one; empty state "No snippets yet.". The editor dialog ("Add snippet" / "Edit snippet")
has a single-line "Shortcut" field with the help "Use 1–40 letters, numbers, or underscores."
(error state when non-empty and invalid) and a "Replacement text" field of 4 to 10 lines. "Save"
is enabled only when the shortcut is valid and the replacement is not blank; Cancel discards.

### 2.8 Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `snippets_enabled` | boolean | false | Whether any snippet lookup runs | Text expansion | Enable snippets |
| `snippets_prefix` | string (1 char) | `!` | The trigger prefix | Text expansion | Snippet prefix |
| `snippets_v1` | string (JSON object) | absent (no snippets) | The shortcut to replacement map | Manage snippets | (list) |
| `snippets_presentation` | string: `off`, `floating_popup`, `suggestion_bar` | `floating_popup` | Where matches are drawn | Text expansion | Show matches in |
| `snippets_exact_on_space` | boolean | true | Space expands a unique exact match | Text expansion | Expand exact match with Space |
| `snippets_accept_prefix_with_space` | boolean | false | Space accepts the highlighted non-exact match | Text expansion | Accept prefix match with Space |
| `snippets_accept_with_tab` | boolean | true | Tab accepts | Text expansion | Accept with Tab |
| `snippets_accept_with_enter` | boolean | false | Enter accepts | Text expansion | Accept with Enter |

## 3. Clipboard history

### 3.1 Capture

From the moment the keyboard service is created, PhysiBoard listens to the system clipboard.
Each time the primary clip changes (and once at service creation, for whatever is already on
the clipboard), the first item of the clip is taken if the clip declares a `text/*` type (a clip
with no declared type is also accepted), coerced to text, and stored unless the result is empty.
The stored timestamp is the wall-clock time of capture, not the clip's own timestamp.

There is no sensitive-content rule: a clip flagged sensitive by its source (a password manager's
copy) is stored like any other and shown in full on the panel. This is a known gap.

Duplicates: a clip whose text equals an existing entry's text (exact, case-sensitive) is not
added again; the existing entry's timestamp is refreshed so it moves to the top of its group.

Capture only happens when `clipboard_history_enabled` was true at service creation; the flag is
read once, so changing it takes effect after the keyboard service restarts. Android itself only
reports clipboard changes to the current input method while it is the default keyboard and, on
Android 10 and later, generally only while it has focus; what a background copy in another app
yields is therefore what the system chooses to deliver.

### 3.2 Storage

Entries live in the SQLite database file `pastiera_clipboard.db` (schema version 1), table
`CLIPBOARD` with columns `ID` (integer primary key), `TIMESTAMP` (integer, milliseconds),
`PINNED` (0 or 1), `TEXT`. An in-memory copy is the working set: every read is served from it
and every write is applied to it first and to the database on a single background thread, so
the keyboard's input thread never waits for disk. On startup the table is loaded in the
background; a clip copied before the load finished is already in memory, so a stored row with
the same text is deleted as superseded rather than loaded twice.

If the database cannot be opened (for example before the user unlocks the device after boot),
the history simply reports zero entries and the open is retried on the next use.

Ordering everywhere: pinned entries first, then most recent first within each group.

### 3.3 Retention and cleanup

`clipboard_retention_time` is a number of minutes, default 5. Non-pinned entries older than
that are removed; pinned entries never expire. A value of 0 or less means never delete.

Cleanup runs: on every capture; on every refresh of the panel; and every 60 s while an input
view is active (the timer starts when the keyboard's input view starts and stops when it
finishes or the service is destroyed). The 60 s timer also pushes the current count to the strip
badge. Cleanup requests that are not forced are ignored if one ran less than 5 s ago; the ones
listed here are all forced.

There is no user interface for either the enable flag or the retention time. The strings for a
"Clipboard Retention Time" row ("0 = never delete clips", "%d minutes", "Apply") exist but no
screen uses them. Both keys travel in backups.

### 3.4 The count and the strip

The number of entries (pinned and not) is pushed to the strip whenever an entry is inserted,
removed or moved, when the initial load completes, and every 60 s. The badge and its flash are
in status-bar.md, section 6.1.

### 3.5 The panel (Sym page 3)

Opened by the strip's clipboard button (toggle), by the Sym cycle when `clipboardEnabled` is on
in the pages configuration, or from the quick-actions overlay; closed by its own close button,
Back, or the strip. All of that is in layers-sym-alt.md, sections 4.2 to 4.3.

Geometry, hardware mode: the panel is 177 dp tall (331 px on the Titan), edge to edge (the Sym
container's side padding removed), with a header row at the top and a scrolling grid below it
that starts under the measured header height. Header: 8 dp side padding, 8 dp top, 4 dp bottom;
title "Clipboard History" at 12 sp in the text color at alpha 180/255, and on the right "Clear
All" at 12 sp in the accent color (fallback #FF6B6B), disabled when the list is empty. Grid: 3
columns, each card 64 dp tall with 12 dp padding, 4 dp gaps between cards (none at the outer
edges), 8 dp padding around the grid and an extra 128 dp (two card heights) of bottom padding so
the last row can always be scrolled clear of the close button. Card text 14 sp, at most 2 lines,
ellipsised, in the theme's text color (white without a theme). Card background: the theme's
suggestion color for ordinary clips; the accent at alpha 95/255 for pinned clips (without a
theme: white at alpha 40 and rgb 7,7,212 at alpha 60 respectively), 1 dp divider-colored border
when themed, 6 dp corners. Empty state: "No clipboard history" at 14 sp centered, text color at
alpha 128. A close button (36 by 32 dp, 4 dp padding, 6 dp corners, the status bar button color,
fallback red 220,38,38 at alpha 95) sits at the bottom end corner over the grid. In the software
keyboard mode the panel is given the software keyboard's height instead of 177 dp.

Interactions:

| Action | Behaviour |
|---|---|
| Tap a card | The clip's text is committed into the app at the caret as finished text (no composing, no auto-space, no autocorrect). The panel stays open; `sym_auto_close` does not apply to it. |
| Long-press a card | A context menu with "Pin" (or "Unpin" when pinned) and "Delete". |
| Pin / Unpin | Toggles the flag and refreshes the timestamp, so the entry jumps to the top of its new group; the list then scrolls to the top. Pinned entries also survive "Clear All" and retention. |
| Delete | Removes the entry even if pinned (it is unpinned first, then removed). |
| Clear All | Removes every non-pinned entry and clears the system clipboard (Android 9 and later). Pinned entries stay. |
| Close button | Asks the Sym session to close the page. |

The list is re-read only when the entry count differs from the last rendered count, so a pin
that does not change the count is refreshed by the menu action itself, and a copy while the panel
is open appears at the next strip refresh that sees the new count. On refresh the scroll position
is preserved unless a pin requested a scroll to top. The visible cards use immutable snapshots so
a change of pinned state or timestamp is detected and redrawn.

An older floating "clipboard history popup" with title, entries, pin and delete buttons (the
comment calls it the Shift+Ctrl+V popup) is still compiled in but nothing opens it; it has no
trigger.

### 3.6 Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `clipboard_history_enabled` | boolean | true | Whether clips are captured at all (read once at service start) | none (no UI) | (strings only) |
| `clipboard_retention_time` | long, minutes | 5 | Age after which non-pinned clips are removed; 0 or less = never | none (no UI) | Clipboard Retention Time |

Related but owned elsewhere: the strip slot preference that places the clipboard button
(status-bar.md 6.3) and `sym_pages_config` `clipboardEnabled` (layers-sym-alt.md 4.1).

## 4. The emoji picker (Sym page 4)

### 4.1 Emoji data

Emoji come from nine text files under `assets/common/emoji/`, one per category, listed here in
display order with their ids, the tab icon and the shipped line counts:

| File | Category id | Label | Tab icon | Lines |
|---|---|---|---|---|
| `SMILEYS_AND_EMOTION.txt` | `SMILEYS_AND_EMOTION` | Smileys & Emotion | satisfied face | 171 |
| `PEOPLE_AND_BODY.txt` | `PEOPLE_AND_BODY` | People & Body | person | 400 |
| `ANIMALS_AND_NATURE.txt` | `ANIMALS_AND_NATURE` | Animals & Nature | paw | 160 |
| `FOOD_AND_DRINK.txt` | `FOOD_AND_DRINK` | Food & Drink | fork and knife | 131 |
| `TRAVEL_AND_PLACES.txt` | `TRAVEL_AND_PLACES` | Travel & Places | airplane | 219 |
| `ACTIVITIES.txt` | `ACTIVITIES` | Activities | football | 85 |
| `OBJECTS.txt` | `OBJECTS` | Objects | light bulb | 266 |
| `SYMBOLS.txt` | `SYMBOLS` | Symbols | symbols glyph | 224 |
| `FLAGS.txt` | `FLAGS` | Flags | flag | 270 |

A recents category (`RECENTS`, label "Recents", clock icon) is prepended when the recents list
is not empty. Any other `.txt` in the directory would be appended after Flags with its file name
as label; the id "Emoticons" has a label string but no file.

File format: one entry per line; tokens separated by single spaces; the first token is the base
emoji, the remaining tokens are its variants (for `👋` the five skin-tone forms). `minApi.txt`
has one line per Android API level: the level followed by the emoji that the platform font
first shipped at that level (12 lines).

An emoji is available on the device when either its listed minimum API is known, positive and
at or below the running API level, or the system font reports a glyph for it. Unlisted emoji
(Unicode Emoji 17.0, which no API level maps to yet) are therefore shown only when the font has
them. Bases and variants are filtered independently; an unavailable base drops its whole line; a
category that ends up empty is dropped. The parsed categories are cached for the life of the
process.

The assets are generated by a pinned script from Unicode Emoji 17.0 `emoji-test.txt` and the
CLDR 48.2.1 JSON annotations; the API map is E4.0 24, E5.0 26, E11.0 28, E12.0 and E12.1 29,
E13.0 30, E13.1 31, E14.0 32, E15.0 33, E15.1 34, E16.0 35. Checking the assets against the
pinned sources is a repository chore, not a runtime behavior.

### 4.2 Search data and the language chain

Search terms come from `assets/common/emoji_search/<locale>.tsv` for nine locales: `de`, `en`,
`es`, `fr`, `hy`, `it`, `pl`, `ru`, `uk` (1249 lines each). A line is
`emoji<TAB>name<TAB>keyword|keyword|...`; blank lines are skipped, a missing name is allowed.

The locale chain is built from the system's ordered locale list: for each locale, its full tag
then its bare language (`de-CH` gives `de-CH` then `de`), then `en`, duplicates removed. The
first entry is the preferred locale. For each chain entry the file tried is the tag lowercased
with `-` replaced by `_`, then the bare language; the first file that exists and parses to at
least one line is used, and a chain entry with no file is skipped. So on an English Titan the
chain is `en-US`, `en`, and only `en.tsv` loads; on a German one `de` terms are preferred and
`en` terms still match.

Index: for every available emoji (base and its variants share one entry), the names and
keywords from every loaded locale are normalised (lowercase, Unicode decomposition with combining
marks removed, only letters and digits kept, runs of whitespace, `-` and `_` collapsed to one
space, trimmed) and deduplicated; each term remembers whether it is a name or a keyword and
whether it came from the preferred locale. An emoji with no metadata in any loaded file is
indexed by its own literal string. Up to 3 indexes (keyed by the chain) are cached; the oldest
is evicted.

Scoring a query (trimmed, then normalised the same way; an empty result gives no results):

| Condition | Score |
|---|---|
| The raw query equals the base emoji | 2000 |
| The raw query equals one of the variants | 1900 |
| A name term equals the query | 1700 (+25 if from the preferred locale) |
| A keyword term equals the query | 1600 (+25) |
| A name term starts with the query | 1400 (+25) |
| A keyword term starts with the query | 1300 (+25) |
| A name term contains the query (only when the query is 2+ characters) | 1000 (+25) |
| A keyword term contains the query (2+ characters) | 900 (+25) |

The best term wins; the category's position (0 for Smileys, 8 for Flags) is subtracted as a
tie-breaker. Results are sorted by score descending, then category order, then the emoji
string, and cut to 200.

### 4.3 Layout

The picker is a vertical stack: the scrolling grid on top and a 32 dp tab row at the bottom,
edge to edge. Height in hardware mode: 177 dp, or 1.5 times that when
`emoji_picker_expanded_height` is on (default on), which is 265 dp (496 px) on the Titan; the
other Sym pages keep their own height. In software keyboard mode the picker takes the software
keyboard's height.

Grid: the column count is the largest number of 48 dp cells with 4 dp gaps that fit in the
screen width minus 16 dp, clamped to 4..10; on the Titan's 1080 px width that is 10 columns
(D1). Each emoji is drawn as text at 28.8 sp, centered in a cell at least 48 dp square, 4 dp gaps
(none at the outer edges), 8 dp padding around the grid plus 44 dp extra at the bottom. Category
headers are 1 dp spacers, not titles; the tab row is the only category indication. Tabs get equal
width, 4 dp padding, the theme's text color; the selected tab is the accent at alpha 100 with a 1
dp divider border (white at alpha 100 without a theme), 6 dp corners.

Tab row, left to right: a search toggle button (magnifier, 32 dp square), a keyboard-switcher
button that is visible only in software keyboard mode, the category tabs (one per category
present, recents first), and a close button (36 by 32 dp, same style as the clipboard panel's).

While loading, a centered progress indicator is shown; a failure to load anything shows "Unable
to load emoji". Loading happens the first time the page opens, on every open when it was on
another page last, and on a settings-triggered refresh; a reopen straight after the same page
merely scrolls to the top. The selected tab follows the first visible item while the user
scrolls (not while a tab-tap scroll is in flight); tapping a tab jumps to that category's first
row. Tapping the Recents tab also asks for a recents refresh.

### 4.4 Choosing an emoji

Tap: the emoji is committed into the app as finished text. If both `sym_auto_close` and
`sym_auto_close_on_touch` are on (both default on) the page is asked to close first and the
commit is posted right after, so the strip's redraw and the insert do not fight; otherwise the
commit is immediate and the page stays open.

Long press on an emoji that has variants: a light popup (the theme's key popup color, fallback
white at alpha 0xEE, 12 dp elevation) above the cell, centered on it and clamped to the screen,
listing the base then each variant at 24 sp with 12 dp by 8 dp padding; tapping one commits it
the same way and dismisses the popup. Outside touches dismiss it. This is the skin-tone chooser:
the tone comes from the asset line, the chosen tone is inserted as is, and no tone preference is
remembered per emoji. An emoji without variants ignores the long press.

Recents: every chosen emoji (base or variant, as inserted) goes to the front of a list of at
most 40 kept in the separate preferences file `recent_emojis_prefs` under the key
`recent_emojis` as a JSON array of strings, most recent first; choosing one that is already
first changes nothing; choosing one further down moves it to the front and drops nothing; a new
one pushes the 41st off the end. The Recents section is rebuilt from that list with each entry's
variants looked up from the categories. The redraw is deferred: it is applied only when the grid
is idle, and, when the choice was made from the Recents section itself, only once the user has
moved to another tab, so the row being tapped does not shuffle under the finger; when made
from another section it waits until the grid is near the top. Recents appearing for the first
time (or disappearing entirely) inserts or removes the section and its tab while keeping the
current scroll anchor.

### 4.5 Search and the search input

The search toggle shows or hides a search panel over the bottom of the grid: a single-line
field ("Search emoji..." hint, 14 sp, 8 dp by 5 dp padding, 7 dp corners, suggestion color
background) inside a 6 dp padded panel. Showing the panel focuses the field and turns capture
on; hiding it turns capture off. The field never asks for a system on-screen keyboard.

Capture means hardware keys are typed into the search field instead of the app while page 4
is open, capture is on, and the key is not Back, not Sym, and not a pure modifier:

| Key | Behaviour while capture is on |
|---|---|
| A printable key | The character the active layout produces for it (Shift-aware, so QWERTZ and accents are respected) is inserted; if the layout has nothing, the key's own Unicode character; control characters are handed to the field as a key event instead. Inserted text replaces a selection (or a pending select-all range) when there is one, otherwise appends at the end. |
| Space | Inserts a space. |
| Backspace | Deletes the selection (or the pending select-all range) if any, else the last character; consumed even when empty. |
| Enter | Consumed, does nothing (there is no "insert first result"). |
| Up, Page Up / Down, Page Down / Left / Right | When delivered through the field's own connection: caret to start / end / one left / one right. |
| Ctrl+A / C / X / V | Select all (remembered as a pending replacement range) / copy / cut / paste inside the field. On the Titan the Fn key arrives as Ctrl, so Fn+A selects the search text. |
| Other Ctrl combinations | Handed to the field as shortcuts. |
| Alt or Meta combinations | Not captured; they reach the app. |

The matching key releases are consumed too so the app underneath never sees half a key.

Capture is dropped, and typing returns to the app, when: the user taps the search field while
capture is on (tapping toggles it; the field dims to alpha 0.75 when off and its caret hides);
the app's own selection is seen to change between two captured keys (the app's caret moved, so
the user is working there); a caret-position update arrives from the app after the first one
following the capture start; or the input session ends. While capture is on, the app's caret
position is monitored (the single caret-monitoring switch is shared with the caret badge and is
reconciled so neither feature turns it off under the other).

Each edit of the field schedules a search 120 ms later. A non-empty trimmed query switches the
grid to a flat result list in score order, dims the tab row to alpha 0.55 and disables tab taps;
no results shows "No emoji found"; an empty query restores the sections. Results support the
same tap and long press as the sections. A search that cannot run because the index failed to
build shows "Unable to load emoji".

### 4.6 Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `emoji_picker_expanded_height` | boolean | true | Picker height 265 dp instead of 177 dp | Customize SYM Keyboard | Larger emoji picker ("Use about 1.5x height for the emoji search page; other SYM pages keep their normal height.") |
| `recent_emojis` (file `recent_emojis_prefs`) | string, JSON array | absent | The recents list, most recent first, at most 40 | none | (Recents tab) |

`sym_auto_close`, `sym_auto_close_on_touch` and `sym_pages_config` `emojiPickerEnabled` are in
layers-sym-alt.md. The recents file is not part of backups.

## 5. The pickers inside settings

Both are dialogs opened from the "Customize SYM Keyboard" screen when the user taps a letter of
an editable page (layers-sym-alt.md 4.4): the emoji dialog for the Emoji page, the Unicode
dialog for the Symbols page. What they return is written into that page's map by the caller.

### 5.1 The emoji dialog

Title "Select Emoji", or "Select emoji for letter X" when a letter is known, with a "Close"
button; a "Search" field ("Search emoji..." placeholder); when not searching, a horizontally
scrolling row of category chips (the nine categories, no recents); a grid of 48 dp cells with 2
dp spacing and 4 dp padding, as many columns as fit in the screen width (minimum 4; 11 on the
Titan); the current category's entries, or the search results (same index and ranking as
section 4.2, all of them, no 200 cut beyond the search's own). Tapping an emoji returns its base;
long press on one with variants opens a horizontally scrollable popup (base then variants at 24
sp; light in day theme, dark in night theme) and tapping there returns that variant. Loading and
"Unable to load emoji" states as in the panel; "No emoji found" for an empty search.

### 5.2 The Unicode character dialog

Title "Select unicode character" or "Select character for X", a "Close" button, then a row with
a "Custom character" text field and an "Add" button (returns whatever was typed, if not blank),
a full-width "Reset to Default" button (returns the empty string, which the caller treats as
"use the shipped character"), a row of seven category chips and a grid of the selected category
(48 dp cells, 28.8 sp glyphs, 2 dp spacing, minimum 4 columns, 11 on the Titan). Tapping a glyph
returns it. The categories, in chip order, and their contents:

| Chip | Contents (in order) |
|---|---|
| Punctuation | `„ “ ” ‘ ’ " ¿ ¡ … — – « » ‹ › ‚ ' ' • ‥ ‰ ′ ″ ‴ ‵ ‶ ‷ ‸ ※ § ¶ † ‡ ; : ! ? . , ‽ ⁇ ⁈ ⁉ ( ) [ ] { } < >` |
| Mathematical Symbols | `± × ÷ ≠ ≤ ≥ ≈ ∞ ∑ ∏ √ ∫ ∆ ∇ ∂ α β γ δ ε π Ω θ λ μ σ φ ω ½ ¼ ¾ ⅓ ⅔ ⅕ ⅖ ⅗ ⅘ ⅙ ⅚ ⅛ ⅜ ⅝ ⅞ ∝ ∠ ∡ ∢ ∟ ∴ ∵ ∶ ∷ ∼ ∽ ≀ ≁ ≂ ≃ ≄ ≅ ≆ ≇ ≉ ≊ ≋ ≌ ≍` |
| Currencies | `€ £ ¥ $ ¢ ₹ ₽ ₩ ₪ ₫ ₦ ₨ ₩ ₪ ₫ ₦ ₨ ₩ ₪ ₫ ₭ ₮ ₯ ₰ ₱ ₲ ₳ ₴ ₵ ₶ ֏` (the repeats are as shipped) |
| Technical Symbols | `° ~ \` { } [ ] < > ^ % = \ \| & @ # * + - _ © ® ™ ℠ ℡ ℣ ℤ ℥ Ω ℧ ℨ ℩ K Å ℬ ℭ ℮ ℯ ℰ` |
| Arrows | `← → ↑ ↓ ↔ ↕ ↗ ↘ ↙ ↖ ⇐ ⇒ ⇑ ⇓ ⇔ ⇕ ⇗ ⇘ ⇙ ⇖ ⇠ ⇡ ⇢ ⇣ ⇤ ⇥ ⇦ ⇧ ⇨ ⇩ ⇪ ⇫ ⇬ ⇭ ⇮ ⇯ ⇰ ⇱ ⇲ ⇳` |
| Variations | Accented and modified Latin letters, grouped A to Z, uppercase before lowercase, in the diacritic order grave, acute, circumflex, tilde, diaeresis, ring, macron, breve, ogonek, dot, caron, stroke, hook below, plus the ligatures and IPA forms of each letter (about 560 glyphs); the source lists an empty string between letter groups as a separator, but the dialog filters empty strings out, so no visible gap appears |
| Miscellaneous | Set-theory and relation operators U+2205 to U+22FF (∅ ∈ ∉ ... ⋿, the ⊂ to ⋿ block complete), then the Letterlike Symbols block U+2100 to U+214F (℀ ... ⅏) |

The Variations category is the largest and the last-but-one chip; the chip row scrolls
horizontally.

### 5.3 Orphan assets

`assets/common/emoji_shortcodes.json` (a JSON object with `provider` = `unicode`, `categories`
mapping 3956 emoji to category ids, and `shortcodes` mapping 4199 names to arrays of emoji) and
`assets/common/symbol_shortcodes.json` (about 150 names such as `euro`, `pi`, `rightarrow`,
`mdash` mapping to single characters) are still shipped but nothing reads them since the 2.0
removal of the `:shortcode:` triggers. They are data for a feature that no longer exists.

## 6. Launcher shortcuts: keys that launch things

### 6.1 Storage

`launcher_shortcuts` holds a JSON object keyed by the key's Android keycode as a decimal string
(`"62"` for Space). Each value is an object:

| Field | Meaning |
|---|---|
| `type` | `app` (legacy app entry), `command` (everything written since the command catalog exists), `quick_launcher` (legacy), `shortcut` (reserved, never executed) |
| `packageName`, `appName` | Set for app-type entries and for commands that launch a package |
| `action`, `data` | Reserved legacy fields |
| `commandId` | The command's id (section 8.2), for example `app:com.whatsapp`, `pastiera.quick_launcher`, `device.shade.notifications` |
| `source` | The command source's storage value: `apps`, `pastiera`, `app_actions`, `device_control`, `nav_actions` |
| `kind` | `App`, `PhysiBoardAction`, `AppAction`, `DeviceControl`, `NavAction` |
| `title`, `subtitle` | The command's label and subtitle at assignment time |
| `launch` | The launch spec object (section 8.1): `{"type":"app_package","packageName":...}`, `{"type":"intent_uri","action":...,"data":...,"packageName":...,"componentName":...,"categories":[...],"flags":[...]}`, `{"type":"internal_action","actionId":...}` or `{"type":"nav_action","mappingType":...,"value":...}` |

Loading: an entry without `launch` derives one from its legacy `type` (`app` from
`packageName`; `quick_launcher` to the internal action `open_quick_launcher`); unknown keys and
malformed entries are skipped; a corrupt preference logs and yields no shortcuts (it is not
rewritten). The parsed map is cached against the raw preference string so a key press does not
re-parse it.

Only one key can hold the quick launcher: saving a quick launcher assignment first removes every
other entry that is one (by type, by command id `pastiera.quick_launcher`, or by launch spec
`open_quick_launcher`).

Default assignment: the first time the shortcuts are read (any read, including from the key
handler), if `quick_launcher_default_assigned` is not yet true and no entry is a quick launcher,
Space gets the quick launcher and the flag is set, unless Space already holds something else, in
which case nothing is written and the settings screen shows the hint "New: SYM can open the
quick launcher. PhysiBoard would normally assign it to Space, but Space already has a shortcut.
Open the assignments page and choose “Open quick launcher” on any key to try it." The tutorial's
QuickLauncher page offers "Use SYM + Space for QuickLauncher", which overwrites Space and turns
`launcher_shortcuts_enabled` on.

Assignable keys: the 26 letters, Backspace (`KEYCODE_DEL`), Space, Enter. Keys other than these
can appear in the preference (a backup could carry them) but are never matched by the home
screen and power shortcut paths, which accept only those 29.

### 6.2 The three ways an assigned key fires

**A. On the home screen** (`launcher_shortcuts_enabled`, default off). With no editable field
focused, no Ctrl latch active, the foreground package being one that answers the HOME intent
(the list is queried once per service lifetime and cached), and the pressed key one of the 29:
if the key has an assignment it runs and the key is consumed; if it has none, the assignment
sheet (section 6.4) opens and the key is consumed. Nav mode keys are handled before this, so a
key that nav mode owns never reaches it.

**B. Power shortcuts** (`power_shortcuts_enabled`, default on), anywhere without an editable
field, including the home screen. Pressing Sym (key down, repeat 0) arms the mode and consumes
the key: nav mode, if active, is switched off for the duration; a toast "Press shortcut key to
launch" is shown 500 ms later if the mode is still armed (so an immediate chord shows no toast);
the mode disarms by itself after 5000 ms. Pressing Sym again while armed disarms it. While armed,
the next key that is one of the 29 disarms the mode, restores nav mode if it had been on, and
then behaves exactly like case A (assigned: run; unassigned: open the sheet). Keys that are not
among the 29 leave the mode armed. Toasts of the same text within 1000 ms are collapsed into one.

A Sym that is physically held while the quick launcher's key is pressed (the event reports Sym
down) fires the quick launcher directly, even if the mode had not been armed, provided no Ctrl
latch is active.

**C. In a text field.** With an editable field, power shortcuts on, the key pressed with Sym
held or with a Sym tap still pending release (a key pressed between Sym down and Sym up), repeat
0, and an assignment on that key: the assignment runs and the key is consumed. The Sym release
then does not open or cycle a Sym page (the chord counts as used; layers-sym-alt.md 5.3). Keys
without an assignment fall through to the Sym chord symbol lookup. This is how Sym+Space opens
the quick launcher while typing, and it takes precedence over the Sym+Enter extra send and over
any Sym page character on that key. Unassigned keys never open the sheet from a text field.

The home screen path additionally requires `launcher_shortcuts_enabled`; the power shortcut
paths do not, so with the defaults (home screen off, power shortcuts on) an assigned key works
via Sym everywhere and does nothing bare.

### 6.3 What runs

The entry's `commandId` is resolved against the live command catalog (so an app that was
uninstalled no longer resolves and a renamed label is refreshed); if it does not resolve, the
stored `launch` spec is run as a command of the stored kind and source; if there is no launch
spec either, nothing runs. Special cases: a quick launcher entry always goes through the quick
launcher opener (section 7.1), whatever its other fields; an `app` type entry whose command
fails falls back to a plain launch of `packageName`. Execution semantics are in section 8.3.
Failures show a toast with the reason ("Could not open app", "Command not available", and so
on). A successful run consumes the key.

### 6.4 The assignment sheet

A transparent, animation-free activity (excluded from recents, no history, single-top) holding a
modal bottom sheet, at most 75% of the window height, with a 40 dp drag handle. Tapping the dim
area outside closes it. Content:

- Header: "Shortcut" and a chip with the key's name (`Q`..`M`, `⌫`, `␣`, `⏎`, or "Key 62" for
  anything else) and a close icon.
- A filter row: a search toggle (magnifier) which reveals a rounded "Search commands..." field
  (focused 100 ms after the sheet expands), then chips "All" plus one per command source present
  ("Apps", "PhysiBoard", "App actions", "Device control", "Navigation"), then, when the key
  already has an assignment, a red "Remove" chip.
- The command grid: adaptive columns of at least 100 dp, cells of aspect ratio 0.85 with the
  command's icon (the app icon, or a Material icon per section 8.5) above its single-line label.
  With no source chip chosen, the commands are grouped under a full-width header per source.

Which commands: every command that lists the "assigned key" surface (section 8.2), from every
source, regardless of the quick launcher's per-source visibility setting. Ordering with an empty
search and a letter key: apps whose label begins with that letter (case-insensitive) first,
alphabetically, then the rest by source rank (Apps, PhysiBoard, App actions, Device control,
Navigation) and label; otherwise by source rank and label. A search keeps commands whose label,
subtitle or any search token contains the query (case-insensitive). Empty states: "No commands
found", or "No results for "query"".

Choosing a command writes the entry (type `command`, all fields from the command) and returns
result code 1 ("assigned"); when the sheet was opened by a key press (not from settings) the
command also runs immediately, so an unassigned key pressed on the home screen both assigns and
launches. "Remove" deletes the entry and returns result code 2 ("removed"). Opened from the
settings screen the sheet is started with the "skip launch" extra and only assigns. The intent
extras are `key_code` (int) and `skip_launch` (boolean).

### 6.5 The assignments screen

Settings > Extras > "PhysiBoard-QuickLauncher" (also reachable from the home screen's status
card and the tutorial) opens a hub titled "PhysiBoard-QuickLauncher" with the intro "These
settings share one launcher-key assignment list. Choose where PhysiBoard should listen for
those assigned keys.", the blocked-default hint when it applies, and:

1. "Homescreen shortcuts" switch, tagged "Experimental" ("Listen for your assigned keys on the
   Android home screen. The keys themselves are set below, in Assigned launcher keys.").
2. "SYM key shortcuts" switch ("Hold SYM with one of your assigned keys to launch an app or
   action. The keys are set below, in Assigned launcher keys.").
3. "Behaviour" row ("Launcher provider, search/ranking rules, and SYM+Space from text fields.")
   opening the quick launcher behaviour screen (section 7.8).
4. "Appearance" row opening the quick launcher appearance screen.
5. "Assigned launcher keys" row ("Tap a key to assign or replace a command. Assigned keys are
   shared by both trigger modes." or "... Quick launcher is currently assigned to ␣.").

The assigned keys screen draws a fixed QWERTY grid of square keys in three rows: `Q W E R T Y U
I O P`, `A S D F G H J K L ⌫`, `Z X C V ␣ B N M ⏎`, the second and third rows centered. A key
shows: the app's icon filling the key for an installed app; a magnifier icon over the key letter
for the quick launcher; the command's Material icon over a short label for other commands; the
key letter alone when unassigned (assigned keys use the primary container color, unassigned the
surface variant). Tap opens the assignment sheet for that key with launching skipped, and the
grid refreshes on result codes 1 and 2. Long-press-and-drag an app key onto another key swaps
the two entries (both directions; dropping on nothing does nothing); the dragged icon follows the
finger at alpha 0.8 and the key under it is highlighted. Opening the screen removes entries whose
app is no longer installed (by package, for both legacy and command entries).

### 6.6 Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `launcher_shortcuts` | string (JSON object) | `{}` (then Space = quick launcher on first read) | The key to command map | Assigned launcher keys | (grid) |
| `launcher_shortcuts_enabled` | boolean | false | Bare assigned keys fire on the home screen | PhysiBoard-QuickLauncher | Homescreen shortcuts |
| `power_shortcuts_enabled` | boolean | true | Sym-armed and Sym-held assigned keys fire, everywhere | PhysiBoard-QuickLauncher | SYM key shortcuts |
| `quick_launcher_default_assigned` | boolean | false | Set once the Space default has been written or found; stops the default from being re-applied after the user removes it | none | |

The 1.x keys `quick_launcher_text_field_shortcuts`, `quick_launcher_alt_space_in_text_fields`
and `quick_launcher_alt_shortcuts_outside_text_fields` are dropped on migration and restore; the
key binding answers those questions now.

## 7. The quick launcher

### 7.1 Opening and closing

The quick launcher is opened by its assigned key (section 6.2), by the "PhysiBoard
QuickLauncher" command from any surface, and from nav mode when bound there.
`quick_launcher_behavior` decides what "open" means: `pastiera` (default) opens PhysiBoard's
own sheet; `niagara` sends `android.intent.action.VIEW` of `niagara://search` to package
`bitpit.launcher` (browsable category, new task, clear top) and falls back to PhysiBoard's sheet
if that fails to start. The "PhysiBoard QuickLauncher" command itself always opens PhysiBoard's
sheet.

PhysiBoard's sheet is a transparent full-screen activity (no title, no system animation on
either open or close, excluded from recents, no history, single-top, soft input always hidden,
not touch-modal). The key press sends a "toggle" intent carrying the boolean extra
`brobata.physiboard.inputmethod.extra.TOGGLE_QUICK_LAUNCHER`; if the activity is already on
top, the intent is delivered to it and it dismisses, so the same key opens and closes. The
command path sends a plain "open" intent without the extra.

Dismissal: tapping the dim area, the chevron at the end of the search box, Back or Escape, or
Sym held plus the quick launcher's own key. Back, Escape and the Sym chord are acted on at key
release, with the key down consumed and remembered, so the release is not forwarded to the app
underneath; a cancelled release does nothing. A plain Sym tap is not consumed by the sheet (it
reaches the keyboard normally). Dismissal plays the exit animation (`quick_launcher_animation_ms`
below) and then finishes; a duration of 0 finishes immediately; a second dismiss request during
the animation is ignored.

Appearance: a bottom-anchored surface with 14 dp by 8 dp padding (0 in the collapsed pill), a
"PhysiBoard-QuickLauncher" title, a rounded search box with a magnifier, the query or "Start
typing..." in muted text, and the chevron. Width is `quick_launcher_width_percent` of the
screen (50..100, default 100), height at most 78% of the window. Pill mode
(`quick_launcher_pill_mode`, default off) shows only the search box at 72% width until the first
character is typed, then expands. Open and close animate with a vertical slide of the configured
duration and a fade of half that (default 120 ms; range 0..320).

### 7.2 What it lists

Commands with the quick launcher surface, from the sources enabled for the quick launcher
(section 8.6; defaults: Apps and PhysiBoard on, App actions, Device control and Navigation off).
Apps are shown at once from the app-list cache (after syncing package changes) and the full
catalog is reloaded in the background; "Loading apps..." shows while the list is empty during
that reload. Hidden entries (section 7.5) are excluded everywhere.

Empty query: favourites in their saved order (ties by label), then every other entry
alphabetically by label, grouped under source headers when there are more than 4 entries in
total; with `quick_launcher_limit_results` on, only the favourites are shown and the empty state
reads "Start typing to search apps". With no entries at all: "No apps found"; with a query and
no matches: "No results for "query"".

### 7.3 Typing into it

Every key down is examined before the sheet's own widgets: Enter launches the top match (a
second launch from the release is suppressed; a release without a prior handled down launches,
unless cancelled); Backspace removes the last character; Back, Escape and the Sym chord are the
dismiss keys; any other key without Ctrl that resolves to text appends it and re-filters. Text
resolution: with `quick_launcher_respect_keyboard_layout` (default on) the active PhysiBoard
layout (resolved from the current subtype, falling back to the saved layout name) maps the
keycode, Shift-aware; when it maps nothing, the event's own character is used unless it is a
control character. Alt-modified letters still go through the layout, so Alt does not switch the
sheet into symbol entry. There is no caret in the query; editing is append and delete-last only.

Auto-launch: with `quick_launcher_auto_start_single` (default off), a non-blank query that leaves
exactly one entry launches it immediately; the flag that prevents repeat auto-launches resets on
every query change and customisation change.

### 7.4 Ranking

The query and every candidate string are lowercased and trimmed. Lower scores rank first; ties
break by label. For each entry the following are computed and the smallest wins:

| Match on | Score |
|---|---|
| Alias equals query | 0 |
| Alias starts with query | 2 + alias length |
| A word of the alias starts with query | 12 + alias length |
| Alias contains query | 30 + position |
| Alias subsequence (see below) | subsequence score + 50 |
| Label equals query | 0 |
| Label starts with query | 10 + label length |
| A word of the label starts with query | 40 + label length |
| Subtitle (package name for apps) contains query | 200 + position |
| Search tokens contain query | 220 + position |
| Label subsequence | 80 + total gap characters skipped + label length |
| Typo-tolerant label match (`quick_launcher_typo_tolerant_ranking`, default on) | 180 + 25 × edit distance + length difference; only for queries of 3+ characters; allowed distance 1 for 3..5 characters, 2 for 6+; computed against each word of the label, the label without spaces, and every prefix of those within the allowed length window, using edit distance with transpositions |
| Subtitle subsequence | subsequence score + 240 |

When an alias is set and it matches, the alias score is used alone (the label and subtitle
scores are ignored for that entry); when an alias is set but does not match, the normal scores
apply. A favourite's normal score is reduced by 25 (not below 0). A subsequence match requires
every query character to appear in order. With `quick_launcher_limit_results` on, only the best 3
are shown. The settings screen's "How ranking works" dialog summarises this as: exact app name,
app name prefix, word prefix, abbreviation/subsequence, typo-tolerant, then package name.

### 7.5 Rows and per-entry customisation

Each row: the command's icon (app icon or Material icon), the label (or `alias | label` when
`quick_launcher_show_alias_first`, default on, and an alias is set), the subtitle (or the source
label when there is none), and on the top match the hint "Enter". Row colors: the top match is
tinted with `quick_launcher_static_top_highlight_color` (default 0x7A4285F4) when
`quick_launcher_static_top_highlight` is on (default off), otherwise with the entry's chosen
color or a color derived from its icon at alpha 0.58; a favourite gets a 2 dp border in
`quick_launcher_favorite_color` (default "dynamic", meaning derived from the icon) when
`quick_launcher_highlight_favorites` is on (default on); with `quick_launcher_icon_colors`
(default off) every row is tinted from its icon at alpha 0.28. The icon-derived color is the
alpha- and saturation-weighted average of a 32 by 32 rendering of the icon, pushed to
saturation 0.34..0.72 and value 0.58..0.92; icons that yield nothing use a hue per source (apps
214, PhysiBoard 145, app actions 282, device control 28, navigation 190).

Long press on a row opens a menu: a "Search alias" field with Clear and Save; "Favorite" /
"Unfavorite"; for favourites "Move up" and "Move down" (swapping saved orders with the
neighbour); "Hide"; "Dynamic entry color"; and eight "Entry color" swatches (0x7A4285F4,
0x7A34A853, 0x7AFABB05, 0x7AEA4335, 0x7AA142F4, 0x7A00ACC1, 0x7AFF7043, 0x7A888888). Every
choice is saved at once and the list re-filters.

Storage, `quick_launcher_command_customizations`: a JSON object keyed by command id, each value
`{"favorite":bool,"hidden":bool,"custom_search":"alias","favorite_order":int,"color":int}`;
`favorite_order` defaults to the largest possible int (unordered) and a new favourite gets one
more than the current highest order; an entry that is back to all defaults is removed from the
object. The settings "Customize entries" dialog (section 7.8) edits the same object, including
unhiding.

The sheet re-reads the source visibility preference while open and rebuilds its list when it
changes.

### 7.6 The Enter conflict

The quick launcher's key is checked in the text-field path before the Enter handler, so binding
the quick launcher to Enter and enabling an app's "SYM + Enter" extra send makes the launcher
win silently (per-app-behavior.md 3.8 and its edge case E6). The "SYM + Space is assigned to
something else" strings exist for the tutorial's conflict card; nothing warns about Enter.

### 7.7 Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `quick_launcher_behavior` | string: `pastiera`, `niagara` | `pastiera` | PhysiBoard sheet or Niagara search (with fallback) | Behaviour | QuickLauncher behaviour ("Choose whether the shortcut opens PhysiBoard search or Niagara Launcher search. Niagara falls back to PhysiBoard if unavailable.") |
| `quick_launcher_auto_start_single` | boolean | false | Launch when one result remains | Behaviour | Open unique match automatically |
| `quick_launcher_limit_results` | boolean | false | Favourites only until typing, then top 3 | Behaviour | Show only top search results |
| `quick_launcher_respect_keyboard_layout` | boolean | true | Query typed through the active layout | Behaviour | Use active keyboard layout |
| `quick_launcher_typo_tolerant_ranking` | boolean | true | Edit-distance matches | Behaviour | Typo-tolerant search |
| `quick_launcher_animation_duration_ms` | int, 0..320 | 120 | Slide/fade duration | Behaviour | Animation duration ("%d ms") |
| `quick_launcher_width_percent` | int, 50..100 | 100 | Sheet width | Appearance | Launcher width ("%d%% screen width") |
| `quick_launcher_pill_mode` | boolean | false | Compact pill until typing | Appearance | Pill mode |
| `command_surface_sources` | string (JSON) | see 8.6 | Which sources feed the sheet | Appearance ("QuickLauncher entries") | one switch per source |
| `quick_launcher_command_customizations` | string (JSON) | `{}` | Favourites, hidden, aliases, colors, order | Appearance ("Customize entries") and the sheet's long-press menu | |
| `quick_launcher_highlight_favorites` | boolean | true | Favourite border | Appearance | Highlight favorites in list |
| `quick_launcher_favorite_color` | int (ARGB), or Int.MIN_VALUE for dynamic | dynamic | Border color | Appearance | (swatches, "Dynamic") |
| `quick_launcher_icon_colors` | boolean | false | Tint rows from icons | Appearance | Tint entries from app icon colors |
| `quick_launcher_show_alias_first` | boolean | true | `alias \| label` display | Appearance | Show search alias before entry name |
| `quick_launcher_static_top_highlight` | boolean | false | Fixed top-match color | Appearance | Use static top-match highlight color |
| `quick_launcher_static_top_highlight_color` | int (ARGB) | 0x7A4285F4 | That color | Appearance | (swatches) |

### 7.8 The settings screens

"Behaviour" (title "Behaviour"): the provider picker with its description, the four switches
above in the order auto-start, limit results, respect layout, typo-tolerant, a "How ranking
works" button opening the "QuickLauncher ranking" dialog, and the animation duration slider.

"Appearance" (title "Appearance"): the width slider, pill mode switch, "QuickLauncher entries"
("Choose which sources appear in PhysiBoard search.") with one switch per source, "Customize
entries" ("Favorites, hidden entries, and search aliases") opening a dialog with a "Search
entries" field, "All" / "Favorites" chips, and per entry: star, hide/show, edit pencil (alias
field "Search alias"), up and down arrows for favourites, and a color chooser with "Dynamic"
plus the swatches; then "Entry appearance" with the four switches highlight favourites, icon
colors, static top highlight, alias first, and the favourite and top-highlight color swatches.
Empty states: "No commands available for the selected sources.", "No entries match "q"".

## 8. The command catalog

### 8.1 Model

A command has an id, a source, a kind, a label, an optional subtitle, an icon (an app drawable
or a Material icon), a launch spec, a set of surfaces where it is offered (assigned key, quick
launcher, nav mode), and search tokens. The four launch specs are: launch a package (its launcher
intent, new task); start an intent (action, optional data URI, optional target package or
component, categories, and "flags" strings, where `clear_top` adds the clear-top flag and
`key=value` strings become string extras, resolved before starting; an unresolvable intent fails
with "Command not available", a security refusal with "Command blocked"); an internal PhysiBoard
action by id; a nav action (a mapping type and value executed through nav mode, only when an
editor connection exists).

### 8.2 Sources and commands

Source rank order: Apps, PhysiBoard, App actions, Device control, Navigation.

**Apps** (`apps`, "Apps"): one command per package that has a launcher activity, id
`app:<package>`, label the app name, subtitle the package, icon the app icon, surfaces all
three, tokens app name and package. Built from the cached installed-app list.

**PhysiBoard** (`pastiera`, "PhysiBoard"; the storage value is kept as `pastiera` because saved
assignments contain it):

| Id | Label / subtitle | Internal action | Surfaces | Does |
|---|---|---|---|---|
| `pastiera.quick_launcher` | PhysiBoard QuickLauncher / Open PhysiBoard search | `open_quick_launcher` | assigned key, nav mode | Opens PhysiBoard's sheet (section 7.1) |
| `pastiera.main` | PhysiBoard / Open app settings | `open_main_activity` | assigned key, nav mode | Opens the app's main activity |
| `pastiera.voice_assistant` | Voice assistant / Open it already listening | `start_voice_assistant` | all three | dictation.md section 11; "No voice assistant is set up on this device." on failure |
| `pastiera.toggle_software_keyboard_mode` | Toggle Keyboard Mode / Switch Virtual / Hardware | `toggle_software_keyboard_mode` | all three | Toggles the temporary software keyboard mode and, when the toggle toasts are enabled, shows the resulting mode |

**App actions** (`app_actions`, "App actions"): each is an intent into a third-party app and is
listed only when that app can resolve it. Ids and targets: `niagara.search` (`niagara://search`)
and `niagara.agenda` (`niagara://agenda`) in `bitpit.launcher`; `tasker.select_task`
(`net.dinglisch.android.tasker.ACTION_TASK_SELECT`), `tasker.preferences`
(`net.dinglisch.android.tasker.ACTION_OPEN_PREFS`) and `tasker.create_shortcut`
(`android.intent.action.CREATE_SHORTCUT`) in `net.dinglisch.android.taskerm`;
`homeassistant.navigate` (`homeassistant://navigate`), `homeassistant.assist`
(`android.intent.action.ASSIST`) and `homeassistant.voice_command`
(`android.intent.action.VOICE_COMMAND`) in `io.homeassistant.companion.android`. URI commands
carry the browsable category; the others use their action alone. Icon: the target app's icon.
Surfaces: all three.

**Device control** (`device_control`, "Device control"), surfaces all three, subtitle is the
group:

| Id | Label (group) | Does |
|---|---|---|
| `device.home` | Home screen (System) | Starts the HOME intent |
| `device.media.play_pause`, `device.media.previous`, `device.media.next` | Play / pause, Previous track, Next track (Media) | Dispatches the media key down and up through the audio service |
| `device.volume.up`, `device.volume.down`, `device.volume.mute` | Volume up, Volume down, Mute volume (Audio) | Adjusts the music stream, showing the system volume panel |
| `device.brightness.up`, `device.brightness.down` | Brightness up / down (Display) | Sends `input keyevent` for the brightness keycodes through Shizuku on a background thread; fails with "Shizuku required" when Shizuku is not running or not granted; success only means the command was sent |
| `device.shade.notifications`, `device.shade.quick_settings` | Open notifications, Open quick settings (System) | Calls the hidden status bar expand method; if refused and the embedded ADB broker is paired, runs `cmd statusbar expand-notifications` or `expand-settings` on a background thread; otherwise "Shade unavailable" |
| `settings.android.main`, `.apps`, `.default_apps`, `.input_method`, `.accessibility`, `.language_input`, `.bluetooth`, `.wifi`, `.internet_panel` (Android 10+, "System panel"), `.display`, `.sound`, `.nfc`, `.battery`, `.notifications`, `.pastiera_notifications` | Settings, Apps, Default apps, Keyboard settings, Accessibility, Language & input, Bluetooth, Wi-Fi, Internet, Display / brightness, Sound & vibration, NFC, Battery, Notifications, PhysiBoard notifications (Settings) | Starts the corresponding `android.settings.*` intent; the last one adds the app package extra. Listed only when resolvable. |

**Navigation** (`nav_actions`, "Navigation"), nav mode surface only, icon "navigation":
keycode commands `nav.keycode.<NAME>` for DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, TAB,
MOVE_HOME, MOVE_END, PAGE_UP, PAGE_DOWN, ESCAPE, DPAD_CENTER, FORWARD_DEL (labels Up, Down,
Left, Right, Tab, Home, End, Page up, Page down, Escape, Center, Forward delete; subtitle
"Navigation"), and action commands `nav.action.<name>` for copy, paste, cut, undo, select_all,
expand_selection_left, expand_selection_right, move_word_left, move_word_right,
expand_selection_word_left, expand_selection_word_right, page_start, page_end,
media_play_pause, media_previous, media_next (subtitle "Action"). Each executes the nav mapping
of that type and value (trackpad-caret-nav.md).

### 8.3 Where commands are bound

- Assigned keys (section 6): the sheet lists the assigned-key surface.
- The quick launcher (section 7): the quick launcher surface, filtered by source visibility.
- Nav mode and the Ctrl map: a mapping of type `command` whose value is a command id runs that
  command when the key is pressed in nav mode or with the Ctrl latch; a command that does not
  list the nav mode surface is ignored and the key falls through. The nav mode settings screen
  offers the nav mode surface's commands for binding. Details of the mapping file are in
  trackpad-caret-nav.md and keys-and-modifiers.md.

Lookups by id read the live catalog each time, so the app list is queried on each resolution.

### 8.4 Failure feedback

Every failed execution shows a short toast with the reason, except when executed from a context
that asks for silence. Reasons in use: "Package not available", "Could not open app", "Command
not available", "Command blocked", "Command failed", "Could not open QuickLauncher", "Could not
open PhysiBoard", "Audio unavailable", "Shizuku required", "Shade unavailable", "Nav mode
unavailable", "No input context", "Nav action failed", "Unknown action", and the assistant's
"No voice assistant is set up on this device.".

### 8.5 Icons

Where a command has an app drawable it is shown; otherwise a Material icon by rule, first match
wins: apps: grid of apps; app actions: agenda -> event, tasker -> task check, homeassistant.assist
and .voice -> microphone, other homeassistant -> home, other -> magnifier; `pastiera.quick_launcher`
-> magnifier; `pastiera.main` -> gear; media play/pause -> play, previous -> skip previous, next ->
skip next; volume up/down/mute -> the volume glyphs; brightness -> sun; ids containing
default_apps or `settings.android.apps` -> apps; input_method -> keyboard; accessibility ->
accessibility figure; language or locale -> globe; bluetooth -> bluetooth; wifi or internet ->
wifi; display -> sun; sound -> volume; nfc -> contactless; battery -> battery saver; notification
-> bell; nav keycodes -> arrows, tab, first/last page, vertical align top/bottom, close, backspace;
nav actions -> copy, paste, cut, undo, select all, arrows for selection and word moves, first/last
page, play/skip; any other device control -> gear; PhysiBoard and navigation sources -> command
key; else magnifier.

### 8.6 Source visibility

`command_surface_sources` is a JSON object keyed by source storage value with
`{"quick_launcher":bool}`; missing sources take the defaults (`apps` true, `pastiera` true,
`app_actions` false, `device_control` false, `nav_actions` false). Only the quick launcher
surface is filtered; assigned keys and nav mode always see every source.

## 9. Typing sounds and tap vibration

### 9.1 Typing sounds

`typing_sound_mode` selects `off` (default), `click`, `typewriter` or `custom`. The two
built-in packs are the raw resources `typing_click_*` and `typing_typewriter_*`, each with five
groups: `normal` 1..24, `space` 1..5, `backspace` 1..5, `enter` 1..5, `modifier` 1..5 (`.ogg`;
in the repository, `app/src/main/res/raw/typing_click_normal_1.ogg` and so on, 98 files). The
group is chosen by keycode: Space; Backspace; Enter; Shift, Ctrl, Alt and Sym as modifier;
everything else normal. A custom pack maps its folders to the same groups and a group with no
files borrows the normal group.

A sound plays on every hardware key down with repeat count 0 while an editable field is active,
for every key except Back (modifiers included; the Titan's Fn arrives as Ctrl and so clicks as a
modifier), and for every on-screen keyboard key press. One file of the group is picked at random,
played at a random volume between 0.82 and 1.0 (both channels) and a random rate between 0.965
and 1.035, at most 8 overlapping streams. Files that have not finished loading are skipped. The
audio usage is `typing_sound_output_mode`: `media` (default) plays on the media stream; `system`
as assistance sonification and `notification` as a notification, both of which follow silent
mode and Do Not Disturb. The pool is rebuilt whenever any of `typing_sound_mode`,
`typing_sound_output_mode`, `typing_sound_custom_file_name` or `typing_sound_updated_at`
changes, and released when the service is destroyed.

Custom pack import: a `.zip` picked with the system document picker, containing audio files
(`ogg`, `wav`, `mp3`, `m4a`) inside folders named `normal`, `space`, `backspace`, `enter`,
`modifier`; at most 96 files, 2 MB per file, 16 MB in total; entries outside the target folder
are refused; files are renamed `001.ext`, `002.ext` in order; the old pack is kept until the new
one is fully extracted. Success toasts "Typing sound pack imported", failure "Could not import
this sound pack. Use a ZIP with audio files in normal/, space/, backspace/, enter/, and modifier/
folders.". The import row, the output-mode row and the "Sound pack format" row are hidden from
the settings screen in 2.x ("hidden to declutter"), so `custom` and a non-media output mode can
only be reached through the preference itself or a restored backup; the mode keys are not in
the backup contract, so in practice they are unreachable.

### 9.2 Tap vibration

Taps on the strip's suggestion slots vibrate (status-bar.md 5.3): with `tap_haptic_use_system`
(default on) through the system keyboard-tap haptic; with it off, a one-shot vibration of
`tap_haptic_duration_ms` (default 25, clamped 5..80) at default amplitude, skipped when the
device reports no vibrator. The clipboard and emoji buttons and the other strip buttons always
use the system keyboard-tap haptic regardless of this setting. Dictation cues are separate
(dictation.md).

### 9.3 Settings and the screen

Settings > "Sound & Haptics": a "Typing Sounds" row whose dropdown offers Off, Keyboard click,
Typewriter (the custom entry is hidden); a "Tap vibration" switch ("Use Android keyboard haptics
for suggestions and variations on compatible devices."); when that is off, a "Custom vibration:
N ms" slider ("Used when Android keyboard haptics are disabled.") from 5 to 80 in steps of 5
that plays the vibration on every change; then the dictation haptics rows (dictation.md).
The tutorial's "Typing sounds (gimmick)" page offers the same three modes with a demo field.

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `typing_sound_mode` | string: `off`, `click`, `typewriter`, `custom` | `off` | Which pack plays, if any | Sound & Haptics | Typing Sounds |
| `typing_sound_output_mode` | string: `media`, `system`, `notification` | `media` | Audio stream / usage | (hidden) | Typing Sound Output |
| `typing_sound_custom_file_name` | string | absent | Folder name of the imported pack | (hidden) | |
| `typing_sound_custom_display_name` | string | absent | Display name of the imported pack | (hidden) | Custom sound pack… |
| `typing_sound_updated_at` | long | absent | Bumped on import to force a reload | none | |
| `tap_haptic_use_system` | boolean | true | System haptic versus fixed vibration for slot taps | Sound & Haptics | Tap vibration |
| `tap_haptic_duration_ms` | long, 5..80 | 25 | Fixed vibration length | Sound & Haptics | Custom vibration: %d ms |

## 10. Titan-specific facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The display is 1080 px wide at 1.875 px per dp, so the emoji picker grid has 10 columns (the clamp's maximum), the clipboard panel is 331 px tall and the expanded emoji picker 496 px, and the settings dialogs get 11 columns. | status-bar.md D1 (1080 by 1200 at density 300); the column formulas in sections 4.3 and 5 |
| D2 | Sym is keycode 63 / scancode 253; every "Sym held" test in sections 6 and 7 reads the Sym meta bit of the event, and the Sym tap-versus-chord rule of layers-sym-alt.md 5.2 decides whether the release opens a page. | task brief; layers-sym-alt.md 5.1 |
| D3 | Fn never sends key-up and arrives as Ctrl with scancode 251, so Fn plus a key counts as a modifier for text expansion (never expands), as Ctrl for the emoji search (Fn+A selects the search text, Fn+V pastes into it), and as the modifier sound group for typing sounds. | task brief; sections 2.6, 4.5, 9.1 |
| D4 | The Titan has no dedicated emoji key; the Minimal Phone emoji keycode 666 that toggles page 4 is irrelevant on it. Page 4 opens from the strip button, the Sym cycle, or a bound command only. | layers-sym-alt.md 4.3 |
| D5 | Brightness up and down as commands need Shizuku on the Titan; the shade commands use the embedded ADB broker as the fallback. Both were added as key-bindable device controls in 1.2.0 ("Open notifications / Open quick settings from a key"). | PHYSIBOARD_CHANGES.md 1.2.0; section 8.2 |
| D6 | Sym+Space is the shipped quick launcher trigger on a fresh install (Space is unassigned there), and it works inside text fields because power shortcuts default on. | section 6.1 default assignment; tutorial strings "Hold SYM and press Space to open QuickLauncher from anywhere." |

## 11. Edge cases, quirks and known bugs

| Situation | Behaviour | Why |
|---|---|---|
| A snippet shortcut typed in uppercase (`!SIG`) | Expands; matching is case-insensitive and the replacement is inserted as stored | shortcuts are lowercased on both sides |
| Prefix typed right after a word (`mail!sig`) | No trigger | a boundary (start, whitespace, or one of `( [ { " '`) must precede the prefix |
| Prefix inside quotes (`"!sig`) | Trigger | the quote is a boundary character |
| Space with two prefix matches and no exact match, prefix-space off | Space goes through as a normal space; the matches clear because the token now ends in a space | expansion only consumes Space for an exact match unless the prefix-space option is on |
| Presentation Off, Tab pressed on an exact match | Expands without a trailing space | Tab falls back to the exact match when nothing is visible |
| Presentation Off, Up/Down/Escape | Not consumed | nothing is visible, so navigation keys are not intercepted |
| Snippet with leading spaces or trailing newline | Inserted verbatim | replacement text is never trimmed |
| Expansion in a terminal app on the exact-typing list | Never happens | restricted fields clear the lookup |
| Text changes between the key down and the commit (the token no longer ends the text) | Nothing is typed; matches clear | the commit re-checks the text before deleting |
| Popup while the keyboard window is not up (no anchor) | Nothing is drawn; keys still accept | the popup needs the keyboard window as anchor |
| Suggestion bar presentation while suggestions are disabled for the app | The three slots still show the matches | expansion forces the suggestion row |
| Popup position | Top center of the keyboard window, shifted up by its own height | as shipped; verified by a unit test |
| Copy of a password from a password manager | Stored in history and shown in full | no sensitive-content check exists |
| Copy the same text twice | One entry, moved to the top | duplicates refresh the timestamp |
| Pin an entry, wait past retention | It stays | retention skips pinned entries |
| Retention 0 | Nothing ever expires | 0 or less disables cleanup |
| Change `clipboard_history_enabled` while running | No effect until the keyboard service restarts | the flag is read once at creation |
| Clear All | Pinned entries stay; the system clipboard is also emptied | by design |
| Tap a clip | Inserted; the panel stays open even with auto-close on | the clipboard panel ignores `sym_auto_close` |
| Copy while the panel is open | Appears on the next strip refresh that sees a new count | the panel re-reads only on a count change |
| Database not openable (device still locked after boot) | History reads as empty; opening retried on next use | documented fallback |
| Clipboard retention or enable setting wanted | No screen has them | rows were never built; strings exist |
| Emoji absent from the device font (new Unicode 17 emoji) | Not shown in any category or search result | availability requires a glyph when no API level is known |
| Long press a flag or another emoji without variants | Nothing | no variants, no popup |
| Choose a skin-tone variant | The variant is inserted and becomes a recent; the base's default tone is unchanged | no per-emoji tone memory |
| Choose an emoji from Recents | The Recents row does not reorder until you leave the Recents tab | deferred refresh to avoid shuffling under the finger |
| Tap the search field while capture is on | Capture turns off (field dims); typing goes to the app | tapping toggles capture |
| Tap into the app's text while searching | Capture turns off at the next key | the app's selection changed between keys |
| Press Enter in the emoji search | Nothing (consumed) | there is no "insert first result" |
| Emoji search with a German system locale and an English word | English terms still match, German ones rank 25 higher | the chain always ends with `en`; preferred-locale bonus |
| Query of one character | Only equality and prefix matches | contains needs 2+ characters |
| The settings emoji dialog | Has no Recents category | recents are only in the panel |
| Currencies chip | Shows `₩ ₪ ₫ ₦ ₨` twice | the list is duplicated in the shipped data |
| Variations chip | No gaps between letter groups | the separator markers are filtered out before display |
| `emoji_shortcodes.json` / `symbol_shortcodes.json` | Shipped, never read | leftovers of the removed shortcode feature |
| Assigned key pressed bare on the home screen with `launcher_shortcuts_enabled` off | Nothing | only power shortcuts are on by default |
| Unassigned letter pressed while power shortcut mode is armed | The assignment sheet opens (and the mode disarms) | unassigned keys open the sheet outside text fields |
| Unassigned key with Sym in a text field | Falls through to the Sym chord character | text-field path only fires existing assignments |
| Sym pressed on the home screen, then nothing for 5 s | Mode disarms; nav mode returns if it was on | 5000 ms timeout |
| Sym pressed twice quickly outside a field | Armed then disarmed; no toast if under 500 ms | toggle semantics; toast delayed 500 ms |
| Sym is the screen trackpad trigger | Power shortcuts still arm on Sym down when no field is active | the router checks power shortcuts before nav mode keys (trackpad-caret-nav.md for the trackpad's own claim on Sym) |
| Quick launcher bound to Enter, app with "SYM + Enter" extra send | The launcher opens; the message is not sent; no warning | per-app-behavior.md E6 |
| Space already assigned to an app before 2.0 | The quick launcher is not auto-assigned; a hint appears in settings | the default never overwrites |
| Remove the quick launcher from every key | It is not re-added | `quick_launcher_default_assigned` stays true |
| Backup restored with a shortcut on a non-letter key (say F1) | Stored but never fires | only the 29 keys are checked |
| Assign from a key press on the home screen | The chosen command runs immediately as well | launch is skipped only from the settings screen |
| App uninstalled after assignment | The key stops resolving; on the home screen path the plain package launch also fails; the assignments screen deletes the entry on its next open | live catalog resolution |
| Quick launcher open, press its own key with Sym held | Closes on release | toggle on key up |
| Quick launcher open, plain Sym tap | Not consumed; the keyboard's Sym handling applies | tested |
| Back in the quick launcher | Consumed on down, closes on up; the app underneath never sees the release | tested |
| Alt+letter in the quick launcher | Typed through the layout as the letter, not an Alt symbol | tested |
| Ctrl+letter in the quick launcher | Ignored | Ctrl-modified keys are skipped |
| Niagara behaviour chosen but Niagara not installed | PhysiBoard's sheet opens | fallback on failure to start |
| Alias set and matching | Only the alias score counts | alias short-circuits |
| Two-character query with a typo | No typo match | typo tolerance needs 3+ characters |
| Hidden entry | Never listed, even when searched | filtered first |
| Brightness command without Shizuku | "Shizuku required" toast | shell key events need Shizuku |
| Shade command with the hidden API refused and no broker pairing | "Shade unavailable" | both paths failed |
| Media commands | Reported as success even if no player is listening | the key event is dispatched, not acknowledged |
| Typing sound on Back | Silent | Back is excluded |
| Typing sound on a held key | Only the first press sounds | repeats excluded |
| Typing sound on Shift, Alt, Ctrl, Sym, Fn | The modifier group sounds | modifiers are not filtered; Fn reads as Ctrl |
| Custom sound pack | Unreachable from the UI in 2.x | import row hidden; mode key not in backups |
| Tap vibration off (custom duration) | Applies to suggestion slot taps only; strip buttons keep the system haptic | buttons call the system haptic directly |

## 12. Test cases

Each case is an input sequence and the expected outcome, written so a JVM test can encode it.
"Text" is the text before the caret unless stated.

| # | Setup | Input | Expected |
|---|---|---|---|
| T1 | snippets on, prefix `!`, snippet `sig` -> `Regards` | text `Hello !sig`, then Space | text `Hello Regards ` (the 4 characters `!sig` deleted, `Regards ` committed) |
| T2 | as T1 | text `mail!sig`, then Space | no expansion; a normal space is typed |
| T3 | as T1 | text `!si`, presentation floating popup, exact-on-space on, prefix-space off, Space | not consumed by expansion (no exact match); normal space |
| T4 | as T1 but prefix-space on, presentation suggestion bar | text `!si`, Space | `!si` deleted, `Regards ` committed |
| T5 | as T1, accept-with-tab on | text `!sig`, Tab | `!sig` deleted, `Regards` committed, no trailing space |
| T6 | as T1, accept-with-enter off | text `!sig`, Enter | not consumed by expansion |
| T7 | as T1, snippet value `  first\nsecond\n  ` | text `!sig`, Tab | that exact value committed, whitespace and newline intact |
| T8 | snippets off | text `!sig`, Space | no editor read, no expansion |
| T9 | restricted field (password) | text `!sig`, Space | no editor read, no expansion |
| T10 | selection not collapsed | any key | no editor read, no expansion |
| T11 | as T1 | schedule a refresh, then Space immediately (before 24 ms) | expansion still happens (synchronous refresh on Space) |
| T12 | as T1 | text `!` | matches = every snippet, sorted by shortcut length then name |
| T13 | prefix validation | `!` valid; `:` invalid; `aa` invalid; ` ` invalid; `a` invalid |
| T14 | shortcut validation | `sig` valid; `my_sig2` valid; 41 letters invalid; `si-g` invalid; `` invalid |
| T15 | stored presentation `future-value` | read | floating popup |
| T16 | trigger detection | `:id` open (closed=false); `:id:` closed; `https://example.org/:id` and `12:30` no trigger |
| T17 | exact-match uniqueness | two matches with shortcut `id` from different sources | no exact match |
| T18 | clipboard | copy `a`, copy `b`, copy `a` | entries in order `a`, `b` (two entries; `a` newest) |
| T19 | clipboard, retention 5 | entry aged 6 min unpinned, entry aged 6 min pinned, entry aged 1 min | after cleanup: the pinned and the 1 min entries remain, pinned first |
| T20 | clipboard, retention 0 | entry aged 1 day | remains after cleanup |
| T21 | clipboard | pin entry X, then Clear All | X remains; non-pinned gone |
| T22 | clipboard | delete a pinned entry from the panel menu | removed |
| T23 | clipboard cleanup debounce | two non-forced cleanups 2 s apart | the second is skipped |
| T24 | emoji availability, API 36, glyph absent | emoji not in `minApi.txt` | unavailable; with glyph present: available |
| T25 | emoji availability | `😀` listed at API 23, running API 33 | available regardless of glyph |
| T26 | emoji search normalisation | `Grinning-Face_ ` | `grinning face` |
| T27 | emoji search, English index | query `grin` | `😀` among results with a keyword-prefix score of 1300 minus its category order; exact base `😀` as query scores 2000 |
| T28 | emoji search | query `a` | no contains matches, only equality and prefix |
| T29 | locale chain | system locales `de-CH`, `en-US` | chain `de-CH`, `de`, `en-US`, `en` |
| T30 | recents | add `😀`, `😃`, `😀` | list `😀`, `😃`; adding 41 distinct emoji keeps the newest 40 |
| T31 | emoji search capture | panel visible, capture on, key Y with layout resolver returning `z` | search text `z` |
| T32 | emoji search capture | same, resolver returns nothing, event char `y` | search text `y` |
| T33 | emoji search capture | text `zy`, Ctrl+A through the search connection | selection 0..2 |
| T34 | emoji search capture | text `old` selected, key N | text `n`, caret at 1 |
| T35 | emoji search capture | text `old` selected, Backspace | text empty |
| T36 | launcher shortcuts storage | assign quick launcher to Space, then to Enter | only Enter holds it |
| T37 | launcher default | fresh preferences, first read | Space = quick launcher, `quick_launcher_default_assigned` true |
| T38 | launcher default | Space already holds an app, first read | unchanged; "blocked" reported |
| T39 | swap | app on Q, quick launcher on Space, swap Q and Space | Q = quick launcher, Space = app |
| T40 | quick launcher activity, animation 0 | Back down, Back up | not finishing after down; finishing after up; both consumed |
| T41 | quick launcher, Space = quick launcher | Space down with Sym meta, Space up with Sym meta | consumed; finishing only after up |
| T42 | quick launcher | Sym down, Sym up | neither consumed; not finishing |
| T43 | quick launcher | key A down | consumed (query grows) |
| T44 | quick launcher | key A down with Alt meta | consumed (typed through the layout) |
| T45 | quick launcher intents | toggle and open intents | both carry the no-animation flag |
| T46 | ranking | entries `Telegram`, `Termux`, `Settings`; query `te` | `Telegram` (10+8) before `Termux` (10+6)? No: `Termux` scores 16 and `Telegram` 18, so `Termux`, `Telegram`, then `Settings` only if a subsequence exists (`t`,`e` in order: yes, 80 + gap 1 + 8 = 89) |
| T47 | ranking, typo tolerance on | label `whatsapp`, query `whatsap` | distance 1 match, score 180 + 25 + 1 = 206, unless a prefix match wins (it does: `whatsapp` starts with `whatsap`, 10 + 8 = 18) |
| T48 | ranking, alias | alias `wa` on WhatsApp, query `wa` | score 0 for WhatsApp |
| T49 | ranking, favourite | two entries scoring 40 and 20, the first a favourite | first becomes 15 and ranks first |
| T50 | ranking, limit results | 5 matches | 3 shown |
| T51 | customisation storage | favourite off, hidden off, alias empty, order default, no color | entry removed from the object |
| T52 | source visibility | no preference | apps and pastiera enabled for the quick launcher, the other three disabled; every source enabled for assigned keys |
| T53 | typing sound group | Space -> space; Del -> backspace; Enter -> enter; Shift/Ctrl/Alt/Sym -> modifier; A -> normal |
| T54 | typing sound gating | key with repeat 1, or Back, or no editable field | no sound |
| T55 | tap haptic duration | stored 200 | read as 80; stored 1 read as 5 |
| T56 | typing sound mode | stored `bogus` | read as `off` |

## 13. Keep / Drop for 3.0

| Item | Verdict | Reasoning |
|---|---|---|
| Snippets with prefix, 29-key-independent trigger, exact/prefix acceptance | keep | Cheap, self-contained, and the one part of text expansion the maintainer asked to keep in 2.0 |
| Floating popup presentation | keep | Works without the strip; the default |
| Suggestion bar presentation | undecided | Depends on the 3.0 strip having replaceable slots; keep only if the strip keeps three slots |
| Colon shortcode trigger machinery, `emoji_shortcodes.json`, `symbol_shortcodes.json` | drop | Feature removed in 2.0; the assets are dead weight |
| Clipboard history capture, SQLite persistence, pinning, retention | keep | Used daily on the Titan; the maintainer's baseline puts the clipboard button on the strip |
| Retention and enable settings rows | keep (build them) | The preferences exist and are backed up but have no UI; 3.0 should expose them or fix the retention default |
| Sensitive-clip exclusion | keep (add it) | Password managers flag clips sensitive; 2.x stores them; 3.0 should honour the flag |
| The unused floating clipboard popup | drop | Nothing opens it |
| Clipboard panel as a Sym page with a close button | keep | The only way to see history without a mouse |
| Emoji picker with categories, tabs, recents, skin-tone popup | keep | The Titan has no emoji key; this is the emoji input |
| Emoji search with CLDR data in nine languages | keep | The search-field capture is the Titan's way of typing an emoji name from hardware keys; keep the data pinned to a Unicode and CLDR version |
| Locale chain with English fallback | keep | Cheap and covers every dictionary language |
| `emoji_picker_expanded_height` | keep | Screen is short; the taller picker is the default |
| Keyboard-switcher button in the picker | drop | Software keyboard only |
| Emoji picker height following the software keyboard | drop | No on-screen keyboard in 3.0 |
| Settings emoji dialog and Unicode dialog | keep | Needed to customise the Sym Emoji and Symbols pages; simplify the Unicode lists (dedupe currencies, fix separators) |
| Launcher shortcuts storage as a keyed JSON map with a launch spec | keep | Backups carry it; the model is sound |
| Home screen bare-key launch (`launcher_shortcuts_enabled`) | undecided | Off by default and marked experimental; the Titan's launcher search already eats letter keys; keep only if a user asks |
| Power shortcuts (Sym-armed, Sym-held) inside and outside text fields | keep | Sym+Space is how the quick launcher opens; on by default |
| The 500 ms toast and 5000 ms disarm | keep | Numbers are fine; the toast is the only feedback |
| Assignment sheet opened by an unassigned key | undecided | Surprising when Sym is pressed by accident on the home screen; consider requiring assignment from settings only |
| Drag-to-swap on the assignments screen | drop | Touch-only nicety; tap-to-assign covers it |
| Quick launcher sheet, ranking, layout-aware typing, favourites, hidden, aliases | keep | The Titan's app switcher from the keyboard; ranking and alias rules are the substance |
| Niagara behaviour | keep | The maintainer runs Niagara (memory: status bar over Niagara's home); a one-line intent |
| Pill mode, width percent, entry colors, static top highlight, icon tints | undecided | Cosmetic; keep the defaults, drop the settings if the 3.0 settings tree needs room |
| Animation duration setting | drop | Fix at 120 ms; a setting for a slide is noise |
| Command catalog with the five sources | keep | Shared by assigned keys, quick launcher and nav mode |
| App actions for Niagara, Tasker, Home Assistant | keep | Listed only when installed; cheap |
| Device control: media, volume, home, shade | keep | Shade needs the broker, which 3.0 keeps |
| Device control: brightness via Shizuku | undecided | 3.0 has the embedded broker; route brightness through it instead of Shizuku or drop the two commands |
| Android settings commands | keep | Zero cost |
| Navigation commands as nav mode bindings | keep | Owned by the nav mode doc; the catalog just exposes them |
| Typing sounds (click, typewriter) | undecided | Called a gimmick in the app's own tutorial; off by default; the 98 sound files add size |
| Custom sound pack import | drop | UI already hidden; unreachable |
| Output mode (media/system/notification) | drop | UI hidden; fix on media if sounds stay |
| Tap vibration switch and custom duration | keep | Cheap; the fixed-duration path matters on ROMs where keyboard-tap haptics are muted |

## 14. Provenance

- app/src/main/java/brobata/physiboard/inputmethod/expansion/SnippetExpansionSource.kt
- app/src/main/java/brobata/physiboard/inputmethod/expansion/TextExpansionController.kt
- app/src/main/java/brobata/physiboard/inputmethod/expansion/TextExpansionEngine.kt
- app/src/main/java/brobata/physiboard/inputmethod/expansion/TextExpansionModels.kt
- app/src/main/java/brobata/physiboard/inputmethod/expansion/TextExpansionPopup.kt
- app/src/main/java/brobata/physiboard/TextExpansionSettingsScreen.kt
- app/src/main/java/brobata/physiboard/ExtrasScreen.kt
- app/src/main/java/brobata/physiboard/SettingsManager.kt (snippet, clipboard, launcher shortcut, quick launcher, command source, typing sound, tap haptic, Sym auto-close and emoji picker height accessors)
- app/src/main/java/brobata/physiboard/SettingsMigration.kt
- app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- app/src/main/java/brobata/physiboard/core/InputContextState.kt
- app/src/main/java/brobata/physiboard/core/SymLayoutController.kt
- app/src/main/java/brobata/physiboard/core/NavModeController.kt
- app/src/main/java/brobata/physiboard/clipboard/ClipboardDao.kt
- app/src/main/java/brobata/physiboard/clipboard/ClipboardDatabase.kt
- app/src/main/java/brobata/physiboard/clipboard/ClipboardHistoryEntry.kt
- app/src/main/java/brobata/physiboard/clipboard/ClipboardHistoryManager.kt
- app/src/main/java/brobata/physiboard/clipboard/ClipboardHistoryPopupView.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/ClipboardHistoryView.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/ClipboardButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/statusbar/button/EmojiButtonFactory.kt
- app/src/main/java/brobata/physiboard/inputmethod/StatusBarController.kt
- app/src/main/java/brobata/physiboard/inputmethod/CandidatesBarController.kt
- app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt
- app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt
- app/src/main/java/brobata/physiboard/inputmethod/NotificationHelper.kt
- app/src/main/java/brobata/physiboard/EmojiPickerDialog.kt
- app/src/main/java/brobata/physiboard/EmojiRecyclerViewAdapter.kt
- app/src/main/java/brobata/physiboard/EmojiEntryRecyclerViewAdapter.kt
- app/src/main/java/brobata/physiboard/inputmethod/ui/EmojiPickerView.kt
- app/src/main/java/brobata/physiboard/data/emoji/EmojiAvailability.kt
- app/src/main/java/brobata/physiboard/data/emoji/EmojiRepository.kt
- app/src/main/java/brobata/physiboard/data/emoji/EmojiSearchRepository.kt
- app/src/main/java/brobata/physiboard/data/emoji/RecentEmojiManager.kt
- app/src/main/java/brobata/physiboard/UnicodeCharacterPickerDialog.kt
- app/src/main/java/brobata/physiboard/UnicodeCharacterRecyclerViewAdapter.kt
- app/src/main/java/brobata/physiboard/SymCustomizationScreen.kt
- app/src/main/java/brobata/physiboard/LauncherShortcutsScreen.kt
- app/src/main/java/brobata/physiboard/inputmethod/LauncherShortcutAssignmentActivity.kt
- app/src/main/java/brobata/physiboard/inputmethod/LauncherShortcutController.kt
- app/src/main/java/brobata/physiboard/inputmethod/QuickLauncherActivity.kt
- app/src/main/java/brobata/physiboard/inputmethod/QuickLauncherOpener.kt
- app/src/main/java/brobata/physiboard/CustomizationSettingsScreen.kt
- app/src/main/java/brobata/physiboard/NavModeSettingsScreen.kt
- app/src/main/java/brobata/physiboard/TutorialActivity.kt
- app/src/main/java/brobata/physiboard/SettingsScreen.kt
- app/src/main/java/brobata/physiboard/SettingsActivity.kt
- app/src/main/java/brobata/physiboard/commands/AppActionCommandSource.kt
- app/src/main/java/brobata/physiboard/commands/AppCommandSource.kt
- app/src/main/java/brobata/physiboard/commands/CommandExecutor.kt
- app/src/main/java/brobata/physiboard/commands/CommandJson.kt
- app/src/main/java/brobata/physiboard/commands/CommandModel.kt
- app/src/main/java/brobata/physiboard/commands/CommandRegistry.kt
- app/src/main/java/brobata/physiboard/commands/CommandSource.kt
- app/src/main/java/brobata/physiboard/commands/DeviceControlCommandSource.kt
- app/src/main/java/brobata/physiboard/commands/NavCommandSource.kt
- app/src/main/java/brobata/physiboard/commands/PhysiBoardCommandSource.kt
- app/src/main/java/brobata/physiboard/inputmethod/CommandMaterialIcons.kt
- app/src/main/java/brobata/physiboard/inputmethod/AssistantLauncher.kt
- app/src/main/java/brobata/physiboard/AppListHelper.kt
- app/src/main/java/brobata/physiboard/inputmethod/TypingSoundPlayer.kt
- app/src/main/java/brobata/physiboard/TypingSoundSettingsRow.kt
- app/src/main/res/raw/ (file names only)
- app/src/main/res/values/strings.xml
- app/src/main/AndroidManifest.xml
- app/src/main/assets/common/emoji/ (all files)
- app/src/main/assets/common/emoji_search/ (all files)
- app/src/main/assets/common/emoji_shortcodes.json
- app/src/main/assets/common/symbol_shortcodes.json
- scripts/update_emoji_assets.py
- app/src/test/java/brobata/physiboard/inputmethod/expansion/TextExpansionEngineTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/expansion/TextExpansionControllerTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/expansion/TextExpansionPopupTest.kt
- app/src/test/java/brobata/physiboard/SettingsManagerTextExpansionTest.kt
- app/src/test/java/brobata/physiboard/data/emoji/EmojiAvailabilityTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/ui/EmojiPickerViewSearchInputTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/QuickLauncherActivityTest.kt
- PHYSIBOARD_CHANGES.md
- docs/plans/rebuild-from-scratch.md
- docs/plans/2.0-settings-walkthrough.md
- docs/spec/README.md
- docs/spec/layers-sym-alt.md
- docs/spec/status-bar.md
- docs/spec/per-app-behavior.md
- docs/spec/dictation.md
