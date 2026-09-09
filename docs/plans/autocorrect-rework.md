# Autocorrect rework

Status: proposed, not started. Written 2026-09-08.

## The ask

Autocorrect "messes me up half the time." Make it good.

## The insight

It is not badly tuned. It is missing the two stages that make a correction engine
predictable, and it has no way to tell a confident correction from a marginal one.

Today the pipeline is:

    typed word -> SymSpell (uniform-cost Damerau, distance <= 2) -> ~8 candidates
               -> eight boolean shape gates -> commit or don't

Three things are wrong with that shape, and they compound:

1. **Retrieval is blind to the keyboard.** `SymSpell.damerauDistanceLimited`
   (`SymSpell.kt:130-161`) costs every edit at exactly 1. Substituting a neighbouring
   key and substituting a key on the far side of the board are the same distance, so
   they are the same candidate quality. The geometry that exists
   (`SuggestionEngine.kt:47-104`, `keyboardDistance` at `:163`) runs *after* retrieval
   as a boolean reject at >2.5 keys and a +0.4/+0.2 score nudge. It never influences
   which candidate wins, only whether an already-chosen one is vetoed.

2. **Nothing is scored in context.** `SuggestionEngine.suggest()` takes
   `currentWord: String` and nothing else - there is no previous-word parameter
   anywhere in its signature chain. Every correction is decided on the word alone.
   Meanwhile `UserNGramStore` holds real learned bigrams and
   `AutoReplaceController.kt:147` explicitly refuses to let them near a correction.

3. **There is no confidence.** `SuggestionResult.score` is computed from ~13 additive
   magic constants and then used *only to order the three visible suggestions*. It is
   never compared against a threshold. The commit decision is a conjunction of eight
   booleans (`AutoReplaceController.kt:460-466`). A correction is either structurally
   permitted or not; "how sure are we" is not a question the code can ask.

The project's own KDoc already names the felt consequence
(`TypoShapeProfile.kt:18-39`): two outcomes that differ only in whether the typo
happened to preserve word length are, "from the user's chair, indistinguishable from
random."

**The fix for "it corrects wrongly half the time" is mostly to correct less often, and
to be right about when.** That is a confidence threshold, not a better dictionary.

## Before any of this: the shipped config is the most aggressive one available

`app/src/main/assets/common/default_settings.json` (under `entries`) ships:

    auto_replace_on_space_enter = true
    use_keyboard_proximity      = true
    max_auto_replace_distance   = 2     <- the accessor default is 1

Absent from the baseline, so falling to their accessor defaults: `auto_correct_enabled`
(true), `suggestions_enabled` (true), `accent_matching_enabled` (true),
`use_edit_type_ranking` (false). Those are all sane, so their absence is harmless.

The consequential one is `max_auto_replace_distance = 2`. Combined with English being
the only language on `TypoShapeProfile.LETTER_SLIPS` (`maxLengthDelta = 2`), the shipped
English configuration permits the widest edits the engine can express: distance 2, with
a length change of up to 2 in either direction. Every marginal candidate that the eight
shape gates do not veto gets committed, because there is nothing else to stop it.

**This is the most likely direct cause of the complaint, and it is a one-tap test.** Set
*Maximum correction distance* to 1 and type for a day. If the badness largely goes away,
the engine work below is about *earning back* distance 2 safely rather than about
rescuing something broken - and W3 (confidence) becomes the whole story, because a
threshold is exactly the mechanism that lets distance 2 be available without being
applied indiscriminately.

Do this before writing code. It costs nothing and it tells you which problem you have.

## Recommended architecture

Keep SymSpell. Change its job.

    typed word
      -> SymSpell: RECALL only. Uniform cost, over-generate, ~16 candidates.
      -> KeyboardCostModel: RESCORE. Real-valued channel cost per candidate.
      -> ContextPrior: bigram prior from UserNGramStore, given previous word.
      -> Score = channel + prior + frequency.  ONE number.
      -> Threshold:  >= commit    -> autocorrect
                     >= suggest   -> candidates bar only
                     below        -> leave the text alone
      -> Shape gates stay, but only as hard vetoes (never correct an acronym, etc.)

The important move is separating **recall** from **precision**. SymSpell's
delete-neighbourhood index structurally requires uniform-cost edits, so weighting it
directly is not on the table. It does not need to be: retrieval only has to contain the
right answer somewhere in ~16 candidates. Deciding which of those 16 is right is where
geometry, context and frequency belong, and that stage is cheap because it runs over 16
short strings, not a 48k index.

This also retires the false choice the current code makes. `max_auto_replace_distance`
is the only tuning dial a user gets, and it is an integer edit distance - a blunt
instrument that cannot express "distance 2 but all adjacent keys and the bigram agrees"
versus "distance 1 across the board with no context support." A confidence threshold
can.

### Geometry source

Do not extend the hardcoded grid at `SuggestionEngine.kt:47-104` (compact layout,
split bottom row, qwerty/azerty/qwertz only, silently falls back to QWERTY).

`SoftwareKeyboardLayoutTemplates.kt:24` already has per-family row strings:

    Family.QWERTY -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Family.QWERTZ -> listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")

Derive adjacency from those. One source of truth, correct per layout family, and it
generalises to every language the keyboard ships without new data. The physical Titan
rows are staggered differently from a rectangular grid, so add a per-row x-offset
constant if measurement shows it matters - but start from the row strings.

### Cost model

Per-edit costs, replacing uniform 1.0:

| Edit | Cost | Rationale |
|---|---|---|
| Substitution, adjacent key | 0.35 | The dominant physical typo |
| Substitution, distance 2 keys | 0.7 | Plausible |
| Substitution, far key | 1.6 | Almost never a slip; probably a different word |
| Transposition | 0.4 | Very common, already Damerau-detected |
| Deletion (dropped letter) | 0.5 | Common on a physical keyboard |
| Insertion (doubled letter) | 0.3 | Key bounce, extremely common |
| Insertion (other) | 0.9 | |
| First-letter change | +0.8 surcharge | People rarely miss the first key of a word they meant |

Numbers are a starting point to be fitted against the corpus, not asserted truth. The
point is the *shape*: adjacent-key slips should be cheap enough that a distance-2
adjacent typo outscores a distance-1 far-key one. That single inversion is most of what
makes the current behaviour feel arbitrary.

## Workstreams, in dependency order

### W0. Turn the aggression down and see what is left  (minutes, on the device)
Set *Maximum correction distance* to 1 and use it for a day. No code. This separates
"the engine is wrong" from "the engine is set to maximum reach", and the answer decides
how much of W2-W6 is actually needed. See the section above.

### W1. Evaluation harness  (the gate on everything after it)  — BUILT, corpus not yet real
Nothing below this line can be tuned without it, and tuning by feel is how a keyboard
gets quietly worse.

`TypoShapeProfileTest.kt` was built from a real debug export off the device on
2026-08-31 - that is the seed corpus and the proof the approach works.
`DebugCaptureStore.recordAutoCorrectionAttempt` already records every attempt with a
rejection reason from an 11-value enum.

Build: a `(typed, intended, previous_word, locale)` corpus; an offline runner that
replays it through the real scorer; and a report of **precision, recall, and
false-correction rate** - the last being the one that matters, because a wrong
correction costs far more than a missed one. Gate every later change on it.
Target something like: false-correction rate down, recall held flat.

**Status 2026-09-08.** Built and passing. `AutocorrectEval` replays a corpus through the
real `SymSpell`, the real `SuggestionEngine` ranking and the real commit predicate - the
commit rule was extracted from `handleBoundary` into
`AutoReplaceController.shouldAutoReplace(ReplaceFacts)` so the harness scores the shipped
rule rather than a copy of it that would drift. `AutocorrectEvalTest` is the ratchet.

First numbers, 74 cases, shipped configuration:

| | distance 2 (shipped) | distance 1 |
|---|---|---|
| fixed | 33 | 31 |
| missed | 7 | 9 |
| wrong | 0 | 0 |
| clobbered | 0 | 0 |
| false-correction rate | 0.000 | 0.000 |
| recall | 0.825 | 0.775 |

**Read this as a statement about the corpus, not about the keyboard.** A zero
false-correction rate against 105 words of vocabulary is close to meaningless: `form` has
almost nothing to be confused with when the dictionary is that small, and clobbering a
correctly-typed word is precisely the failure that needs a crowded dictionary to appear.
The harness is sound; the corpus is not yet able to reproduce the reported problem.

**Next, and it is the whole job:** get real data into it. Two routes, do both.
1. Run the harness against the real 50k `en_base.dict` instead of the curated vocabulary.
   The `EvalDictionaryRepository` seam already allows it; it needs an asset-loading path
   and will be slow enough to want its own Gradle task rather than the fast test loop.
2. Harvest a real corpus off the device. `DebugCaptureStore.recordAutoCorrectionAttempt`
   already records every attempt with its rejection reason, and
   `recordAutoCorrectionCommit` records every commit. What is missing is the *intended*
   word, which only the user can supply - so the practical form is an export of recent
   commits that the maintainer annotates. `TypoShapeProfileTest` was built exactly this way
   from the 2026-08-31 export and is the proof the loop works.

Until (1) or (2) lands, treat the numbers above as a smoke test that the plumbing is real,
not as a measurement of correction quality.

**Update, same day: route (1) landed.** `AutocorrectEvalRealDictionaryTest` runs the same
corpus against the shipped 48k-key `en_base.dict` through the real
`AndroidDictionaryRepository`. It is opt-in so the normal loop stays fast:

    ./gradlew :app:testDebugUnitTest -Pphysiboard.eval.realDictionary=true

With a real dictionary the corpus finally bites. 74 cases:

| | distance 2, proximity on (shipped) | distance 1, proximity on | distance 2, proximity off |
|---|---|---|---|
| fixed | 31 | 29 | 31 |
| missed | 7 | 10 | 6 |
| wrong | 2 | 1 | 3 |
| clobbered | 1 | 1 | 1 |
| false-correction rate | **0.041** | 0.027 | 0.054 |
| recall | 0.775 | 0.725 | 0.775 |
| precision | 0.912 | 0.935 | 0.886 |

### What the failures actually are

    definately -> defiantly   (meant definitely)
    wierd      -> wired       (meant weird)
    salve      -> slave       (meant salve)   [not in dictionary]

The report now separates **coverage holes from bad decisions** by checking whether the
typed word is in the dictionary at all. `salve` is not, so the engine had no way to know
it was a real word - that is W7, not a scoring failure, and no threshold or cost model
would have saved it. Two of the 34 controls are missing from the shipped dictionary.

That leaves two genuine decision failures, and they are the same shape: **a real word beats
the intended one at equal or lower edit distance**, and nothing in the scorer can prefer
the right one. `defiantly` is a perfectly good word; so is `wired`. This is precisely the
case that uniform-cost retrieval plus boolean gates cannot handle, and precisely what W2
(geometry costs), W3 (threshold) and W5 (context prior) are for. A bigram prior would
settle `wierd -> weird` immediately in almost any sentence.

### Three findings that change the plan's emphasis

1. **Proximity ranking earns its keep.** Turning it off raises the false-correction rate
   from 0.041 to 0.054 and fixes nothing extra. It is on by default, correctly. W2 should
   extend this idea into the cost model rather than replace it.
2. **The distance dial trades exactly as predicted** - distance 1 is safer (0.027) and
   misses more (recall 0.725 vs 0.775). It is a blunt instrument doing a job a confidence
   threshold does better, which is the argument for W3.
3. **W0 will not fix clobbering.** `salve -> slave` happens at distance 1 as well - it is a
   transposition. Turning the dial down does not touch it. So the config change is worth
   doing for comfort, but the real work is W3 and W7, not W0.

Ratchets are set at the measured values in both eval tests. Tighten them when a change
earns it; never loosen one to make a change pass.

### W2. Geometry into scoring
`KeyboardCostModel` derived from the row strings. Rescore SymSpell's output. Delete the
boolean `isNearbySubstitution` veto and the ±0.2/±0.4 nudges - the cost model subsumes
both. Raise SymSpell's candidate count (8 -> 16) since precision now comes later.

### W3. Confidence threshold
One score, two thresholds, three outcomes (commit / suggest / leave alone). Keep the
shape gates as vetoes only. Retire `max_auto_replace_distance` as the primary dial in
favour of a plain-language aggressiveness setting that maps to the threshold; keep the
old key honoured for one release.

### W4. Collapse to one pipeline
Only after W3, because W3 forces the shape of it.

Today: `AutoCorrectionManager` (dead on the normal path), `AutoCorrector` (a mutable
`object` singleton with process-global `rejectedWords`, not per-locale, not per-field),
and `AutoReplaceController` each own a boundary derivation and an undo. The two undos
(`AutoCorrectionManager.kt:20-73` and `AutoReplaceController.kt:548-613`) are
near-identical 65-line algorithms that have already drifted - one uses `beginBatchEdit`,
one does not.

Make the exact-substitution table a *candidate source* feeding the single pipeline, not
a parallel engine. One boundary derivation, one undo, one rejected-word set.

### W5. Context prior
Pass the previous word into `SuggestionEngine.suggest()`. Use `UserNGramStore` as a
prior over correction candidates. The store, schema, async write queue and
`PendingLearningOverlay` all exist - this is wiring, not construction. Must not do a
synchronous SQLite read on the boundary keystroke; reuse the overlay/cache pattern.

### W6. Durable rejection memory
`rejectedWords` is cleared on the next keystroke (`SuggestionController.kt:288-291`) and
never persisted, so "no, don't correct that" survives about one keypress. Make
(locale, typed -> candidate) rejections durable and decay them slowly. Also detect the
manual fix - backspacing over an autocorrected word and retyping it is currently not
noticed at all. This is what users mean when they say a keyboard learns.

### W7. Frequency data and payload
- Own the corpus ingest. Today `<lang>_base.json` arrives from outside the repo with
  frequency already quantised to 0-255, and `effectiveFrequency`
  (`DictionaryRepository.kt:299-304`) is a hand-tuned `(raw/255)^0.75 * 1600` guess at
  inverting a quantisation this project does not control. Regenerating `en` from Leipzig
  with real counts removes the guess.
- Free win, unrelated to quality: `app/build.gradle.kts:206-209` tries to exclude
  `assets/common/dictionaries/*_base.json` via `packaging.resources`, which filters Java
  resources, not Android assets. Verified against the shipped 2.0.6 APK: **13 `_base.json`
  files, 27.4 MB, all dead weight** next to the 12 `.dict` files actually loaded. One of
  them (`lt_base.json`, Lithuanian) has no matching `.dict` at all. Use
  `androidResources.ignoreAssetsPatterns` or delete the sources.
- Retire the duplicate generators in `scripts/` - `build_symspell_dict.py` and
  `backup_truncate_and_convert.py` write mutually incompatible `symDeletes` (full term
  vs truncated prefix), and `DictionaryRepository.kt:474-493` silently rebuilds the map
  at load time on every device to absorb the difference.

## Pre-mortem

**It ships worse and nobody can say why.** Highest risk by far. Autocorrect quality is
not observable from a diff, and the failure is diffuse. Guardrail: W1 before anything
else; every subsequent PR reports false-correction rate against the corpus; no tuning
constant changes without a number attached.

**Memory.** `SuggestionController.kt:908-914` already catches `OutOfMemoryError` on
dictionary load and treats "suggestions stay off for this locale" as an expected
outcome, with a 13-21 MB CBOR decoded at roughly 3x file size. Guardrail: the rescoring
stage must add no resident index - it operates on <=16 candidates per boundary. Do not
introduce a second in-memory model.

**Latency on the boundary keystroke.** Correction runs synchronously in the space
keypress path. Rescoring 16 short candidates is negligible; a SQLite bigram read on that
path is not. Guardrail: budget it in ms and measure; context lookups come from a cache,
never a blocking DB hit.

## Decision log

- **Keep SymSpell, demote it to recall.** Rewriting it to weighted costs would mean
  abandoning the delete-neighbourhood index, which is what makes lookup fast. Recall
  does not need to be smart; precision does, and precision is cheap at 16 candidates.
- **Geometry from the software row strings, not a new data file.** One source of truth,
  free coverage of every layout family, no hand-authored table to drift.
- **Confidence threshold over more shape rules.** Eight booleans is already too many and
  the ninth will not help. A score can express the marginal cases the booleans cannot.
- **Eval harness before engine work.** The one non-negotiable ordering constraint.
- **Reuse `UserNGramStore` rather than build an n-gram LM.** The learned bigrams are
  already there and already personal to the user, which a shipped LM would not be.
