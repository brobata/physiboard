# PhysiBoard Production Roadmap

*Arch plan 2026-08-19. Repo: `~/projects/pastiera`, branch `feat/fn-long-press-speech`. Device ground truth: `docs/titan2elite/DEVICE.md`.*

## The Ask
Turn the PhysiBoard fork into an iPhone-grade keyboard for physical-keyboard phones: settings you can navigate without thinking, onboarding that pays off in 60 seconds, touchable UI at Titan 2 Elite geometry, smart backlight, polished dictation — while staying a good GPLv3 citizen that upstreams generic work to Pastiera.

## The Insight
The fork's real product is **opinionation**. Pastiera is a power-user toolbox (29 screens, everything off by default, tiny touch targets); PhysiBoard's value is *decisions already made*: strong defaults, fewer visible choices, device-tuned ergonomics. Every workstream below is a variation on "decide for the user, let them override."

## Domain Lenses Applied
**Mobile IME** (battery, IME-process stability, square-screen layout) + **OSS-fork governance** (GPLv3, upstream hygiene) + **hardware/vendor integration** (AGUI settings, AW9523, scancode profiles). Concerns these force that weren't in the ask:
1. **IME-process discipline** — the keyboard service must never jank or crash from a settings/backlight feature; anything sensor- or settings-observer-based needs strict lifecycle handling (register on screen-on only, unregister in onDestroy).
2. **Battery** — a light-sensor listener held 24/7 is a battery bug. Sample only while screen is on; use `SENSOR_DELAY_NORMAL` + hysteresis.
3. **Fork/upstream commit hygiene** — every commit must be classifiable as *upstreamable* or *fork-only*, or rebasing onto upstream becomes hell within months.
4. **Vendor coupling** — AGUI keys (`agui_keyboard_background_light`, FUNC3, scancode 251) can change in a firmware update; isolate them behind a device-profile layer with graceful no-op fallback.
5. **Muscle memory** — settings reorg must not change behavior or pref keys; only location and presentation.

---

## Workstream 1 — Settings: component fix, then catalog, then IA 🟢

### 1a. `SettingsSwitchRow` rewrite (bug fix, ships first)
- Min-height not fixed-height; description `maxLines=2` + `TextOverflow.Ellipsis`; tapping the row body expands full description inline (animated).
- Row block: 48dp minimum interactive height; switch gets 48dp touch target.
- Add `@Preview(widthDp=574, heightDp=640)` (and fontScale 1.3) to every settings screen touched.

### 1b. Settings Catalog (the architecture piece)
Single source of truth: `SettingsCatalog.kt` — a list of
`SettingEntry(id, prefKey, titleRes, descriptionRes, icon, group, screenRoute, keywords)`.
Powers: (1) **search** (field pinned at top of the main settings screen; instant filter, result rows deep-link to their screen and flash-highlight the row), (2) the top-level group list, (3) future generated screens. No behavior change; every existing pref key untouched.

### 1c. IA regroup: 29 screens → 7 groups
*Keys & Modifiers · Typing & Corrections · Voice · On-Screen Keyboard · Appearance · Shortcuts & Launcher · Advanced.*
- Rules (iPhone standard): ≤8 top-level groups, ≤1 level of nesting before content, one concept per screen, expert toggles behind an "Advanced" link at each screen's bottom, plain-language labels ("When you tap Shift once…").
- Split `CustomizationSettingsScreen` (2,140 lines / 4 domains) and `TextInputSettingsScreen` (892 lines / 6 domains) along group lines; kill duplicate entry points (Launcher shortcuts ×2, Nav mode ×3).
- **Voice** becomes a real top-level section: Fn-hold toggle, haptics, end-of-speech pause, offensive words, continuous mode (WS-5).

**Alternatives considered:** full generated-from-catalog UI (🔴 too big a rewrite, kills upstream diff-ability); search-only without regroup (🟡 fallback if regroup stalls — search alone fixes 80% of discovery pain).

## Workstream 2 — Bottom bar & surfaces 🟢
- **Bar size setting**: Compact 40dp / Comfortable 48dp (default on T2E) / Large 56dp. Buttons get ≥48dp touch areas — via real size where room allows, `TouchDelegate` where visuals must stay small (bar is classic Views, not Compose).
- **Modifier state visuals** (the pizzazz): one component, three states — *pressed* = tonal tint, *latched* = filled accent, *locked* = filled accent + dot badge; 150ms animated transitions via `ValueAnimator`. Same accent family as the amber/slate brand.
- **Six-surface audit** at 574×640dp: status bar, Pastierina strip, variations bar, sym pages, emoji picker, hamburger menu. Each gets an emulator screenshot before/after; wide content scrolls, nothing clips.

## Workstream 3 — Smart backlight 🟡→🟢 after spike
- **Step 0 (spike, on-device, 30 min):** characterize the vendor's `com.agui.server.functional.KeyboardLightController` (already registered on the ALS sensor). Does the stock behavior already do light-based control? Does the global setting flip take effect instantly (confirmed writable)? Any hidden brightness/timeout settings keys? Decision gate: **configure** vendor behavior vs **override** it.
- **Design (override path):** `KeyboardBacklightManager` inside the IME service. Inputs: `ACTION_SCREEN_ON/OFF` receiver, `TYPE_LIGHT` sensor (registered only while screen on), user lux threshold. Rule per user spec: **backlight ON whenever screen is on AND ambient < threshold**; OFF otherwise. Hysteresis: on below `T`, off above `1.5×T`, 10s minimum dwell — no flicker at dusk.
- **Settings UX:** slider with **live lux readout** ("Right now: 12 lux — below your threshold, backlight on") so the user calibrates by looking at the room, not guessing numbers.
- **Permission flow:** effector is `Settings.Global` write → needs `WRITE_SECURE_SETTINGS` granted once via ADB. Settings screen shows a status card with the exact `adb` command, a copy button, and live granted/not-granted detection.
- **Brightness tiers ("minimal glow")**: blocked pending hardware dimming discovery; AW9523 supports 256-step dimming but no userspace interface found. Root-optional stretch goal; do not block v1.

## Workstream 4 — Two-tier onboarding 🟢
- **Quick Setup (≤3 screens, <60s):** (1) Welcome + Enable/Select keyboard with live status chips (polls `InputMethodManager`; buttons launch the system dialogs inline); (2) "Impact defaults" screen — one switch list, all pre-checked, applied in bulk: Fn-hold dictation (scancode from device profile), dictation haptics, mic in status bar, Enter-behavior presets for detected chat apps, comfortable bar size, smart backlight (if permission present); (3) Done + two buttons: *Start typing* / *Guided setup (5 min)*.
- **Guided Setup (optional wizard):** a `WizardHost` that sequences the *existing* key choice screens (Modifiers → Nav Mode → Auto-correction → Status-bar buttons → Voice) with progress dots, per-step "skip", and "use recommended" as the primary CTA on each step. Reuses screens — no duplicated UI to maintain.
- First-use contextual hints replace the current 11-page tutorial (keep the tutorial reachable under Help).

## Workstream 5 — Dictation continuous mode 🟡
- Setting: "Keep listening until dismissed." On `onResults`: commit text + restart the recognizer session (~200ms gap, no start-haptic on restarts). Stops on: Fn-hold again, mic button tap, editor focus loss, or a user-configurable max silence (reuses the pause slider; hard cap 30s per session segment for battery).
- This is also the guaranteed fix if the end-of-silence extras turn out to be ignored by Google's recognizer (test pending on-device).

## Workstream 6 — Icon & brand 🟡
Design brief: the keyboard-keys mark (already shipped as welcome art) as the adaptive foreground, slate `#1E293B` bg, amber `#F59E0B` spacebar accent; monochrome layer for themed icons; round + legacy densities. Replace the temp Brobata oval. Keep the oval in About as the "by Brobata" mark.

## Workstream 7 — Upstream PR strategy 🟢
- **Branch model:** `physi-main` (fork trunk) = upstream/main + fork-only commits (branding, defaults, device opinions). Every generic feature developed on its own branch cut from `upstream/main`, merged into `physi-main`, PR'd upstream.
- **PR order (build maintainer trust small→large):** (1) tagline hardcode → string resource (done, tiny), (2) SettingsSwitchRow clipping fix, (3) Fn-hold speech + scancode setting, (4) dictation haptics/pause/masking toggles, (5) settings search, (6) Titan 2 Elite curated profile additions. Each PR: screenshots, the measured-data rationale from DEVICE.md, defaults off.
- Monthly `git fetch upstream && rebase` cadence; fork-only commits stay a short, reorderable tail.

---

## Five extra suggestions (beyond the backlog)
1. **Settings export/import** — ALREADY EXISTS upstream (`backup/BackupManager.kt` + `RestoreManager.kt`, under Advanced). Action reduced to: surface "Restore my setup" in Quick Setup onboarding. ✅
2. **Per-app raw mode** — SHIPPED v1 (2026-08-19): `RestrictedReason.APP_RAW_MODE` + `app_raw_mode_packages` pref + "Raw mode apps" screen under Smart features; disables suggestions/autocorrect/autocap/double-space per app. Upstreamable. ✅
3. **Fn-chord app launcher** — FUTURE, needs definition with user (which chords, conflict with Ctrl chords, discoverability). 🟡
4. **Clipboard pins** — FUTURE, needs definition with user (pin UX, storage, sync with clipboard retention). 🟡
5. **Quick Settings tile** — SHIPPED v1 (2026-08-19): `KeyboardBacklightTileService` in physi source set toggles the vendor backlight global setting; WRITE_SECURE_SETTINGS granted on user device. Will grow into smart-backlight mode toggle in WS-3. ✅

## Pre-mortem
1. **Upstream divergence rots the fork** → strict fork-only vs upstreamable commit discipline (WS-7), monthly rebase, PRs early and small.
2. **Vendor firmware update renames AGUI keys / changes FUNC3 delivery** → all vendor touchpoints behind `TitanDeviceProfile`; every vendor call no-ops gracefully and surfaces a "device behavior changed" notice instead of crashing the IME.
3. **Settings reorg regressions** — a moved toggle that stops working is worse than a jumbled menu → pref keys never change, catalog entries are tested (unit test: every catalog `prefKey` exists in SettingsManager), emulator screenshot suite per screen, and the reorg ships one group at a time.

## Build Order
- [ ] **Phase 0 (1 session):** backlight spike on-device · SettingsSwitchRow fix · settings export/import (small, unblocks everything)
- [ ] **Phase 1:** SettingsCatalog + search (no moves)
- [ ] **Phase 2:** IA regroup, split monster screens, previews + screenshot suite
- [ ] **Phase 3:** bottom bar — sizes, touch delegates, modifier states, six-surface audit
- [ ] **Phase 4:** KeyboardBacklightManager + lux slider + permission flow
- [ ] **Phase 5:** Quick Setup + Guided Setup wizard
- [ ] **Phase 6:** continuous dictation · real icon · upstream PR batch 1–3
- [ ] **Ongoing:** per-app profiles, Fn-chords, clipboard pins, QS tile as slack-time items

**Complexity:** Medium-Complex overall; each phase is independently shippable and dogfooded on the T2E before the next starts.

## Decision Log
- **Sketch-depth planning, no 20q interview** — discovery already happened live in-session with measured device data.
- **Catalog-driven settings, not a full generated-UI rewrite** — keeps upstream diffs reviewable.
- **Backlight rule = screen-on + below-threshold** (user clarified: not typing-gated); typing-gated glow deferred with hardware dimming.
- **Reuse existing screens inside the wizard** rather than building parallel onboarding UIs.
- **Defaults differ from upstream deliberately** — PhysiBoard's identity is opinionated defaults; upstream PRs always ship features default-off.
