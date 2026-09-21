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

## Edge cases

| Case | Handling |
|---|---|
| 2.x user updates to 3.0 | Importer maps settings, pairing survives, one "what changed" card |
| Feature missing in 3.0 | Release notes list what was dropped and why, before the update |
| Dictionary repo still serving the old format | 3.0 asks under a new path, old path untouched |
| Broker pairing lost by a key path change | Key files stay at 2.x paths, covered by an importer test |
| Spec gap discovered mid-build | Add to the spec from behavior on the device, never from reading old source |

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
