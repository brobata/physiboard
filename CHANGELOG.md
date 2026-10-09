# Changelog

This file starts with 3.0, a clean-room rewrite; it does not carry the 2.x line's history. See
`legacy-2.x` for that.

## 3.1.0 (unreleased)

- A Fill page on Sym: one-time codes from your notifications, typed with one key, held in memory
  for ten minutes and never stored or sent. Needs notification access.
- Password manager suggestions on the Fill page, experimental and off.

## 3.0.0 (2026-10-08)

PhysiBoard 3.0 is a ground-up rebuild for the Titan 2 Elite's keyboard. Autocorrect now reads the
sentence, dictation keeps listening until you stop it, and apps keep the whole screen: there is no
bar while you type. Press Sym for emoji and symbols.

<!-- /card -->

**Our own dictionary**
- PhysiBoard now builds its own English word list instead of borrowing one: 80,000 words ranked by
  how often people actually write them, and checked against a spelling lexicon so common
  misspellings ("alot", "teh", "thier", "seperate") are never treated as real words and always get
  corrected.
- A new word-pair table, built from about 1.8 million example sentences, lets autocorrect read the
  sentence: "definately not" becomes "definitely not", never "defiantly".
- Slurs are kept out of the word list, so autocorrect never suggests one. Profanity stays.
- Your personal dictionary carries over, and words added through other apps' "Add to dictionary"
  are respected too.

**Typing**
- Autocorrect knows the Titan's key positions and the sentence around each word: in testing it fixed
  75% of typos (2.x: 30%) and changed a correctly spelled word about once in 4,500.
- An optional fix for mixed-up words (its/it's, your/you're, their/there, then/than and more), off
  until you switch it on. Backspace undoes any correction.
- A system spell checker: apps underline misspellings and offer PhysiBoard's corrections in their
  own menu.

**Accents and special characters**
- Long press can type an accent (diacritic) instead of the Alt symbol: hold a letter for its first
  accent (ą, é, ñ, ü…), then press the key shown on the accent bar for another, or tap the letter
  again to step through them all.
- Accents come in your language's order for 18 languages (Polish, French, German, Spanish,
  Portuguese, Italian, Czech, Slovak, Romanian, Turkish, Dutch, Swedish, Danish, Norwegian,
  Hungarian, Catalan, Vietnamese, Gaelic), and every letter's list can be edited, any character
  you like.
- Long press can also be set to type a capital, a Sym symbol or an emoji.
- Search every Unicode symbol by name (arrows, maths, currency, shapes, music…) from the 🔍 on the
  Symbols page.

**No bar, Sym pages instead**
- The suggestion bar is gone; apps keep the whole screen. Sym steps through Emoji, Symbols and GIFs
  in an order you choose, and each can be switched off. Double-tap Sym for a list of every page.
- Emoji search, skin tones (with a default tone), and three pages of your own to fill with
  whatever characters you use.
- GIF search (KLIPY), off by default in this release: switch it on in Customize SYM Keyboard.

**Dictation**
- Hold Fn to start. It keeps listening through pauses and stops when you press Fn again, press any
  key, or go quiet for the time you choose (2.5 s by default). Music pauses while you talk.
  On-device recognition with punctuation where the phone has it. Fixes the cut-offs and "no speech"
  pop-ups of 2.x, whose cause was found and removed.

**Terminals and the web**
- Terminal mode (formerly Exact typing) for any app you pick: nothing corrected or capitalised, and
  Ctrl, Esc, Tab, arrows and Alt symbols go straight to the app.
- Alt symbols and Shift capitals now reach web pages and web apps that Chrome 148+ used to drop.

**Privacy**
- Private mode: the keyboard learns nothing and makes no network requests. Fields that ask not to
  be learned from (incognito tabs, banking apps) get the no-learning part automatically.
- Links you copy and paste through the keyboard lose their tracking parameters.

**Also**
- Keyboard backlight and notification ring through the built-in ADB pairing, the T2E toolbox,
  backup and restore, an update checker, and a one-shot importer from 2.x settings.

Not carried forward from the line this succeeds: the AOSP-derived soft keyboard view and its
theming system, custom input styles, the multi-device layout tree, and anything that existed only
for a device this project does not ship to.
