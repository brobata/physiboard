# Closing the gap to 2.0.7

Built 2026-09-26 from a section-by-section reading of all fifteen `docs/spec/` documents
against the 3.0 tree. The spec is the record of 2.0.7; anything it describes that 3.0 does not
do is listed here. Items the documents' own Keep/Drop tables drop are excluded.

Order is by what stops the maintainer using the keyboard, then by what they reach for daily.

## 1. Blockers, the keyboard loses work or a whole feature is inert

- [x] Dictation cuts off mid-sentence, types nothing, explains nothing (dictation SS2.6, SS6.3, SS6.6)
- [ ] Installing a dictionary has no effect on typing: the loader never reads the installed tiers (dictionaries SS3, SS4)
- [ ] Download and import can never succeed: wrong host, wrong format, wrong file names (dictionaries SS5)
- [ ] Personal dictionary words are never known, never protected, never suggested (dictionaries SS7, autocorrect SS2.2)
- [ ] Nav mode cannot be entered and its key map is never consulted (trackpad SS5.2, SS5.5)

## 2. Dead controls, a button or screen that does nothing

- [ ] The hamburger quick-actions overlay (status bar SS6.1, SS6.4)
- [ ] Long-press a suggestion to hide or delete it (status bar SS5.3, autocorrect SS5)
- [ ] The Customize SYM Keyboard screen does not exist; the stored page config is never read (layers SS4, SS5.9)
- [ ] Fn Layer key edits never reach the keyboard (keys SS12)
- [ ] The speech engine picker never applies the chosen engine (dictation SS4.1, SS4.2)
- [ ] The orange key's assistant switch writes nothing to the phone (dictation SS11.3, toolbox SS9)
- [ ] App Language changes nothing and no translations ship (app shell SS15, SS25)
- [ ] Diagnostics cannot capture a key event, and the last-keyboard-event panel is absent (app shell SS10)
- [ ] The status-bar theme Reset wipes the whole group with no confirmation (status bar SS9.4)
- [ ] Per-style suggestion locales are parsed and never read (settings SS2.3)

## 3. Missing features the maintainer uses

- [ ] WebAPK host lookup: PersaLink and every web app are invisible to per-app rules (per-app SS2.2)
- [ ] Enter never knows nav mode is on (per-app SS3.5, SS3.10)
- [ ] The system status-bar modifier icon, 26 combinations (keys SS13.1)
- [ ] Next-word suggestions (autocorrect SS4)
- [ ] The add-this-word candidate for an unknown word (autocorrect SS2.2)
- [ ] The Add substitution sheet (autocorrect SS6)
- [ ] Italian and French rule sets and the per-language switch (autocorrect SS7)
- [ ] The keyboard-surface swipe (trackpad SS3)
- [ ] Touch-screen-awake wake lock (trackpad SS6)
- [ ] The smart-backlight-paused nudge (status bar SS10)
- [ ] Suggestion-row accessibility announcements (status bar SS5.6)
- [ ] The colour-picker dialog and the live keyboard preview (status bar SS9.4, SS9.5)
- [ ] Seventeen physical layouts beyond qwerty, and a layout picker (layers SS9.2, SS9.6)
- [ ] Typing sounds: built, unwired, no assets (settings SS2.13)
- [ ] Package-change tracking and orphaned-shortcut cleanup (per-app SS7)
- [ ] The Exact typing screen (per-app SS9)
- [ ] Bounce and accidental-press filters: built, unwired (keys SS10, SS11)
- [ ] Auto-capitalisation suppression after a manual Shift (text SS9.3)
- [ ] The settings baseline version, so a wrong default can be re-seeded (settings SS2.15, SS4)
- [ ] Backup misses the Fn map, variations and custom layouts (settings SS7)
- [ ] The release scripts and the certificate pin check (app shell SS24)
- [ ] The surface-transition retry (status bar SS3.2, SS14)

## 4. Polish

Everything in the two gap reports marked MINOR: screen copy, ordering, confirmations, progress
rows, sorting by label rather than package, permission rows re-read on resume, the setup
screen's cursor fade and delayed scroll, and the rest.

## Decisions taken without asking, because the maintainer asked for none

- A field that tells the keyboard it wants no suggestions gets no automatic capital either.
  Their terminal was being capitalised. The spec's numbered cases use fields that carry no such
  flag and are unaffected.
- The dictionary tier badge follows the document's Keep/Drop verdict, not its prose.
- Home and the app chooser get an accessibility service, because an input method is only sent
  keys while a text field has focus and no amount of hardening changes that.
