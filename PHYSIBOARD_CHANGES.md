# PhysiBoard Change Notes

PhysiBoard is a GPLv3 fork of [Pastiera](https://github.com/palsoftware/pastiera) by
Andrea Palumbo (PalSoftware) and contributors. This file documents the fork's changes,
as required by GPLv3 §5(a). Package: `brobata.physiboard` (`physi` product flavor).

## Unreleased

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
