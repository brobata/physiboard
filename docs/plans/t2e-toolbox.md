# PhysiBoard → Titan 2 Elite Toolbox

Status: plan, not built. Written 2026-08-24.

## Decisions locked

1. **Disable first.** `pm disable-user` is the default action. Full `pm uninstall --user 0`
   is offered per item, behind an explicit risk warning.
2. **Titan 2 Elite only.** The catalog is hard-gated on device model. Inert elsewhere.
3. **No upstream merges.** We maintain forward. Pastiera compatibility is no longer a
   constraint, so options can be deleted rather than hidden.

## The reframe: the problem is options, not bytes

The stated goal was "slim it down". Measuring the shipped 1.0.8 APK says that instinct
points at the wrong target:

```
194.3 MB  assets/common   <- dictionaries
  9.9 MB  classes.dex     <- ALL Kotlin, 257 files
  1.8 MB  resources.arsc
  0.9 MB  res/*.ttf       <- JetBrains Mono
```

Every line of Kotlin is 5% of the payload. Deleting features would be weeks of
destructive refactoring for roughly zero megabytes.

The actual complaint was *"so many options I'll never use and it's confusing."* That is
cognitive load, and it lives in the **settings surface**, not the codebase. So:

- **Product change** = curate the settings surface. Ships fast, no regression risk.
- **Housekeeping** = delete the dead code behind it, later, once nothing references it.
- **Size** = drop unused language dictionaries. One lever, reversible, unrelated to both.

Do not conflate the three. Only the first is what the user is asking for.

## Target information architecture

The app stops presenting as a keyboard with device features bolted on, and starts
presenting as a device toolbox that also types.

```
Home            status + only what needs action (unchanged philosophy)

Device          <- NEW: the toolbox, the reason the app exists
  Keyboard backlight        (existing)
  Remove bloat              (NEW)
  Fn -> Ctrl                (existing, moved from "Fn Layer")
  Orange key                (existing, moved from Voice)
  ADB pairing / status      (existing, promoted from Backlight screen)
  Reset device settings to stock

Keyboard        essentials only
  Typing            auto-capitalisation, Exact Typing apps, auto-correct
  Voice             dictation + assistant triggers
  Screen trackpad
  Theme
  Sound & haptics

Advanced        one drawer for everything not in the two lists above
About
```

Everything currently reachable stays reachable. It moves to Advanced rather than
vanishing, so nothing needs deleting to ship the simplification.

## Safety model

The blast radius changed. A keyboard bug types a wrong letter; a toolbox bug uninstalls
someone's dialer, on a stranger's phone, shipped via an auto-updating GPLv3 release.

- **Code-level denylist**, not a UI convention. Never removable:
  `com.agui.shortcutsettings` (owns the orange key we ship), `com.agui.settings`,
  `com.agui.update` (killing it ends OTAs forever), `com.agui.spacebarkey`,
  every `*overlay*` package (RRO resources, not apps), and anything matching
  telephony / dialer / SMS / SystemUI.
- **Catalog-validated packages only.** The broker is a shell. No free-text package field,
  ever. Same injection class as the VendorSideKeyManager fix in 1.0.8.
- **Journal before acting.** Package, timestamp, prior state, written before the command
  runs, so Restore All works even if a later catalog drops the entry.
- **All-or-nothing batches.** No broker, no partial application.
- **Restore All is always one tap**, and is wired into Reset device settings to stock.

## Catalog (inventoried on-device 2026-08-24)

### Safe - factory tooling, no user purpose
com.bhpme.AgingTest, com.agui.factorytest, com.agui.calibration,
com.agui.app.memtester, com.agui.app.imei, com.agui.batterystatsdumper,
com.agui.app.apninfocollector, com.debug.loggerui, com.devices116, com.swatch.gps,
com.example.feedback

### Optional - real features you may not want
com.agui.systemmanager, com.agui.appblock, com.agui.applock, com.agui.privatespace,
com.agui.frozen, com.agui.aguigrabageclear, com.agui.studentmodel, com.agui.game,
com.agui.bedtimesetting, com.agui.callrecord, com.agui.providers.pedometer,
com.agui.rotationcontrol, com.agold.autopoweronoff, com.agui.toolbox,
com.iqqijni.bbkeyboard

### Keep by default, flag as useful hardware
com.tiqiaa.icontrol (IR blaster), com.android.fmradio, com.agui.nfc

### Blocked - denylist
com.agui.shortcutsettings, com.agui.settings, com.agui.update, com.agui.spacebarkey,
com.agui.keyboard + com.agui.overlay.kika (keep one fallback IME installed),
all *overlay* packages

### On com.agui.systemmanager
Verified on device: `dumpsys deviceidle enabled` = 1, app-hibernation API live, and the
package had **0 running processes**. It is a duplicate layer over AOSP power management,
not a provider of it. Removing it leaves Doze, App Standby buckets, Adaptive Battery,
Battery Saver, the battery-optimization allowlist, appops background restrictions and the
Android 14+ cached-process freezer untouched, because those live in system_server.

Caveat: many OEM ROMs patch aggressive killing into system_server itself, so removing the
app may remove the UI without removing the behaviour. Testable, and worth testing against
the Android Auto disconnects - the same suspect covers both.

## Build order

1. Catalog asset + denylist + device gate (no UI)
2. Removal journal
3. PackageRemover over the broker, dry-run first
4. Bloat remover screen
5. **Prove restore works before shipping any removal path**
6. Device section IA; move existing screens under it
7. Keyboard section collapse; overflow to Advanced

New: `toolbox/BloatCatalog.kt`, `toolbox/PackageRemover.kt`, `toolbox/RemovalJournal.kt`,
`ToolboxScreen.kt`, `BloatRemoverScreen.kt`, `assets/common/bloat_catalog.json`
Modified: `SettingsManager`, `SettingsCatalog`, `SystemChangeManager`, home nav

Complexity: Medium. The privileged mechanism is proven; the work is curation and rails.

## Decision log

- Toolbox lives inside the IME because the IME is what survives boot on this ROM. A
  foreground service from BOOT_COMPLETED crashes on this OEM - proven in 1.0.1, which is
  why AutoReArmService was deleted. A standalone toolbox app could not re-apply state.
- Disable over uninstall by default: same user-visible outcome, far cheaper mistake.
- Settings curation over code deletion: the measurement says deletion buys no size, and
  confusion is a UI problem.

---

# Wave 2: device tweaks

Everything below was probed on a real Titan 2 Elite on 2026-08-24. Capability first —
anything not verified is marked as such.

## Verified capabilities

| Probe | Result | Meaning |
|---|---|---|
| `func2_*` | all `NoOperate` | A second programmable key shipping bound to nothing |
| `home_ recent_ shift_r_ sym_ *_programmable_key_*` | present | Four more remappable keys beyond func1/func2 |
| `wm density` / `wm size` | `300`, `1080x1200` (override `1076x1200`) | Density changeable and resettable |
| animation scales | all `1.0` | Three globals |
| `one_handed_enabled` | `null` | AOSP feature present, never enabled |
| `notification_history_enabled` | `null` | Same |
| `/sys/class/leds/` | empty | NO notification-LED control. Do not promise it |
| battery charge-control node | absent | NO 80% charge limit. Do not promise it |
| fingerprint tunables | none | No sensitivity or timeout knobs exist in AOSP or this vendor |

## The governing rule: auto-revert, not confirmation

Anything that can make the phone unusable needs a **timed auto-revert**, not a confirm
dialog. Density is the proof: set it wrong and the UI is unreadable, so a "confirm" button
is useless — you cannot read the screen to press it.

The pattern, borrowed from every monitor resolution dialog ever:

    journal -> apply -> "Keep this? Reverting in 15s" -> revert unless confirmed

Build it once as a reusable primitive; every risky tweak after this reuses it.

## Tiers

### Tier 1
1. **Second programmable key (func2)** — free, mechanism already proven by the orange key.
   Needs a `getevent` while the user presses candidate side keys to learn which one it is.
2. **Display density** — the most-requested Android tweak there is, and on a 1080x1200
   screen fitting more rows is a real daily win. First consumer of the auto-revert primitive.
3. **Animation speed** — three globals, instantly felt, zero risk.

### Tier 2 — Pixel parity
4. Notification history, 5. One-handed mode. Both AOSP, both unset here, both one secure
   write away. Ship as a **"Pixel feel" preset** rather than as more toggles.

### Tier 3 — real work
6. Flip to Shhh (accelerometer + DND). 7. Fingerprint-swipe for notifications — note the
   spacebar sensor is owned by com.agui.spacebarkey, which is on the denylist.

### Not possible on this hardware
Notification LED, battery charge limit (no kernel nodes), Now Playing / Call Screen
(Google's on-device models).

## Findings worth surfacing to the user, not building

- `screen_off_timeout = 2147483647` — the screen never sleeps. Likely costs more battery
  and heat than every vendor app combined.
- `dumpsys fingerprint`: accept 197 / reject 117 / acquire 441 / lockout 3. A 37% reject
  rate on a sensor that is also the spacebar. No setting fixes this; re-enrolling the same
  finger at several angles does.

## Design principle for the whole toolbox

Presets, not switches. "Pixel feel", "Faster", "Debloated" — with individual controls
behind Customise. The failure mode to avoid is becoming the pile of confusing options this
app is trying to escape.
