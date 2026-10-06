# Autocorrect that reads the sentence

Status: built, measured on the desktop harness, **not yet verified on the Titan**. Spec:
`docs/spec/autocorrect-suggestions.md` §10 (the mix-up exception), §12 (the sentence harness),
§16 (W2 and W5).

## Why

Another keyboard advertises "fixed 61% of typos, changed a correct word only 1 time in 3,700;
reads the sentence, so 'wagged it's tail' becomes 'wagged its tail'; Backspace undoes any fix".
The maintainer wants PhysiBoard's autocorrect at least that aggressive and sentence-aware.

The suggestion bar is gone for good, so a correction has no on-screen alternatives: it must be
right on its own, and Backspace straight after is the only way back. A wrong correction costs more
than a missed one, so every step of aggressiveness here was bought with a measurement.

## What changed

### 1. The word-pair table is read (`ContextModel`, `:core:dict`)

`app/src/main/assets/dictionaries/en.bigrams` (built by `scripts/build_bigrams.py` from Tatoeba,
CC BY 2.0 FR) is parsed into primitive arrays: the words as one UTF-8 blob with an offset per id,
an id permutation sorted by word bytes for lookup by binary search, the unigram counts, and the
pairs as a CSR matrix (row offsets, next ids ascending within each row, `short` counts). A lookup is
two binary searches; nothing is boxed.

`P(next | previous)` is interpolated Kneser-Ney with one absolute discount (0.75): the pair count
less the discount over the row total, plus the mass the row gave up spread over how many distinct
words each word follows. That continuation distribution is the point: a word that only ever
follows one other word (`francisco`) is cheap after it and dear anywhere else, which a plain
unigram backoff cannot say. An additive floor keeps a dictionary word the table never saw at a
small, finite probability rather than zero. One parameter, textbook value, no tuning table; the
table is large enough not to need the discount estimated.

The reader checks magic, version and CRC and every offset; anything wrong returns null and the
keyboard corrects exactly as it did before the table existed. `:ime` reads the asset on the same
background thread that loads the dictionary (missing asset = no context) and hands the primary
language's table to the pipeline.

| | |
|---|---|
| Asset | 3.84 MB (`en.bigrams`), 53,383 words, 496,515 pairs |
| Parse, desktop JVM | 50 ms (expect a few hundred ms on the Titan, off the main thread) |
| Resident | about 4.7 MB |

### 2. The word just finished: a noisy channel (`ContextCorrection`, `ChannelCost`)

When the table is loaded, a word no dictionary or word store spells this way is rescored as a
noisy channel. Every dictionary word within reach (up to 24 keys from the fuzzy index, edit
distance 2, or 1 for words of four letters or fewer, each key's spelling variants included so
`its`/`it's` both compete) gets

    score = ln P(candidate | word before)  -  7 × slip cost(candidate -> typed)

The prior is half the context probability and half the plain word frequency
(`unigramMix = 0.5`), so a lone word in an empty field does not lose `receive` just because few
sentences start with it. The word before is read the way the table was counted
(`SentenceContext`): sentence start at the start of the text, a new line, or `. ! ? ; :`; commas,
quotes and brackets passed over; digits and symbols mean "no context".

The slip cost uses the Titan's staggered rows (`TitanKeyGeometry`), starting from §16's table:

| Slip | Cost |
|---|---|
| substitution, keys touching | 0.35 |
| substitution, one key between | 0.8 |
| substitution, vowel for vowel (spelling, not fingers) | 0.9 |
| substitution, farther | 1.6 |
| transposition | 0.4 |
| dropped letter / half of a double dropped | 0.5 / 0.3 |
| doubled letter | 0.3 |
| extra key beside a pressed one / any other extra letter | 0.45 / 0.9 |
| first letter changed (not by swapping the first two) | +0.8 |
| apostrophe added / removed | 0.15 / 0.6 |

Against the candidates stands "meant as typed": a weight of `ln` -13 for a lowercase word of three
letters, 1.5 less per further letter (a long unknown string is a slip far more often than a word),
-12 for a capitalised word starting a sentence, -9 for a capitalised word inside one (a name, far
more often than a typo). The best candidate replaces the word only when it holds at least 70% of
the total. There is no "suggest" band: a close call is left alone.

Kept hard limits: a known word is never touched (§10); two-letter words are never corrected; no
lowercase word becomes a capitalised one or an acronym; an all-caps or inner-capital word is left
(`USSR`, `iPhone`); a known word plus a contraction or possessive ending is a word (`must've`,
`Sami's`, `players'`); British and American spellings of one word are both words (`realised`,
`neighbours`, `travelled`); a known word plus an inflection ending is a word the dictionary lacks,
not a slip (`millennials`, `rehydrated`); the user's rejection (Backspace undo) holds as before;
personal words are known words, and also candidates.

Two pre-existing defects fixed on this path only: an accent or apostrophe repair may only add
marks, never strip them (the old path turned every unlisted possessive into a plural: `adapter's`
-> `adapters`, `players'` -> `players`, `Baháʼí` -> `Baha'i`), and a typed word with an inner
capital is no longer "case-repaired" to the dictionary's casing (`McDonald's` -> `Mcdonald's`,
`iPhone` -> `iphone`). Without the table the old decision path runs unchanged, defects included
(its rule matcher now sees 64 characters before the word instead of 32, which changes nothing,
since rules match at the end); every other bundled language is on it.

Settings on this path (fixed after review; it used to ignore two of them):
`max_auto_replace_distance` caps the distance as before, and at 0 ("Off") blocks every slip but
still lets a missing apostrophe or accent be added (`I dont` -> `I don't`), as the old path and §13
say; `accent_matching_enabled` off removes that repair (the typed word's other spellings are not
candidates); `use_keyboard_proximity` has no effect here, by decision: the slip costs are the key
geometry, so turning it off would mean pricing `tge` and `tme` alike for `the`, which no setting
asks for. The switch keeps its meaning on the path without a table (every other language), and its
subtitle says so.

### 3. The word before: the mix-up fix (`WordMixups`, behind `fix_word_mixups`, off)

When word N's boundary arrives, after word N has been settled, word N-1 is judged if it is in a
hand-curated confusion set, it is separated from N by exactly one space, it stands on its own (the
start of the text, a space, or an opening quote or bracket before it: never `@your` or
`site.com/its`), and the field reported it:
`P(N-1 | N-2) × P(N | N-1)` for it and each twin. A twin replaces it only when it is at least
`e^5` (about 150 times) likelier overall, no worse than `e^2` on either side alone ("Was there
money" fits "their money" on the right but "was there" on the left), and the table has seen it
beside one of the two neighbours at least five times. Case is kept (`Your welcome` -> `You're
welcome`), the pronoun is always a capital (`ill go` -> `I'll go`), a curly apostrophe in the
user's text stays curly, and word N goes back exactly as the field held it unless it was itself
corrected.

Both edits are one replacement of the span from N-1 to the cursor, so one Backspace puts back
exactly what was typed; the rejection it records covers each word, so the next Space neither redoes
the mix-up nor re-corrects the word after it. An editor that reports nothing never reaches this
(the drift check stops the boundary first).

Fixed after review: the previous word must have been typed here, in sequence (from an empty
word, ended by a boundary the engine evaluated, with no cursor move, input restart, undo, paste,
suggestion accept or Backspace behind the current word since), and a word the user chose on
purpose (put back by an undo, or accepted from a suggestion) is pinned past the next word's
letters. Before, the rejection was the only guard and it emptied on the next letter, so
`it's tail`, undone and continued as `tails`, was flipped again; an accepted `it's` was flipped by
the next word; and tapping after old text and pressing Space judged a word typed long before.
`ContextPipelineTest` replays each through the keyboard's pipeline. Off by default, as every new Space behaviour ships:
Settings > Auto-correction > "Fix mixed-up words".

## Measurements

The harness (`core/text/src/test/kotlin/.../eval/SentenceEvalTest.kt`, §12) replays each sentence
word by word through `BoundaryEngine`, exactly as the keyboard calls it: the tracked word, the
window of text before the cursor (corrections included), the shipped dictionary, the default word
list and the shipped Titan settings (automatic correction on, distance 2, proximity on). Corpora,
from 6,000 Tatoeba sentences held out of the table's counts:

- a: the sentences as written: 45,645 correct words;
- b: one synthetic Titan slip per sentence in a word of three or more letters, the five kinds in
  equal measure (adjacent-key substitution, dropped letter, doubled letter, transposition, extra
  adjacent key), seeded: 5,999 typos;
- c: `en_cases.tsv` (the device corpus) and `en_misspellings.tsv` (classic misspellings), each
  word typed alone into an empty field: 166 typos, 35 controls;
- d: sentences using a confusion-set word, flipped to a twin (up to 100 per member), replayed to
  the word after it.

BEFORE is the engine at a357dee, measured by swapping the harness's one engine call
(`EngineCall.kt`) into a checkout of that commit; same harness, same corpora, same assets.

| Corpus | | fixed | missed | wrong | correct words changed | recall | changed per words |
|---|---|---|---|---|---|---|---|
| a clean | BEFORE | | | | 319 | | 1 per 143 |
| a clean | AFTER | | | | 10 | | **1 per 4,564** |
| a clean, test half | BEFORE | | | | 157 | | 1 per 145 |
| a clean, test half | AFTER | | | | 6 | | 1 per 3,818 |
| b Titan typos | BEFORE | 1,823 | 3,485 | 691 | 265 | 0.304 | 1 per 149 |
| b Titan typos | AFTER | 4,500 | 1,411 | 88 | 6 | **0.750** | 1 per 6,606 |
| b, test half | BEFORE | 934 | 1,707 | 359 | 135 | 0.311 | 1 per 147 |
| b, test half | AFTER | 2,284 | 668 | 48 | 5 | 0.761 | 1 per 3,982 |
| c misspellings | BEFORE | 121 | 40 | 5 | 0 | 0.729 | none in 35 |
| c misspellings | AFTER | 138 | 25 | 3 | 0 | **0.831** | none in 35 |
| d mix-ups (shipped sets) | BEFORE | 0 of 1,773 | | | | 0 | |
| d mix-ups (shipped sets) | AFTER, setting on | 1,414 of 1,773 | | | 0 on clean text | **0.797** | |

Correct words changed on clean text, by kind of word (45,645 words):

| | known (in a dictionary) | unknown lowercase (207) | unknown capitalised, sentence start (475) | unknown capitalised, mid-sentence (228) |
|---|---|---|---|---|
| BEFORE | 3 | 56 | 189 | 71 |
| AFTER | 0 | 9 | 1 | 0 |

The old engine changed a quarter of the unknown words it met, nearly all of them names (Tatoeba
is full of Ziri, Rima, Mennad, Skura: `Ziri` -> `Ziti`, `Rima` -> `Roma`) and contractions the
dictionary lacks (`must've` -> `muscle`, `That'll` -> `That's`). The ten left after: `texting`,
`texted` (missing from the dictionary), `fait` and `accompli` (French), `valour`, `leant`,
`attester`, and three that are typos in Tatoeba's own text (`presidenf`, `albumn`, `Mery` for
`Mary`), so the honest count of correct words changed is seven.

Recall on b counts every typo, including the 752 (12.5%) that happen to spell another real word
(`form` for `from`; most dropped letters in short words); §10 forbids touching those, so the
ceiling without the mix-up fix is about 0.87. Of the typos that are not real words, 0.858 are
fixed. By kind:

| Slip | BEFORE recall | AFTER recall |
|---|---|---|
| extra adjacent key | 0.302 | 0.864 |
| adjacent substitution | 0.397 | 0.740 |
| doubled letter | 0.321 | 0.918 |
| dropped letter (489 of 1,211 are real words) | 0.182 | 0.354 |
| transposition | 0.318 | 0.875 |

Against the claim that prompted this: 75% of typos fixed against their 61%, one correct word in
4,564 changed against their one in 3,700, both on text that is not theirs. The comparison is
indicative only: different text, different typo model.

### Confusion sets (corpus d, every measured set switched on)

Rule for keeping a set: at least 10 flips measured, at least 60% of them fixed, and not one
correct word changed on the 45,645-word clean text. "Originals" are the set's words in the clean
text, all switched on.

| Set | kept | flipped | fixed | wrong | originals | changed |
|---|---|---|---|---|---|---|
| its / it's | kept | 119 | 104 | 2 | 119 | 0 |
| your / you're | kept | 165 | 129 | 0 | 217 | 0 |
| their / there / they're | kept | 346 | 256 | 1 | 195 | 0 |
| then / than | kept | 77 | 54 | 0 | 77 | 0 |
| to / too | kept | 162 | 130 | 0 | 1,723 | 0 |
| lose / loose | kept | 13 | 9 | 0 | 13 | 0 |
| whose / who's | kept | 13 | 8 | 0 | 13 | 0 |
| were / we're / where | kept | 374 | 271 | 5 | 211 | 0 |
| cant / can't | kept | 71 | 66 | 0 | 71 | 0 |
| wont / won't | kept | 55 | 54 | 1 | 55 | 0 |
| lets / let's | kept | 30 | 30 | 0 | 30 | 0 |
| ill / i'll | kept | 38 | 32 | 0 | 38 | 0 |
| know / no | kept | 174 | 152 | 0 | 286 | 0 |
| new / knew | kept | 88 | 83 | 0 | 88 | 0 |
| quite / quiet | kept | 25 | 19 | 0 | 25 | 0 |
| weather / whether | kept | 23 | 17 | 0 | 23 | 0 |
| of / off | dropped: changed a correct word | 142 | 100 | 0 | 605 | 1 |
| here / hear | dropped: 51% fixed | 108 | 55 | 0 | 108 | 0 |
| well / we'll | dropped: 40% fixed | 47 | 19 | 0 | 48 | 0 |
| accept / except | dropped: 7 flips only | 7 | 7 | 0 | 7 | 0 |
| affect / effect | dropped: 2 flips only | 2 | 0 | 0 | 2 | 0 |
| advice / advise, brake / break, desert / dessert, lead / led, passed / past, peace / piece, principal / principle | dropped: under 10 flips | 2-9 each | | | | 0 |

`accept/except` fixed all seven and is the first to re-measure with more text. `of/off` changed
"off" to "of" once in clean text; it stays out.

### Tuning, and how much to trust it

The constants were swept (`SentenceSweepTest`, opt-in) on the first 3,000 sentences only, then
the last few candidates compared on all 6,000 to pick the one that stays under one change per
3,000 words on both halves (dev 4, test 6) with the most recall; the test-half rows above are
therefore not perfectly untouched. The sweep's main lessons: lowering the share needed to commit
or the weight of "as typed" buys recall and costs clean-text changes at roughly one for one;
the vowel cost has a cliff (0.6 changed a frequent sentence-start name 160 times; 0.9 does not);
the inflection rule halved clean-text changes for 0.7% recall.

The held-out sentences are not independent of the counts in the way real typing is: Tatoeba has
many near-duplicate sentences (the same sentence with another name), so the table has very
likely seen most held-out pairs in a sibling sentence. Real chat text will read worse to the
table than Tatoeba does, especially for the mix-up fix. That is the strongest reason the mix-up
fix ships off.

## Speed

Desktop JVM (the Titan is roughly 5 to 10 times slower), per boundary, all 45,645 clean words with
the mix-up fix on:

| | mean | median | 99th percentile | worst |
|---|---|---|---|---|
| BEFORE (every word) | 1,349 µs | 1,357 µs | 2,003 µs | |
| AFTER, clean text | 17 µs | 2 µs | 362 µs | 2.5 ms (JIT) |
| AFTER, text with one typo per sentence | 129 µs | 3 µs | 1,892 µs | 6.9 ms |
| AFTER, a word the dictionary does not know | about 1,400 µs | | | |
| AFTER the walk fix, clean text | 10 µs | 3 µs | 232 µs | 1.1 to 5 ms (JIT, GC) |
| AFTER the walk fix, text with one typo per sentence | 70 µs | 4 µs | 890 to 950 µs | 2.3 to 5.8 ms |
| AFTER the walk fix, a word the dictionary does not know | about 520 µs (0.7 ms at five letters or more) | | | |

A known word now costs microseconds: the old path ran the full suggestion ranking (a 200-entry
completion lookup and a distance-2 fuzzy walk) on every boundary only to discover the word was
known. An unknown word still pays the distance-2 fuzzy walk, which is nearly all of its cost
(the rescoring of up to 24 candidates is about 40 µs). Fixed after review, the walk itself
(`DictionaryIndex.neighbours`, which the path without a table and the suggestion lookups share)
now computes only the diagonal band of each distance row, and at each pruned prefix gallops
forward to the end of its run, comparing in place, instead of bisecting the rest of the 80,000
keys with a fresh prefix string each time: 1.35 ms -> 0.73 ms for a word of five letters or
more, exactly the same results (checked against a brute-force reference at distances 0, 1 and 2).
On the phone that is perhaps 4 to 7 ms for an unknown word against the 12 ms keystroke log line.
`SentenceEvalTest` now holds the 99th percentile on typo text under 1.5 ms (the old walk measures
2.0 ms there), not only the median. Nothing on the boundary does I/O; the table is in memory
before the first boundary uses it.

## Not verified, and judgement calls

- Nothing here has been typed on the Titan. The harness drives the engine, not the keyboard;
  `adb input` bypasses an IME, so device testing is the next step: Backspace after a mix-up fix
  in a real field, Enter and punctuation boundaries with a mix-up, and the phone's own timing log
  for unknown words.
- The mix-up fix ships off. The corpus is Tatoeba, not chat; turn it on for a few days before
  defaulting it.
- Corpus c is typed into an empty field, so every word gets the sentence-start context; that is
  what a lone word in a fresh field really gets.
- British spellings are treated as words, not typos (an American dictionary otherwise rewrites
  `realised`, `neighbours`). A user who wants them Americanised does not get that.
- `texting`, `texted`, `nonessentials` and similar are missing from the English list; adding them
  to `user_defaults.json` or the next list rebuild would remove two of the ten clean-text changes.
- An undo of a combined fix (`it's taill` -> `its tail`) restores both words at once.
- After a mix-up fix, the keyboard's own next-word learning used to keep the pairs with the old
  word (`wagged -> it's`, `it's -> tail`). Fixed after review: the boundary record names the
  rewritten word, and `:ime` takes back the one learn of `wagged -> it's` (a count down by one, not
  the pair's whole history), learns `wagged -> its`, and learns `its -> tail`. The database writes
  now go through one thread in order, so the take-back cannot overtake the learn it takes back.
- The key geometry (`TitanKeyGeometry`) is QWERTY only: the rows are fixed letter strings, not
  read from the active layout. On QWERTZ or AZERTY the letters those layouts move (`y`/`z`;
  `a`/`q`, `z`/`w`, `m`) are priced where QWERTY has them, so a slip onto the key physically beside
  one of them is priced as a far key (fixed less often) and a slip onto a key beside it only on
  QWERTY as an adjacent one; every other letter is right. Only English has a table, so this reaches
  English typed on those layouts only. The fix is to build the rows from the layout's letter
  positions, as the path without a table does (§3.5); not done, since no shipped English input
  style uses either layout.
- With an extra language loaded, its unknown words are weighed with the English table, so a
  Spanish slip leans towards an English word. Correct Spanish words are still known words.
- A previous word at index 0 of a short read is trusted as the start of the text, as the rest of
  the keyboard already does for capitalisation; an app that returns a mid-word cut without saying
  so could in principle make `habits tail` look like `its tail`.
- The confusion-set rule (10 flips, 60%, zero clean changes) is mine; `accept/except` and
  `here/hear` are the closest calls.
- Release APK (unsigned): 9,642,551 bytes, against 9,625,339 at a357dee (+17 KB of code; the
  3.9 MB table was already in a357dee).
