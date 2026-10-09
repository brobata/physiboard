# Closing the gap to 2.0.7

Built 2026-09-26 from a section-by-section reading of all fifteen `docs/spec/` documents
against the 3.0 tree. The spec is the record of 2.0.7; anything it describes that 3.0 does not
do is listed here. Items the documents' own Keep/Drop tables drop are excluded.

Order is by what stops the maintainer using the keyboard, then by what they reach for daily.

## 1. Blockers, the keyboard loses work or a whole feature is inert

- [x] Dictation cuts off mid-sentence, types nothing, explains nothing (dictation SS2.6, SS6.3, SS6.6)
- [x] Installing a dictionary has no effect on typing: the loader never reads the installed tiers (dictionaries SS3, SS4)
- [x] Download and import can never succeed: wrong host, wrong format, wrong file names (dictionaries SS5)
- [x] Personal dictionary words are never known, never protected, never suggested (dictionaries SS7, autocorrect SS2.2)
- [x] Nav mode cannot be entered and its key map is never consulted (trackpad SS5.2, SS5.5)

## 2. Dead controls, a button or screen that does nothing

- [x] The hamburger quick-actions overlay (status bar SS6.1, SS6.4)
- [x] Long-press a suggestion to hide or delete it (status bar SS5.3, autocorrect SS5)
- [x] The Customize SYM Keyboard screen does not exist; the stored page config is never read (layers SS4, SS5.9)
- [x] Fn Layer key edits never reach the keyboard (keys SS12)
- [x] The speech engine picker never applies the chosen engine (dictation SS4.1, SS4.2)
- [x] The orange key's assistant switch writes nothing to the phone (dictation SS11.3, toolbox SS9)
- [x] App Language changes nothing and no translations ship (app shell SS15, SS25)
- [x] Diagnostics cannot capture a key event, and the last-keyboard-event panel is absent (app shell SS10)
- [x] The status-bar theme Reset wipes the whole group with no confirmation (status bar SS9.4)
- [x] Per-style suggestion locales are parsed and never read (settings SS2.3)

## 3. Missing features the maintainer uses

- [x] WebAPK host lookup: PersaLink and every web app are invisible to per-app rules (per-app SS2.2)
- [x] Enter never knows nav mode is on (per-app SS3.5, SS3.10)
- [x] The system status-bar modifier icon, 26 combinations (keys SS13.1)
- [x] Next-word suggestions (autocorrect SS4)
- [x] The add-this-word candidate for an unknown word (autocorrect SS2.2)
- [x] The Add substitution sheet (autocorrect SS6)
- [x] Italian and French rule sets and the per-language switch (autocorrect SS7)
- [x] The keyboard-surface swipe (trackpad SS3)
- [x] Touch-screen-awake wake lock (trackpad SS6)
- [x] The smart-backlight-paused nudge (status bar SS10)
- [x] Suggestion-row accessibility announcements (status bar SS5.6)
- [x] The colour-picker dialog and the live keyboard preview (status bar SS9.4, SS9.5)
- [x] Seventeen physical layouts beyond qwerty, and a layout picker (layers SS9.2, SS9.6)
- [x] Typing sounds: built, unwired, no assets (settings SS2.13)
- [x] Package-change tracking and orphaned-shortcut cleanup (per-app SS7)
- [x] The Exact typing screen (per-app SS9)
- [x] Bounce and accidental-press filters: built, unwired (keys SS10, SS11)
- [x] Auto-capitalisation suppression after a manual Shift (text SS9.3)
- [x] The settings baseline version, so a wrong default can be re-seeded (settings SS2.15, SS4)
- [x] Backup misses the Fn map, variations and custom layouts (settings SS7)
- [x] The release scripts and the certificate pin check (app shell SS24)
- [x] The surface-transition retry (status bar SS3.2, SS14)

## 4. Polish

Swept on 2026-09-26; what remains is listed at the end of this file. Everything else in the two
gap reports marked MINOR: screen copy, ordering, confirmations, progress
rows, sorting by label rather than package, permission rows re-read on resume, the setup
screen's cursor fade and delayed scroll, and the rest.

## Decisions taken without asking, because the maintainer asked for none

- A field that tells the keyboard it wants no suggestions gets no automatic capital either.
  Their terminal was being capitalised. The spec's numbered cases use fields that carry no such
  flag and are unaffected.
- The dictionary tier badge follows the document's Keep/Drop verdict, not its prose.
- Home and the app chooser get an accessibility service, because an input method is only sent
  keys while a text field has focus and no amount of hardening changes that.

## Still open after the 2026-09-26 build-out

- Home and the app chooser across every app. An input method is only sent keys while a text
  field has focus, so this needs an accessibility service. Built in 3.2 after the maintainer's
  decision of 2026-10-09 (not going to the Play Store): an optional service, off until turned on
  in Android's settings (keys-and-modifiers.md 15.1, per-app-behavior.md section 16). Awaiting a
  try on the phone with the service turned on.
- The hosted dictionary repository still publishes the old file format, so downloading a
  dictionary for a language other than English cannot succeed until it publishes the new one.
  Nothing else blocks it; the app side is finished.
- Eighteen keyboard layouts ship, fifteen of them derived from each language's standard national
  keyboard rather than read from the spec, and Armenian with the least confidence of all.
- The modifier status icon is one drawable across all twenty-eight states rather than
  per-combination art.
- The colour wheel's hue can jump near its centre. Cosmetic.
- The bloat catalogue's summaries are paraphrases for twenty-six of its twenty-eight entries;
  the spec quotes only two verbatim and the clean room forbids reading the rest from 2.x.
