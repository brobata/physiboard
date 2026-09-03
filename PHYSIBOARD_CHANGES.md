# PhysiBoard Change Notes

PhysiBoard is a GPLv3 fork of [Pastiera](https://github.com/palsoftware/pastiera) by
Andrea Palumbo (PalSoftware) and contributors. This file documents the fork's changes,
as required by GPLv3 §5(a). Package: `brobata.physiboard`.

## 2.0.5 (2026-09-03)

The buttons you assign to the status bar now actually appear on it.

<!-- /card -->

- **The status bar shows the buttons you picked.** Every choice made under *Status Bar Theme
  → Buttons* was ignored. The page saved it correctly, but the bar was still looking the
  setting up under the name it had before 2.0 renamed it — a name 2.0 then deletes. Finding
  nothing there, the bar quietly fell back to its own defaults and showed the language button
  and the menu button no matter what you chose. The button layout the app ships with was
  discarded the same way. Because the bar now reads your actual setting, it may change
  appearance after this update: what it shows is whatever the settings page has been telling
  you it shows all along.
- **Changing a button updates the bar straight away.** Assigning one used to take effect only
  the next time the keyboard was rebuilt, so the bar looked unchanged when you went back to it.

## 2.0.4 (2026-09-02)

You can now set up Enter-to-send for any app on your phone, including Messenger.

<!-- /card -->

- **Any app can be given its own Enter behaviour.** The list only ever showed a handful of
  messengers, and the button that added your own was a bare "+" in the top bar that nobody
  found — so an app that was missing looked unsupported rather than simply not added yet. There
  is a plain *Add app* button at the end of the list now. The chooser itself was unusable too:
  the settings above it left roughly one row of space to scroll several hundred apps through.
  Picking the app comes first now, with a search box and room to see the list, and the settings
  follow once you have chosen. Facebook Messenger is listed alongside the other messengers,
  which is why Enter did nothing there — the feature was never applied to it at all.
- **The Diagnostics screen fits on the screen.** *Share* was cut off mid-word, the export
  options were squeezed into a column so narrow that a label broke in the middle of a word, and
  the *Last Keyboard Event* heading appeared underneath the values it labels.
- **A bug report describes the app you were using.** The report recorded the last text field the
  keyboard saw — but opening Diagnostics to send the report made that PhysiBoard's own field, so
  every report came back describing PhysiBoard instead of the app being reported. It now keeps
  the last field from another app as well, which is what makes an Enter-to-send problem
  diagnosable at all.

## 2.0.3 (2026-09-01)

The app now checks that its device features actually work, instead of assuming they still do.
Enter-to-send does what you set it to, and autocorrect fixes dropped letters.

<!-- /card -->

- **The Enter send method was a setting that did nothing.** *App overrides* has offered a send
  method — App action, Plain Enter, Ctrl+Enter, Auto — since 2.0. The keyboard never read it,
  and always used the app's send action. Anyone whose messenger ignores that action pressed
  Enter and nothing happened at all: no send, no newline, no error. The keyboard now honours the
  choice, and *Auto* keeps the old behaviour. The reason it could not be detected is that
  Android's `performEditorAction` reports whether the connection is alive, not whether the app
  acted, so a swallowed key looked like a success.
- **Per-app Enter settings were ignored outside a fixed list of apps.** The check for "is this
  one of the messengers we ship a default for" ran before the check for "has the user chosen
  something for this app", so an override on WhatsApp Business, a Signal fork or Slack was
  saved, shown as configured, and never applied. A preset still only applies to tested apps; an
  app you name yourself now always counts.
- **Autocorrect fixes dropped and added letters.** It only ever accepted corrections that kept
  the word the same length, or added a doubled letter. So `definetly` and `sensitivy` stayed
  wrong while same-length slips were fixed, which is why it felt arbitrary — and why *Maximum
  correction distance* promised more than it could deliver, since a bigger edit almost always
  changes length. Word completion and grammatical endings are still left alone: `work` does not
  become `works`. How far a correction may change a word's length is now decided per language,
  because a dropped letter is a typo in English but ordinary word-building in Turkish or
  Hungarian; English is enabled, every other language keeps the previous behaviour until its
  results have been checked by someone who reads it.
- **Features are checked, not assumed.** Everything that reaches the phone itself — the
  keyboard backlight, the screen trackpad, the orange side key, the bloat remover, the
  notification ring — used to decide it was working by remembering that it had been set up
  once. Nothing noticed when a system update, a reboot or another app quietly took that away,
  so the app kept reporting success while the feature did nothing. Each of these now checks the
  real state when you open its screen, tells you which part is missing, and offers the button
  that fixes it. Two screens can no longer disagree, either: there is one answer now, shared,
  rather than each screen asking separately and getting a different reply.
- **A mistyped pairing code no longer strands the app.** Setting up wireless debugging generates
  the key it pairs with *before* it checks the code you typed, and "paired" was never more than
  "a key is stored" — so a single wrong digit left the app permanently certain it was paired,
  against a key the phone had never accepted. Everything behind the pairing — the backlight, the
  overlay grant, the screen trackpad, the package tools — was then gated on that, silently, and
  the only way out was clearing app data and losing every setting. A failed pairing now throws
  away the key it made, and *Forget pairing* on the setup card gets you out if you are already
  stuck. A key from a pairing that worked is never touched. Setup also no longer claims to be
  ready when Wireless debugging is switched off — it says so, and points at the switch rather
  than telling you to pair again, because the pairing is not what is wrong in that case.
- **The Status screen takes you where the fix is.** It reported that the keyboard was not
  enabled, or not the active one, and left you to find the setting yourself. Those rows are now
  tappable and open Android's keyboard list or the keyboard picker. The screen also re-reads
  when you come back to it, instead of showing the state you left rather than the one you chose.
- **The keyboard backlight says why it stopped.** Every step that needs wireless debugging
  failed silently: it checked whether you had ever paired, never whether wireless debugging was
  still on — and Android turns that off after a restart. The result was a switch that read "on",
  a backlight that timed out anyway, and nothing to explain it. It now checks both, says which
  one is missing, and remembers the last failure so it survives the keyboard restarting.
- **Dictionaries you import are no longer overwritten.** Imported and downloaded dictionaries
  shared one folder, so downloading a language destroyed a dictionary you had imported for it,
  with no warning. They are now kept apart, yours always wins, and each one records where it
  came from — which also means the app can finally tell when a newer dictionary is available
  instead of keeping the first one it ever downloaded forever.
- **Dictionaries are served by this project.** All nineteen languages are hosted here rather
  than fetched from upstream, so they cannot disappear from under the app. Every file was
  verified against the upstream checksum before publishing; the corpus is unchanged.
- **Diagnostics can see the features that break.** The debug export now reports pairing state,
  whether wireless debugging is on, the overlay and notification permissions, whether the
  keyboard is selected, and the last outcome of each privileged step — none of which was
  visible before, in a build where the logs that would have shown it are stripped. It also
  records the editor's `imeOptions`, the field that decides whether Enter can send at all.
- **Autocorrections stay on your phone.** The debug export used to include the words you typed,
  as before-and-after pairs. That section is now excluded unless you switch it on.

## 2.0.2 (2026-08-30)

Your settings have been reset to fix some bugs. Won't happen again. Thanks for your support!

<!-- /card -->

- **The notification icon is PhysiBoard's.** Notifications were still drawing the upstream
  Pastiera icon, and in colour, which Android renders as a smudge because a notification icon
  has to be a single-colour silhouette. It is the keycap now.
- **The what's-new card stops at the change record.** It was printing this entire file's
  section, engineering notes and all. It now shows only what is above the `<!-- /card -->`
  marker; everything below stays here, where the licence requires it.

## 2.0.1 (2026-08-29)

Settings reset to a known-good baseline, plus the audit below.

<!-- /card -->


- **The what's-new card shows the right release.** It was a copy of the notes that had to be
  updated by hand, and 2.0.0 shipped with the 1.2.4 note in it. The card is now generated from
  this file at build time, and it understands the way these notes are written.
- **The status bar's height setting is back.** *Bar height* went missing in the 2.0 settings
  rework — the code still read the value, so anyone who had set 48 or 64 was quietly stuck
  with it and had no way to change it. It sits under *Show status bar* again, with all four
  heights. 36 is below Android's minimum touch target and says so: it is there for people who
  type on the keys and only read the bar, and want it out of the way.
- **Failures say so.** A long list of places where an error was logged at a level the shipped
  build strips, or not at all, and the app carried on as if the thing had worked: saving key
  mappings, SYM layers, variations, custom corrections and the personal dictionary now report
  when the write fails; restoring a backup that is not a PhysiBoard backup, or one with an
  unreadable settings file, says so instead of "Restore completed"; dictation that cannot
  reach the text field ends the session with an error instead of leaving the mic listening; a
  stored wireless-debugging key that can no longer be read asks you to pair again instead of
  quietly replacing itself with one the phone does not trust.
- **Nothing is overwritten that could not be read.** A settings file that fails to parse used
  to be replaced by an empty one on the next save, taking every other entry with it. Those
  paths now refuse the write. Importing a typing-sound pack keeps the old pack until the new one
  is in place.
- **Less work per keystroke.** The Alt character map and the launcher shortcut table were being
  re-read from disk on every key press while Alt or Sym was active; both are cached now.
  Clipboard history writes moved off the input thread. The brightness shortcuts no longer stall
  typing while the shell command runs.
- **A leaked background process.** Turning the trackpad settings on and off left a `getevent`
  reader running each time. It is stopped properly now.
- **The update check cannot crash the app** on an unexpected response from GitHub.
- **Accessibility.** The quick-launcher and dictionary rows' icon buttons announce what they
  do; several buttons were smaller than the 48dp minimum touch target.
- **Every visible string is translatable.** Thirty English strings were typed straight into the
  screens, including the onboarding buttons.
- **Housekeeping.** Lint is gated in CI against a baseline; the release pipeline builds the
  sideload variant so ProGuard breakage shows before release day; the app no longer carries
  Play-only dependency metadata, and unused resources are shrunk. Vendor firmware binaries and
  upstream build leftovers were removed from the repository.

## 2.0.0 (2026-08-28)

**You will have to pick PhysiBoard as your keyboard again.** This release renames
part of the app internally, and Android treats a renamed keyboard as a new one, so
it switches you to whatever else you have installed. Every setting you had is still
here — only the selection is lost. PhysiBoard now notices this on the first launch
after updating and offers you one tap back to setup, but that notice needs
notification permission, so this note is the backstop.

- **Settings had two of several things and none of one.** The Extras button on the
  home screen opened the same page as All settings, because Extras had never
  actually been built as a screen. It exists now, and holds the quick launcher,
  input languages and text expansion. Screen trackpad and the SYM layer editor were
  each listed in two places; the duplicates are gone.
- **Advanced is gone.** Backup, restore, diagnostics and reset to stock are on the
  settings list itself. They were the substance of that screen and there was no
  reason to keep them a level down once everything else had moved out.
- **About is reachable again.** It could only be found by typing "about" into the
  search box, which is not where a fork's licence and credits should live.
- **The status bar and the keyboard theme are one page.** The buttons, the cursor
  colours, the LEDs and the theme are all the same subject, and were two separate
  trips. It is called Status Bar Theme and sits under Keyboard.
- **Custom themes work.** The tools to build one were already in the app with no
  way to open them. There is now a button, a colour wheel for each part of the
  keyboard, and a new theme starts as a copy of the one you are already using so
  you can change one colour and save. Seven louder presets added: Synthwave,
  Vapourwave, Hazard, Blueprint, Forest Floor, Rose Gold and Ink and Paper.
- **Theme assignment removed.** Choosing separate light and dark themes and having
  the system pick between them was more explaining than it was worth. You pick a
  theme, and that is the theme you get.
- **The colour wheel fits the screen.** On the Titan the dialog was taller than the
  display, so the brightness slider was cut off at the bottom.
- **Remove bloat has more one-tap sets.** Factory and lab tools, vendor apps and
  games, and a set for the packages that report or record. The Android Auto
  stabiliser is now labelled as in testing, because it is a reasonable hypothesis
  and not a proven fix.
- **The notification ring's screen time is a slider**, anywhere from one minute to
  an hour, rather than a choice of three.
- **Emoji and symbol shortcodes are gone.** Snippets stay, and moved to Extras.
- **Sound and haptics, and the quick launcher's behaviour screen, stop hiding
  their own settings** behind a collapsed Advanced heading.
- **Going back returns you where you were**, rather than to the top of the
  previous screen. The home screen also gained a Status button that tells you
  whether anything still needs setting up.
- **The app is called PhysiBoard in every language now.** All nine translations
  still called it Pastiera, including the name under the icon and in the keyboard
  picker.

## 1.2.4 (2026-08-27)

- **Turn the accent row off again.** The row of è é ê ë under the suggestions had no switch:
  it went missing in the settings redesign, and *Reset* on the Status Bar screen turned the row
  on rather than off, with no way back. *Show variations* is on the Status Bar screen again,
  under *Bar height*, and it now says what it is — off stays off, including after a reset.

## 1.2.3 (2026-08-27)

- **Sym+C / Sym+V copy and paste.** They did not: Sym+letter was reserved for the symbol
  layer and app shortcuts, and copy/paste lived on Ctrl — the Fn key, but only once you have
  remapped it. In a text field Sym+C, V, X and A now copy, paste, cut and select all; the
  switch is on the SYM screen if you want those four chords back.
- **Fit the ring yourself again.** The ring is fitted to one Titan 2 Elite, and it turns out
  panels differ by a few pixels — a user's ring sat off the lens. The fit screen from 1.2.0 is
  back on the Notification ring page: white canvas, drag the ring onto the dark spot, resize,
  set thickness; *Auto* returns the fitted default.

## 1.2.2 (2026-08-26)

- **A status bar you can actually hit.** The hardware-keyboard strip was 32–36dp tall, under
  Android's 48dp touch minimum, and its height was buried in the theme's *Suggestions height*
  slider. It now has its own *Bar height* setting on the Status Bar screen — 36, 48, 56 or
  64dp — and defaults to 56, the height of a text box. The theme slider still sizes the bar
  above the on-screen keyboard and now reaches 2.2×.
- **The status bar can be per app.** *Show status bar* is now Always, Never, or *Only in these
  apps*: pick the apps where the on-screen bar earns its space (messaging, mail) and it stays
  out of the way everywhere else, including the launcher's search box. Messaging, mail and
  social apps (Messages, Gmail, WhatsApp, Messenger, Facebook, Instagram, Telegram, Signal and
  friends) are listed to start with. Existing on/off settings carry over as Always/Never.
- **Clipboard and mic a third narrower**, and the three suggestion slots take the space back.

## 1.2.1 (2026-08-26)

- **A colour per app for the ring** — give Messages one colour and work mail another, and
  know who it is before you pick the phone up. Apps without one use the colour they declare
  for their notifications, or green.
- **What's new that actually says what is new** — the card after an update now shows this
  release's notes instead of a sentence about fixes and improvements.
- **T2E Tools** is what the device hub is called now, since that is what it is.
- **Exact typing** and **Enter key behaviour** moved to the Keyboard hub, where they belong, and
  Exact typing now says what it is for: terminals, SSH and code, where a helpful correction
  turns a command into a typo.
- **The notification ring is fitted to the lens out of the box.** The fit-it-yourself screen from
  1.2.0 is gone — every Titan 2 Elite is the same phone, and the measured fit is now the default.

## 1.2.0 (2026-08-26)

The screen learns to say something while it is off.

- **Open notifications / Open quick settings from a key** — two new device controls in the
  command list, so any key you can assign a command to can pull the shade down.

- **Notification ring** — a glow around the camera hole when a notification arrives and
  the screen is off, in the app's colour, with the waiting apps' icons below it if you want them. The Titan 2
  Elite has always-on display compiled out of its firmware (the product overlay sets
  `config_dozeAlwaysOnDisplayAvailable` false), so this is not AOD: it is a black,
  lock-screen-topping screen on an AMOLED panel, where black pixels are off. It ends on a
  touch, a key or an unlock, and after the time you choose it stops holding the screen on and
  lets the phone's own timeout put it to sleep — black to black, nothing flashes. Only
  notifications you could dismiss qualify: nothing for downloads, playback or apps running in
  the background, and the proximity sensor keeps it from lighting in a pocket. The
  screen is launched the way alarms and calls are launched, through a silent full-screen
  notification the ring cancels the moment it appears, because that is the one route Android
  still leaves an app for turning on a dark screen. Notification access and the full-screen
  permission are granted by the one existing pairing; Reset device settings to stock hands
  both back.

## 1.1.0 (2026-08-25)

PhysiBoard becomes a toolbox as well as a keyboard. It has held a paired ADB session
since 1.0.1 to keep the backlight on; this release uses that access for the rest of what
Unihertz locks away. Everything is reversible, and Reset device settings to stock still
undoes all of it at once.

- **Remove bloat** — the vendor packages Android gives you no way to remove. A curated
  catalog of 29, inventoried on a real Titan 2 Elite and pinned to the firmware it was
  checked against, tiered from production-line test tools to features that merely
  duplicate what Android already does. **Disabling is the default** and is instantly
  reversible; uninstalling is a second, deliberate step behind a warning. **Restore all**
  is driven by a journal rather than the catalog, so a package disabled two releases ago
  still comes back. Packages the phone or PhysiBoard depend on are protected in code and
  can never be offered — including the one that owns the orange side key.
- **Android Auto stabilizer** — one tap disables the six vendor tools whose job is
  stopping background apps from running. Android Auto is a long-running session, which is
  exactly what those interrupt. This is the most likely cause of a connection that keeps
  dropping, not a proven one; it is reversible, which is what makes it worth trying.
- **Screen density** — fit more on a short screen, applied with a fifteen-second countdown
  that reverts itself unless you confirm the screen is still readable. A confirmation
  dialog is no help for a change that makes the screen unreadable.
- **System tweaks** — animation speed across all three of Android's animation settings
  rather than the one most guides mention, plus notification history and one-handed mode:
  supported by Android, never surfaced by this ROM.
- **Key mapping** — every physical key and what it currently does, assembled from both the
  vendor rows the firmware reads before any app sees the key and PhysiBoard's own handling.
  Including the keys bound to nothing.
- **Settings rebuilt around three hubs** — Device, Keyboard and Extras. Settings had grown
  to sixteen top-level rows and the home screen to eight tiles, several opening the same
  places. Every destination now lives on exactly one hub. Nothing was removed: the long
  tail moved to Extras.
- **Fixed: Back from a home tile** walked out through a Settings screen you never opened.

## 1.0.8 (2026-08-24)

- **Fixed: the assistant flashed PhysiBoard on the way through** — holding Sym or the
  orange side key briefly showed the app, and whatever screen had been left open in it,
  before the assistant appeared. The activity that hands off to the assistant now runs
  in a task of its own and draws nothing at all.
- **The speech engine picker says what the engines are** — it listed package names as
  descriptions and showed Google twice, once as "System default" and again under its
  app-drawer name, with nothing to tell them apart. Every engine now has a recognisable
  name and a line saying what choosing it means: whether it runs on the phone, needs a
  signal, or sends your speech to a server. The engine that "System default" currently
  points at is marked as such.
- **Vibration strength for dictation** — the start and stop cues already ran at the
  phone's maximum amplitude, so the only way to make them firmer was to make the pulses
  longer. Light, Standard and Strong do exactly that, and tapping one plays it so you
  can feel the difference. New installs get Strong.

## 1.0.7 (2026-08-24)

- **Ask the assistant without opening its app** — hold Sym, or long-press the orange
  side key, and the assistant opens already listening. Nothing reproduces the system
  assist gesture exactly (it calls the voice-interaction session directly, which is
  closed to apps), and each assistant decides for itself whether a request starts
  listening, so **How the assistant opens** lets you pick which request is sent. A tap
  of Sym still opens the symbol pages; the hold is unavailable while Sym is the screen
  trackpad trigger. The same action is bindable to any shortcut key as the new "Voice
  assistant" command. The side key never reaches a keyboard, so it is redirected via
  the vendor's `func1` settings — a system-level change that stays after uninstall, so
  the previous target is captured and restored by Reset device settings to stock.
- **Choose which speech engine transcribes** — dictation followed whatever the system
  set as its recognizer. **Speech engine** now lists every installed recognition service
  plus the on-device recognizer. Engines differ in accuracy, punctuation, endpointing
  and whether they need a network, and a chosen engine that is later uninstalled falls
  back to the system default rather than leaving dictation dead.
- **The engine can time the end-of-speech pause** (Android 13+) — the keyboard used to
  restart the recognizer after every result and time the pause itself, because the pause
  extras are only hints. It now asks for one long session that the engine ends on your
  pause. The old behaviour remains as the fallback, automatically when an engine refuses
  or never closes a session, and manually via **Let the engine time the pause**.
- **Automatic punctuation** — the engine can punctuate and capitalise what you dictate.
- **Fixed: dictation ended with an error message** — the pause cancelled the recognizer
  while its restarted request was still in flight, and that request reported the silence
  as an error. Nothing had failed, so nothing is reported.

## 1.0.6 (2026-08-23)

- **Fixed: Sym key did nothing with the status bar hidden** — the SYM pages render in
  the same chrome the toggle collapses. The strip now stays collapsed only while no SYM
  page is open, so emoji/symbols/clipboard pages still appear and close again.

## 1.0.5 (2026-08-22)

- **Fixed: end-of-speech pause was ignored** — the recognizer treats the silence
  extras as hints and stopped after ~1s. The IME now owns the pause: it restarts
  listening after each result and ends the session only when its own silence timer
  (your setting) expires, you stop explicitly, or a real error occurs. One start/stop
  cue per session.
- **Fixed: dictation haptics silent in 1.0.4** — notification-class vibration is muted
  when notification vibration is off; back to plain vibration, still full strength.

## 1.0.4 (2026-08-22)

- **Firmer dictation haptics** — max-amplitude, longer start/stop cues, played as
  notification-class vibration so the system touch-feedback level doesn't damp them.
- **First-run defaults synced** with the maintainer's config: first-letter
  auto-capitalization on, 2.5s end-of-speech pause, screen trackpad on, status bar
  hidden. Applied once on a fresh install only.

## 1.0.3 (2026-08-22)

- **Fixed: Exact typing never applied to installed web apps (PWAs)** — a WebAPK's
  text fields live inside its host browser, so the IME saw `com.android.chrome`
  rather than the package the user excluded. Auto-capitalization kept firing in
  terminal-style web apps (the IME can't read their text, so every keystroke looked
  like a sentence start — producing `lIKE tHIS`). The host browser, read from the
  WebAPK's `runtimeHost` metadata, is now matched too; the picker says so on
  web-app rows.
- **New defaults** — the on-screen status bar is hidden and the keyboard theme is
  Slate Dark on a fresh install. Existing settings are untouched.

## 1.0.2 (2026-08-22)

- **Screen trackpad** — hold a trigger key (Space by default; Left/Right/Either Shift
  or Sym selectable) and swipe anywhere on the display to move the cursor. A transparent
  full-screen overlay captures the drag and emits DPAD key events, so it works in every
  app including terminals; Shift during the drag extends the selection. Activation is
  hold (momentary), double tap or single tap (sticky; exit via the trigger key, Back or
  the on-screen pill). A quick tap of the trigger still types the key — the swallowed
  press is replayed through the normal input pipeline — and another key while the
  trigger is down is treated as a chord. New `ScreenTrackpadController`, settings
  screen, search entry, backup schema entries.
- **One pairing applies every privileged step** — new `PrivilegedSetup.applyAll()`
  runs at pairing success, at IME start and from the backlight screen: backlight
  setting (if enabled) plus the trackpad's "Display over other apps" grant via
  `appops`, switching the trackpad on the first time the grant succeeds. Broker calls
  are now serialized — overlapping mDNS discoveries failed silently.
- **GitHub update checks enabled** — per-flavor `GITHUB_REPO` build config
  (`brobata/physiboard` for this flavor, upstream for stable/nightly); versions are
  compared numerically so a local build ahead of the newest release isn't flagged.
- **Trackpad home tile** replaces the Auto-correct tile; new settings deep link.
- **Keyboard swipe screen removed** — the Shizuku-backed keyboard-surface trackpad is
  superseded; its detector code remains, disabled, for upstream parity.
- **About** — the upstream Ko-fi donation button moved into the "Based on Pastiera"
  credits (rendered via a small `{{button:Label|url}}` markdown element); fixed a
  bold-wrapped link rendering as raw markdown.

## 1.0.1 (2026-08-21)

- **Always-on keyboard backlight, set up once** — the smart backlight writes a
  persistent vendor setting that survives reboots; the per-boot wireless-debugging
  dance, lux threshold and post-reboot reminder are gone.
- **Show status bar toggle** — hide the on-screen status bar entirely.
- **One-tap Fn → Ctrl** with a one-tap reset.
- **Reset device settings to stock** (Settings → Advanced) reverts every system-level
  change PhysiBoard makes.
- Fixed: dictation appends after a pause instead of overwriting the previous sentence;
  Exact-Typing (raw-mode) apps reliably keep auto-capitalization off.

## 1.0.0 (2026-08-21)

First public release under the `brobata.physiboard` package — the 0.86-physi work
below, rebranded and signed for distribution via GitHub Releases.

## 0.86-physi — later revisions (2026-08-20)

- **Terminal brand** — new "PhysiBoard Terminal" look: slate + amber palette,
  JetBrains Mono throughout the app UI, and a terminal-style header.
- **Amber keycap icon** — new app/keycap artwork in the amber terminal accent.
- **Full settings redesign** — Smart Features renamed to Fn Layer; a dedicated
  Keyboard swipe screen; Sound & Haptics promoted to the top level; a new Status
  screen; the theme editor stripped down; and the overall settings tree decluttered.
- **Action-surface home** — the home screen is now an action surface; the key-logger
  moved into Diagnostics.
- **Lean onboarding** — first run cut down to a 2-step flow.
- **Embedded wireless-ADB broker** — the keyboard backlight no longer needs the
  Shizuku app or root; it runs on a self-contained in-app wireless-ADB broker
  (one-time pairing, post-reboot guidance notification). The vendored Shizuku ADB
  pairing/connection classes are retained and attributed under Apache-2.0 (see
  `THIRD_PARTY_NOTICES.md`).

## 0.86-physi (2026-08-19)

### Voice & dictation
- **Hold Fn to dictate** — hold the Fn key ~0.6s in any text field to start voice
  typing. Detects the Titan 2 Elite's vendor key delivery (repeat-burst matching by
  scan code); quick Fn+key chords are unaffected. Configurable in Smart Features.
- **Dictation vibration cues** — two quick ticks when the mic starts listening, one
  pulse when it stops.
- **End-of-speech pause slider** — choose how long you can pause (0.5–10s) before
  dictation stops, or keep the system default.
- **"Block offensive words" toggle** — dictation profanity masking is now a real
  setting (the keyboard-level Google voice typing setting never applied here).
  Default on; turn off to transcribe exactly what you say.

### Titan 2 Elite fixes
- **Fixed: Alt/Ctrl chords breaking after Fn presses** — the vendor never delivers
  the Fn key's release, which left Ctrl stuck "pressed" internally and turned Alt+key
  symbol chords into Ctrl+Alt shortcuts. Fn-origin events are now fully contained.
- **Fixed: settings text clipped mid-word** — 29 setting descriptions across 10
  screens now wrap fully on the square 574×640dp screen instead of being cut off.

### Status bar & theme
- **Traffic-light modifier states** — Shift/Alt/Ctrl/Sym indicators now show their state by
  color: **blue** when held, **amber** when latched (one-shot), **green** when locked.
- **Follows system dark/light by default** — the keyboard and its status bar now track the
  system theme out of the box.

### First run & settings layout
- **Opinionated defaults on first install** — Fn-hold dictation and dictation haptics are on
  from the start, so the keyboard is useful immediately instead of everything-off.
- **Cleaner settings** — the top level is regrouped by how people think (Keys & modifiers,
  Typing & corrections, Appearance, Shortcuts & language, System, About) with a search field.

### Settings
- **Settings search** — a search field at the top of Settings finds screens and
  individual settings by name or synonym ("censor", "mic", "terminal"…) and jumps
  straight to them.
- **Raw mode apps** — pick apps (terminals, code editors) where suggestions,
  auto-correction, auto-capitalization, and double-space-period are all disabled.

### Keyboard backlight
- **Smart keyboard backlight** — keeps the keyboard lit past the vendor's 30-second cap
  whenever the screen is on and the room is dark (below your lux threshold). In bright
  light it does nothing and the stock 30s timer takes over. Settings screen with a live
  lux readout and a darkness-threshold slider. Runs on a self-contained in-app
  wireless-ADB broker — no Shizuku app and no root — with a one-time pairing and a
  post-reboot guidance notification. Verified on Titan 2 Elite via the vendor's
  keyboardLightTest hold mode.
- **Keyboard light tile** — toggle the hardware keyboard backlight from the Quick
  Settings pull-down, driven by the same in-app wireless-ADB broker (one-time
  pairing, no Shizuku, no root).

### Branding
- Renamed to PhysiBoard with new artwork; full credits to the Pastiera project and
  all upstream contributors retained in About, including their support links.
- English tagline; "Ricette" features renamed to Recipes; Pastierina → PhysiBar.

### Under the hood
- Fn speech trigger matched by hardware scan code (default 251) with a configurable
  setting, so vendor remapping of the Fn key cannot break it.
- Device research notes in `docs/titan2elite/DEVICE.md` (keymap, vendor backlight
  controller, event delivery model) and roadmap in `docs/plans/physiboard-roadmap.md`.

### Upstream-ready (planned PRs to Pastiera)
- Fn/scan-code speech trigger · dictation haptics/pause/masking · settings search ·
- description-clipping fix · per-app raw mode · top-bar tagline string extraction.
