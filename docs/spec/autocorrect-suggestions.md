# Suggestions and autocorrection

This document specifies the word-suggestion strip, the automatic correction that fires at a word
boundary, the exact text-replacement rules that run before it, the personal dictionary, the
"never overwrite a correctly spelled word" rule and the data pipeline that enforces it, the
offline evaluation harness, and the planned rework. Dictionary file formats, hosting, download
and language switching are in `dictionaries-languages.md`; the strip's geometry, visibility and
insets are in `status-bar.md`; composition, autospace and capitalization are in `text-input.md`.

Vocabulary used below:

- **Current word**: the run of word characters immediately before the cursor that the keyboard
  is tracking as it is typed.
- **Boundary**: a keystroke that ends the current word (Space, Enter, or boundary punctuation).
- **Text replacement**: an exact, user-visible rule such as `dont -> don't`, applied verbatim.
- **Automatic correction** (autocorrect, auto-replace): a dictionary-driven guess that replaces a
  misspelled current word at a boundary.
- **Suggestion**: a candidate word shown on the strip for the user to tap.
- **Known word**: a word present in any active dictionary (main, default user words, or
  personal dictionary), compared after normalization.

## 1. Word tracking

### 1.1 What counts as a word character

The keyboard tracks the current word from the characters it commits itself. A character joins
the current word when it is:

- a letter or a digit, in any script; or
- an apostrophe (straight `'`, or a curly `’`, `‘`, `ʼ`, all of which are stored as straight
  `'`), but only when the current word is non-empty and its last character is a letter or digit.

Any other character (space, punctuation, symbol, emoji) resets the current word to empty. A
leading apostrophe never starts a word.

The current word holds at most 48 characters. Characters typed past that limit are ignored by
the tracker (they still reach the text field). When the word is read back from the field, only
the last 48 characters are kept.

A committed text of the form backspace-then-character (a control character U+0008 followed by
one character) means "replace the last character": the tracker removes one character and then
appends the new one. This is how multi-tap key cycling (see `keys-and-modifiers.md`) keeps the
tracker in step.

Backspace removes the last character of the current word; when it becomes empty the word is
reset. Backspace also refreshes suggestions for the shorter word.

### 1.2 Syncing the tracker with the field

The tracker can drift from the real text (the app may edit its field, or the cursor may move).
Three events re-read the word from the field:

| Event | What is read | Delay |
|---|---|---|
| The cursor moves (selection collapsed, not caused by the keyboard's own commit) | Up to 128 characters before and 128 after the cursor; the word is the run of non-boundary characters around the cursor | 120 ms debounce; a second move within 120 ms cancels the first read |
| A text field is entered | Same as above | Immediate if the dictionary is loaded; otherwise deferred until it is |
| A boundary key is pressed | Only the text before the cursor, up to 128 characters | Immediate, silent (no new suggestions), then the boundary is processed |

A word boundary for these reads is: any whitespace; any of `.,;:!?()[]{}\/"`; an apostrophe
whose preceding character is not a letter or digit; any other non-letter, non-digit character.
An apostrophe between two word characters is not a boundary, so `we'll` is one word.

When the cursor lands on empty space (no word around it), the tracker resets and the strip
shows next-word or starter suggestions (section 4).

When the text field changes (new app, new field, nav mode toggled, a double-space period, a
pasted or expanded text), the tracker resets and the strip is cleared.

## 2. When suggestions are computed

Suggestions are recomputed on every change of the current word, provided:

1. `suggestions_enabled` is true;
2. the field is not restricted (password, URI, email address, filter fields, and apps in raw
   mode disable suggestions; see `per-app-behavior.md`);
3. the primary dictionary for the current subtype language has finished loading. If it has not,
   the load is scheduled in the background and the strip refreshes when it completes.

Computation runs off the main thread. Each request carries a generation number; when a result
arrives it is discarded if the current word has changed since the request was made, so the
strip never shows suggestions for a word the user has already moved past.

Up to **3** suggestions are shown. The count is fixed; there is no setting.

When more than one dictionary is active (additional suggestion languages for the current input
style, see `dictionaries-languages.md`), each active dictionary is queried with the same word and
the same limit, and the lists are merged: primary-dictionary results get a score bonus of
**0.35**, everything is sorted by adjusted score descending then candidate length ascending,
duplicates (case-insensitive) are dropped, and the first 3 survive. An additional dictionary that
has not loaded yet is skipped for this request and scheduled to load.

## 3. Candidate retrieval and ranking for the current word

### 3.1 Normalization

Every comparison in this section happens on a normalized form of the word: lowercase in the
dictionary's locale; curly apostrophes made straight; `œ`, `Œ` folded to `oe`, `æ`, `Æ` to
`ae`, `ĳ`, `Ĳ` to `ij`, `ß` to `ss`; accents and combining marks removed; then every
character that is not a letter removed, except that the suggestion-side normalization keeps
apostrophes (so `l’Œil` becomes `l'oeil`) while the dictionary-key normalization drops them
too (so `l'oeil` and `loeil` share a key).

Dictionary entries keep their original spelling and casing; only their lookup keys are
normalized. A word therefore has one key and possibly several entries (`perche` -> `perché`,
`Perché`, ...).

### 3.2 Frequency scale

Dictionary entries carry a raw frequency 0..255 (in the English list `the` is 222 and the rarest
word is 66). For ranking, raw frequency is mapped to an effective frequency
`(raw / 255) ^ 0.75 * 1600`, floored at 1:

| raw | 222 | 150 | 100 | 80 | 66 | 30 | 1 |
|---|---|---|---|---|---|---|---|
| effective | 1442 | 1074 | 792 | 670 | 580 | 321 | 25 |

Personal-dictionary words start at raw frequency 1 (effective 25) and gain 1 per use.

### 3.3 The apostrophe branch

If the word contains exactly one apostrophe, not at either end, and the part before it (the
"prefix") is all letters and either at most 3 letters long or one of `dall`, `dell`, `nell`,
`sull`, `coll`, `quell`, `quest`, and the part after it (the "root") is at least 3 letters and
all letters, then:

1. suggestions are computed for the root alone with limit `min(2 * limit, 12)`;
2. only candidates at edit distance 0 or 1 from the root are kept, at most `2 * limit`;
3. each candidate is recomposed: if it already starts with the prefix (case-insensitive) the
   prefix part is stripped and the user's prefix is put back; if it contains any other
   apostrophe it is dropped; otherwise the user's prefix is prepended and the candidate is
   recased to match the root the user typed;
4. the first `limit` recomposed candidates are the result. If none survive, the whole word is
   processed by the ordinary path below.

So `dell'amivo` suggests `dell'amico`, `l'oeil` suggests `l'œil`, and `l'oreal` suggests
`l'Oréal` because the entry `L'Oréal` is recased to the lowercase root.

### 3.4 Sources of candidates

For a normalized word of length N:

**Completions (prefix matches).** Up to 200 entries are gathered from the prefix index, starting
with the bucket for the first `min(N, 4)` letters and falling back to shorter prefixes until 200
distinct words are seen. An entry is a completion when its normalized form starts with the
normalized word, it is longer than the typed word, and either it is a user word (personal or
default) or its effective frequency is at least 300 (N ≤ 2), 250 (N = 3), 200 (N = 4) or 150
(N ≥ 5). Because the rarest main-dictionary word has effective frequency 580, this first filter
only removes user words of very low count.

**Fuzzy matches.** N = 1: none. N = 2 or 3: up to `2 * limit` terms from the fuzzy index.
N ≥ 4: up to `4 * limit`. The fuzzy index is a delete-neighbourhood index over normalized keys
with **maximum edit distance 2** and a **prefix length of 4** (only the first 4 letters of each
key generate deletes; a typo beyond the fourth letter is still found because the final distance
check runs on the whole word). Distance is optimal-string-alignment Damerau-Levenshtein: an
insertion, deletion, substitution or adjacent transposition each cost 1. Terms come back sorted
by distance ascending, effective frequency descending, length ascending. An exact key match is
returned at distance 0. A candidate whose length differs from the input by more than 2 is never
returned.

**Accent-stripped fuzzy matches.** When `accent_matching_enabled` is on and N > 1 and stripping
accents from the normalized word changes it, a second fuzzy lookup runs on the stripped form,
up to `2 * limit` terms.

**Single-letter elisions.** When N = 1, the prefix bucket for `<letter>'` is read (up to 80
entries) and the entries of length 2 or 3 that start with that letter followed by an apostrophe
(such as `l'`, `d'`) are added at distance 0.

**Single-letter variants.** When N = 1, the top 5 entries for that letter's key are considered
(so typing `e` can offer `è` and `é`).

### 3.5 Filters applied to every candidate

A candidate is discarded when any of these hold:

| Filter | Condition |
|---|---|
| Single-letter noise | N ≤ 2, candidate is one character and not the typed letter |
| Too far for a short word | N ≤ 2 and distance > 1 |
| Rare completion | Candidate is a completion (starts with the word, longer) from a non-user entry and its raw frequency is below 150 (N ≤ 2), 100 (N = 3), 80 (N = 4) or 60 (N ≥ 5) |
| Distant substitution | `use_keyboard_proximity` is on, distance > 0, candidate has the same length as the word, and it is not a transposition and some differing position pairs two keys more than 2.5 key-widths apart on the layout grid |
| Same word | Candidate spelling is identical to what was typed (case-sensitive); accent and case variants are kept |
| Proper-noun completion | Candidate is a completion, the typed word starts lowercase, the candidate starts uppercase, and it is not a user word (`hard` must not complete to `Hardy`) |

Key distances use a 3-row grid: row 0 `q w e r t y u i o p` at columns 0..9, row 1
`a s d f g h j k l` at 0..8, row 2 `z x c v` at 0..3 and `b n m` at 6..8 (the gap is the space
bar). AZERTY and QWERTZ are the same grid with the letters remapped; any other layout name is
treated as QWERTY. Distance is Euclidean over (row, column). A key not on the grid imposes no
constraint.

For each surviving term the entries considered are: N = 1: the top 5 entries for that key;
distance 0: the top 3; otherwise the single highest-frequency entry.

### 3.6 Score

Each candidate's score is a sum of the terms below, then multiplied by **5** when the entry is a
personal or default user word.

| Term | Value |
|---|---|
| Distance | `1 / (1 + distance)` |
| Frequency | `effective / 1600` |
| Prefix bonus, N = 1 | 0 for forced completions; otherwise as N ≤ 2 |
| Prefix bonus, N ≤ 2 | 2.0 forced completion; 1.8 completion; 1.5 any prefix match |
| Prefix bonus, N ≥ 3 | 5.0 forced completion (from the completion source); 4.0 completion; 3.0 any prefix match |
| Edit type (only when `use_edit_type_ranking` is on and distance > 0) | +0.5 if candidate is one longer (a dropped letter); +0.4 same length and every changed key is adjacent (≤ 1.15) or a transposition, else +0.2; one shorter: +0.3 if the word has a doubled letter that the candidate breaks, +0.1 if the word has any doubled letter, else 0 |
| Accented single letter | +0.8 when N = 1, candidate is one accented letter |
| Accented same length | +0.4 when N > 1, candidate has the same length and carries an accent |
| Plain same letter | -2.0 when N = 1 and the candidate is the same unaccented letter |
| Elision | N = 1: +1.0 for a 2-character `x'` entry, +0.55 for a 3-character one |
| Single-letter length | N = 1: -0.2 per character beyond 2 |
| Length similarity | +0.35 same length; +0.2 differs by 1; +0.05 by 2; else -0.15 × min(diff, 4) |
| Digits or symbols in candidate | -4.0 (special char, distance > 0, N ≤ 2); -2.2 (special char, distance > 0); -3.0 (digit, N ≤ 2); -1.5 (digit); -1.2 (symbol, N ≤ 2); -0.6 (symbol). Apostrophes are not symbols |
| Long completion | -0.35 when the word has ≥ 4 characters and the completion is ≥ 3 longer |
| Same root | +0.25 when distance is 1 and the candidate equals the word once apostrophes are removed |

### 3.7 Order shown

Candidates are kept in a list of `limit` best, ordered by three tiers:

1. personal and default user words before everything else;
2. completions (for N = 1: same key, different spelling) before edit-distance candidates;
3. within a tier: lower distance first, then higher score, then shorter candidate.

Candidates are deduplicated by lowercase spelling. The word itself is never suggested.

Sources are considered in this order: the single-letter variants, then completions (marked as
"forced" completions), then single-letter elisions, then fuzzy and accent-stripped fuzzy
results.

### 3.8 Strip layout

The three results map to strip slots as: result 1 in the centre, result 2 on the right, result 3
on the left. When an add-word candidate exists (section 6.2) it takes the left slot instead of
result 3, unless one of the results already equals it (case-insensitive). When only the add-word
candidate exists, it is drawn alone at full width. Tapping a slot inserts it (section 5).

## 4. Next-word and starter suggestions

After a "soft" boundary (Space, comma, semicolon or colon) the strip shows predictions for the
next word instead of going blank:

- **Learned bigrams.** Every completed word is learned as "previous word -> this word" and, at
  sentence start, as "sentence start -> this word", per locale, in a local SQLite database
  `user_ngrams.db` (table `bigrams`: locale, prefix, next word, count, last used). Learning is
  queued off the main thread; a just-learned pair is visible immediately through an in-memory
  overlay. Predictions are ordered by count descending then recency. The prefix key is the
  previous word normalized (lowercase, ligatures folded, accents stripped, non-alphanumerics
  removed); the stored next word keeps its display form.
- **Starter words.** When fewer than 3 predictions exist, the remaining slots are filled with
  the most common dictionary words: the top 48 entries by effective frequency across the primary
  dictionary (personal words first, then frequency, then length), excluding one-letter words,
  default user words and words containing anything but letters and apostrophes. The primary
  dictionary's starters get the 0.35 merge bonus over additional dictionaries.
- A period, Enter or any other hard boundary learns the pair, then resets the context: the next
  word is a sentence start.
- Typing any letter replaces predictions with current-word suggestions.
- Next-word and starter suggestions are shown lowercase, or with a leading capital when Caps
  Lock, Shift, one-shot Shift or the latched shift layer is active.

Hiding a next-word suggestion from the strip (section 5) forgets that bigram; deleting a user
word forgets it as a next word under every prefix.

## 5. Tapping, hiding and deleting a suggestion

**Tap.** The whole word around the cursor (up to 64 characters before and after, bounded as in
1.2) is deleted and the suggestion committed in its place. Casing follows the typed word: all
letters uppercase (and more than one letter) -> uppercase; first letter upper, rest lower ->
leading capital; all lowercase -> lowercase, except that a candidate with internal capitals
(`McCartney`, `iPhone`) or exactly one uppercase letter keeps its own casing. If the cursor sits
where auto-capitalization applies and `auto_capitalize_first_letter` is on, the first letter is
capitalized regardless. A space is appended unless the suggestion ends with an apostrophe or the
character after the cursor is whitespace; the space is marked as an auto-space so a following
punctuation key can swallow it (see `text-input.md`). The slot flashes and a tap haptic fires.
Latched Shift or Alt layers are released and a one-shot Shift is consumed.

**Long press on a suggestion** turns the slot into an action slot with a hide button and, when
the word is in the personal dictionary, a delete button. Hide removes the word from the current
list and back-fills the list with starter words (excluding the hidden one); for a next-word
suggestion it also forgets the bigram. Delete removes the word from the personal dictionary in
every loaded dictionary, forgets it as a next word everywhere, and refreshes. Both give haptic
feedback. Any other change to the strip cancels action mode.

**Trackpad.** A gesture on the screen trackpad accepts the suggestion in the corresponding third
(left = result 3, centre = result 1, right = result 2) with the same insertion behavior; see
`trackpad-caret-nav.md`.

## 6. Personal dictionary

### 6.1 Storage and precedence

Personal words live in the preference `user_dictionary_entries` as a JSON array of objects
`{"w": word, "f": count, "u": lastUsedMillis}`; spelling and case are kept as entered, and
lookups are case-insensitive. Adding a word that already exists increments `f` and refreshes
`u`. Every accepted correction or text replacement whose result is a personal word increments
`f` as well. The store is one list shared by all languages; it is merged into every dictionary
that is loaded, and a word added while several dictionaries are loaded goes into all of them
immediately (indexes and fuzzy index alike). It is part of the settings backup.

Default user words come from `common/dictionaries/user_defaults.json`, copied on first use to
`user_defaults.json` in the app's private files directory and edited there. The shipped file is
`[{"w":"PhysiBoard","f":30},{"w":"BlackBerry","f":25},{"w":"Parenzo","f":20}]`. These rank like
personal words but are excluded from starter suggestions.

Both kinds are "known words": a personal word is never autocorrected, and it can never be
filtered out of suggestions by frequency or capitalization rules.

Settings screens announce changes with the broadcast
`brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED` (package-internal); the keyboard then
re-reads both files, purges removed words from its indexes and rebuilds the fuzzy index in the
background.

### 6.2 Adding from the strip

An **add-word candidate** exists when the current word (trimmed) contains a letter or digit,
the primary dictionary is loaded, and the word is not known. It also exists after an automatic
correction whose result is unknown, and after undoing a correction (the original word). It is
cleared when the cursor leaves that word, when a letter or digit is typed, on a boundary without
correction, on tapping it, and on context reset.

The candidate is shown in the left slot with a yellow plus icon. Tapping it adds the word to the
personal dictionary, clears the candidate, flashes the slot and refreshes the strip. Long-pressing
it opens the add-substitution sheet (section 8.5).

**Gesture.** When `trackpad_gesture_add_word_enabled` is true, the left-third trackpad gesture
adds the candidate; when `trackpad_gesture_add_word_full_width_enabled` is also true and the
candidate is the only thing on the strip, any third does. The gesture is refused on SYM pages, in
restricted fields, when suggestions are off, and when the strip has neither suggestions nor a
candidate. After adding, a space is committed and marked as auto-space, unless the character
after the cursor is whitespace or boundary punctuation, in which case no space is added and any
pending auto-space is cleared. A haptic fires.

### 6.3 Personal dictionary screen

Reached from Auto-correction > "Personal dictionary". Lists default and personal words together,
sorted case-insensitively, with search, a plus button (add dialog, single line, OK enabled when
non-blank), an edit pencil (rename) and a delete button per row. Renaming or deleting a default
word edits `user_defaults.json`; if that file is unreadable the operation reports "save failed"
rather than overwriting it. Every change sends the update broadcast.

## 7. Boundary handling

### 7.1 Which keys are boundaries

For the physical keyboard, the text pipeline treats these as boundary keys: Space; Enter; and
any key whose typed character is one of `.,;:!?()[]{}\/"`. An apostrophe is never a boundary.
The boundary character used for the decision is the typed character, or `' '` for Space and
`'\n'` for Enter; for a Space typed on the on-screen keyboard the same path runs with a null
event.

Enter is special. When the field declares an editor action (GO, SEARCH, SEND, NEXT, DONE,
PREVIOUS, or any custom action label), Enter performs no autocorrection at all. Otherwise the
correction runs with `'\n'` as boundary; if it commits the boundary itself the Enter key is
consumed so the app does not add a second newline.

### 7.2 Order of operations at a boundary

With suggestions active for the field (not restricted, engine present):

1. The tracker is synced to the text before the cursor (1.2).
2. If there is no input connection: the boundary is recorded as not applicable, nothing changes.
3. The 32 characters before the cursor are scanned backwards past whitespace and boundary
   punctuation; if the first other character is not a letter, digit or apostrophe (for example
   an emoji), no correction runs ("hard boundary before cursor") and the boundary is committed.
4. If the current word is blank, the boundary is committed.
5. The lookup word is the apostrophe root when the word splits per 3.3, else the word.
6. **Text replacement** (section 8) if `auto_correct_enabled`: an exact rule match replaces the
   word and the boundary is appended (except Space after a replacement ending in an apostrophe).
   Done.
7. If `auto_replace_on_space_enter` is off: the boundary is committed. Done.
8. **Primary case repair**: if the word equals its lookup word, contains a letter, has no
   uppercase letter, the primary dictionary's entries for its key (top 8) contain no entry spelled
   exactly as typed, and one of them equals it ignoring case and contains an uppercase letter,
   that entry replaces the word (`problem` -> `Problem` when only `Problem` exists; `und` stays
   when `und` exists beside `Und`). Skipped if the word was rejected (7.5). Done.
9. **Automatic correction** (section 9). If it commits, done.
10. Otherwise the boundary is committed and the last-replacement memory is cleared.

Steps 6, 8 and 9 all commit the same way: delete the word before the cursor, commit the
replacement, remember the pair for undo, reset the tracker, then append the boundary. A haptic
fires on every replacement. Each attempt is recorded in the debug capture (section 12) with its
outcome.

### 7.3 Committing the boundary

- Space: if the text already ends with a space nothing is added; else a space is committed and
  verified; if the field did not take it, a Space key event is sent and verified again. A
  committed space is marked as an auto-space.
- `?`, `!`, `;`, `:` with `french_punctuation_spacing`: a narrow no-break space (U+202F) plus the
  mark, replacing trailing ordinary spaces.
- `,` with `comma_space`: comma followed by a space, replacing a space before the comma.
- Other characters are committed as typed.
- When the boundary character is in the auto-space punctuation set and an auto-space is pending,
  the pending space is replaced by the punctuation (see `text-input.md`).

Boundaries also drive next-word learning (section 4): after the boundary, the completed word (the
replacement if one happened) is learned against the previous word.

### 7.4 Restricted fields and the legacy path

When suggestions are disabled for the field (password, URI, email address, filter, raw-mode
app) the engine above is bypassed, and text replacements are disabled for the same fields
(`auto_correct_enabled` is treated as false whenever the field is restricted). The net effect in
a restricted field: no rule, no case repair, no correction; a boundary is committed as typed,
with comma-space and French spacing still honoured.

A second, older replacement path exists for the case "suggestions disabled but text replacements
enabled": it reads 100 characters before the cursor, runs the same rule lookup, replaces the
match and appends the boundary (Enter appends the newline itself only in a multi-line field).
Because both flags are derived from the same field restriction, this path is unreachable in
practice. It keeps its own undo memory and rejected set with the semantics of 7.5, which is one of
the duplications the rework removes (section 16, W4).

### 7.5 Undo with Backspace and rejection memory

When `auto_replace_on_space_enter` is on and a replacement was the last thing that happened,
pressing Backspace undoes it:

1. The text before the cursor (replacement length + 2 characters) must end with the replacement,
   or with the replacement followed only by whitespace or boundary punctuation.
2. The replacement and any such trailing characters are deleted and the original word is
   committed in their place (without a trailing space).
3. The original word, lowercased, and its apostrophe root if any, are added to the rejected set.
4. The original becomes the add-word candidate, so one tap adds it to the personal dictionary.
5. The undo memory is cleared; a second Backspace deletes normally.

The undo memory is cleared when any character is typed, and when a boundary passes without a
replacement. The rejected set is cleared when the user types any letter or digit, so a rejection
survives only until the next word starts. Rejection is checked for text replacements, case
repair and automatic correction alike. It is never persisted.

The legacy path's undo (7.4) behaves the same, except that its rejected set is cleared on the
next non-Backspace key that produces a letter or digit and it does not set an add-word candidate.

## 8. Text replacements (substitutions)

### 8.1 Rule sets

A rule set is a JSON object whose keys are triggers and values are replacements; the optional
key `__name` holds a display name. Bundled sets live at
`common/autocorrect/auto_corrections_<code>.json` for codes `it`, `en`, `es`, `fr`, `de`, `pl`
and `x-pastiera`. Shipped contents:

| Code | Rules |
|---|---|
| `en` | 51 rules: `i -> I`, negated contractions (`dont -> don't` ... `doesnt -> doesn't`), `youll`, `shell -> she'll`, `theyll`, `itll`, `thatll`, `ive -> I've`, `youve`, `weve`, `theyve`, `youre`, `hes`, `shes`, `its -> it's`, `theyre`, `thats`, `whats`, `whos`, `wheres`, `whens`, `hows`, `whys`, `lets`, `heres`, `theres`, `youd`, `hed`, `shed -> she'd`, `wed -> we'd`, `theyd`, `itd`, `thatd`, `id -> I'd`, `im -> I'm`, `ill -> I'll` |
| `it` | 18 rules: multi-word `cos e -> cos'è`, `com e`, `dov e`, `chi e -> chi è`, `c ho -> c'ho`, `forza juve -> forza napoli`; `qual'è -> qual è`; accent repairs `perche`, `perchè`, `poiche`, `affinche`, `finche`, `dacche`, `sicche`, `benche`, `cosi`, `gia`, `piu` |
| `fr` | about 260 accent and elision repairs (`etre -> être`, `jai -> j'ai`, `coeur -> cœur`, `ct -> c'était`, `titan -> Titan`, ...) including a few capitalized triggers (`Cetait`, `Noel`, `Etre`, `Ca`) |
| `x-pastiera` | `bb -> BlackBerry`, `ppp -> %` |
| `de`, `es`, `pl` | empty |

Custom rules per code are stored in the preference `auto_correct_custom_<code>` as the same JSON
object (string). Custom rules overlay the bundled set of the same code, key by key. Any preference
with that prefix and a code outside the seven bundled ones defines a custom set (a "custom
language"). Custom sets are part of the backup.

Loading happens at keyboard start and after every edit from a settings screen or the
add-substitution sheet.

### 8.2 Which sets are searched

The preference `auto_correct_enabled_languages` holds a comma-separated list of codes. When it is
unset or the empty string, reading it yields the default pair: the system language if it is one
of `it`, `en`, `es`, `fr`, `de`, `pl`, else `en`, plus `x-pastiera`. The searched sets are the
enabled codes that have a rule set loaded. The list as read is therefore never empty, which
matters for the known-word guard in 8.3. (The Text Replacements screen stores the empty string
when every set is switched on; on the next read that means the default pair, not "all".)

### 8.3 Matching

Given the text before the cursor including the boundary just typed:

1. **Symbol triggers first.** A trigger that contains any non-alphanumeric character (`:e1`,
   `@@@`) matches when the text, with only trailing whitespace trimmed, ends with it
   (case-insensitive) and the character before it is a boundary or the text starts there. The
   longest such trigger wins. A rejected trigger is skipped.
2. Trailing whitespace and boundary punctuation are then trimmed; if anything trimmed was neither
   whitespace nor boundary punctuation (an emoji, say), no rule applies.
3. **Two-word sequences.** The last two words (words separated by boundary characters) are looked
   up in custom rules, then bundled rules, per enabled set. A match applies unconditionally (this
   is how `cos e -> cos'è` works). A rejected sequence is skipped.
4. **The last word.** Custom rules, then bundled rules, per enabled set, lowercase trigger. A
   match applies when the word is not a known word, or the replacement differs from the word only
   in accents, case or punctuation, or the enabled-languages list as read is non-empty. Because
   that list is never empty when read on the device (8.2), in practice **a matching rule always
   applies, known word or not**: with English rules on, `its`, `ill`, `wed`, `shell`, `id` and
   `hes` are rewritten on Space. The known-word guard only takes effect in tests that run the
   lookup without a settings store; a clean-room implementation should decide whether to enforce
   it (see section 18).

The replacement's casing follows the trigger as typed: an all-uppercase word (with at least one
letter) uppercases the replacement from its first letter on; a leading capital capitalizes the
replacement's first letter (after any apostrophe); otherwise the replacement is used as stored
(`i -> I`, `ive -> I've` are stored capitalized).

### 8.4 Screens

**Auto-correction** (category screen, label "Auto-correction"): see the settings table for the
rows. The "Manage text replacements" row is only shown while text replacements are on.

**Text Replacements** (label "Text Replacements"): lists the system language first (subtitle
"System language"), then "Other Substitutions" (every bundled code and every enabled subtype
language, sorted), then "Custom Substitutions". Each row has an Edit button and a switch. Turning
a set off when it is the last enabled one is refused with the toast "At least one substitution
required for operation". Toggling maintains the list: when every set is on, the preference is
stored as the empty string. `x-pastiera` is never listed, so the Recipes set cannot be toggled or
edited from the UI.

**Edit** (title = language display name): the rules as "original -> corrected" rows with a
search box matching either side, a delete button per row, a plus button and tap-to-edit. The
add/edit dialog has Original, Corrected and a checkbox "Add replacement to personal dictionary"
(default on) which adds the corrected text to the personal dictionary if it has a letter or digit
and is not already there. Save writes the whole visible list to `auto_correct_custom_<code>` with
the new or edited rule first (trigger lowercased), keeps any stored `__name`, and reloads. The
screen loads custom rules if any exist, else the bundled ones; so the first save copies the
bundled set into the custom set.

### 8.5 Add-substitution sheet

Long-pressing the add-word candidate on the strip opens a bottom sheet (a transparent activity
excluded from recents) titled "Add substitution" showing "Replacement: <word>", a "Shortcut"
field (focused after 120 ms) and the checkbox "Also add replacement to dictionary" (default on).
Save is enabled when the shortcut is non-blank. Saving stores `shortcut (trimmed, lowercased) ->
word` at the front of the custom set for the current subtype language (falling back to `it` when
the subtype has no language), adds that language to the enabled list if absent, reloads the
rules, optionally adds the word to the personal dictionary, and shows "Substitution saved" or
"Could not save substitution". Tapping outside dismisses. A blank shortcut or replacement, or the
trigger `__name`, is refused.

## 9. The automatic correction decision

Runs at step 9 of 7.2. The engine is asked for **2** suggestions for the lookup word with the
current settings (accent matching, proximity, edit-type ranking).

**Confidence** is the relative margin between the top candidate and the runner-up:
`(top score - runner-up score) / top score`, clamped to 0..1. It is 1.0 when there is no
runner-up or the runner-up is not a current-word candidate, and 0.0 when the top score is not
positive. The threshold is **0.02**; there is no preference for it.

Facts gathered about the top candidate (after apostrophe recomposition):

- orthographic variant: candidate and word differ, but have the same dictionary key (accent,
  ligature or case-and-accent only);
- case variant: equal ignoring case, not equal;
- known: the lookup word is known in any active dictionary (an additional dictionary that is
  still loading counts as "known", deferring correction rather than risking a wrong one);
- exact known: some entry is spelled exactly as the lookup word (case-insensitive);
- exact primary case: the primary dictionary has an entry spelled exactly as typed;
- rejected: in the rejected set (7.5).

The candidate is committed only when **all** of these hold:

| Gate | Rule |
|---|---|
| Known word | The word is not known; or it is a case variant and the primary dictionary has no entry in the typed case; or it is an orthographic variant and no entry is spelled exactly as typed |
| Not rejected | The word is not in the rejected set |
| Confidence | confidence ≥ 0.02 |
| Safe shape | See below |
| Minimum length | Lookup word ≥ 3 characters (≥ 2 for an orthographic variant) |
| Length ratio | Candidate length ≤ floor(word length × 1.25), or the candidate is the word with one letter doubled |

**Safe shape**, evaluated in order, first decision wins:

1. Refuse unless the candidate is a current-word suggestion (never a next-word or starter word).
2. Refuse a distance-0 candidate unless it is an orthographic or case variant.
3. Refuse when distance > `max_auto_replace_distance`.
4. Refuse when the word is all lowercase and the candidate is acronym-like (≥ 2 letters, all
   uppercase).
5. Refuse when the word starts lowercase and the candidate starts uppercase, unless it is a case
   variant (`hallo` never becomes `Halle`).
6. Same length: accept if orthographic, or a case variant, or the first letter is unchanged
   (`ding` never becomes `fing`; `fihg` may become `fing`).
7. One longer and the extra letter doubles a neighbour (`wil -> will`): accept.
8. Per-language length allowance: English allows a length change of up to 2; every other
   language allows 0. Accept when the change is within the allowance, the candidate is not a pure
   prefix or suffix extension or truncation of the word (`work` never becomes `works`, `behavio`
   never becomes `behavior`, `kaputte` never becomes `kaputt`), and the first letter is unchanged.
9. Otherwise accept only if orthographic and the candidate has the lookup word's length.

The chosen candidate is recased like a tapped suggestion (section 5, without the auto-capitalize
override). If the recased result equals the typed word, nothing is replaced ("same replacement").

When the decision is negative, the first matching reason is recorded in the debug capture:
`no_suggestion`, `rejected_by_user`, `known_word`, `distance_too_high`, `not_current_word`,
`not_edit_distance`, `acronym_candidate`, `unsafe_shape`, `too_close_to_call`, `word_too_short`,
`candidate_too_long`, `constraints_not_met`. Other outcomes recorded: `no_input_connection`,
`hard_boundary_before_cursor`, `empty_word`, `auto_replace_disabled`, `same_replacement`.
Commits record the source (`TEXT_REPLACEMENT`, `PRIMARY_CASE`, or the dictionary source of the
candidate) and the distance. The capture keeps the last 100 events and is excluded from the debug
export unless "incl. autocorrections" is switched on.

"Too close to call" means: the candidate was structurally acceptable but a rival scored within
2 percent of it, so the correction is left on the strip for the user to tap instead of being
imposed.

## 10. The rule: a correctly spelled word is never overwritten

Stated plainly: if the typed word is in any active dictionary, automatic correction leaves it
alone. The only replacements a known word can receive are a case repair (`problem` ->
`Problem` when the dictionary has only the capitalized form) and an accent repair (`perche` ->
`perché` when no entry is spelled `perche`). Text replacement rules are exempt from this rule in
practice (8.3, step 4).

The rule is only as good as the dictionary: a real word the dictionary has never heard of looks
exactly like a typo. Measured on 2026-09-09 against the previous English list (50,000 entries,
encyclopedic corpus), 14 of 116 ordinary English words were missing (`salve`, `gaunt`, `glean`,
`lithe`, `canny`, `dowdy`, `flout`, `imbue`, `jostle`, `loathe`, `shirk`, `spurn`, `vex`,
`ember`) and 4 of them were overruled (`salve -> slave`, `lithe -> litre`, `dowdy -> dowry`,
`flout -> flour`). The English list was therefore rebuilt (section 11), after which 0 of 116 are
missing and 0 are overruled, the false-correction rate on the typo corpus fell from 0.041 to
0.014, and recall fell from 0.775 to 0.650. The evaluation asserts "known words overruled = 0"
and "ordinary words overruled ≤ 0" as invariants; they are not to be loosened.

Only English was rebuilt. The other eleven bundled lists came from the same upstream process and
very likely have the same coverage problem.

## 11. The English word-list pipeline

Inputs and steps of `scripts/build_en_wordlist.py` (requires `pip install wordfreq
pyspellchecker`), run as:

    python3 scripts/build_en_wordlist.py --size 80000 \
        --out app/src/main/assets/common/dictionaries/en_base.json \
        --tsv /tmp/vocab_80k.tsv

1. Take the top `6 × size` words from the `wordfreq` English ranking (a blend of subtitles, web,
   books, Twitter and Wikipedia).
2. Keep a word only if it matches `^[a-z]+(?:'[a-z]+)?$` (lowercase letters with at most one
   internal apostrophe; no digits, no hyphens, no capitals) and is not a single letter other
   than `a` or `i`.
3. Keep a word only if it is in the `pyspellchecker` English lexicon (160,572 words). This is
   the filter that keeps real misspellings out: `alot`, `teh`, `thier`, `untill`, `definately`,
   `seperate`, `occured`, `recieve`, `goverment`, `wierd` are all in the frequency ranking's top
   80,000 and would otherwise become "known words" that can never be corrected. Measured: without
   the lexicon, recall fell from 0.775 to 0.250 as the list grew.
4. Take the first `size` survivors (80,000). Warn if fewer.
5. Map each word's Zipf frequency linearly from the list's [min, max] onto [66, 222], rounded and
   clamped, so `the` scores 222 and the rarest word 66, matching the scale the other lists use.
6. Write `en_base.json` as a JSON array of `{"w": word, "f": frequency}`, and optionally a
   `word<TAB>frequency` TSV for the evaluation sweep.

Then `python3 scripts/build_symspell_dict.py --input <en_base.json> --output
app/src/main/assets/common/dictionaries_serialized/en_base.dict` builds the shipped CBOR file:
the normalized-key index (spelling and case preserved per entry), the prefix index for prefixes
of 1..4 letters, the delete neighbourhood for the first 4 letters of each key at distance up to
2, and the metadata `{"maxEditDistance": 2, "prefixLength": 4}`. At load time the keyboard
re-derives the delete map against the actual keys (older files stored truncated prefixes rather
than whole terms) and rebuilds the fuzzy index from effective frequencies.

Sizes: `en_base.json` 80,000 entries (was 50,000); `en_base.dict` 20.5 MB (was 13.2 MB); the
APK nevertheless shrank from 50.2 MB to 48.5 MB because 13 build-only `_base.json` files
(27.4 MB) that 2.0.6 shipped by accident are now excluded from assets. Other bundled `.dict` files
are 12.5 to 20 MB each. `lt_base.json` exists with no matching `.dict`.

## 12. The evaluation harness

JVM tests (Robolectric, SDK 33) under the app's unit tests replay a corpus through the shipped
retrieval, ranking, shape gates and commit predicate, so the measurement cannot drift from the
rule the keyboard uses. Corpora are TSV resources under `app/src/test/resources/autocorrect/`:

| File | Content | Size |
|---|---|---|
| `en_cases.tsv` | `typed<TAB>intended`; equal columns mark a control (a real word that must survive). Sections: dropped/added letters from the device export of 2026-08-31 (`definetly`, `sensitivy`, `Oliva`), 20 classic misspellings, 7 transpositions, 10 adjacent-key slips, 34 controls that sit one edit from a commoner word (`form`, `trail`/`trial`, `quiet`/`quite`, `salve`/`slave`, `work`/`works`, ...) | 74 rows: 40 typos, 34 controls |
| `en_controls.tsv` | Ordinary English words (verbs, adjectives, nouns); column two is a human note | 116 unique words |
| `en_vocab.tsv` | `word<TAB>raw frequency` for the small evaluation dictionary, including distractors such as `from` for `form` | 104 words |

Each row's outcome: FIXED (typo corrected to the intended word), MISSED (typo left alone), WRONG
(typo corrected to something else), CLOBBERED (a control was replaced), UNTOUCHED (a control
survived). Each row also records whether the typed word is in the dictionary, separating
coverage holes from decision failures. Metrics:

- false-correction rate = (WRONG + CLOBBERED) / total rows (the headline number);
- recall = FIXED / typos;
- precision = FIXED / (FIXED + WRONG + CLOBBERED), 1.0 when nothing was committed;
- uncovered controls = controls missing from the dictionary.

The replay uses the same two-candidate confidence computation, the same shape gate, and the same
commit predicate as the keyboard, with rejection memory empty (it cannot carry over between words)
and "exact primary case" equal to "exact known" (the vocabulary is lowercase). Settings under
test are the shipped ones: distance 2, proximity on, edit-type ranking off, accent matching on,
confidence 0.02.

Ratchets (tighten when earned, never loosen):

| Test | Dictionary | Assertions |
|---|---|---|
| Small-vocabulary eval (always runs) | `en_vocab.tsv` through a real fuzzy index (prefix length 7 there) | false-correction rate ≤ 0.000; recall ≥ 0.775; controls ≥ one third of rows; distance 1 and distance 2 must differ in at least one of fixed/wrong/clobbered |
| Real-dictionary eval (opt-in) | shipped `en_base.dict` via the real load path | no known word clobbered (invariant); false-correction rate ≤ 0.014; recall ≥ 0.650; wrong ≤ 1 |
| Ordinary-word sweep (opt-in) | shipped `en_base.dict`, `en_controls.tsv` | known words overruled = 0 (invariant); real words overruled ≤ 0 |
| Vocabulary sweep (opt-in, no assertions) | any candidate TSV lists | prints fixed/missed/wrong/recall/fcr and missing/overruled per list, and a confidence sweep over thresholds 0, 0.02, 0.05, 0.10, 0.20, 0.35, 0.50 |

How to run:

    ./gradlew :app:testDebugUnitTest --tests '*AutocorrectEvalTest*'
    ./gradlew :app:testDebugUnitTest -Pphysiboard.eval.realDictionary=true \
        --tests '*AutocorrectEvalRealDictionaryTest*'
    ./gradlew :app:testDebugUnitTest --tests '*VocabularySweepTest*' \
        -Pphysiboard.eval.vocab=/tmp/vocab_shipped.tsv,/tmp/vocab_80k.tsv

The real-dictionary run decodes a 20 MB CBOR file into three in-memory representations and is
slow, hence opt-in; without the property it is skipped, not failed.

Measured history (real dictionary, distance 2, proximity on):

| Configuration | fixed | missed | wrong | clobbered | fcr | recall | precision |
|---|---|---|---|---|---|---|---|
| Old 50k list, threshold 0 | 31 | 7 | 2 | 1 | 0.041 | 0.775 | 0.912 |
| Old list, distance 1 | 29 | 10 | 1 | 1 | 0.027 | 0.725 | 0.935 |
| Old list, proximity off | 31 | 6 | 3 | 1 | 0.054 | 0.775 | 0.886 |
| Old list, threshold 0.10 | 29 | 10 | 1 | (1 real word overruled) | 0.014 | 0.725 | 0.967 |
| Rebuilt 80k list, threshold 0.02 (shipped) | | | ≤ 1 | 0 | 0.014 | 0.650 | |

Proximity ranking earns its keep (turning it off raises the false-correction rate and fixes
nothing extra). The threshold and the rebuilt list fix the same failure by different means, so
after the rebuild the threshold was lowered from 0.10 to 0.02 to avoid paying twice.

## 13. Settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `auto_correct_enabled` | boolean | true | Whether text replacement rules run at a boundary (both paths) | Auto-correction | "Text replacements" (subtitle: Apply saved rules like "ca -> ça" when pressing Space or Enter.) |
| `auto_correct_enabled_languages` | string, comma-separated codes | unset or empty: read as system language if in it/en/es/fr/de/pl else en, plus x-pastiera | Which rule sets are searched; because the value read is never empty, the known-word guard for rules is always bypassed on the device | Text Replacements | per-set switch |
| `auto_correct_custom_<code>` | string (JSON object, optional `__name`) | none | Custom rules overlaying the bundled set for `<code>`; unknown codes define custom sets | Edit screen; add-substitution sheet | "Add Correction" / "Add substitution" |
| `suggestions_enabled` | boolean | true | Suggestions on the strip, next-word predictions, add-word candidate, and the whole boundary engine (off routes boundaries to the legacy path) | Auto-correction | "Suggestions while typing" |
| `accent_matching_enabled` | boolean | true | Second fuzzy lookup on the accent-stripped word; passed into every suggestion request | Auto-correction | "Accent & spelling marks" |
| `auto_replace_on_space_enter` | boolean | code default false; shipped Titan baseline true | Automatic correction and case repair at boundaries; Backspace undo | Auto-correction | "Automatic correction" (subtitle: Use dictionary suggestions to fix likely typos when pressing Space or Enter.) |
| `max_auto_replace_distance` | int 0..3 | code default 1; shipped Titan baseline 2 | Largest edit distance a correction may have; 0 shows "Off" and blocks every fuzzy correction (accent and case repairs at distance 0 still pass) | Auto-correction, shown only while automatic correction is on; slider with 3 steps | "Maximum correction distance" |
| `use_keyboard_proximity` | boolean | code default false; shipped Titan baseline true | Drops same-length candidates that need a far-key substitution; adjacent-key bonus in edit-type ranking | Auto-correction | "Keyboard Proximity Ranking" |
| `use_edit_type_ranking` | boolean | false | The edit-type score term (insert 0.5 > substitute 0.4/0.2 > delete 0.3/0.1/0) | Auto-correction | "Edit Type Ranking" |
| `user_dictionary_entries` | string (JSON array of `{"w","f","u"}`) | `[]` | The personal dictionary | Personal dictionary; strip add-word | "Personal dictionary" |
| `trackpad_gesture_add_word_enabled` | boolean | true | Whether the left-third trackpad gesture adds the add-word candidate | none (no UI; backed up) | "Add words with gestures" (string exists, unused) |
| `trackpad_gesture_add_word_full_width_enabled` | boolean | true | Whether any third adds the candidate when it is alone on the strip | none (no UI) | "Full-width add-word swipe" (string exists, unused) |

Not settings: the confidence threshold (0.02), the suggestion count (3), the per-language length
allowance (English 2, all others 0), the primary-dictionary merge bonus (0.35), the cursor
debounce (120 ms).

Related settings owned by other documents and read here: `comma_space`,
`french_punctuation_spacing`, the auto-space punctuation set (`text-input.md`), the additional
suggestion languages per input style (`dictionaries-languages.md`), `auto_capitalize_first_letter`
(`text-input.md`).

The shipped baseline (`common/default_settings.json`, applied once to every install) sets
`auto_replace_on_space_enter = true`, `max_auto_replace_distance = 2` and
`use_keyboard_proximity = true`; the other keys fall to their code defaults. On a Titan the
effective configuration is therefore the most aggressive one the engine offers: distance 2 with a
length change of up to 2.

Switch enablement on the Auto-correction screen: "Automatic correction", "Accent & spelling
marks", "Keyboard Proximity Ranking" and "Edit Type Ranking" are greyed out while
`suggestions_enabled` is false. The "Suggestions while typing" switch is itself greyed out while
false, so once turned off it cannot be turned back on from this screen (only by a settings
restore or reset).

## 14. Device facts

- D1. The typed-word corpus rows `definetly -> definitely`, `sensitivy -> sensitivity` and
  `Oliva -> Olive` come from a debug export off the maintainer's Titan 2 Elite on 2026-08-31,
  where the first two were logged as skipped with reason `unsafe_shape` and the third applied.
  Evidence: header comments of the shape-profile test and `en_cases.tsv`; commit 51df601.
- D2. The shipped Titan baseline enables automatic correction at distance 2 with proximity
  ranking on, captured from a working Titan 2 Elite. Evidence: `common/default_settings.json`
  entries and the baseline code comment; commit "settings baseline" history in
  `PHYSIBOARD_CHANGES.md` 2.0.2.
- D3. Proximity distances use the Titan's compact physical layout: three letter rows with the
  bottom row split around the space bar (`z x c v` left, `b n m` right). Evidence: the layout
  comment in the ranking source; `docs/plans/autocorrect-rework.md` notes the real rows are
  staggered differently and proposes deriving adjacency from the layout row strings instead.
- D4. Every replacement fires the keyboard's haptic feedback on the Titan. Evidence: haptic
  calls at each commit site; `dictation.md` covers the haptic mechanism.
- D5. Autocorrections were removed from the debug export by default in 2.0.3 because the export
  was leaving the phone with the user's typed words. Evidence: `PHYSIBOARD_CHANGES.md` 2.0.3
  "Autocorrections stay on your phone".
- D6. Length-changing corrections were enabled for English in 2.0.3 and the English dictionary
  rebuilt with the confidence threshold in 2.0.7. Evidence: `PHYSIBOARD_CHANGES.md` 2.0.3 and
  2.0.7; commits 51df601, 6512351, b280e67.

## 15. Edge cases, quirks and known bugs

| Situation | Behavior | Why |
|---|---|---|
| Type `its` then Space with English rules enabled | Becomes `it's ` | Bundled rule `its -> it's`; the known-word guard is bypassed whenever the enabled-languages list is non-empty, which is the default. Same for `ill`, `wed`, `shell`, `id`, `hes`, `shed` |
| Type `dont` in an email or URL field, then Space | `dont ` unchanged | Restricted fields disable both the suggestion engine and text replacements |
| Backspace right after `its -> it's` | Restores `its`; `its` is not replaced again until a new letter is typed | Undo and the rejected set; rejection is cleared by the next letter or digit |
| Undo, then Space again without typing | `its ` committed, not replaced; a second Backspace deletes the space normally | The word is in the rejected set and the undo memory was cleared by the undo |
| Every rule set switched on in the Text Replacements screen | Only the system language set and Recipes are searched | The empty string is stored and read back as the default pair |
| Delete a bundled rule (for example `its -> it's`) on the Edit screen | The rule keeps applying | Save writes the remaining rules as the custom set, which overlays the bundled set; a bundled rule absent from the custom set is not removed. Verify on device |
| Turn "Suggestions while typing" off | The switch is disabled and cannot be turned back on from that screen | The switch's enabled state is bound to the value it controls |
| PhysiBoard Recipes (`x-pastiera`: `bb`, `ppp`) | Cannot be toggled or edited in the UI | Filtered out of every list on the Text Replacements screen while enabled by default |
| A shipped Titan with the code defaults | Would not autocorrect at all | Code default for `auto_replace_on_space_enter` is false; only the baseline turns it on |
| An additional suggestion dictionary is still loading | Words are treated as known; no fuzzy correction | Deferring is cheaper than a wrong replacement |
| Word preceded by an emoji, then Space | No correction, no rule | The 32-character scan finds a non-word symbol: hard boundary |
| Typo that resolves to two real words almost equally (`definately`) | Not committed when the margin is < 2 percent; offered on the strip | Too close to call |
| `definately` in practice | Corrected to `defiantly` | The scorer prefers `defiantly` clearly, not narrowly; see section 16 |
| `wierd` with the old list | Corrected to `wired` | Same shape: a real word at equal or lower distance; the rebuilt list and threshold reduce wrong to ≤ 1 |
| `work` then Space when `works` is more frequent | Left alone | Pure suffix growth is never a correction |
| `hallo` when the dictionary has `Halle` | Left alone | Lowercase input is never corrected to a capitalized candidate unless case-only |
| `problem` when only `Problem` exists | Becomes `Problem` | Primary case repair, runs even when the word is known elsewhere |
| `derriere` in French | Becomes `derrière` | Accent-only variant at distance 0 passes the orthographic exception, minimum length 2 |
| `Und` typed where `und` is the entry | Left alone | The recased replacement equals the typed word: same replacement |
| `l'a` | Not split | Root shorter than 3 letters |
| `rock'nroll` | Not split | Prefix longer than 3 and not in the allowed list |
| Single letter typed | No fuzzy candidates; accented variants and elisions offered | N = 1 skips the fuzzy index |
| Word longer than 48 characters | Tracker stops growing | Hard cap |
| User word added while another language is loaded | Appears in both | The personal dictionary is global; quick-add touches every loaded dictionary |
| A word tapped from the strip while Caps Lock is on | Not uppercased by Caps Lock | Casing follows the typed word, not modifier state; only next-word and starter suggestions read the modifiers |
| Enter in a field with a Send action | No correction, no rule | Enter bound to an editor action is never a correction boundary |
| Enter in a multi-line field, legacy path corrects | Newline committed by the keyboard, Enter consumed | Multi-line commits its own boundary; single-line leaves Enter to the app |
| Out of memory while loading a dictionary | Suggestions stay off for that language | The load is abandoned and not retried until the next request |
| Frequency thresholds 300/250/200/150 on completions | Never remove a main-dictionary word | Effective frequency of the rarest word (raw 66) is 580 |
| Completion filter on raw frequency (150/100/80/60) | For a 4-letter input, words with raw frequency below 80 never complete; for a 3-letter input, below 100; for 1 to 2 letters, below 150 | Second, raw-frequency filter for non-user completions |
| Source comment says the threshold default is 0.10 | The default is 0.02 | Comment not updated when the dictionary rebuild lowered it; the changelog and commit b280e67 are correct |
| Backup | Personal words, enabled sets, custom sets and `user_defaults.json` are included; `user_ngrams.db` is not | Backup contract |

## 16. Known unfixed cases and the planned rework

**Still wrong: `definately -> defiantly`.** Both `definitely` and `defiantly` are real words at
edit distance 2 from the typo; the scorer prefers `defiantly` clearly (frequency and same-length
bonus), so no margin threshold catches it and no word list changes it. It needs a cost model that
knows `a` and `i` are not adjacent keys, or a context prior that has seen "definitely not" and
never "defiantly not". Recall on the typo corpus sits at 0.650 as a deliberate debt.

**Structural gaps** named in `docs/plans/autocorrect-rework.md` (status: proposed, W1 and W3
and W7 done):

- W0: turn the distance dial to 1 for a day as a diagnostic. Not a fix: `salve -> slave` is a
  transposition at distance 1.
- W1: evaluation harness. Done (section 12).
- W7a/b: word list. Done for English (section 11). Remaining: the other eleven languages; owning
  the corpus ingest so the 0..255 quantization and the `^0.75` curve stop being a guess.
- W2: geometry into scoring. Replace the uniform-cost retrieval-then-veto with a rescoring pass
  over about 16 fuzzy candidates using per-edit costs (adjacent substitution 0.35, two keys away
  0.7, far key 1.6, transposition 0.4, dropped letter 0.5, doubled letter 0.3, other insertion
  0.9, first-letter change +0.8 surcharge), deriving adjacency from the layout row strings
  (`qwertyuiop`, `asdfghjkl`, `zxcvbnm`) rather than the hand-coded grid. Delete the 2.5-key veto
  and the 0.2/0.4 nudges.
- W3: confidence threshold. Done as a margin test with a fixed 0.02; the plan still wants one
  score with two thresholds (commit / suggest / leave alone) and a plain-language aggressiveness
  setting replacing `max_auto_replace_distance`, keeping the old key honoured for one release.
- W4: collapse the three boundary paths (legacy text-replacement manager, the process-global
  rule engine with its own rejected set, and the suggestion engine's boundary) into one: one
  boundary derivation, one undo, one rejected-word set, with text replacements as a candidate
  source rather than a parallel engine. The two undo algorithms have already drifted (one uses a
  batch edit, one does not).
- W5: context prior. Pass the previous word into suggestion requests and use the learned bigrams
  as a prior over correction candidates, from the in-memory overlay, never a synchronous database
  read on the boundary keystroke.
- W6: durable rejection memory. Persist (locale, typed -> candidate) rejections with slow decay,
  and notice the manual fix (backspacing over a correction and retyping it), which today is not
  detected at all.

Guardrails from the plan: every tuning change reports the false-correction rate; the rescoring
stage adds no resident index; the boundary keystroke budget is measured in milliseconds.

## 17. Test cases

Each row: setup, input sequence, expected outcome. "Type X then Space" means the letters of X
committed one by one through the tracker, then the Space boundary. Dictionary = a small
vocabulary unless stated. Settings default to code defaults unless stated.

| # | Setup | Input | Expected |
|---|---|---|---|
| T1 | Tracker | `H`, `i` | current word `Hi`, two change notifications |
| T2 | Tracker | `Hello`, `!` | reset notification; current word empty |
| T3 | Tracker | `Tests`, Backspace ×5 | `Test`, `Tes`, `Te`, `T`, then empty |
| T4 | Tracker | `a`, then U+0008 `b` | current word `b` |
| T5 | Tracker | `l`, `'`, `a` | `l'`, then `l'a` |
| T6 | Tracker max 5 | `1234567` | current word `12345` |
| T7 | Dictionary not ready, entry `hallo` | suggest `hallo` | empty; after ready, suggest `hall` -> non-empty |
| T8 | `hallo` 200, QWERTY, proximity on | suggest `hsllo` and `hmllo` | `hallo` scores higher for `hsllo`; `hallo` absent for `hmllo`; present with proximity off |
| T9 | `perché` 200, accent matching | suggest `perche` | first candidate `perché` |
| T10 | `derrière` 200 | suggest `derriere` | first `derrière`, distance 0 |
| T11 | `Problem` 200, `Probleme` 250 | suggest `problem` | list contains `Problem` |
| T12 | `hallo` 100 main, `hallx` 100 user | suggest `hall` | first `hallx`, source user |
| T13 | `apple` 200, `queen` 200 | QWERTY `qpple` -> contains `apple`; AZERTY `aueen` -> contains `queen` | |
| T14 | `œil` 116, `Neil` 97 | suggest `oeil` -> `œil` first; suggest `l'oeil` -> `l'œil` first | |
| T15 | `amico` 120 | suggest `dell'amivo` | first `dell'amico` |
| T16 | `L'Oréal` 120, `l'oral` 220 | suggest `l'oreal` -> `l'Oréal`; suggest `l'orak` -> `l'oral` | |
| T17 | Casing | `apple`/`app` -> `apple`; `apple`/`App` -> `Apple`; `apple`/`APP` -> `APPLE`; `McCartney`/`mcc` -> `McCartney`; `McCartney`/`MCC` -> `MCCARTNEY`; `apple`/`app` forced -> `Apple`; `l'amico`/`L'am` -> `L'amico`; `123`/`12` -> `123` | |
| T18 | Fuzzy index, `hallo` 100 | lookup `hallo` -> distance 0; `hxllo`, `hllo`, `haallo`, `halol` -> distance 1; `hxxlo` -> distance 2; `hxxxo` -> absent | |
| T19 | Fuzzy index, `hallo` 10, `halle` 100 | lookup `hallx` | `halle` then `hallo` |
| T20 | Fuzzy index, prefix 7, `donaudampfschiff` | lookup `donaudampfschixf` and `donaxdampfschiff` | both find the word |
| T21 | Fuzzy index, `io`, `il` | lookup `i` | both found |
| T22 | Split | `l'amico` -> `l'` + `amico`; `l'a` -> none; `l’amico` -> `l'`; `dell'amico` -> `dell'` + `amico`; `rock'nroll` -> none | |
| T23 | Variants | accent-only: `perche`/`perché` true, `oeil`/`œil` true, `hallo`/`halle` false, `hallo`/`hallo` false; case-only `problem`/`Problem` true | |
| T24 | Safe shape, distance 2, en | `definetly -> definitely` d2 accept; `sensitivy -> sensitivity` d2 accept; `Oliva -> Olive` d1 accept; `work -> works` refuse; `behavio -> behavior` refuse; `elly -> ally` refuse; `definetly` d5 refuse; next-word kind refuse; `dfntly -> definitely` refuse; `tr` and empty language: `definetly` refuse, `Oliva -> Olive` accept in `tr` and `hu` | |
| T25 | Safe shape, distance 1 | `ich -> bin` (next-word, d0) refuse; `hal -> hallo` d0 refuse; `wil -> will` accept; `kaputte -> kaputt` refuse; `idk -> IFK` refuse; `hallo -> Halle` refuse; `ding -> fing` refuse; `fihg -> fing` accept; `problem -> Problem` d0 accept | |
| T26 | Rule engine, custom `de` `{"agree":"are"}`, `agree` known | text `agree ` | no rule |
| T27 | Custom `fr` `ca -> ça`, enabled {fr}, `ca` known | text `Ca ` | `Ca -> Ça` |
| T28 | Custom `en` `teh -> the`, enabled {en} | text `Dont ` | `Dont -> Don't` (bundled rules survive a custom overlay) |
| T29 | Enabled {en, fr}, everything known | text `Dont ` in `de` -> `Don't`; `ca ` -> `ça` | known-word guard bypassed |
| T30 | Enabled {fr}, word known | `jespere` -> `j'espère`, `etre` -> `être`, `Noel` -> `Noël`, `cestadire` -> `c'est-à-dire`, `luimeme` -> `lui-même`, `leau` -> `l'eau` (27 pairs in the source test) | |
| T31 | Boundary, `da` 255, `ca` 50, auto-replace on d1, rule `Ca -> Ça` | word `Ca`, Space | text `Ça `, replaced, replacement `Ça` |
| T32 | Boundary, auto-replace off, rule `tssst -> totallynewsubstitution` | word `tssst`, Space | `totallynewsubstitution ` (rules run with auto-replace off) |
| T33 | Boundary, rule maps `qu'on` to itself | word `qu'on`, Space | `qu'on `, reported replaced |
| T34 | Boundary, `derrière` 200, French, auto-replace on d1 | word `derriere`, Space | `derrière ` |
| T35 | Boundary, auto-replace off, French spacing on, `bonjour` | word `bonjour`, `;` | `bonjour ;` (narrow no-break space), not replaced, committed |
| T36 | Boundary, comma-space on, `hello` | word `hello`, `,` | `hello, ` |
| T37 | Boundary, auto-replace off, on-screen Space (null event) | word `hello` | `hello `, committed true |
| T38 | Custom `fr` `ct -> c'était`, enabled {fr}, `CT` 255, `ct` known | word `Ct`, Space | `C'était ` |
| T39 | `trat` 255, `that` known via active dictionaries, auto-replace on d1 | word `that`, Space | `that `, not replaced |
| T40 | `und` 255, auto-replace on | word `Und`, Space | `Und `, not replaced |
| T41 | `Problem` 220, `problemlos` 255, `problem` known elsewhere | word `problem`, Space | `Problem ` |
| T42 | `und` 255, `Und` 200 | word `und`, Space | `und ` |
| T43 | All smart features off, text `id` | physical Space; on-screen Space | `id ` both |
| T44 | Older replacement path (7.4), custom `fr` `ct -> c'était`, enabled {fr}, text `Ct` | Space | `C'était `; with French spacing and boundary `?`: `C'était ?` |
| T45 | Older replacement path (7.4), comma-space, auto-correct off | `Hi` + `,` -> `Hi, `; `Hi ` + `,` -> `Hi, `; `Hi,` + `,` -> no duplicate | |
| T46 | Add-word auto-space | text `Wiederspruch`, nothing after | `Wiederspruch `, auto-space pending; then `.` -> `Wiederspruch. `; with `.` after cursor: unchanged, no pending |
| T47 | Gesture policy | left third, enabled, candidate, no suggestions -> add; right third, full-width, no suggestions -> add; centre third, full-width, suggestions present -> no; disabled -> never | |
| T48 | Substitution store | add ` OMW ` -> `on my way` with existing `brb`: keys `omw, brb`; blank trigger or blank replacement refused; adding `svp` for `fr` makes `fr` enabled and `svp` resolves immediately; adding `tssst` for `de` resolves `tssst ` when not known | |
| T49 | Edit dialog checkbox | replacement ` pamphlet ` -> added as `pamphlet`; existing `Pamphlet` -> not duplicated; `?!` -> refused | |
| T50 | Next word | learn `ich bin` after Space -> after `ich ` strip shows `bin` then starters; `;` keeps context; `.` learns then resets; typing a letter overrides; dismiss forgets the bigram | |
| T51 | Confidence | top 1.0, runner-up 0.99 -> 0.01 (< 0.02, too close); top 1.0, no runner-up -> 1.0; top 0 -> 0 | |
| T52 | Eval, small vocabulary, distance 2, proximity on | 74 cases | fcr 0.000, recall ≥ 0.775, controls ≥ 24 |
| T53 | Eval, shipped English dictionary | 74 cases; 116 controls | 0 known words clobbered; fcr ≤ 0.014; recall ≥ 0.650; wrong ≤ 1; 0 controls missing; 0 overruled |

## 18. Keep / Drop for 3.0

| Item | Decision | Reason |
|---|---|---|
| Current-word tracking with the 48-character cap and apostrophe rule | Keep | Core of every feature here |
| Cursor-move re-read with 120 ms debounce | Keep | Needed for apps that edit their own fields |
| Prefix completions and fuzzy retrieval (distance 2, prefix 4) | Keep | The suggestion strip depends on it; retrieval is not the weak part |
| The 13-term additive score | Undecided | Works, but the rework replaces it with a cost model; port it verbatim first, then rescore |
| Keyboard grid for proximity | Keep, from row strings | The Titan is the only device; derive the grid from its layout rather than a hand table |
| AZERTY and QWERTZ grids | Drop | Titan 2 Elite ships QWERTY; other layouts are software remaps, keep only if a Titan variant exists |
| Edit-type ranking toggle | Drop | Off by default, unmeasured, subsumed by the planned cost model |
| Accent matching toggle | Keep | Cheap and correct for French and Italian users |
| Three-slot strip mapping (centre, right, left) | Keep | Users have learned it; the trackpad thirds depend on it |
| Next-word bigram learning and starter words | Keep | Personal, private, already asynchronous |
| Long-press hide and delete on the strip | Keep | Only way to remove a learned word without settings |
| Add-word candidate slot and trackpad add gesture | Keep | Titan-specific interaction that works |
| Add-substitution sheet from long press | Keep | Quick path to a personal rule |
| Personal dictionary and default user words file | Keep | Rule 10 depends on it; the defaults file can become plain personal words |
| Text replacement rule sets, bundled `en`, `it`, `fr`, `x-pastiera` | Keep, as a candidate source | W4: fold into the single pipeline; the `its -> it's` class of rules needs the known-word guard actually enforced |
| Empty bundled `de`, `es`, `pl` sets | Drop | Nothing in them |
| `forza juve -> forza napoli` | Drop | Upstream joke rule |
| Two-word rule sequences | Keep | Needed for Italian elisions |
| Symbol triggers | Keep | Overlaps with text expansion; decide one owner in `expansion-clipboard-pickers-launcher.md` |
| Second (older) text-replacement path | Drop | Unreachable on the device; both flags it depends on derive from the same field restriction |
| Rules disabled in restricted fields | Keep | Email and URL fields must never be rewritten |
| Separate rejected sets and undo memories | Drop | W4: one of each |
| Session-only rejection cleared by the next letter | Replace | W6: durable rejections with decay |
| Case repair from the primary dictionary | Keep | Cheap, measured safe |
| Confidence margin with fixed 0.02 | Keep | Then evolve into commit/suggest thresholds with a user dial |
| `max_auto_replace_distance` slider 0..3 | Keep for one release | Replace by an aggressiveness dial per W3 |
| Per-language length allowance table | Keep | Only English is verified; others stay conservative |
| The known-word invariant and the rebuilt English list pipeline | Keep | The one rule users feel |
| The other eleven bundled dictionaries | Undecided | Same upstream corpus problem; rebuild per language or drop languages nobody on a Titan uses |
| Eval harness, corpora and ratchets | Keep | The only gate on quality; extend with the device-harvested corpus |
| Debug capture of attempts and commits, off by default in exports | Keep | How the corpus gets harvested |
| "Suggestions while typing" switch that disables itself | Fix | Bug; the switch must stay enabled |
| Hidden `x-pastiera` set in the UI | Fix or drop | Either list it or remove the set |
| On-screen keyboard Space path into the boundary engine | Drop | No soft keyboard in 3.0 |
| Text-field trigger for a boundary from the on-screen keyboard (null event) | Drop | Same |

## 19. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/docs/plans/autocorrect-rework.md
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md
- /home/disdiqqq/projects/pastiera/scripts/build_en_wordlist.py
- /home/disdiqqq/projects/pastiera/scripts/build_symspell_dict.py
- /home/disdiqqq/projects/pastiera/scripts/README.md
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/SuggestionEngine.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/AutoReplaceController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/SuggestionController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/SymSpell.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryRepository.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryIndex.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/DictionaryEntry.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/WordNormalization.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/CasingHelper.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/SuggestionSettings.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/UserDictionaryStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/CurrentWordTracker.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/TypoShapeProfile.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/NextWordPredictor.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/suggestions/UserNGramStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/AutoCorrectionManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/Punctuation.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/core/InputContextState.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AutoCorrector.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AddWordCommitHelper.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/AddSubstitutionActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/TrackpadAddWordGesturePolicy.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/InputEventRouter.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/CandidatesBarController.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/DebugCaptureStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/suggestions/SuggestionButtonHandler.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/suggestions/ui/FullSuggestionsBar.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AutoCorrectionSubstitutionStore.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AutoCorrectEditScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AutoCorrectionCategoryScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AutoCorrectSettingsScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsBaseline.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/AppBroadcastActions.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/BackupContract.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/backup/RestoreManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/default_settings.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_de.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_en.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_es.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_fr.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_it.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_pl.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/autocorrect/auto_corrections_x-pastiera.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries/user_defaults.json
- /home/disdiqqq/projects/pastiera/app/src/main/assets/common/dictionaries_serialized/ (directory listing)
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/eval/AutocorrectEval.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/eval/AutocorrectEvalTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/eval/AutocorrectEvalRealDictionaryTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/eval/VocabularySweepTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/eval/EvalDictionaryRepository.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/AutoReplaceControllerLogicTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/SuggestionEngineTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/CasingHelperTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/SymSpellTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/CurrentWordTrackerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/WordNormalizationTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/SuggestionControllerNextWordTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/UserNGramStoreTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/suggestions/NextWordPredictorAsyncLearningTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/core/AutoCorrectionManagerTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/AddWordCommitHelperTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/TrackpadAddWordGesturePolicyTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/inputmethod/DisabledSmartFeaturesRegressionTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/TypoShapeProfileTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/AutoCorrectEditScreenTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/AutoCorrectionSubstitutionStoreTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/resources/autocorrect/en_cases.tsv
- /home/disdiqqq/projects/pastiera/app/src/test/resources/autocorrect/en_controls.tsv
- /home/disdiqqq/projects/pastiera/app/src/test/resources/autocorrect/en_vocab.tsv
- git log messages for commits b280e67, 6512351, 3d3cf3d, de1bc37, 6a422be, 51df601
