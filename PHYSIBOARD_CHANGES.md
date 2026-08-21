# PhysiBoard Change Notes

PhysiBoard is a GPLv3 fork of [Pastiera](https://github.com/palsoftware/pastiera) by
Andrea Palumbo (PalSoftware) and contributors. This file documents the fork's changes,
as required by GPLv3 §5(a). Package: `brobata.physiboard` (`physi` product flavor).

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
