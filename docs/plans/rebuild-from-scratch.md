# Rebuild from scratch (3.0)

Status: decided, not started. Written 2026-09-19.

## The ask

Replace every line inherited from Pastiera with code we wrote, so PhysiBoard is ours in
architecture, attribution, and license.

## Decisions (made 2026-09-19)

1. **Clean room, license free.** The rewrite goes through a written behavioral spec and is
   implemented from the spec only. The new code carries a license of our choosing. It stops
   being GPLv3 in the commit that removes the last upstream line, not before.
2. **No on-screen keyboard.** The Titan always has hardware keys. Emoji, symbol, and Unicode
   pickers are overlays, not a keyboard. The AOSP LatinIME-derived view and its theming system
   are dropped, not ported.
3. **2.x is frozen to crash fixes** from the day the spec phase ends until 3.0 ships as Latest.
4. **License (decided 2026-09-21): GPLv3**, with a commercial license available from the
   maintainer, a trademark notice on the name and icon, and a contributor agreement that lets
   the maintainer relicense contributions. Tips through GitHub Sponsors; the About screen gets
   a *Support PhysiBoard* row.

## The insight

The fork is still mostly Pastiera. Surviving main-source lines by author, measured with
`git blame` on 2026-09-19:

| Source | Lines |
|---|---|
| Upstream authors (Zauner, Palumbo, Mitchell, others) | ~67,000 |
| Fork authors (freecost, brobata) | ~21,700 |
| Total Kotlin under `app/src/main` | ~88,900 |

The three heaviest files are upstream god classes: `SettingsManager.kt` (6,348 lines),
`PhysicalKeyboardInputMethodService.kt` (5,728), `StatusBarController.kt` (3,177).

What makes PhysiBoard PhysiBoard is not that code. It is the Titan facts: Fn never sends
key-up and arrives as Ctrl with scancode 251; Sym is keycode 63 / scancode 253; the backlight
sysfs paths; the camera-hole ring; the embedded ADB broker; the WebAPK host mapping; Google's
recognizer closing the mic ~2 s after any sound. All of that is ours already, recorded in
memory, docs, and the change notes. A Titan-only IME needs far less generic plumbing than
Pastiera carries. Expect the rewrite to land at roughly a third of the current size.

## Domain concerns

- **Clean-room discipline.** A rewrite by someone who has spent a year inside the Pastiera
  source is legally grey unless it goes through a written spec. Two phases: the spec phase
  writes `docs/spec/` from using the app and reading the code; the build phase implements from
  the spec only, with the legacy branch checked out nowhere on the build machine. One person
  can do this if the phases are separated in time and the spec is the only bridge.
- **Upgrade path.** The applicationId `brobata.physiboard`, the signing key, and the GitHub
  release channel do not change. Every 2.x install checks brobata/physiboard releases, so 3.0
  reaches users only as an ordinary release there.
- **Settings and pairing survive.** Same applicationId keeps the preferences file, the ADB
  broker key pair, user dictionary, substitutions, text expansions, and per-app lists on disk.
  3.0 needs a one-shot importer. Losing the broker pairing is the failure users report first.
- **Data that is not ours.** Pastiera's serialized dictionary format and the `<lang>_base.dict`
  contract are theirs. The rewrite defines its own format and rebuilds the 18 non-English lists
  from Leipzig Corpora directly, the way `scripts/build_en_wordlist.py` already does for
  English. CLDR emoji annotations, the vendored Shizuku ADB code (Apache), and the OpenGameArt
  typing sounds stay with their notices.
- **GPL obligations on 2.x never end.** Every 2.x release stays GPLv3 with its source
  available. The new license applies from the first release with no upstream code.

## Approach

Spec-first clean-room rewrite, Titan-only, in the same repo and release channel, dogfooded
through the sideload applicationId from the first milestone that can type, shipped as 3.0 at
feature parity for the features a Titan owner uses.

Rejected:

| Approach | Why not |
|---|---|
| Hard refactor in place, keep GPL | Delivers the architecture but stays Pastiera and stays GPL |
| Strangler fig inside the current tree | Never becomes ours until 100% replaced; rewriting each module while reading the old one fails the clean-room test |
| New repo, new applicationId | Every user reinstalls, loses settings and pairing, no auto-update |

## What 3.0 ships

Hardware key pipeline with Fn, Sym, Alt, Ctrl, Shift and sticky/locked modifiers. Sym and Alt
layers with pages. Nav mode. Autocorrect and suggestions with 19 dictionaries. The candidates
strip as the status bar with configurable buttons and the per-app dip. Per-app Enter behavior,
exact typing, WebAPK host expansion. Text expansion. Dictation with our own silence timer and
re-listen. Screen trackpad and caret badge. Clipboard history. Emoji and Unicode pickers as
overlays. Keyboard backlight, smart backlight, notification ring, embedded ADB broker. Toolbox
(bloat remover, system tweaks, display density). Backup, update checker, diagnostics,
onboarding.

## What 3.0 drops

The AOSP-derived soft keyboard view and its theming system. Custom input styles. The
multi-device layout tree. Telex. Input-subtype machinery beyond what language switching needs.
Everything that exists for a device we do not ship to.

## Module architecture

Gradle modules with pure-Kotlin cores that test on the JVM without Robolectric.

    :core:keys      key event normalization, modifier state machine, layer resolution
                    (Fn/Sym/Alt), hold detection, bounce and accidental-press filters.
                    No Android imports. Input: KeyStroke. Output: Action.
    :core:text      composition, autospace, deferred punctuation, autocap, autocorrect
                    confidence, suggestion ranking. No Android imports.
    :core:dict      PhysiBoard dictionary format, loader, user dictionary, substitutions.
    :device:titan   scancodes, backlight sysfs paths, ring geometry, Fn quirks. The one
                    place Unihertz facts live.
    :broker         vendored Shizuku pairing + privileged setup. Apache, unchanged.
    :speech         recognizer session with our own silence timer and re-listen.
    :ime            InputMethodService as a thin adapter: KeyEvent in, Action out,
                    InputConnection writes. Status bar, trackpad overlay, caret badge.
    :settings       Compose screens over typed DataStore. One screen per hub, no god class.
    :app            manifest, DI wiring, update checker, diagnostics, onboarding.

Keypress data flow: `KeyEvent` -> `:device:titan` normalizes scancode and the Fn burst ->
`:core:keys` resolves layer and modifiers into an `Action` -> `:core:text` mutates a
`Composition` and emits `InputConnection` ops -> `:ime` applies them and updates the strip.
Every layer is a pure function of state plus input. The autocorrect eval harness under
`core/suggestions/eval/` is the pattern for testing the whole pipeline.

## Settings

Typed DataStore with a single versioned schema. `LegacyImport` runs once on the first launch
of 3.0: reads the 2.x SharedPreferences file and the per-app JSON lists, maps them by name,
and records the import so it never runs twice. Broker key files and the user dictionary stay at
their 2.x paths. The spec phase records every 2.x preference key with its default so the
importer is written from the spec, not from `SettingsManager.kt`.

## Dictionary format

Our own: a header with language, version, and checksum, then a sorted word list with 16-bit
frequency, plus a reserved bigram block for the planned autocorrect rework. Built by an
extended `build_en_wordlist.py` that handles all 19 languages from Leipzig. The dictionary repo
serves `<lang>.pbd` under a new path and verifies the checksum. The old `<lang>_base.dict` path
stays untouched for 2.x installs.

### Building the 19 dictionaries: a coverage and licensing problem, measured 2026-09-21

The English rebuild that 2.x shipped worked because two sources were intersected: a frequency
ranking (which knows what people actually write) and a spelling lexicon (which knows what is a
real word). Frequency alone is not enough, and that is not a matter of taste: real text contains
real misspellings, so a list built from frequency alone knows `alot` and `teh` and can then
never correct them.

Measured against the two libraries the English build used, the 19 languages split three ways:

| Recipe available | Languages |
|---|---|
| Frequency and lexicon, the full recipe | de, en, es, fr, it, nl, pt, ru (all bundled) |
| Frequency only, no lexicon | cs, da, el, hu, no, pl, sv, tr, uk, vi (da, no, pl, uk are bundled) |
| Neither | gd |

So four bundled languages (Danish, Norwegian, Polish, Ukrainian) would ship with exactly the
defect the English rebuild removed.

Spelling dictionaries for the missing languages do exist, around 80 of them packaged for Linux,
and they would close the gap. The catch is licensing, and it now matters in a way it did not
before 3.0 chose its licence: most of those dictionaries are GPL-family. Filtering a Leipzig
frequency list through one arguably makes the result a derivative of it. That is harmless for
the GPLv3 distribution and awkward for the commercial licence, because a word list the
maintainer cannot relicense cannot be included in a commercial build.

**Decision: build the filter ourselves, from the corpus.** That removes the licensing question
entirely rather than answering it per language, gives one pipeline for all 19 instead of a
patchwork, and leaves the output fully the maintainer's to relicense. It is also the only option
that scales to a twentieth language.

What was measured on 2026-09-21, on English, to check the idea is real. Three signals were
tested, each derivable from the corpus alone with no outside word list:

| Signal | What it says | Result alone |
|---|---|---|
| Edit dominance | a misspelling sits one edit from a much commoner word | Fails. `vex` scores 234 and `alot` scores 48, so any cut that removes the typo removes the word. |
| Derivational family | a real word has relatives: vex, vexed, vexing | Fails. `because` has no family at all; `teh` has nine. |
| Source conservatism | the word survives in a corpus built from fewer, cleaner sources | Fails. The conservative list still contains `teh` and `alot`, and drops `vex`, `ember`, `flout`, `salve`, `vegetate` and `quell`, which is exactly the "ordinary words missing" defect the English rebuild removed. |

No single signal separates them, which is why a borrowed lexicon was used the first time.
Combined, they look viable: on the sample above, requiring both a high dominance and an empty
family flags `alot`, `untill`, `definately`, `occured` and `becuase` while leaving every real
word untouched, including the fragile ones. That is the right shape, because the invariant is
"a correctly spelled word is never removed" and recall is the debt taken knowingly.

**English is the control, and that is what makes this safe to attempt.** A known-good 80,000
word English list already exists, built the old way with a lexicon. The corpus-only method is
developed against it: if it reproduces English quality on the eval harness, it has earned the
right to build the other eighteen. If it does not, that is known before anything ships, and the
fallback is the per-language lexicon hunt with its licensing question intact.

**Measured properly on 2026-09-21, the corpus-only filter does not clear the bar. The decision
above is withdrawn.** Recording the negative result rather than the hope, because the hope was
written here first.

Two things went wrong with the earlier reasoning:

*The lexicon is not ground truth, so "reproduce the lexicon list" was the wrong target.* The
English spelling lexicon rejects `centre`, `labour`, `favourite`, `programme`, `colour`,
`realise` and `organisation`, which a British user types every day, while admitting
`stoichiometry` and `supernumerary`. A word list built to match it inherits its bias. The right
target is the pair of properties the keyboard actually needs: it must contain what people type,
and it must not contain misspellings, because a typo the keyboard believes is a word can never
be corrected.

*Against that target, the two corpus signals leak in both directions.* Tested on words that must
go and words that must stay: seven of sixteen misspellings survive, including `teh`, `thier`,
`wich`, `recieve` and `seperate`, and two real words are removed, `ember` and `tv`. Removing a
real word is the failure the whole invariant exists to prevent, so this is disqualifying, not
merely unfinished.

The reason is structural rather than a matter of tuning. `ember` sits one edit from `member`,
has no derivational family in the corpus, and is therefore indistinguishable by these signals
from a misspelling of `member`. `tv` is two letters from `to`. Short words and confusable words
occupy the same region of the space as typos, and no threshold over these two axes separates
them.

**Where that leaves the nineteen languages.** The honest options are now: use a spelling lexicon
per language and resolve its licence, which is the only method measured to work; ship suggestions
only for the eight languages that have one; or find a signal these tests did not cover, which
would need data wordfreq does not carry, such as per-source corpora separating edited text from
unedited, or n-gram context. The first is the shortest path and the licence question is narrower
than it first looked, since several of the available dictionaries are permissively licensed. It
should be settled per language, with the licence recorded next to each word list.

The experiment scripts are in the session scratchpad and the method is written down here, so the
next attempt starts from the measurements rather than repeating them.

## Edge cases

| Case | Handling |
|---|---|
| 2.x user updates to 3.0 | Importer maps settings, pairing survives, one "what changed" card |
| Feature missing in 3.0 | Release notes list what was dropped and why, before the update |
| Dictionary repo still serving the old format | 3.0 asks under a new path, old path untouched |
| Broker pairing lost by a key path change | Key files stay at 2.x paths, covered by an importer test |
| Spec gap discovered mid-build | Add to the spec from behavior on the device, never from reading old source |

## What milestone 2 established, and one constraint it found

The four pure-Kotlin modules are built and committed: `:core:keys` (87 tests), `:core:dict`
(55), `:device:titan` (63) and `:core:text` (113). No Android import in any of them, so the
whole typing pipeline runs under a JVM test.

**A sequencing rule the IME module must obey.** A letter's case is resolved by `:core:keys`
before `:core:text` ever sees it. But the spec requires that when a deferred space fires, the
capitalisation rules are re-evaluated so the letter landing after it is capitalised on that same
keystroke. A pure function handed an already-resolved letter cannot do that retroactively.

So `:ime` must ask `:core:text` whether a deferred space is pending BEFORE it asks `:core:keys`
to resolve the incoming keystroke, and feed that answer in. This is a sequencing responsibility
that spans all three modules and belongs in the IME's own tests, not in any one module. It is
written down here because it is invisible from inside each module and would otherwise be
rediscovered as a bug: the first sentence typed after a question mark would quietly lose its
capital.

**Two spec contradictions to resolve** rather than leave to whoever reads them next: the
double-space behaviour is described one way in its test row and another way in the trailing-space
rule, and the Alt-layer follow-up reads as unconditional while only one of its three branches is
explicitly exempted. Both were implemented literally and marked in the code.

## Pre-mortem

1. **The parity cliff kills it.** 3.0 stays "almost ready" for months. Guardrail: sideload it
   on the phone from the first milestone that can type, correct, and dictate, even with no
   settings UI. It becomes the daily keyboard early.
2. **The clean room leaks.** A quirk comes up, the old service gets opened "just to check", and
   the new file is derived. Guardrail: the spec captures every quirk as a numbered fact with
   device evidence and a test case. During the build phase `legacy-2.x` is checked out nowhere
   on this machine.
3. **Two codebases forever.** Guardrail: decision 3 above. 2.x gets crash fixes only.

## Build order

1. **Spec phase, 2.x still open.** Write `docs/spec/` from behavior: one document per feature,
   every device quirk as a numbered fact with evidence, every setting with its default and 2.x
   preference key. Record the pipeline test corpus (key sequences -> expected text) on the
   Titan. Then move `main` to `legacy-2.x`, tag it, and start `main` as an orphan branch.
2. **Pipeline core.** `:core:keys`, `:core:text`, `:core:dict`, `:device:titan`, JVM tests
   against the corpus. New dictionary format and the builder script for all 19 languages.
3. **Minimal IME.** `:ime` that types, corrects, and shows the strip. Sideload it. Daily-drive it.
4. **Device features.** Broker, backlight, ring, trackpad, caret badge, dictation.
5. **App behaviors.** Per-app Enter, exact typing, nudge, text expansion, clipboard, pickers.
6. **Settings and importer.** Compose hubs, DataStore, legacy import, onboarding, diagnostics,
   update checker, backup.
7. **Release.** License change commit, notices rewritten to credit Pastiera as the project this
   succeeds rather than derives from, changelog reset to 3.0.0, published as Latest.

## Complexity

Complex. Roughly 25-35k lines of new code across nine modules, plus a spec phase that has to be
honest before a line is written. Milestones 1-4 decide whether it works.

## Decision log

- Same applicationId, signing key, and GitHub repo: the update channel is the only distribution.
- Titan-only from the first line: every dropped Pastiera subsystem is a device we do not ship to.
- The spec is the only bridge between old and new: that is what makes a license change defensible.
- Pure-Kotlin cores: the autocorrect eval harness proved JVM-testable logic is how this project finds bugs.
- No soft keyboard: the Titan always has hardware keys, pickers are overlays.
- 2.x frozen to crash fixes: the rewrite starves otherwise.
