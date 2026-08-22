# PhysiBoard Change Notes

PhysiBoard is a GPLv3 fork of [Pastiera](https://github.com/palsoftware/pastiera) by
Andrea Palumbo (PalSoftware) and contributors. This file documents the fork's changes,
as required by GPLv3 §5(a). Package: `brobata.physiboard` (`physi` product flavor).

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
