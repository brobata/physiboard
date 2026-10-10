# Changelog

This file starts with 3.0, a clean-room rewrite; it does not carry the 2.x line's history. See
`legacy-2.x` for that.

## 3.2.0 (2026-10-10)

Mixed-up words fixed for everyone, a short setup for new installs, and personal words that can't
go missing.

<!-- /card -->

**Typing**
- Fixing mixed-up words (its/it's, your/you're, their/there, then/than) is now on for everyone.
  It shipped off in 3.0 and 3.1 while it was being proven; updating turns it on once, and
  turning it off again afterwards sticks.

- The Shift, Alt, Ctrl and Sym badge shows beside the cursor again. It had been placed from the
  text box's own corner instead of the screen's, so in most apps it sat far from where you type.

**Setting up**
- A fresh install opens on three short pages: turn PhysiBoard on, make it your keyboard, and the
  optional extras (accessibility service, display over other apps, spell checking). Each page
  updates by itself as you go, and Skip is always there. Anyone updating never sees them; Help
  → "Show the tutorial" opens them any time.

**Your words are safe**
- Deleting, adding or renaming a word in Personal dictionary no longer erases a word the keyboard
  learned while that screen was open, and Undo no longer drops one either.
- A damaged dictionary file is left alone and reported, instead of being replaced by the next
  word learned.
- Restoring a backup brings your personal words back into the keyboard right away.

**Privacy**
- The voice-typing diagnostic log writes nothing in private mode or for any dictation started in
  an incognito field, and reads the same in release builds as in test builds. It never logged
  your words.

## 3.1.0 (2026-10-09)

A new look for everything PhysiBoard draws, settings that are easy to find, Backspace that works
the moment a messaging app opens, and your Shift, Caps and Sym indicators back in the status bar.

<!-- /card -->

**A new look**
- Settings redesigned: home is a status card, the Titan toolbox, search, and a short list of
  categories, each showing its current state at a glance ("Autocorrect on · mix-ups off").
- A terminal skin over a modern layout: every screen is a prompt (`physiboard:~/voice$`), clean
  panes, rounded switches, and easy-to-read descriptions. Long explanations fold behind "About …"
  so the controls get the screen.
- The keyboard's own panels (Sym pages, emoji, GIFs, clipboard, the accent bar, the quick
  launcher) wear the same skin and spring open and closed.
- A new keycap icon, a themed icon for Android's icon styles, and a matching splash screen.
- Swipe back to preview the screen underneath; resets and deletions happen at once with "Undo";
  settings remember where you were.

**Typing**
- Backspace works as soon as a messaging app opens, before you tap the box.
- An optional accessibility service focuses the text box when you start typing (the cursor shows
  up) and makes Fn and Sym shortcuts work with no text box, on the home screen or in the camera.
  Home tells you when it's off; turn it on yourself or with one tap if Titan tools are paired.
- Hold Sym and an assigned key to change or remove its shortcut; a tap still launches.
- Spell checking turns on by itself once you pair Titan tools (unless you've chosen another spell
  checker); "Reset device settings to stock" undoes it.

**Indicators and feel**
- Shift, Caps lock, Alt, Ctrl, Sym and nav mode show in the status bar again, each with its own
  icon, including one-press and locked versions.
- Feedback vibrations are off by default; turn them on in Sound & haptics. Dictation still buzzes
  when the mic is live.

**Dictation**
- In the car: Spotify or Audible taking the audio back, or the head unit pressing play, no longer
  cuts you off; only a phone call ends a session. The start waits for a Bluetooth or car
  microphone to be live, and your music resumes exactly once when you stop.

**Titan toolbox**
- The keyboard backlight, notification ring and screen density get a featured card on home with
  their live state, and pairing is one tap away.

**One-time codes (new)**
- A Fill page on Sym: verification codes from your notifications, typed with one key, held in
  memory for ten minutes and never stored or sent. Grant notification access in Privacy to use it.

**Fixes**
- Panels that need "Display over other apps" open that switch for you instead of just saying so.
- The quick launcher sheet now actually appears; Fn layer "Revert to default" no longer wipes your
  other key settings without asking.
- Dozens of layout fixes: no clipped dialogs, readable grey text, full-width key grids.

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
