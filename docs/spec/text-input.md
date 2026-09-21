# Text input: composition, spacing, punctuation, capitalization, selection

This document describes how PhysiBoard turns an already-resolved character (a letter, a
digit, a punctuation mark, Space, Enter, Backspace) into text inside the app's text field,
and what it does around that text: automatic spaces, deferred spaces, the double-space period,
automatic capitalization, selection and cursor shortcuts, Vietnamese Telex, and how the
keyboard reads the field to make these decisions. Which physical key produces which character
is the subject of the keys and layers documents. The autocorrect and suggestion engine is
specified separately; here it appears only at the hand-off boundary.

Everything below is the 2.x behavior on the Titan 2 Elite unless a row says otherwise.

## 1. Vocabulary

- **Boundary punctuation**: the fixed set `. , ; : ! ? ( ) [ ] { } \ / "` (fourteen
  characters). A character in this set, or any whitespace, ends a word. Apostrophes are
  deliberately not in it. Any other character that is not a letter or digit also ends a word.
- **Apostrophe**: the straight apostrophe `'` and the three curly variants U+2019, U+2018 and
  U+02BC are treated as the same character everywhere in this subsystem. An apostrophe ends a
  word only when the character before it is not a letter or digit ("l'amico" is one word;
  "'hello" starts a word after the apostrophe).
- **Auto-space candidates**: the eleven characters `. , ; : ! ? \ / " ) ] }` the user may
  choose in the "Punctuation spacing" dialog. Both spacing lists below are subsets of these,
  always stored in this canonical order, and both are empty by default.
- **"Remove before" list**: characters that, when typed immediately after a space the keyboard
  inserted itself, replace that space (see 6.3).
- **"Before next text" list**: characters after which the keyboard withholds the following
  space until more text is typed (see 6.6).
- **Sentence end**: the text before the cursor, ignoring trailing whitespace, ends in `.`, `!`
  or `?`, and for `.` the character before it is not another `.` (an ellipsis is not a
  sentence end). "Sentence end followed by whitespace" additionally requires at least one
  whitespace character after the mark.
- **Auto-space**: a single space the keyboard inserted on its own after a completed word
  (accepted suggestion, auto-replace, autocorrect, add-word, and, with suggestions active,
  every space the user types after a word). The keyboard remembers that the most recent space
  was an auto-space with one flag; the flag is per keyboard, not per field, and is cleared
  whenever text is committed through most other paths.
- **Restricted field**: a field whose type is password, URL, email or "filter", or any field
  in an app the user marked as raw-mode. Details in section 3.

## 2. How the keyboard reads the field

The keyboard never keeps a private copy of the document. Every decision is made by asking the
app for a window of text around the cursor at the moment of the keystroke. The windows used:

| Decision | Characters read before cursor | After cursor | Notes |
|---|---|---|---|
| Double-space period | 100 | 0 | |
| Spaced hyphen to dash | 100 | 0 | |
| Mid-word quote to apostrophe | 2 | 0 | |
| Smart quotes | 240 | 0 | |
| Auto-space replacement by punctuation | 2 | 0 | plus 240 for the unclosed-quote scan |
| Comma space, French spacing | 16 | 0 | |
| Autocorrect hand-off (legacy substitution table) | 100 | 0 | |
| Autocorrect undo on Backspace | corrected word length + 2 | 0 | |
| Auto-capitalization | whole document via the extracted-text request; falls back to 200 before, 200 after, plus the selected text | | selection is treated as removed |
| Auto-cap suppression key | 200 | 1 | |
| Accepting a suggestion | 64 | 64 | word span is found in this window |
| Telex rewrite | 64 | 0 | |
| Delete last word | 100 | 0 | |
| Word-wise cursor and selection moves | whole document via extracted text; falls back to 1000 before and 1000 after | | |
| Character-wise selection moves | whole document via extracted text; falls back to 1000 before and 1 after | | |
| Forward delete at line start | 1 | 0 | |
| Add-word auto-space | 0 | 1 | |
| Auto-replace hard-boundary check | 32 | 0 | |

If the app returns nothing for a read (a null answer, which terminal emulators, some web
views and remote-desktop apps do), the feature that needed the read does not run: no
double-space period, no smart punctuation, no auto-cap (the Shift one-shot the keyboard had
requested is cleared), no word-wise moves. The character itself is still committed. When the
extracted-text request fails but the before/after reads succeed, selection helpers estimate the
cursor position as "length of the text before the cursor", which is wrong when the field holds
more than 1000 characters before the cursor; character-wise moves then operate on the wrong
offsets. The auto-cap suppression key (section 9.5) is the concatenation of the 200 characters
before the cursor and 1 character after it; when the app returns null there is no key and
suppression cannot be recorded.

The keyboard also asks the app for selection updates. Every cursor change that is not the
one-character forward step caused by its own last commit clears the deferred-space state,
refreshes suggestions, drops the add-word candidate if the cursor left its word, and schedules a
status bar refresh (coalesced; Telegram emits one update per hardware key).

## 3. Field types

On every field start (and every restart) the keyboard classifies the field once from the
editor info the app supplies. The classification drives every switch below.

| Field | How detected | Suggestions | Autocorrect | Auto-cap | Double-space period | Variations | Other |
|---|---|---|---|---|---|---|---|
| Normal text | class TEXT, no restricting variation | on | on | on | on | on | |
| URL | variation URI (checked first) | off | off | off unless "Shift in all text fields" | off | on (browsers use the bar as search) | |
| Password | variations PASSWORD, VISIBLE_PASSWORD, WEB_PASSWORD, NUMBER_VARIATION_PASSWORD | off | off | off, always | off | on | |
| Email | variation EMAIL_ADDRESS | off | off | off unless "Shift in all text fields" | off | off (no accents in addresses) | |
| Filter (search-as-you-type lists) | variation FILTER | off | off | off unless "Shift in all text fields" | off | on | |
| Raw-mode app | package is in the user's raw-mode list and no field variation above applies | off | off | off, always | off | on | per-app document |
| Number, phone | class NUMBER or PHONE | as text | as text | as text | as text | on | every letter key types its Alt-layer character (digits) without Alt; held Ctrl with A, C, X or V always performs select-all, copy, cut or paste instead of passing the shortcut to the app |
| Date/time | class DATETIME | as text | | | | | counts as "really editable" |
| Not editable | class NULL or 0 | none | | | | | the keyboard still evaluates auto-cap if the field is marked editable at all |

Additional flags read from the same editor info:

- `TYPE_TEXT_FLAG_CAP_CHARACTERS`: Caps Lock is turned on when the field starts. No
  per-character auto-cap runs in such a field.
- `TYPE_TEXT_FLAG_CAP_WORDS`: Shift one-shot is requested whenever the cursor is at the start
  of the field or right after whitespace or boundary punctuation, and after every Space or
  Enter, regardless of the user's auto-cap settings.
- `TYPE_TEXT_FLAG_CAP_SENTENCES`: same triggers as the user's own auto-cap, but only if at
  least one of the two auto-cap settings is on; when both are off the flag is ignored.
- `TYPE_TEXT_FLAG_MULTI_LINE`: decides whether autocorrect on Enter commits the newline
  itself (section 7).
- IME action (Go, Search, Send, Next, Done, Previous, or a custom action label): Enter
  performs the action and skips autocorrect and suggestions.
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS`: the keyboard sets this flag itself on every editable
  field it starts, so the app's own spell-check underline is suppressed. It never reads the
  flag.

"Really editable" means class TEXT, NUMBER, PHONE or DATETIME. A field that is editable but
not really editable (some custom views) gets auto-cap evaluation and nothing else.

The restrictions are computed per field, not per app, except raw mode. "Shift in all text
fields" (`auto_capitalize_restricted_fields`) lifts the auto-cap restriction for URL, email and
filter fields only; password fields and raw-mode apps stay off.

## 4. Composition: what is composed and what is committed

Ordinary typing on the hardware keyboard never composes. Every character is committed to the
app the moment its key goes down, with the cursor placed after it. There is no underlined
"current word" region and nothing for the app to see as pending. The word the suggestion
strip shows is tracked by the keyboard from the characters it committed and from reads of the
field, not from a composing span.

Composition is used only in three narrow places:

1. **Variations replacement** (long-press or strip pick that swaps an already committed
   character for an accented variant): the keyboard finishes any composition the app has,
   marks the target character's span as the composing region, commits the replacement over
   it, finishes composition again, and restores the selection adjusted by the length change.
   All inside one batch edit. If the app refuses to set the composing region, nothing is
   changed.
2. **Dictation**: partial results are shown as composing text and replaced as the recognizer
   refines them; the final result is committed. Specified in the dictation document.
3. **Before every boundary**: before Space, Enter or boundary punctuation are processed the
   keyboard finishes whatever composition the app may be holding (some apps compose on their
   own), so the reads that follow see committed text. The same happens before multi-tap
   replaces its previous character, before a text expansion, before Telex rewrites a
   syllable, and before an Enter that is turned into an editor action or a plain Enter key.

Because there is no composing region, a word the user has typed is "finished" only in the
sense that a boundary was typed after it; the app already holds all of it.

## 5. Committing characters on the Titan

### 5.1 Letters

A letter key with no modifier commits the layout's lowercase letter. With Shift held, Shift
one-shot, the Shift layer latched, or Caps Lock (without Shift), it commits the uppercase
form. Shift one-shot is consumed by the first letter it capitalizes; it is not consumed by a
punctuation mark or digit. After each commit the status bar is refreshed 50 ms later.

If the layout marks the key as multi-tap (some layouts only; no default Titan layout does),
a second press of the same key within 400 ms deletes the previous character and commits the
next variant in one batch edit.

### 5.2 Punctuation and digits: the Alt layer

The Titan 2 Elite has no punctuation or digit keys (D1). Everything that is not a letter comes
from the Alt layer, in one of two ways:

- **Alt held or latched, then a letter key**: the Alt-layer character is committed directly.
  Before committing, the keyboard runs, in this order: the deferred-space check (6.6), French
  spacing (6.5), auto-space replacement (6.3); if none applies it clears the auto-space flag
  and commits the character. Then the "Alt character inserted" follow-up runs (5.4).
- **Letter key held with no Alt**: the letter is committed immediately; if the key is still
  down after the long-press threshold (`long_press_threshold`, default 500 ms, clamped to
  50..1000 ms) the letter is deleted and the Alt-layer character is committed with exactly the
  same three checks and the same follow-up. Releasing the key before the threshold leaves the
  letter and notifies suggestion tracking of it. This is the default long-press mode ("alt");
  other modes (variations, Sym pages, Shift) are in the layers document.

Alt+Space always commits a plain space (this blocks Android's symbol picker). "Release Alt
with Space" (`clear_alt_on_space`, default on) ends an Alt one-shot or latch when Space or
Enter is pressed; with `alt_latch_stays_on_space` (default off) a latch survives and only the
one-shot is dropped.

### 5.3 Sym pages and chords

Characters chosen on a Sym page or through a Sym+key chord are committed as plain text with
no spacing or capitalization logic at all: no deferred space, no auto-space replacement, no
comma space, no boundary hand-off. Emoji picked from the Sym emoji page likewise.

### 5.4 The "Alt character inserted" follow-up

After an Alt-layer character is committed (either way in 5.2), the keyboard:

1. Marks a deferred space as pending if the character is in the "Before next text" list.
2. Refreshes the status bar.
3. If the character is an apostrophe (any variant) and the character before it is a letter
   or digit, tells suggestion tracking that the word continues (so "we'" can still become
   "we'll").
4. Else, if the character is boundary punctuation, hands the text before the cursor to the
   legacy autocorrect substitution engine (section 14) with the punctuation already in the
   field; comma spacing and French spacing may run inside that hand-off.
5. Else, if the character is a letter (a variation replacing a letter), tells suggestion
   tracking about it.
6. Otherwise (a digit or symbol), resets the tracked word.

Note that Alt-typed punctuation therefore never reaches the suggestion engine's
auto-replace; only Space and Enter do on the Titan. Section 14 has the full boundary contract.

### 5.5 Every commit of a non-Alt character

For a key pressed with neither Alt nor Ctrl active, before any of the smart features below
run, the keyboard checks the deferred space (6.6): if one is pending and the typed character
is not in the no-space-before set, a space is committed first, the tracked word is reset,
and if the typed character is a letter the auto-cap rules are re-evaluated so the letter after
"? " gets Shift one-shot.

## 6. Spaces and punctuation

### 6.1 A plain Space with suggestions active

"Suggestions active" means the field is not restricted (section 3). Whether the user's
suggestions switch is on does not matter for this path.

1. Double-space period check (6.7). If it fires, stop.
2. Spaced hyphen to dash (6.8). If it fires, stop.
3. Mid-word quote (6.9), then smart quotes (6.10). If either fires, stop.
4. Auto-cap after punctuation and after Enter are evaluated (section 9), and field cap flags
   may request Shift one-shot.
5. The space is handed to the suggestion engine as a boundary. Whatever the engine decides
   (replace the word or not), the engine itself makes sure the field ends with a space: if the
   text before the cursor already ends with a space, it commits nothing; otherwise it commits
   one space, re-reads, and if the app still shows no trailing space sends a Space key down/up
   pair as a last resort. In every case where a trailing space now exists the auto-space flag
   is set. Comma space and French spacing take precedence for the boundary character but do
   not apply to a plain space.
6. The key is consumed. The app never sees a Space key event.

Consequence: two consecutive spaces cannot be typed in an unrestricted field. The second
Space (outside the 500 ms double-space window, or when the double-space period is off)
finds a trailing space and commits nothing. This has not been confirmed on the device; it
follows from the trailing-space rule and needs device evidence.

### 6.2 A plain Space with suggestions disabled (restricted field)

Steps 1 to 4 as above, except that double-space period is off in restricted fields. Then the
legacy autocorrect hand-off runs (section 14) when autocorrect is enabled; if it corrects the
word it also commits the space and sets the auto-space flag (unless the corrected word ends
in an apostrophe, in which case no space is added). If it does nothing, comma space cannot
apply to a space, and the key falls through to the system, which delivers a normal Space key
event to the app. No auto-space flag is set. Two consecutive spaces work here.

### 6.3 Auto-space and its replacement by punctuation ("Remove before")

An auto-space is set after: accepting a suggestion from the strip or by trackpad swipe
(unless the replacement ends in an apostrophe, or the character after the cursor is already
whitespace); an auto-replace or autocorrect that committed a space; adding a word from the
strip (a space is committed unless the next character is whitespace or boundary punctuation);
comma space (6.4); and, per 6.1, every user-typed space in an unrestricted field.

When the flag is set and the next character typed is in the "Remove before" list
(`auto_space_punctuation`, default empty), and the two characters before the cursor are a
letter or digit followed by a space, the space is deleted and the punctuation plus a space is
committed in one batch edit: "hello " + "," gives "hello, ". The flag is cleared. If the two
characters do not match (no space, or a space after another space or punctuation), nothing is
replaced and the character goes through the normal path.

Special cases:

- A straight double quote `"` in the list is only treated as closing when the current line
  (text after the last newline, excluding the space itself, within 240 characters) contains an
  unclosed opening quote. An opening quote is one at line start or preceded by whitespace,
  an opening bracket, a guillemet, a low or high curly quote, or a dash. If there is no
  unclosed quote the space is kept, the flag is cleared, and the quote is committed after the
  space: "Ceci est un guillemet " + `"` stays "Ceci est un guillemet "". A stray attached
  quote earlier in the line ("Erster Versuch" attached to a word) does not count as opening.
- Brackets `( ) [ ] { }` typed while the flag is set and not in the list clear the flag and
  are committed normally after the space: an accepted word followed by ":" and ")" gives
  "text :)" with the default (empty) list, so ASCII smileys survive. Adding ":" to the list
  gives "text: )".
- Backspace clears the flag. Letters do not clear it (so a letter key held into a long-press
  punctuation can still replace the space), but by then the two-character check fails
  anyway because the letter sits between the space and the cursor.
- The flag is also cleared before any Alt-layer commit that did not replace the space, by
  the double-space period, by French spacing, and on Backspace.

### 6.4 Comma space (`comma_space`, default off)

Applies to the comma only, in both the Alt path and the boundary hand-off:

| Text before cursor | Result |
|---|---|
| "Hi" | "Hi, " |
| "Hi " (one or more spaces) | spaces removed, "Hi, " |
| "Hi," (comma already committed by the Alt path) | "Hi, " |
| "Hi ," (space before an already committed comma) | "Hi, " |
| "Hi, " (already spaced) | unchanged, flag set |

In every case the auto-space flag is set afterwards, so a following "Remove before"
character folds into it. Periods and other marks are not affected. Comma space runs before
the "Remove before" replacement when both could apply.

### 6.5 French punctuation spacing (`french_punctuation_spacing`, default off, no UI)

For `? ! ; :` the keyboard removes any spaces (ordinary, no-break U+00A0, narrow no-break
U+202F) directly before the cursor and commits a narrow no-break space U+202F followed by the
mark, provided the text before those spaces is not empty and does not end in whitespace. The
auto-space flag is cleared. "bonjour" + "?" gives "bonjour" + U+202F + "?". With
`french_punctuation_only_french` (default off) it applies only while the active input
language is French (language code "fr", parsed from the current subtype's locale). The two
switches lost their rows in the 2026-08-21 settings redesign and can now only be set by
backup import or a stale preference; the label strings ("French punctuation spacing", "Only
for French layouts") still exist.

### 6.6 Deferred space ("Before next text", `space_after_punctuation`, default empty)

After a character in this list is committed, the keyboard remembers that a space is owed but
does not insert it. It is inserted, as its own commit, immediately before the next committed
text whose first character is not whitespace and not in the no-space-before set
`. , ; : ! ? / \ ) ] } » ›`. If the next character is in that set, the debt is kept (so "?"
then "!" then "W" gives "?! W"). If the next character is whitespace the debt is cancelled
without inserting anything ("!" then Space gives "! ", not "!  "). Enter, Backspace, leaving
the field, and any cursor move other than the one-step forward caused by the keyboard's own
commit cancel the debt. Sending a message therefore leaves no trailing space.

The debt is set by: the non-Alt character path, the Alt path (both short and long press),
and the software keyboard path. It is checked by the same three paths. Sym page characters
neither set nor honor it.

### 6.7 Double-space period (`double_space_to_period`, default on)

Two Space presses within 500 ms (measured from the first Space key-down) convert the trailing
space to ". ":

- Requires the field to allow it (not restricted) and the text before the cursor to end in
  exactly one space (the character before the space is not a space), or, when an auto-space
  is pending, in exactly two spaces (an auto-space plus the first typed space) with no third.
- Does not fire if the text already ends in sentence punctuation before the space(s).
- Deletes the one or two spaces, commits ". ", clears the auto-space flag, and evaluates
  auto-cap (section 9) so the next letter is capitalized when "Capitalize after sentence end"
  is on.
- The Space timer is reset by any other key; a Space arriving 500 ms or more after the last
  one starts a new window. When the feature is off the timer is not kept at all.

The first Space of the pair is processed normally (6.1 or 6.2). Result for "hello", Space,
Space: "hello. ".

### 6.8 Spaced hyphen to dash (`spaced_hyphen_to_en_dash`, default off)

When Space is pressed and the text before the cursor ends in " -" (space, hyphen), and the
text before that pair is not blank on the current line, the hyphen is replaced by an en dash
U+2013 (`spaced_hyphen_dash_style` = "en_dash", default) or an em dash U+2014 ("em_dash")
followed by a space: "hello -" + Space gives "hello – ". A hyphen at line start ("  -")
stays a hyphen, so list markers survive. Off in restricted fields.

### 6.9 Mid-word quote to apostrophe (`mid_word_quote_to_apostrophe`, default off)

When a letter is typed and the two characters before the cursor are a letter followed by a
straight double quote, the quote is replaced by an apostrophe and the letter committed:
"qu"" + "o" gives "qu'o"; "dé"" + "à" gives "dé'à". The replacement waits for the letter:
typing the quote alone leaves it. A quote at word start (""" + "w") stays a quote, and a
quote followed by punctuation ("qu"" + ",") stays a quote. Works when editing inside a word
("qu"|n" + "o" gives "qu'on"). Suggestion tracking is told about the apostrophe and the
letter. Exists because the Titan 2 (non-Elite) put the double quote on Alt+K where the
apostrophe sits on the Elite (D2). Off in restricted fields.

### 6.10 Smart quotes (`smart_quotes`, default off; `smart_quotes_style`, default "german_guillemets")

When the typed character is a single trailing delimiter (whitespace, or one of
`- – — . , ; : ! ? ) ] } » ›`) and the text before the cursor ends in a straight double
quote, the keyboard looks back up to 240 characters for the matching opening quote (a quote
at text start or preceded by whitespace, an opening bracket, a guillemet, a curly quote or a
dash). The quoted span must be non-blank and contain no newline and no other quote. The
whole span, quotes included, is deleted and re-committed with the style's pair, followed by
the delimiter. Styles: "german_guillemets" » «, "french_guillemets" « », 
"french_guillemets_narrow_spaced" "« " and " »" (ordinary spaces inside), "german_low_high"
„ “, "english_curly" “ ”. Examples: ""Hallo"" + Space gives "»Hallo« "; "Sogenannter
"Hooligang"" + "-" gives "Sogenannter »Hooligang«-"; "foo"bar"" + Space is unchanged (the
first quote is attached to a word). Typing the closing quote itself changes nothing; the
conversion waits for the delimiter. Off in restricted fields.

### 6.11 Word boundaries for tracking

The suggestion strip's notion of the current word uses the boundary definition of section 1.
When the user accepts a suggestion the word span is found by walking left from the cursor
over non-boundary characters (max 64) and right over non-boundary characters (max 64); the
whole span is deleted and replaced, casing applied from the typed word (details in the
autocorrect document), Shift one-shot consumed if it was set, and a space appended unless the
replacement ends in an apostrophe (strip and trackpad) or the next character is already
whitespace (strip only).

## 7. Enter

Enter first goes through the per-app Enter behavior (send, newline, Shift-newline; per-app
document). What remains here is the text-level part:

- Before any Enter handling the deferred-space debt is cancelled and a Shift one-shot is
  consumed (a Shift-Enter must not capitalize the next line by accident).
- When Enter is turned into a newline by the app behavior, the keyboard finishes composition,
  commits "\n" as text, evaluates auto-cap for the new line, and resets the tracked word.
- When Enter is turned into an editor action, the keyboard finishes composition, evaluates
  auto-cap, then performs the action. Autocorrect and suggestions are skipped.
- When the app has an IME action (Go, Search, Send, Next, Done, Previous, or a custom
  label) and nothing above consumed the key, the text pipeline lets the key fall through to
  the system untouched: no autocorrect, no auto-replace.
- Otherwise, in a restricted field with autocorrect on, the legacy substitution engine runs
  with "\n" as the boundary; on a multi-line field it also commits the newline itself and the
  key is consumed; on a single-line field the correction is applied and the key still falls
  through so the app receives Enter.
- Otherwise the suggestion engine runs with "\n" as the boundary; if it committed the
  newline (it does whenever it ran with a live connection, replacement or not) the key is
  consumed so the app does not get a second newline.

## 8. Backspace and deleting

Backspace with nothing special pending is not handled by the keyboard: it falls through to
the system, which delivers the DEL key to the app; the app deletes one character or the
selection. Before falling through the keyboard does the following, in order:

1. Cancels the deferred-space debt and clears the auto-space flag.
2. If text is selected, none of the forward-delete alternatives below apply.
3. **Shift+Backspace** (`shift_backspace_delete`, default off) or **Alt+Backspace**
   (`alt_backspace_delete`, default off, Alt held, latched or one-shot): deletes one
   character after the cursor instead, and consumes the key.
4. **Backspace at line start** (`backspace_at_start_delete`, default off), with neither
   Shift nor Alt: if the app reports zero characters before the cursor, deletes one character
   after it and consumes the key. (It checks the field start, not the line start, despite the
   label.)
5. **Undo an auto-replace** (dictionary-based, `auto_replace_on_space_enter` on): if the last
   thing the keyboard did was replace a word, and the text before the cursor still ends in the
   replacement optionally followed by whitespace or boundary punctuation, the replacement and
   that trailing boundary are deleted and the original word is re-committed with no space; the
   original is remembered as rejected so it is not replaced again until the user types a new
   letter. Key consumed.
6. **Undo a legacy autocorrect** (autocorrect on): same shape, using the legacy engine's
   last correction. Key consumed; tracked word reset.
7. Otherwise the tracked word drops its last character (suggestions refresh) and the key
   falls through.

Ctrl+Backspace (Fn+Backspace on a remapped Titan, or Ctrl latched): with a selection, the
selection is replaced by nothing; without one, the last word before the cursor is deleted (the
whitespace after it, then the run of non-whitespace before that, within 100 characters).
Deleting a selection this way and deleting a word are both consumed.

Swipe-to-delete (`swipe_to_delete`, default off; provider `swipe_to_delete_provider`,
default "native_ime"): with the "titan2_keycode" provider, keycodes 322 and 404 (the Titan
keyboard's swipe gesture, D4) delete the last word the same way; with the feature off or the
other provider those two keycodes are swallowed so the app never sees them. The native
provider is the screen trackpad's swipe, specified in the trackpad document.

Backspace on an auto-inserted space is nothing special: the space is one committed character
like any other and the app deletes it; the keyboard only forgets the auto-space flag and the
deferred debt.

## 9. Auto-capitalization

Auto-cap never types an uppercase letter by itself. It requests a Shift one-shot; the next
letter is committed uppercase and the one-shot is consumed. The status bar shows the armed
Shift.

### 9.1 When it is evaluated

- Field start and restart (after resetting any existing Shift one-shot).
- Every selection or cursor update from the app.
- After Space (only if Shift one-shot is not already armed), after Enter, after the
  double-space period, after a deferred space was inserted before a letter, and after a
  variation or Alt character (through the status bar refresh path).
- When accepting a suggestion at a position that would be capitalized, the suggestion's first
  letter is uppercased instead of arming Shift.

### 9.2 The decision

With the field's own cap flags checked first (section 3): CAP_CHARACTERS wins and disables
per-letter auto-cap; CAP_WORDS arms Shift at every word start and ignores user settings;
CAP_SENTENCES uses the rules below but only if at least one user setting is on. Then, if the
field is restricted for auto-cap, the one-shot is cleared and nothing is armed. Otherwise:

- "Capitalize at text start" (`auto_capitalize_first_letter`, default on): arm when the text
  before the cursor (selection excluded) is empty, or ends in a newline.
- "Capitalize after sentence end" (`auto_capitalize_after_period`, default on): arm when
  the text before the cursor is a sentence end followed by at least one whitespace character.
  ". " arms; "." without a space does not; "..." does not; "! " and "? " do.
- Otherwise clear the one-shot, but only if it was auto-cap that armed it. A Shift the user
  pressed is left alone.

A tab or other whitespace after the mark counts. Text after the cursor is read but does not
change the decision.

### 9.3 Interaction with Shift

- Shift one-shot armed by auto-cap and Shift pressed by the user are the same one-shot; the
  keyboard remembers which of the two armed it so that a context change clears only the
  automatic one.
- If the user taps Shift while auto-cap has it armed (turning it off), the keyboard records
  the current cursor context (200 characters before, 1 after) as suppressed. Auto-cap will
  not arm again while that exact context recurs, which covers the selection updates that
  follow immediately. The suppression is cleared when a new field starts (not on a restart of
  the same field). Every empty field looks the same ("|"), so the suppression is per field
  session, not per text.
- Caps Lock does not interact: with Caps Lock on, letters are uppercase anyway.
- Enter consumes a pending one-shot before the newline is processed; the newline then
  re-arms it if "Capitalize at text start" is on.
- A letter typed with Shift one-shot armed is committed uppercase and disarms it; a digit,
  space or punctuation typed while armed leaves it armed (first-letter rule: "space or
  non-alphabetic character inserted as first character" was fixed to disarm through the
  selection update, which sees the text no longer empty).

### 9.4 Restricted fields

`auto_capitalize_restricted_fields` (default off, shown only while "Capitalize at text
start" is on): when on, URL, email and filter fields get auto-cap like normal fields.
Password fields and raw-mode apps never do.

### 9.5 Suggestions and casing

When the strip shows next-word or sentence-start suggestions at a position where auto-cap
would arm, the suggestions are displayed and inserted with a capital first letter. Accepting
a suggestion consumes an armed one-shot.

## 10. Selection and cursor shortcuts

All of these live on the Ctrl layer (Fn on a Titan with Fn remapped to Ctrl, or the Ctrl
latch and nav mode; keys document) and on Sym chords. The default Ctrl map (asset
`common/ctrl/ctrl_key_mappings.json`, replaceable by the user's nav-mode mapping file):

| Key | Action | Behavior |
|---|---|---|
| A | select_all | system select-all context action |
| C / X / V / Z | copy / cut / paste / undo | system context actions |
| W / R | expand_selection_left / right | move the selection's moving edge one character; a fresh selection anchors at the cursor |
| U / I | expand_selection_word_left / right | move the moving edge to the previous word start / next word end |
| N / M | move_word_left / right | collapse and move the cursor to the previous word start / next word start; with Shift held, expand instead |
| S / D / E / F, J / K / L | DPAD_LEFT / DOWN / UP / RIGHT | sent as key events; with Shift held, sent with Shift meta so the app extends the selection |
| Y / H | PAGE_UP / PAGE_DOWN | key events, Shift-aware |
| Q | ESCAPE | key event |
| T | TAB | key event |
| O | DPAD_CENTER | key event |
| G | none | falls through |
| P | toggle_minimal_ui | status bar |
| B | command | toggle software keyboard mode |

Other mapping types: "page_start" and "page_end" send MOVE_HOME / MOVE_END with Ctrl (and
Shift when held); "native_ctrl" passes the original Ctrl combo to the app; "keycode" may also
name MOVE_HOME, MOVE_END, FORWARD_DEL. An unknown action or keycode falls through to the app.
After DPAD, HOME, END and PAGE keys the status bar is refreshed 50 ms later.

Selection semantics:

- Word boundaries for these moves are whitespace only. "Previous word start" skips trailing
  whitespace then the word; "next word start" skips the word then whitespace; "next word end"
  skips whitespace then the word. In "alpha beta gamma" with the cursor at 16, word-left goes
  to 11; from 0, word-right goes to 6; selecting word-right from 0 selects 0..5, then 0..10.
- The anchor of a selection is remembered while the selection matches what was last set;
  reversing direction shrinks the selection back toward the anchor, and reaching the anchor
  collapses it ("alpha", expand-left from 5 gives 4..5; expand-right then gives 5..5). If
  the app changes the selection itself the remembered anchor is dropped and the next expand
  assumes the anchor is the end (moving left) or the start (moving right).
- Character-wise expansion refuses to move before 0 or past the text length. Word-wise
  expansion without extracted text is not possible and does nothing; word-wise cursor moves
  fall back to the before/after reads (1000 each).
- Collapsing: moving the cursor left with a selection collapses to the selection start;
  right collapses to the end.

When Ctrl is physically held (the Fn burst) and nav mode for held Ctrl is off, the keyboard
prefers the app's own shortcuts and passes the combination through untouched, except in
number and phone fields where select-all, copy, cut and paste are forced (D5 background). The
Ctrl one-shot and latch use the map above. Layout-aware shortcuts (`layout_aware_ctrl_shortcuts`,
default off; keys document) resolve the letter through the active layout first.

Sym edit shortcuts (`sym_edit_shortcuts`, default on, on the SYM screen): in a text field,
Sym+C, Sym+V, Sym+X and Sym+A perform copy, paste, cut and select-all through the system
context actions before any app shortcut or Sym chord sees the key. Off, the four chords go
back to the Sym layer. Added in 1.2.3 because Ctrl on the Titan is the Fn key only after a
remap (D5).

## 11. Vietnamese Telex

Active only while the layout named `vietnamese_telex_qwerty` (asset
`common/layouts/vietnamese_telex_qwerty.json`) is selected. On every letter key with no Alt
and no Ctrl, no repeat, after the smart features above have had their turn and before the
character is committed normally: the keyboard reads up to 64 characters before the cursor,
takes the trailing run of letters as the syllable, and applies the Telex key:

- Shape keys `a e o` after the same base vowel add a circumflex (ca + a gives câ, de + e
  gives dê, mo + o gives mô); `w` adds a breve to a (trang + w gives trăng) and a horn to o
  or u (mo + w gives mơ, tu + w gives tư); `uo` + w becomes ươ as a cluster (tuo + w gives
  tươ, tuong + w gives tương); `d` after d gives đ; a further d after đ appends a literal d.
  A shape key only applies when the letters after the target vowel form a valid Vietnamese
  tail (vowels only, or a coda from c ch m n ng nh p t).
- Tone keys `s f r x j` place acute, grave, hook, tilde, dot below on the tone-bearing vowel
  (ta + s gives tá; tá + f gives tà). Placement: the single vowel; else, ignoring the u of
  qu- and the i of gi-, a shaped vowel (ơ of ươ preferred, else the last shaped one); else
  the middle vowel of a uy- triphthong; else o of oa/oe; else the vowel before a final i, y
  or u (except uy, which tones the y); else the last vowel. Examples: hoa + f gives hòa,
  qua + s gives quá, gia + f gives già, giai + r gives giải, thuy + s gives thuý, huê + s
  gives huế, tươ + r gives tưở. A tone key is refused when the syllable has two separated
  vowel groups.
- Pressing the same tone key again removes the tone and types the key literally (hẻ + r
  gives her). Pressing a shape key on an already shaped vowel removes the shape and types
  the key (xô + o gives xoo, mơ + w gives mow). `z` strips every diacritic in the syllable
  (tưở + z gives tuo).
- Case is preserved (TA + S gives TÁ, D + D gives Đ, Ô + O gives OO).
- Foreign sequences are left alone: tel + e, tele + x, Tele + x are not rewritten. Any key
  outside the Telex set, or a syllable that would not change, falls through to normal
  typing.

A rewrite finishes composition, then deletes the whole syllable and commits the rewritten one
in a single batch edit, consumes a Shift one-shot, and refreshes the status bar after 50 ms.

## 12. The software keyboard text handler

The on-screen keyboard (2.x only) feeds its typed strings into a reduced pipeline: Space
first tries text expansion, then the deferred-space check, then double-space period, then the
suggestion boundary (when suggestions are allowed in the field), else commits a plain space
and skips the next selection update; any other string runs the deferred-space check, is
committed, tracked, and refreshes the status bar and expansion state. No smart quotes, no
dash, no mid-word quote, no French spacing, no Telex. 3.0 drops it.

## 13. Timings and limits

| Value | Where |
|---|---|
| 500 ms | double-space window |
| 500 ms default, 50..1000 ms | long-press threshold for Alt-layer characters |
| 400 ms | multi-tap window |
| 50 ms | delay before the status bar refresh after a commit or cursor key |
| 100 | characters read for double-space, dash, autocorrect hand-off, delete last word |
| 240 | characters read for smart quotes and the unclosed-quote scan |
| 200 + 200 | auto-cap fallback reads |
| 200 + 1 | auto-cap suppression key |
| 1000 + 1000 | selection fallback reads |
| 64 + 64 | suggestion acceptance word span |
| 64 | Telex read |
| 32 | auto-replace hard-boundary read |
| 16 | comma space and French spacing reads |
| 3 | suggestions shown |
| 300 ms, then up to 10 retries every 100 ms | inserting a dictation result when the connection is not back yet |

## 14. The boundary with autocorrect

Two engines exist; which one a boundary reaches depends on the field and the key.

**Suggestion engine (auto-replace)**: reached by Space and Enter in unrestricted fields (and
by punctuation typed through a layout that has punctuation keys, which no Titan layout has).
Hand-off: the key code and event, the live connection. Before handing off, the tracked word
is re-synchronized from the text before the cursor. The engine receives: the word before the
cursor as tracked, the boundary character, and the settings snapshot (autocorrect on,
suggestions on, accent matching, auto-replace on space/enter with its maximum distance,
keyboard proximity, edit-type ranking, French spacing decision, comma space, the "Remove
before" list). It returns: whether it replaced the word, whether it committed the boundary,
and the replacement. Text-input's obligations afterwards: consume the key when the boundary
was committed, mark the auto-space (the engine does this itself for a space), trigger a
haptic on replacement, and treat the replacement as the add-word candidate.

**Legacy substitution engine (autocorrect)**: reached by Alt-layer boundary punctuation on
the Titan, by Space and Enter in restricted fields, and by punctuation from a software or
layout key when suggestions are disabled. Hand-off: up to 100 characters before the cursor
plus the boundary character (already committed for the Alt path, pending for the others),
whether the boundary should be committed by the engine, and a callback answering "is this
word in an active dictionary". It returns the word to replace and its replacement, or
nothing. When it returns a pair that differ, text-input deletes the word (plus the already
committed boundary if it is the last character), commits the replacement, records it for
Backspace undo, triggers a haptic, and commits the boundary again: a space (marked
auto-space, skipped after a replacement ending in an apostrophe), a newline, or the
punctuation through French spacing, then comma space, then plain. If it returns nothing,
comma space may still run for a comma. Before the hand-off, if an auto-space is pending and
the punctuation is in the "Remove before" list, the replacement of 6.3 runs instead and the
engine is not consulted; brackets clear the flag and skip the engine.

Undo, rejected words, casing of replacements, confidence and the dictionaries are in the
autocorrect document.

## 15. Settings

All rows live on the "Smart Features" screen (Settings category label "Smart Features")
unless the screen column says otherwise. "Advanced" is the collapsed section at the bottom of
that screen.

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `auto_capitalize_first_letter` | boolean | true | arm Shift at field start and after a newline | Smart Features, Capitalization | Capitalize at text start |
| `auto_capitalize_after_period` | boolean | true | arm Shift after ". ", "! ", "? " | Smart Features, Capitalization | Capitalize after sentence end |
| `auto_capitalize_restricted_fields` | boolean | false | auto-cap also in URL, email, filter fields (never password or raw-mode) | Smart Features, Advanced (only while the first row is on) | Shift in all text fields |
| `double_space_to_period` | boolean | true | two spaces within 500 ms become ". " | Smart Features, Spacing and punctuation | Double Space inserts period |
| `clear_alt_on_space` | boolean | true | Space or Enter ends Alt one-shot and latch | Smart Features, Keyboard behavior | Release Alt with Space |
| `alt_latch_stays_on_space` | boolean | false | with the row above, keep an Alt latch and drop only the one-shot | keys document | see keys document |
| `auto_show_keyboard` | boolean | true | create the input view when a really editable field starts (status bar surface) | Smart Features, Keyboard behavior | Show keyboard automatically |
| `physical_keyboard_currency_symbol` | string | "€" | the Alt-layer character of KEYCODE_GRAVE; no effect on the Titan (D3) | Smart Features, Currency Symbol | Currency Symbol |
| `shift_backspace_delete` | boolean | false | Shift+Backspace deletes forward | Smart Features, Delete | Shift + Backspace |
| `alt_backspace_delete` | boolean | false | Alt+Backspace deletes forward | Smart Features, Delete | Alt + Backspace |
| `backspace_at_start_delete` | boolean | false | Backspace with nothing before the cursor deletes forward | Smart Features, Advanced | Backspace at line start |
| `auto_space_punctuation` | string, subset of `.,;:!?\/")]}` in that order | "" | "Remove before" list (6.3) | Smart Features, Advanced, Punctuation spacing dialog, column "Remove before" | Punctuation spacing |
| `space_after_punctuation` | string, same alphabet | "" | "Before next text" list (6.6) | same dialog, column "Before next text" | Punctuation spacing |
| `comma_space` | boolean | false | space after comma, preceding space removed | Smart Features, Advanced | Space after comma |
| `spaced_hyphen_to_en_dash` | boolean | false | " - " + Space becomes a dash | Smart Features, Advanced | Hyphen to dash |
| `spaced_hyphen_dash_style` | string: "en_dash", "em_dash" | "en_dash" | which dash | dropdown on the row above (labels "–" and "—") | Hyphen to dash |
| `mid_word_quote_to_apostrophe` | boolean | false | letter + `"` + letter becomes an apostrophe | Smart Features, Advanced | Quotes inside words |
| `smart_quotes` | boolean | false | straight pair becomes typographic pair on the next delimiter | Smart Features, Advanced | Quotation mark style |
| `smart_quotes_style` | string: "german_guillemets", "french_guillemets", "french_guillemets_narrow_spaced", "german_low_high", "english_curly" | "german_guillemets" | which pair (labels "»...«", "«...»", "« ... »", "„...“", "“...”") | dropdown on the row above | Quotation mark style |
| `french_punctuation_spacing` | boolean | false | narrow no-break space before ? ! ; : | none since 2026-08-21 (label strings "French punctuation spacing" remain) | French punctuation spacing |
| `french_punctuation_only_french` | boolean | false | the row above only while the input language is "fr" | none | Only for French layouts |
| `sym_edit_shortcuts` | boolean | true | Sym+C/V/X/A edit instead of typing | SYM screen | Sym+C/V/X/A: copy, paste, cut, select all |
| `swipe_to_delete` | boolean | false | keycodes 322/404 or the trackpad swipe delete the last word | trackpad document | see trackpad document |
| `swipe_to_delete_provider` | string: "titan2_keycode", "native_ime" | "native_ime" | which swipe source | trackpad document | see trackpad document |
| `long_press_threshold` | long, 50..1000 | 500 | hold time before a letter becomes its Alt-layer character | layers document | see layers document |
| `auto_replace_on_space_enter` | boolean | false | dictionary auto-replace on Space/Enter and its Backspace undo | autocorrect document | |
| `auto_correct_enabled` | boolean | true | legacy substitution engine and text replacements | autocorrect document | |
| `suggestions_enabled` | boolean | true | strip suggestions and word tracking; does not change the Space path of 6.1 | autocorrect document | |

The "Punctuation spacing" dialog shows one row per candidate with two checkboxes ("Remove
before", "Before next text"), a Reset button that empties both lists, and a help dialog
("How punctuation spacing works") whose text promises that "Remove before" only removes a
space PhysiBoard inserted, not one typed manually. Per 6.1 that promise holds only in
restricted fields; in normal fields every typed space after a word is marked. The row summary
reads "Remove before: . , · Before next text: ?" or "Off".

## 16. Titan 2 Elite facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The Titan 2 Elite keyboard has letter keys, Space, Enter, Backspace, two Shifts, Alt, Sym (keycode 63, scancode 253) and Fn (scancode 251), and no punctuation or digit keys; every punctuation mark and digit comes from the Alt layer: Q W E R S D F Z X C give 0 1 2 3 4 5 6 7 8 9; T ( Y ) U _ I - O + P @ A * G / H : J # K ' L " V ? B ! N , M . | docs/titan2elite/DEVICE.md scancode map; asset `devices/titan2elite_qwerty/alt_key_mappings.json` |
| D2 | The Titan 2 (non-Elite) Alt layer differs: K gives `"` and M gives `?`; the mid-word quote feature and its end-to-end tests were written for that layout | asset `devices/titan2/alt_key_mappings.json`; tests named titan2AltQuote and titan2AltQuestion; commit 7e91a77 "Add configurable mid-word quote replacement" |
| D3 | Neither Titan Alt map contains KEYCODE_GRAVE, so the "Currency Symbol" setting changes nothing on Titan hardware; it exists for keyboards with a dedicated currency key | both Alt map assets; commit ec353b4 "Add hardware currency symbol selector" |
| D4 | The Titan 2 keyboard swipe gesture arrives as keycode 322, and on some units as 404; the "titan2_keycode" provider maps both to delete-last-word | commits c60bd38 "Implement swipe to delete feature" (2025-11-13) and 625b185 "Support alternate swipe delete keycode", closes #174 |
| D5 | Copy and paste live on Ctrl, which on the Titan is the Fn key only after the user remaps it; Sym+C/V/X/A were added so editing does not depend on the remap | PHYSIBOARD_CHANGES.md 1.2.3; commit 54b5fdc |
| D6 | Fn sends no key-up and arrives as Ctrl with scancode 251 in a repeat burst; a quick Fn+X chord delivers only the X key with META_CTRL_ON. Ctrl-layer selection shortcuts on the Titan therefore see "Ctrl physically pressed" from the letter's meta state, and with nav-mode-for-held-Ctrl off they pass through to the app instead of running the map in section 10 | docs/titan2elite/DEVICE.md, Fn event delivery model |
| D7 | Terminal-style web apps (WebAPKs hosted by Chrome) return nothing for surrounding-text reads, so every keystroke looked like a sentence start and auto-cap produced "lIKE tHIS"; the fix matches the host browser for raw mode | PHYSIBOARD_CHANGES.md 1.0.3 |
| D8 | Raw-mode apps keep auto-cap off even when "Shift in all text fields" is on | commit 863f2f4 "keep auto-capitalization off in raw-mode apps"; PHYSIBOARD_CHANGES.md 1.0.3 and 1.0.0 |

## 17. Edge cases, quirks, known bugs

| Situation | Behavior | Why |
|---|---|---|
| Two spaces typed in a normal field, more than 500 ms apart or with double-space off | only one space ends up in the field | the suggestion boundary ensures a trailing space and commits nothing when one exists; needs device confirmation |
| Space typed in a normal field, then `,` from the Alt layer with "," in "Remove before" | "word, " | every typed space in a normal field is flagged as auto-space, contrary to the help text |
| Same in a URL or password field | "word ," | restricted fields let Space fall through to the app; no flag |
| Accepted suggestion, then ":" and ")" with default lists | "word :)" | brackets clear the flag and keep the space so smileys survive (commit 228d8dd) |
| Accepted suggestion, then `"` with `"` in "Remove before", no open quote on the line | space kept, quote after it | a quote without an open partner is an opening quote (commit 44137c0) |
| Suggestion whose replacement ends in an apostrophe (Italian elisions like "dell'") | no space appended, no auto-space flag | commit 228d8dd; commit 386de68 |
| Deferred space pending, user types Enter or moves the cursor | debt cancelled, no trailing space | so a sent message does not end in a space |
| Deferred space pending, user types "!" then "W" | "?! W" | punctuation in the no-space-before set keeps the debt |
| Deferred space pending, user types Space | "! " | whitespace cancels the debt without duplication |
| "?" typed on Alt, "?" in "Before next text", then "w" | "? W" | the inserted space triggers auto-cap re-evaluation before the letter is committed |
| Double Space after "hello." | "hello.  " (second space normal) | sentence punctuation already present |
| Double Space right after an accepted suggestion | "word. " | the auto-space plus one typed space count as the pair |
| Space pressed 500 ms or later after the previous Space | no period | window measured between key-downs |
| Ellipsis "..." then Space | no auto-cap | a period preceded by a period is not a sentence end |
| Field starts with a selection covering everything (Ctrl+A) | auto-cap evaluates as if the selection were deleted: empty text arms Shift | selection is subtracted from the before/after reads (commit c9f2136) |
| User taps Shift to disarm auto-cap, then keeps typing | no re-arm at that cursor context until a new field | suppression keyed on 200 chars before + 1 after |
| Shift+Enter in a chat app | next line not capitalized by the stale one-shot | Enter consumes Shift one-shot first (PR #260); the newline may re-arm it |
| Field with CAP_CHARACTERS | Caps Lock on at field start, user can turn it off; never re-applied per letter | flag handled once |
| Field with CAP_SENTENCES but both user auto-cap rows off | no capitalization | user settings win over the field flag |
| Field with CAP_WORDS and both user rows off | capitalized at every word start anyway | field flag wins for CAP_WORDS |
| App returns null for text reads (terminals, remote desktops) | no smart punctuation, no auto-cap, no word moves; characters still typed | every feature is a read-then-write |
| App has more than 1000 characters before the cursor and no extracted text | character-wise selection moves use wrong offsets | fallback estimates the position as the read length |
| Word-wise selection expansion in an app without extracted text | nothing happens | the helper requires the full text |
| Selection changed by the app (touch) after a keyboard expansion | the next expansion assumes the anchor is the far edge in the direction of travel | remembered anchor is dropped when it no longer matches |
| Ctrl held (Fn burst) with S/D/E/F and nav-mode-for-held-Ctrl off | passed to the app, not DPAD | app-native shortcuts preferred when Ctrl is physical |
| Same in a number field with A/C/X/V | select-all, copy, cut, paste forced | commit 4342a26 "Fix Ctrl shortcuts in numeric fields" |
| Number field, letter key | Alt-layer digit typed without Alt | numeric fields always use the Alt map |
| Number field, Ctrl+V while Ctrl latched | paste | Ctrl has precedence over the digit mapping |
| Backspace with text selected and Shift+Backspace on | normal Backspace, selection deleted by the app | alternatives are disabled with a selection |
| "Backspace at line start" in the middle of a multi-line field | does nothing unless the cursor is at the very start of the field | it checks for zero characters before the cursor, not the line |
| Ctrl+Backspace with nothing before the cursor | consumed, nothing deleted | delete-last-word finds nothing |
| Alt-typed punctuation with suggestions on and a misspelled word before it | only the legacy substitution table can correct it; dictionary auto-replace does not run | Alt path hands off to the legacy engine only |
| Comma typed on Alt with comma space on: "Hi ," | "Hi, " | the space before the already committed comma is cleaned |
| French spacing on, "bonjour " then "?" on Alt | "bonjour" + U+202F + "?" | existing spaces are replaced by the narrow no-break space |
| French spacing on, "?" at the very start of the field or after a newline | plain "?" | nothing to attach to |
| French spacing rows missing from Settings | only reachable through a backup that carries the keys | dropped by the 2026-08-21 settings redesign (commit 2514c28); keys and labels survive |
| Smart quotes on, quote closed then a letter typed | nothing converted, letter appended | conversion waits for a delimiter |
| Smart quotes on, span contains a newline | not converted | multi-line spans are excluded |
| Mid-word quote on, quote typed after a letter and then a comma | quote kept | only a following letter triggers |
| Multi-tap layout, key held | system repeats ignored on multi-tap keys | holding must not cycle variants |
| Telex layout, "tele" + x | "telex" | foreign-word guard (commit 955989f) |
| Telex layout, đ + d | "đd" | a second d appends instead of toggling back (commit 973174d) |
| Telex rewrite with Shift one-shot armed | one-shot consumed even though the rewrite committed lowercase text | consumed unconditionally after a rewrite |
| Sym page character after an accepted suggestion | committed after the auto-space, flag untouched | Sym commits bypass all spacing logic |
| Text expansion, dictation result, clipboard paste | commit as plain text; deferred debt untouched, auto-space flag untouched | those paths do not call the spacing checks |
| Dictation result arrives while the field is gone | retried up to 10 times every 100 ms after an initial 300 ms wait, then dropped | connection may return late |
| Two Enter presses in an unrestricted multi-line field without IME action | two newlines, each committed by the keyboard | the suggestion boundary commits "\n" and consumes Enter |
| Enter in a single-line field without IME action, restricted, autocorrect corrects the word | word corrected, Enter still delivered to the app | single-line keeps the app's Enter |
| Status bar shows suggestions after the user pressed Backspace into the middle of a word | suggestions for the word before the cursor only | tracker syncs from the text before the cursor, not after |

## 18. Test cases

Each row is an initial field state (`|` marks the cursor), a key sequence, and the resulting
field text. Settings not mentioned are at their defaults; "normal field" means class TEXT
with no restricting variation and no IME action. Alt+X means the Titan Alt-layer character of
key X (D1).

| # | Field before | Input | Field after | Settings |
|---|---|---|---|---|
| T1 | `hello|` | Space, Space within 500 ms | `hello. |` | |
| T2 | `hello|` | Space, wait 600 ms, Space | `hello |` in a normal field; `hello  |` in a URL field | (see 6.1 quirk) |
| T3 | `hello.|` | Space, Space within 500 ms | `hello.  |` | |
| T4 | `hello|` | Space, Space | `hello  |` | `double_space_to_period` = false, URL field |
| T5 | `word |` with auto-space flagged | Space, Space within 500 ms | `word. |` | |
| T6 | `hello |` with auto-space flagged | Alt+N (`,`) | `hello, |` | `auto_space_punctuation` = "," |
| T7 | `hello |` with auto-space flagged | Alt+N (`,`) | `hello ,|` | `auto_space_punctuation` = "" |
| T8 | `text |` flagged | Alt+H (`:`), Alt+Y (`)`) | `text :)|` | defaults |
| T9 | `text |` flagged | Alt+H (`:`), Alt+Y (`)`) | `text: )|` | `auto_space_punctuation` = ":" |
| T10 | `Ceci est un guillemet |` flagged | Alt+L (`"`) | `Ceci est un guillemet "|`, flag cleared | `auto_space_punctuation` = `"` |
| T11 | `"bonjour |` flagged | Alt+L (`"`) | `"bonjour" |` | `auto_space_punctuation` = `"` |
| T12 | `Erster Versuch" Zweiter Versuch |` flagged | Alt+L (`"`) | `Erster Versuch" Zweiter Versuch "|` | `auto_space_punctuation` = `"` |
| T13 | `Hi|` | Alt+N (`,`) | `Hi, |` | `comma_space` = true |
| T14 | `Hi |` | Alt+N (`,`) | `Hi, |` | `comma_space` = true |
| T15 | `Hi ,|` | (comma just committed on Alt) | `Hi, |` | `comma_space` = true |
| T16 | `Hi|` | Alt+M (`.`) | `Hi.|` | `comma_space` = true (period unaffected) |
| T17 | `|` | Alt+V (`?`) | `?|`, deferred space pending | `space_after_punctuation` = "?!" |
| T18 | `?|` pending | W | `? W|` | `space_after_punctuation` = "?!" |
| T19 | `?|` pending | Alt+B (`!`), W | `?! W|` | `space_after_punctuation` = "?!" |
| T20 | `!|` pending | Space | `! |` | `space_after_punctuation` = "?!" |
| T21 | `!|` pending | Enter (newline app) | `!\n|` | `space_after_punctuation` = "?!" |
| T22 | `hello -|` | Space | `hello – |` | `spaced_hyphen_to_en_dash` = true |
| T23 | `hello -|` | Space | `hello — |` | same, `spaced_hyphen_dash_style` = "em_dash" |
| T24 | `  -|` | Space | `  - |` | `spaced_hyphen_to_en_dash` = true |
| T25 | `hello -|` | Space | `hello - |` | `spaced_hyphen_to_en_dash` = true, email field |
| T26 | `qu|` | Alt+L (`"`) then O | `qu'o|` | `mid_word_quote_to_apostrophe` = true |
| T27 | `qu"|` | Alt+N (`,`) | `qu",|` | `mid_word_quote_to_apostrophe` = true |
| T28 | `"|` | W | `"w|` | `mid_word_quote_to_apostrophe` = true |
| T29 | `dé"|` | à | `dé'à|` | `mid_word_quote_to_apostrophe` = true |
| T30 | `qu"|n` | O | `qu'o|n` | `mid_word_quote_to_apostrophe` = true |
| T31 | `"Hallo"|` | Space | `»Hallo« |` | `smart_quotes` = true, style "german_guillemets" |
| T32 | `"Bonjour"|` | Space | `« Bonjour » |` | style "french_guillemets_narrow_spaced" |
| T33 | `Sogenannter "Hooligang"|` | Alt+I (`-`) | `Sogenannter »Hooligang«-|` | style "german_guillemets" |
| T34 | `foo"bar"|` | Space | `foo"bar" |` | `smart_quotes` = true |
| T35 | `"Hallo"|` | A | `"Hallo"a|` | `smart_quotes` = true |
| T36 | `"Hallo|` | Alt+L (`"`) | `"Hallo"|` | `smart_quotes` = true |
| T37 | `"Hallo"|` | Space | `"Hallo" |` | `smart_quotes` = true, password field |
| T38 | `bonjour|` | Alt+V (`?`) | `bonjour` U+202F `?|` | `french_punctuation_spacing` = true |
| T39 | `bonjour |` | Alt+V (`?`) | `bonjour` U+202F `?|` | `french_punctuation_spacing` = true |
| T40 | `|` empty normal field | field start | Shift one-shot armed | |
| T41 | `Hello. |` | field start | Shift armed | |
| T42 | `Hello.|` | field start | Shift not armed | |
| T43 | `Wait...|` then Space | | Shift not armed | |
| T44 | `Hello!|` | Space | Shift armed | |
| T45 | `Hello. |` | field start | Shift not armed | `auto_capitalize_after_period` = false |
| T46 | `|` | field start | Shift not armed | `auto_capitalize_first_letter` = false |
| T47 | `|` URL field | field start | Shift not armed | |
| T48 | `|` URL field | field start | Shift armed | `auto_capitalize_restricted_fields` = true |
| T49 | `|` password field | field start | Shift not armed | `auto_capitalize_restricted_fields` = true |
| T50 | `|` normal field in a raw-mode app | field start | Shift not armed | `auto_capitalize_restricted_fields` = true |
| T51 | `hello|` Shift armed by auto-cap | Shift tap (disarm), then a selection update at the same context | Shift stays off | |
| T52 | `Hello|` | Enter (newline app) | `Hello\n|`, Shift armed | |
| T53 | `hello|` Shift armed by user | Enter (newline app) | one-shot consumed before the newline, then re-armed by the new line | |
| T54 | `|` field with CAP_CHARACTERS | field start | Caps Lock on | |
| T55 | `hello |` field with CAP_WORDS | (after Space) | Shift armed | both user rows false |
| T56 | `hello. |` field with CAP_SENTENCES | (after Space) | Shift not armed | both user rows false |
| T57 | `alpha beta gamma|` (16) | Ctrl+N | cursor 11 | |
| T58 | `|alpha beta gamma` (0) | Ctrl+M | cursor 6 | |
| T59 | `alpha beta [gamma]` selection 11..16 | Ctrl+U | selection 6..16 | |
| T60 | `[alpha] beta gamma` selection 0..5 anchored at 0 | Ctrl+I | selection 0..10 | |
| T61 | `[alpha] beta gamma` 0..5 anchored at 0 | Ctrl+U | cursor 0, no selection | |
| T62 | `alpha|` (5) | Ctrl+W | selection 4..5 | |
| T63 | `alph[a]` 4..5 anchored at 5 | Ctrl+R | cursor 5, no selection | |
| T64 | any | Sym+A, Sym+C, Sym+X, Sym+V | select-all, copy, cut, paste context actions | `sym_edit_shortcuts` = true |
| T65 | any | Sym+A | Sym chord for A | `sym_edit_shortcuts` = false |
| T66 | `hello world|` | keycode 322; keycode 404 | `hello |`; then `|` | `swipe_to_delete` = true, provider "titan2_keycode" |
| T67 | `hello world|` | keycode 322 | unchanged, key consumed | `swipe_to_delete` = false |
| T68 | `hello world|` | Ctrl+Backspace | `hello |` | |
| T69 | `hel[lo]` | Ctrl+Backspace | `hel|` | |
| T70 | `ab|c` | Shift+Backspace | `ab|` | `shift_backspace_delete` = true |
| T71 | `ab|c` | Alt (one-shot) then Backspace | `ab|` | `alt_backspace_delete` = true |
| T72 | `a[b]c` | Shift+Backspace | falls through, app deletes selection | `shift_backspace_delete` = true |
| T73 | `|abc` | Backspace | `|bc` | `backspace_at_start_delete` = true |
| T74 | `a|bc` | Backspace | falls through | `backspace_at_start_delete` = true |
| T75 | `|abc` | Shift+Backspace | falls through | `backspace_at_start_delete` = true only |
| T76 | `ab|c` | Shift+Backspace | `ab|` once, not twice | both Shift and Alt rows true, Shift held |
| T77 | `teh |` after auto-replace "teh" to "the " | Backspace | `teh|`, "teh" rejected | `auto_replace_on_space_enter` = true |
| T78 | `ca|` Telex layout | A | `câ|` | |
| T79 | `trang|` | W | `trăng|` | Telex |
| T80 | `tuong|` | W | `tương|` | Telex |
| T81 | `ta|` | S then F | `tá|` then `tà|` | Telex |
| T82 | `hoa|`, `qua|`, `gia|`, `giai|`, `thuy|`, `huê|`, `tươ|` | F, S, F, R, S, S, R | `hòa`, `quá`, `già`, `giải`, `thuý`, `huế`, `tưở` | Telex |
| T83 | `tưở|` | Z | `tuo|` | Telex |
| T84 | `hẻ|` | R | `her|` | Telex |
| T85 | `xô|`, `mơ|` | O, W | `xoo|`, `mow|` | Telex |
| T86 | `TA|`, `D|`, `Ô|`, `Ư|` | S, D, O, W | `TÁ|`, `Đ|`, `OO|`, `UW|` | Telex |
| T87 | `tel|`, `tele|`, `Tele|` | E, X, X | `tele|`, `telex|`, `Telex|` | Telex |
| T88 | `ta|` | K | `tak|` | Telex, key outside the set |
| T89 | `id|` | Space | `id |` (never "I'd") | autocorrect, suggestions and auto-replace all off |
| T90 | `id|` on-screen keyboard | Space | `id |` | same |
| T91 | `hello|` | Alt+M (`.`), Alt+M held past 500 ms on a letter key | period committed; letter deleted then period committed | |
| T92 | `dell|` accepted suggestion "dell'" | strip tap | `dell'|`, no space, no flag | |
| T93 | `wor|ld` accepted suggestion "world" | strip tap | `world |` | |
| T94 | `wor| ld` | strip tap "world" | `world| ld` (no space added, next char is whitespace) | strip only |

## 19. Keep / Drop for 3.0

| Item | Verdict | Reason |
|---|---|---|
| Commit-per-key with no composing region | keep | Apps behave best with committed text; the strip tracks the word itself |
| Finish-composition before every boundary | keep | Some apps compose on their own; cheap insurance |
| Composing-region swap for variations | keep | The only reliable way to replace one character without flicker |
| Surrounding-text reads with the sizes in section 2 | keep, unify | One read per keystroke with one window (240 before, 64 after) would replace the eleven different sizes |
| Field classification (password, URL, email, filter, number, raw-mode) | keep | Users notice the first time a password is corrected |
| Cap flags CAP_CHARACTERS / CAP_WORDS / CAP_SENTENCES | keep | Cheap and correct |
| Setting NO_SUGGESTIONS on every field | keep | Stops double underlines from the app's spell-checker |
| Alt-layer punctuation path with spacing checks | keep | It is the only punctuation path on the Titan (D1) |
| Long-press-for-Alt on a held letter | keep | Core Titan typing habit; threshold 500 ms |
| Multi-tap layouts | drop | No Titan layout uses it |
| Sym-page characters bypassing spacing | undecided | Consistent behavior would be nicer, but nobody has asked |
| Space through the suggestion boundary (6.1), including the trailing-space rule | keep, fix | Keep the flow; make a typed space after a typed space insert one, and stop flagging user-typed spaces as auto-space so the help text becomes true |
| Auto-space after suggestion, auto-replace, add-word | keep | Users expect it |
| "Remove before" list with the opening-quote and bracket rules | keep | Small, tested, avoids ":)" breakage |
| Comma space | keep | Cheap, opt-in |
| French spacing | undecided | Works, but the UI rows are gone; either restore the rows or drop the keys |
| Deferred space ("Before next text") | keep | The only thing that keeps sent messages free of trailing spaces |
| Double-space period, 500 ms | keep | Universal expectation |
| Spaced hyphen to dash | keep | Opt-in, tiny |
| Mid-word quote to apostrophe | drop | Written for the Titan 2 layout where `"` sat on Alt+K (D2); the Elite has the apostrophe there |
| Smart quotes with five styles | keep | Opt-in, tested, no device dependency |
| Enter rules (IME action, multi-line, single-line) | keep | Per-app Enter handling depends on them |
| Backspace undo of auto-replace and legacy autocorrect | keep one | Two engines with two undo paths is the 2.x accident; 3.0 has one engine |
| Shift+Backspace, Alt+Backspace forward delete | keep | Titan has no Delete key |
| Backspace at line start | keep, rename or fix | It checks the field start; either fix it to the line or label it so |
| Ctrl+Backspace delete word / delete selection | keep | Titan has no Delete key |
| Swipe-to-delete keycodes 322/404 | undecided | Needs device evidence that the Elite still emits them; the trackpad provider may be the only one in use |
| Auto-cap (both rows, restricted-fields row, suppression on manual Shift) | keep | Every rule here came from a bug report |
| Ctrl-layer selection and cursor map | keep | Titan has no arrow keys |
| Remembered selection anchor | keep | Without it, reversing direction grows instead of shrinks |
| 1000-character fallback reads | drop | Use extracted text only; apps that refuse it get no selection helpers |
| Physical-Ctrl pass-through vs mapped actions | keep | The Fn burst (D6) makes "held" ambiguous; the rule is settled |
| Sym+C/V/X/A | keep | The reason it exists (D5) does not go away |
| Currency symbol setting | drop | No grave key on the Titan (D3) |
| Vietnamese Telex | drop | Named in the rebuild plan as dropped; no Titan user |
| Software keyboard text handler | drop | 3.0 has no on-screen keyboard |
| Auto-show keyboard | drop | It creates the on-screen input view; 3.0 has only the status bar surface |
| Release Alt with Space, Alt latch stays on Space | keep | Modifier ergonomics on a keyboard with one Alt; keys document owns them |
| Dictation insert retry (300 ms + 10 × 100 ms) | keep | Dictation document |

## 20. Provenance

- app/src/main/java/brobata/physiboard/core/TextInputController.kt
- app/src/main/java/brobata/physiboard/core/AutoSpaceTracker.kt
- app/src/main/java/brobata/physiboard/core/DeferredPunctuationSpaceTracker.kt
- app/src/main/java/brobata/physiboard/core/Punctuation.kt
- app/src/main/java/brobata/physiboard/core/InputContextState.kt
- app/src/main/java/brobata/physiboard/core/AutoCorrectionManager.kt
- app/src/main/java/brobata/physiboard/core/SymLayoutController.kt (commit sites only)
- app/src/main/java/brobata/physiboard/core/suggestions/AutoReplaceController.kt (boundary, undo, hard-boundary sections)
- app/src/main/java/brobata/physiboard/core/suggestions/SuggestionController.kt (boundary, character, undo entry points)
- app/src/main/java/brobata/physiboard/core/suggestions/CurrentWordTracker.kt
- app/src/main/java/brobata/physiboard/inputmethod/AutoCapitalizeHelper.kt
- app/src/main/java/brobata/physiboard/inputmethod/TextSelectionHelper.kt
- app/src/main/java/brobata/physiboard/inputmethod/SoftwareKeyboardTextInputHandler.kt
- app/src/main/java/brobata/physiboard/inputmethod/SymEditShortcuts.kt
- app/src/main/java/brobata/physiboard/inputmethod/AddWordCommitHelper.kt
- app/src/main/java/brobata/physiboard/inputmethod/AltSymManager.kt
- app/src/main/java/brobata/physiboard/inputmethod/MultiTapController.kt
- app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt
- app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt (field lifecycle, Enter, typed-character path, selection updates, Sym chords, suggestion acceptance, dictation insert)
- app/src/main/java/brobata/physiboard/inputmethod/suggestions/SuggestionButtonHandler.kt
- app/src/main/java/brobata/physiboard/inputmethod/telex/VietnameseTelexProcessor.kt
- app/src/main/java/brobata/physiboard/data/mappings/KeyMappingLoader.kt (currency override, Ctrl mapping type)
- app/src/main/java/brobata/physiboard/TextInputSettingsScreen.kt
- app/src/main/java/brobata/physiboard/SymCustomizationScreen.kt (Sym edit shortcuts row)
- app/src/main/java/brobata/physiboard/SettingsManager.kt (keys and defaults for every row in section 15)
- app/src/main/res/values/strings.xml
- app/src/main/assets/common/ctrl/ctrl_key_mappings.json
- app/src/main/assets/common/alt/virtual_alt_key_mappings.json
- app/src/main/assets/devices/titan2/alt_key_mappings.json
- app/src/main/assets/devices/titan2elite_qwerty/alt_key_mappings.json
- app/src/main/assets/common/layouts/vietnamese_telex_qwerty.json (header)
- app/src/test/java/brobata/physiboard/core/TextInputControllerTest.kt
- app/src/test/java/brobata/physiboard/core/DeferredPunctuationSpaceTrackerTest.kt
- app/src/test/java/brobata/physiboard/core/PunctuationTest.kt
- app/src/test/java/brobata/physiboard/core/AutoCorrectionManagerTest.kt
- app/src/test/java/brobata/physiboard/core/InputContextStateTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/TextSelectionHelperWordNavigationTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterForwardDeleteAlternativesTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/InputEventRouterModifierE2ETest.kt
- app/src/test/java/brobata/physiboard/inputmethod/SoftwareKeyboardTextInputHandlerTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/SymEditShortcutsTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/DisabledSmartFeaturesRegressionTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/AddWordCommitHelperTest.kt
- app/src/test/java/brobata/physiboard/inputmethod/telex/VietnameseTelexProcessorTest.kt
- app/src/test/java/brobata/physiboard/TextInputSettingsScreenTest.kt
- docs/spec/README.md
- docs/plans/rebuild-from-scratch.md
- docs/titan2elite/DEVICE.md
- docs/titan2elite/TitanKey.kl
- PHYSIBOARD_CHANGES.md
- git history: 54b5fdc, 863f2f4, 002d555, 44137c0, d3278e2, d9d85a8, 386de68, 4342a26, 228d8dd, 0adec6f, abfb822, c9f2136, f68e64c, b99295a, f00ec5f, 7e91a77, 94bac4a, d9d27f9, 389f249, ccd9993, ec353b4, c60bd38, 625b185, 955989f, 973174d, 2514c28
