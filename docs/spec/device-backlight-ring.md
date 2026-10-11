# Device: keyboard backlight, smart backlight, notification ring, Titan hardware facts

This document specifies everything PhysiBoard does to the Titan 2 Elite hardware itself:
the keyboard backlight (the vendor's master switch, timeout and the Quick Settings tile),
the "smart backlight" feature as it exists today (a persistent always-on timeout) and as it
existed briefly (a light-sensor policy), the notification ring (a glow around the camera hole
while the screen is off), the device setup card that gates every privileged feature, and the
numbered facts about the phone that the rest of the specification relies on.

The embedded ADB broker (pairing, discovery, connection, shell execution, verification) is
specified in `broker-privileged-toolbox.md`. This document describes only what the features
here ask the broker to do, and what happens when the broker cannot do it. Screen density is
also in `broker-privileged-toolbox.md`; it is a toolbox feature and only appears here as a row
on the same hub.

The notification-vibration quirk (notification-class vibration is muted when the system's
notification vibration is off, so dictation cues use plain vibration) is implemented in the
dictation cue path and is specified in `dictation.md`. Nothing in this document vibrates. The
ring's announcement notification explicitly has vibration, sound, lights and badge disabled.

## 1. Where the features live

All three features sit on the **T2E Tools** hub (label "T2E Tools", intro: "Titan-specific
tools. These change the phone itself rather than the keyboard, so anything here that outlives
an uninstall can be undone with Reset device settings to stock."). The hub's header is the
device setup card (section 6). Its rows, in order: Smart keyboard backlight, Remove bloat,
Screen density, System tweaks, Notification ring, Screen trackpad, and the rest of the toolbox.

| Row label | Row description | Opens |
|---|---|---|
| Smart keyboard backlight | Keep the keyboard lit in the dark, past the 30s limit | Smart backlight screen (section 3) |
| Screen density | Fit more on screen, or make everything bigger | Density screen (toolbox spec) |
| Notification ring | A glow around the camera hole while the screen is off | Notification ring screen (section 5) |

The Notification ring screen's "Set up pairing" link and the toolbox rows' pairing hints all
navigate to the Smart backlight screen, because that is the screen that historically hosted the
pairing walkthrough (the setup card is still shown there when the feature is on but not yet
configured).

## 2. The keyboard backlight as the phone runs it

What the vendor firmware does with no help from PhysiBoard (facts D10 to D19):

1. The keyboard LEDs light when the screen turns on and on every keypress.
2. They go dark 30 seconds after the last keystroke. The stock settings UI caps the timeout at
   30 seconds; there is no "keep on" option.
3. A master switch, `Settings.Global` key `agui_keyboard_background_light` (1 = on, 0 = off,
   unset reads as on), turns the keyboard light off entirely. The vendor reads it when the
   screen comes on, so a change made while the screen is already on takes effect at the next
   screen-on or keypress. Flipping it live has been confirmed to work.
4. A timeout, vendor setting `keyboard_brightness_timeout` held by the vendor's own settings
   store (binder service `agui_functional_service`), accepts the sentinel `-1` meaning never
   turn off. The stock value is `30000` (milliseconds). This store survives reboots. Writing it
   as a plain `settings put global` has no effect; it must go through the binder service.
5. Other vendor keys exist and are not used by PhysiBoard: `keyboard_led_brightness`,
   `keyboard_led_auto_switch`, `agui_keyboard_led_timer`, and the broadcast
   `agui.action.CLOSE_KEYBOARD_LIGHT`.

### 2.1 Modes the app offers

The app offers exactly two states of the keyboard light's timing, plus the master switch:

| Mode | How it is reached | What the keyboard does |
|---|---|---|
| Stock ("on while typing") | Smart backlight switch off (default for an install that has not been stamped with first-run defaults) | Lit on screen-on and keypress, dark 30 s after the last keystroke |
| Always on | Smart backlight switch on and the vendor timeout written as `-1` | Lit whenever the screen is on, never times out; dark when the screen is off (the vendor still ties it to the screen) |
| Off | Quick Settings tile "Keyboard light" inactive, or the master switch written 0 by the ring (section 5.8) | LEDs never light, regardless of timeout |

There is no "on while typing only" mode distinct from stock, no brightness level control, and
no light-sensor control in the shipped app (see section 4 for what was tried).

### 2.2 The Quick Settings tile

A Quick Settings tile labelled **Keyboard light** toggles the master switch. Its icon is the
backlight keycap (`pb_ic_backlight`: a keycap with light rising off it, from the shared icon
family, docs/design/design-system.md; amended 2026-10-09 from a bulb).

Behavior:

1. When the tile is shown (the shade is opened), its state is refreshed: unavailable when the
   app does not hold `android.permission.WRITE_SECURE_SETTINGS`; active when
   `agui_keyboard_background_light` reads 1 (or is unset); inactive when it reads 0. The label
   is always "Keyboard light". The subtitle is "Needs ADB grant" when the permission is missing,
   otherwise empty.
2. Tapping without the permission shows a long toast: "PhysiBoard needs WRITE_SECURE_SETTINGS.
   Grant once via ADB: adb shell pm grant brobata.physiboard
   android.permission.WRITE_SECURE_SETTINGS", and refreshes the tile. Nothing is written.
3. Tapping with the permission:
   a. If no original value has been captured yet (`qs_backlight_prev_captured` false), the
      current value of the global is stored once in `qs_backlight_prev` (the sentinel
      `Int.MIN_VALUE` when the system has no value) and `qs_backlight_prev_captured` becomes
      true. Repeated taps never overwrite it. This is what "Reset device settings to stock"
      restores.
   b. Exception: if a notification ring currently has the switch turned off on the user's
      behalf (`ring_backlight_prev_captured` true), the value captured as the original is the
      value the ring recorded (`ring_backlight_prev`), not the live 0, and the ring's record is
      cleared in the same tap. The ring's later restore then finds nothing to do, and the tile
      owns the switch from here.
   c. The global is written to the opposite of its current reading (1 becomes 0, 0 or unset
      becomes 1). A `SecurityException` is logged and swallowed.
   d. The tile refreshes.
4. The permission arrives one of two ways: the user runs the `pm grant` command over a computer
   ADB session, or the paired broker runs it (section 5.9) when the notification ring is
   enabled. The Smart backlight feature itself does not grant it.

Known bug (from the source, not yet seen on a device): the tile's declared component name in
the manifest is `brobata.physiboard.KeyboardBacklightTileService`, but since the namespace
rename of 2026-08-27 the class is compiled in the package `brobata.physiboard.physi`. The
system cannot instantiate a component whose class name does not exist, so the tile is expected
to be missing from the tile picker or to fail when added. Lint runs against a baseline and is
disabled for release builds, so nothing catches it. A rewrite must declare the tile under the
name it compiles to and verify it on the phone.

## 3. Smart backlight (always-on timeout)

### 3.1 What it does

When the switch is on, the app asks the paired broker to write the vendor timeout to `-1` once.
Because the value persists in the vendor's store, the feature has no runtime component at all:
no sensor, no screen receiver, no per-keystroke work, nothing to re-arm after a reboot. Turning
the switch off writes `30000` back. Reset to stock (section 8) does the same and clears the
flags.

The exact broker requests:

| Purpose | Shell line the broker runs |
|---|---|
| Always on | `service call agui_functional_service 2 s16 "keyboard_brightness_timeout" s16 "-1"` |
| Stock | `service call agui_functional_service 2 s16 "keyboard_brightness_timeout" s16 "30000"` |
| Read back | `service call agui_functional_service 1 s16 "keyboard_brightness_timeout"` |

Transaction 2 is SET(key, value); transaction 1 is GET(key) and returns a parcel holding a
UTF-16 string (facts D16, D17).

### 3.2 When the write is attempted

The write is attempted, in this order of events, whenever any of these happen:

1. The user turns the Smart backlight switch on (always-on) or off (stock).
2. The keyboard service is created (IME start) and the switch is on. This is part of "apply
   every privileged step" (also grants the trackpad overlay, and the ring's grants when the ring
   is on).
3. Pairing succeeds (same "apply every privileged step").
4. The Smart backlight screen is open, the switch is on, and a pairing key is stored (covers
   "enable first, pair second"; re-evaluated whenever either of those changes while the screen
   is open).
5. The user taps "Apply again" on the screen (section 3.4).

Before every write, a cheap gate runs on the calling thread: if no pairing key is stored, the
reason `not_paired` is recorded; else if `Settings.Global` `adb_wifi_enabled` is not 1, the
reason `wireless_debugging_off` is recorded; in either case nothing is queued (a queued attempt
would otherwise block for up to 8 seconds of mDNS discovery). Otherwise the write is queued on a
single background worker so calls never overlap.

After the worker runs:

| Outcome | Effect |
|---|---|
| Shell succeeded | `smart_backlight_applied` set to true for the always-on write, false for the stock write; then the value is read back over the same connection and stored in `privileged_backlight_device_value` (string, or absent when unreadable) with `privileged_backlight_device_value_at` (epoch ms); then the step outcome is recorded as ok |
| Shell returned failure | Step outcome recorded as failed with the broker's last error text, or `shell_failed` when it had none |
| Exception | Step outcome recorded as failed with the exception's class name and message |

Step outcomes are stored under `privileged_backlight_ok` (boolean), `privileged_backlight_reason`
(string) and `privileged_backlight_at` (epoch ms). The gate failure is recorded the same way.
The Diagnostics export prints all of these plus `backlight_enabled`, `backlight_applied_flag`,
`backlight_device_value` ("never read" when absent) and `backlight_device_value_at`.

Reading the parcel: the output looks like `Result: Parcel(00000000 00000002 0031002d ...
'....-.1....')`. Word 0 is the exception code, word 1 the character count, and every following
word packs two little-endian UTF-16 code units (low half first). The hex is parsed, never the
quoted ASCII rendering. A count of 0, a count above 64, fewer than two words, or fewer words
than the count needs all yield "unreadable".

### 3.3 The switch

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `smart_backlight_enabled` | boolean | false (the first-run defaults stamp sets it to true once per install) | Whether the always-on timeout is written at the events in 3.2 | Smart keyboard backlight | "Smart backlight", description "Keeps the keyboard backlight on whenever the screen is on. It needs a one-time pairing, and survives reboots." |
| `smart_backlight_applied` | boolean | false | Readiness latch: the always-on value has been written successfully at least once and not reverted since | (not shown as a control) | none |

The switch row is the last thing on the screen. Flipping it writes the preference immediately,
then attempts the write (always-on when turned on, stock when turned off). Both attempts are
silent no-ops when the gate fails, apart from the recorded reason.

### 3.4 The Smart backlight screen

Title "Smart keyboard backlight". While open, the screen re-reads the stored pairing key,
the readiness latch, the gate reason and the last recorded backlight failure every 2000 ms.
It also asks the shared broker verifier for the live status (OK, NOT_PAIRED,
WIRELESS_DEBUGGING_OFF, NO_SERVICE, REJECTED; null while checking), and whenever the verified
status is OK it asks the phone for the timeout's actual value (blocking read on a background
thread) and remembers whether it equals `-1`.

Layout from the top:

1. If the switch is on and the latch is true, a single status line replaces any card:
   - A verified non-OK status wins: "! " followed by the message for the status
     (WIRELESS_DEBUGGING_OFF: "Wireless debugging is off, so the backlight setting cannot be
     applied. Android turns it off after a restart. Turn it back on in Developer options.";
     NOT_PAIRED: "Not paired. Set up wireless debugging below to apply the backlight setting.";
     NO_SERVICE: "Wireless debugging is on but the phone is not advertising it. Turn it off and
     on again in Developer options."; REJECTED: "The phone refused the pairing. This happens
     when a pairing code was mistyped: the app kept a key your phone never accepted. Re-pair to
     fix it."), in the error colour, followed by a **Re-pair** button (forgets the stored key,
     invalidates the shared verdict, sets the latch false, refreshes) and a **Check again** text
     button (reads "Checking…" and is disabled while a check is running).
   - Else, if the cheap gate fails: "! " and the wireless-debugging or not-paired message.
   - Else: "✓ Always on, set up once, survives reboots." in the primary colour.
   - Additionally, when the broker is reachable and the phone reported a value other than `-1`:
     "! Your phone is no longer holding the always-on setting. A system update or another app
     reset it. The backlight will time out until it is applied again." and an **Apply again**
     button (writes always-on, then re-verifies).
   - Additionally, when the last recorded backlight step failed: "Last attempt failed: <reason>"
     in small muted text.
2. Else, if the switch is on (latch false): the device setup card (section 6) is shown inline.
3. Else (switch off): nothing above the switch.
4. The switch row (section 3.3).

The readiness signal is deliberately the latch, not live wireless debugging: Android turns
wireless debugging off across reboots, and the vendor value outlives that. The latch is a
one-way signal that cannot notice the phone losing the value, which is why the phone is asked
directly whenever the broker can be reached.

### 3.5 Without the privileged pairing

Nothing here works without a stored pairing key and wireless debugging on. The switch can be
turned on regardless; the write is skipped, the reason is recorded, and the screen shows the
setup card (unpaired) or the blocker line (paired, debugging off). The keyboard keeps its stock
30 s behavior. No toast, no notification.

## 4. Smart backlight, light-sensor version (removed)

Shipped in 0.86-physi (2026-08-19) and removed in 1.0.1 (2026-08-21). Recorded here because
the roadmap still describes it and a rewrite may want it:

1. Rule: keyboard lit whenever the screen is on and ambient light is below the user's lux
   threshold; otherwise stock behavior.
2. Mechanism: the vendor's `keyboardLightTest` hold mode, `service call agui_functional_service
   7 s16 1` to hold a lit LED past 30 s, `service call agui_functional_service 7 s16 0` plus
   `am broadcast -a agui.action.CLOSE_KEYBOARD_LIGHT` to release and power it down. Hold mode
   does not light a dark LED; it only keeps a lit one lit, so it relied on the vendor lighting
   the LED at screen-on and keypress.
3. Sampling: light sensor registered only while the screen is on, unregistered at screen off
   and at service teardown (a battery rule the roadmap states as a hard constraint).
4. Hysteresis per the roadmap design: on below T, off above 1.5 x T, 10 s minimum dwell.
5. UI: a darkness-threshold slider with a live lux readout.
6. It needed the broker on every screen-on and every reboot, plus a post-reboot guidance
   notification. That per-boot dance, the threshold and the reminder were all dropped when the
   persistent `-1` timeout was found (D15), which made the feature a one-time write.

The roadmap's "minimal glow" brightness tiers were never built: the AW9523 driver supports
256-step dimming but no userspace interface was found (D6).

## 5. Notification ring

### 5.1 What the user sees

The phone is locked with the screen off. A notification arrives. The screen turns on black,
and a thin coloured ring glows around the camera hole in the top-left corner, breathing slowly.
If enabled, up to three small app icons sit lower on the screen. The keyboard stays dark. The
ring ends the moment the user touches the screen, presses any key, or unlocks; if nothing
happens for the configured time (default 10 minutes; first-run defaults stamp 2 minutes), the
ring stops holding the screen on and the phone's own screen timeout turns it off, black to
black, with no flash. A second notification while the ring is up recolours the ring to the new
app, adds its icon, and restarts the timer. Dismissing the last waiting notification elsewhere
ends the ring.

This is not always-on display: the ROM compiles AOD out (D30). It is an ordinary black activity
shown over the lock screen on an AMOLED panel where black pixels are off.

### 5.2 How notifications are observed

The app registers a notification listener service (label "Notification ring", bound by the
system once the user or the broker grants notification access). It runs whether or not the
keyboard is the active IME and needs nothing from the broker at runtime.

For every posted notification, in order:

1. If `notification_ring_enabled` is false, stop.
2. Apply the policy (5.3). If it says skip, stop.
3. Compute the ring colour (5.4).
4. If a ring is already on screen, hand it the new source on the main thread (recolour, icons,
   restart timer) and stop.
5. If the display is interactive (screen on), stop. The ring only answers a dark screen.
6. On a background worker: run the pocket check (5.5); if covered, stop.
7. Darken the keyboard (5.8).
8. Launch the ring (5.6).

For every removed notification: if a ring is on screen, remove that notification's key from the
ring's sources; when none remain, the ring finishes (unless the charger holds it, 5.11.2);
otherwise the ring takes the colour of the most recently added remaining source and redraws the
icons. A removal does not restart the timer (5.7.3: only a new source does).

"A ring is on screen" (points 4 and 5) means a ring screen exists, or a launch was sent less than
15000 ms ago and its screen has not appeared yet. Sources left over from a launch the system
declined (no screen after 15 s) are dropped at the next notification, which then starts a ring of
its own instead of joining one that never appeared. While connected, the listener also hears the
screen, unlock and charger broadcasts for the charging clock (5.11.2).

### 5.3 Which notifications qualify

Evaluated in this order; the first match wins:

| Reason | Condition |
|---|---|
| own app | package is `brobata.physiboard` |
| ongoing | flags include ongoing-event or foreground-service |
| group summary | flags include group-summary (so a thread rings once, for its child) |
| not clearable | the notification cannot be dismissed by the user |
| silent | priority is minimum (-2) or lower |
| (rings) | none of the above |

Downloads, playback, "running in the background" notices and anything undismissable never ring.
There is no per-app allow or block list; the only per-app setting is colour.

### 5.4 Colour

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `notification_ring_default_color` | int (ARGB) | 0xFF34C759 (green) | Fallback colour | Notification ring | "Default colour", description "Used by apps that set no colour of their own, and by apps whose colour is too dark to see on a black screen." |
| `notification_ring_app_colors` | string, JSON object `{ "<package>": <ARGB int>, ... }` | absent (empty) | Per-app colour overrides | Notification ring | "App colours" |

Resolution for a notification from package P with declared colour C:

1. If P is in the per-app map, that colour.
2. Else if C is 0 (none declared), the default colour.
3. Else if C is too dark (WCAG relative luminance below 0.12, computed from the sRGB channels),
   the default colour.
4. Else C.

The picker (shared with the cursor-modifier colours) offers nine quick swatches: green
0xFF34C759, blue 0xFF2F80ED, cyan 0xFF22D3EE, purple 0xFFA855F7, pink 0xFFF472B6, red 0xFFEF4444,
orange 0xFFF97316, yellow 0xFFFACC15, white 0xFFF5F5F5, above a hue/saturation disc with a
separate brightness slider. The brightness slider is floored at 0.15 (never black); the initial
brightness of the current colour is raised to at least 0.35 when the wheel opens. Any live
choice with luminance below 0.12 shows the caution "This is too dark to see on the screen while
it is off. Slide the brightness up." but can still be chosen. Per-app colour is set by "Add an
app" (an app picker) and each listed app has a "Remove" button; the list is sorted by app label,
case-insensitive. A picked colour is stored immediately.

### 5.5 Pocket check

Before launching, the listener reads the proximity sensor once: it waits up to 300 ms for a
reading; the phone counts as covered when the reading is below the smaller of the sensor's
maximum range and 5 cm. No sensor, a registration failure, or no reading within 300 ms all
count as clear (a missed ring is cheaper than a phone that never rings). The sensor is
unregistered as soon as the reading arrives or the wait ends.

### 5.6 Launching on a dark, locked screen

An app cannot start an activity from a background service on Android 10+; the overlay
exemption was narrowed in Android 15 to apps with a visible overlay, which the lock screen
hides. The one route left is a full-screen notification, the mechanism alarms and calls use.
So:

1. If the app may not use full-screen intents (Android 14+ permission) or its notifications are
   blocked, a direct activity start is attempted and its failure logged; the system may refuse.
2. Otherwise a channel `physiboard_notification_ring` ("Notification ring", description "Used
   only to turn the screen on for the ring. Silent, and dismissed on its own.") is created
   once with high importance, no sound, no vibration, no lights, no badge; and notification id
   41 is posted on it: the ring icon (`pb_ic_ring`, a dot inside a ring), title "Notification ring", text "Turning the screen on for a
   notification", priority high, category alarm, group-alert-all (setting "silent" on the
   notification itself would mark it alert-suppressed and SystemUI refuses full-screen launches
   for those), auto-cancel, timeout 15000 ms, full-screen intent pointing at the ring activity
   with the notification key, package and colour as extras.
3. The ring activity cancels notification 41 the moment it is created. The user never sees the
   announcement. If the system never launches the activity, the announcement disappears on its
   own after 15 s.

The intent uses new-task and single-top flags; the activity is single-task, no-history,
excluded from recents, in its own task affinity `brobata.physiboard.ring`, shown when locked,
turns the screen on, with a black window background, no title, fullscreen, no window preview,
no window animation, black status and navigation bars.

### 5.7 The ring on screen

On creation the activity:

1. Records itself as the current ring, cancels the announcement, takes ownership of the
   keyboard restore (cancelling the orphan timer, 5.8).
2. Sets show-when-locked, turn-screen-on, keep-screen-on; lays out into the display cutout
   (mode "always"); sets the window brightness to the chosen level; hides the system bars
   (swipe reveals them transiently).
3. Places the ring (5.7.1) when window insets arrive; if no insets callback ever comes, places
   it at window attach using the override or the Titan fallback.
4. Reads its source from the intent; a launch with no key and not in demo mode finishes at
   once.
5. Registers for `android.intent.action.SCREEN_OFF` and `android.intent.action.USER_PRESENT`;
   either finishes the ring.
6. Starts the breathing animation: a value from 0 to 1 and back, 1800 ms per leg, linear,
   forever.

Ending: touch (finger up) finishes; any key down finishes (and the key is still passed on);
screen off finishes; unlock finishes; last source removed finishes. Expiry (timer, 5.7.3) does
not finish a real ring; it only releases keep-screen-on, so the phone's own timeout ends it.
In demo mode expiry finishes. Every ending path passes through one teardown that restores the
keyboard (5.8), cancels the timer and animation, unregisters the receiver, and clears "current
ring".

#### 5.7.1 Geometry and position

All pixel numbers below are at the Titan's 300 dpi; on any other density they scale by
density/300.

| Source of the ring, in priority order | Values |
|---|---|
| User override (`notification_ring_radius` present) | centre `notification_ring_cx`, `notification_ring_cy`; radius `notification_ring_radius`; stroke `notification_ring_stroke` (all floats, window px). A stored stroke of 0 is replaced by 3 dp |
| The system reports the Titan's own cutout: left 0, top 0, right and bottom each within 2 px of 123 | The fitted ring: centre (78, 80), radius 46, stroke 6 (D28: measured off the panel on 2026-08-26; the lens sits lower and further right than the centre of the reported box, and is smaller than it) |
| The system reports some other cutout rectangle | Centre of the rectangle; radius = half the longer side + gap + half the stroke; gap 3 dp, stroke 3 dp (5.625 px each at 300 dpi) |
| No cutout reported | The Titan's hole assumed as a 123 px square at (0, 0), rung as the previous row |

Example: the 123 px square with gap 6 and stroke 4 gives centre (61.5, 61.5), radius 69.5.

Drawing, every frame, with breath b in 0..1 and alpha factor a = 0.35 + 0.65 b:

1. Background black (the panel is off wherever nothing is drawn).
2. Glow: same colour, alpha 90 a (of 255), stroke width 2.5 x stroke, normal blur of radius
   2 x stroke, drawn as a circle at the ring's centre and radius.
3. Ring: same colour, alpha 255 a, stroke width = stroke, same circle.
4. Icons (only when `notification_ring_icons` is true): the icons of the distinct waiting
   packages, at most the last 3, each 22 dp square, 14 dp apart, centred horizontally, top edge
   at 42 % of the window height, alpha 200 a.

#### 5.7.2 Brightness

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `notification_ring_brightness` | string: `DIM`, `NORMAL`, `BRIGHT` (unknown reads as NORMAL) | `NORMAL` | The ring window's screen brightness: DIM 0.05, NORMAL 0.2, BRIGHT 0.6 of maximum | Notification ring | "Ring brightness", chips Dim / Normal / Bright |

On an AMOLED panel the black rest of the screen costs nothing, so this is the only knob that
trades visibility for battery.

#### 5.7.3 Duration

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `notification_ring_minutes` | int, 1..60 | 10 (first-run defaults stamp: 2) | Minutes the ring holds the screen on after the latest notification | Notification ring | "Keep the screen on for", description "After this the ring lets go and the phone's own timeout turns the screen off. Longer costs battery: the panel is on, even if almost all of it is black.", value shown as "<n> min" |

The slider is continuous from 1 to 60 and rounds down to whole minutes (a stepped slider would
draw 59 tick marks across a 574 dp screen); the value is saved when the drag ends. Each new
source re-adds keep-screen-on and restarts the timer at minutes x 60000 ms. Demo rings use
8000 ms.

#### 5.7.4 Other ring settings

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `notification_ring_enabled` | boolean | false (first-run defaults stamp: true) | Whether posted notifications are considered at all; turning it on also triggers the broker grants (5.9) | Notification ring | "Ring on new notifications", description "Only notifications you could dismiss; nothing for downloads, playback or apps running in the background." |
| `notification_ring_icons` | boolean | false | Draw waiting apps' icons below the ring | Notification ring | "Show app icons", description "Draw the waiting apps' icons below the ring. Off, it is just the ring." |
| `notification_ring_keyboard_dark` | boolean | true | Turn the keyboard master switch off for the ring (5.8) | Notification ring | "Keep the keyboard dark", description "The keyboard backlight normally comes on whenever the screen does, so a ring at night lights up the whole keyboard with it."; below it, when the app lacks WRITE_SECURE_SETTINGS: "Unavailable until the phone has been paired once, on the Keyboard backlight screen." in the error colour (the switch stays operable but inert) |
| `notification_ring_cx`, `notification_ring_cy`, `notification_ring_radius`, `notification_ring_stroke` | float x4, window px | absent | Hand-fitted ring; presence of the radius key means "override in force" | Fit screen (5.7.5) | "Fit the ring to the lens" |

#### 5.7.5 The fit screen

Reached by "Fit the ring to the lens" (description: "The ring comes fitted to one Titan 2
Elite, and panels differ by a few pixels. If yours is off: on a white screen the lens shows as
a dark spot: drag the ring onto it, resize it and set the thickness you want. Auto puts the
fitted default back."). A fullscreen white canvas laid out into the cutout with system bars
hidden, a red ring (0xFFD32F2F) drawn with the same glow, a centred hint "Drag to move · arrows
nudge · + and − resize · [ and ] change thickness · Enter saves", and a bottom bar of buttons:
"−" (radius minus 1 px), "+" (radius plus 1 px), "Thinner", "Thicker" (stroke minus or plus
1 px), "Auto" (clears the four override keys and re-derives the ring from the cutout), "Done"
(saves the four keys and closes).

The screen seeds from the override if present, else from the same cutout logic as the real
ring. Dragging moves the centre by the finger's delta. Keys: DPAD left/right/up/down move the
centre 1 px; `+`, `=` and numpad add grow the radius 1 px; `-` and numpad subtract shrink it;
`[` and `]` change stroke by 1 px; Enter or DPAD centre saves. Radius never goes below the
stroke; stroke is clamped to 1..40 px. Back leaves without saving.

#### 5.7.6 "Try it"

"Try it" (description "Shows the ring for a few seconds with PhysiBoard's own icon. Lock the
phone first to see it the way it will really look.") starts a demo ring directly: source key
"demo", the app's own package, the built-in green, 8000 ms, after which it finishes itself. The
demo does not darken the keyboard (that happens in the listener, not the activity) and is not
subject to the policy or the pocket check. With `notification_ring_clock` on, the demo also shows
the clock (5.11.1), and the description reads "Shows the ring and the clock for a few seconds
with PhysiBoard's own icon. Lock the phone first to see it the way it will really look."; the
demo is never held by the charger.

### 5.8 Keeping the keyboard dark and putting it back

The vendor lights the keyboard whenever the screen comes on, and with the always-on timeout it
then never goes off, so a ring at 3 am would light the whole keyboard. The lever is the master
switch `agui_keyboard_background_light`, written in-process (needs WRITE_SECURE_SETTINGS), not
the timeout (a broker round trip of seconds, useless in the moment a notification lands). The
switch is turned off in the listener, before the launch, because the vendor reads it at
screen-on and by the time the activity exists the screen is already on.

Suppress (listener, background thread, before launch), skipped entirely when any of: the ring
is disabled; `notification_ring_keyboard_dark` is false; the permission is missing; a
suppression is already outstanding (`ring_backlight_prev_captured` true):

1. Read the switch (unset reads as 1). If it is 0 the user turned the keyboard off themselves;
   do nothing and record nothing (there is nothing of ours to put back).
2. Commit (synchronously, not lazily) `ring_backlight_prev` = the value read and
   `ring_backlight_prev_captured` = true, BEFORE touching the switch. A process death between
   the two must leave a restore still to do.
3. Write the switch to 0. If the write fails, clear the record.
4. Arm an orphan timer of 20000 ms: if no ring has taken ownership by then (the system may
   decline the full-screen launch; 20 s is longer than the 15 s announcement timeout), restore.

Restore (any thread, any time; a no-op when no record exists):

1. Cancel the orphan timer, drop ring ownership.
2. If the permission is gone, log an error and KEEP the record (a later grant can heal it;
   dropping it would strand the keyboard off with nothing that knows to put it back).
3. Write the recorded prior value back; on success clear the record.

Restore is called from the ring's teardown (every ending path), from the orphan timer, and at
every process start of the app (off-thread), which heals a ring that darkened the keyboard and
then died with its process. The Quick Settings tile also resolves an outstanding record on its
next tap (section 2.2 step 3b). A second suppress while one is outstanding never overwrites
the recorded value. Reset to stock does not touch this record.

### 5.9 The three grants and the pairing requirement

The ring needs three things the app cannot give itself, plus one more for the dark keyboard:

| Grant | How the screen reads it | Broker shell line that grants it | Manual route |
|---|---|---|---|
| Notification access ("Notification access": "Granted" / "Not granted, the ring cannot see notifications") | the app's listener component is in the enabled listeners | `cmd notification allow_listener brobata.physiboard/brobata.physiboard.ring.NotificationRingListener` | `android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Full-screen intents ("Show over the lock screen": "Allowed" / "Not allowed, the ring cannot turn the screen on") | the notification manager's can-use-full-screen-intent (always true below Android 14) | `appops set brobata.physiboard USE_FULL_SCREEN_INTENT allow` | `android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT` with `package:brobata.physiboard` (Android 14+), else the app's notification settings |
| App notifications enabled ("PhysiBoard notifications": "Allowed" / "Blocked, the ring is announced through a silent notification you never see") | notifications enabled for the app | `appops set brobata.physiboard POST_NOTIFICATION allow` | `android.settings.APP_NOTIFICATION_SETTINGS` with the package extra |
| WRITE_SECURE_SETTINGS (keyboard dark) | the permission check | `pm grant brobata.physiboard android.permission.WRITE_SECURE_SETTINGS` | a computer ADB session |

The first three are run as one shell line joined by `; `. The broker grant runs (best effort,
off-thread, never throws, no-op when all three are already in place or no key is stored):

1. When the user turns the ring switch on.
2. When the user taps "Grant with the paired setup" (shown only when something is missing and
   the verified broker status is OK; shows a spinner while running).
3. As part of "apply every privileged step" (IME start, pairing success, backlight screen) when
   the ring is enabled. The `pm grant` for the dark keyboard runs there too; its success is
   judged by re-checking the permission, not by the shell's exit code (`pm grant` is quiet
   either way), and is recorded under `privileged_ring_backlight_ok` / `_reason` / `_at`
   (`ok`, `not_paired`, the broker's last error, or `shell_failed`).

When something is missing and the broker is not verified OK, the screen shows instead: "Pair
wireless debugging once on the Keyboard backlight screen and every permission here is granted
without a trip into system settings." and a "Set up pairing" link to the Smart backlight
screen. Each missing grant row also has an "Open settings" button for the manual route. The
three grant rows are re-read every time the screen resumes, since they change behind it.

Without the pairing and without the manual grants, the ring switch can be on but nothing
happens: the listener is never bound, so no notification is ever seen.

### 5.10 The Notification ring screen, top to bottom

Title "Notification ring". Intro, kept to two lines so the enable switch sits near the top of
the Titan's short screen: "When a notification arrives with the screen off, a ring lights up
around the camera hole in the app's colour. Touch the screen, press a key or unlock to end it."
(The rest of the screen stays black, which on this AMOLED panel means off; how long the ring
holds the screen on is said by the "Keep the screen on for" row.)

1. The enable switch (5.7.4).
2. Divider; the three grant rows (5.9); the grant button or pairing hint when any is missing.
3. Divider; "Keep the screen on for" slider (5.7.3).
4. "Ring brightness" chips (5.7.2).
5. "Show app icons" switch.
6. "Keep the keyboard dark" switch, with the unavailable note when the permission is missing.
7. Divider; "Default colour" row with a 28 dp colour dot, opens the picker.
8. "App colours" heading and description; one row per app (22 dp dot, label, "Remove"); "Add an
   app" button.
9. Divider; fit description and "Fit the ring to the lens" button.
10. "Try it" description and button (its description names the clock when the clock is on).

From 3.4 a "Clock" section sits between items 6 and 7: "Show the clock" and "Stay on while
charging", the second greyed out with its note while the clock is off (5.11.5).

### 5.11 The clock and staying on while charging (3.4)

The Titan has no always-on display (D30). Two options on the Notification ring screen, both off
by default, make the ring's black screen do the job of one: a clock drawn on it, and that clock
held up while the phone sits on a charger. Neither is an always-on display in Android's sense: the
panel is fully on (at a low brightness, almost every pixel black) for as long as the screen shows.

#### 5.11.1 The clock

With `notification_ring_clock` on, every ring screen (a notification ring, the charging clock of
5.11.2 and "Try it") also draws:

1. The time, in a thin (weight 100) sans-serif face, 72 dp, centred horizontally, its baseline at
   58 % of the window height. It follows the phone's 12/24-hour setting: 12-hour is the hour
   without a leading zero and no AM/PM ("7:05", midnight and noon "12:00"); 24-hour pads the hour
   ("07:05", "19:05"). Minutes always have two digits.
2. The date below it, 16 dp, regular weight, baseline 32 dp below the time's: weekday, month and
   day in the locale's own order and abbreviations (the skeleton "EEEMMMd", "Sat, Oct 10" in US
   English).
3. When `notification_ring_icons` is on, a row of small icons 18 dp below the date's baseline:
   18 dp square, 10 dp apart, centred under the date. One icon per distinct app among the
   notifications the ring is holding (5.2), oldest first, at most 6 (the most recent). Each icon is
   that app's most recent held notification's own small (status bar) icon, tinted grey; when that
   cannot be loaded, the app's launcher icon with its colour removed. With the clock on, the
   coloured icon row of 5.7.1 point 4 is not drawn; the clock's row replaces it. With "Show app
   icons" off there are no icons anywhere, so the clock never says which apps are waiting unless
   the user chose that (the Titan's own lock screen may be set to hide notifications entirely).

Colour: greyish white (RGB C8C8C8) at alpha 170 of 255 for the time, 140 for the date and the
icons. Nothing on the screen has colour except the ring's own glow.

Never drawn: any notification's title, text, sender, image or count. The clock reads the
notifications only for their keys, packages, colours and small icons.

Placement: the camera-hole ring sits in the top-left corner (5.7.1: the fitted ring is centred
at (78, 80) px with radius 46 on the 1080 x 1200 panel, glow included it stays within the top
15 % of the height and the left 15 % of the width). The clock block's top (the time's cap height)
sits near 50 % of the height, so the two never meet, whatever override the fit screen stores
within reason.

Updates: the time and date are redrawn on the system's minute tick
(`android.intent.action.TIME_TICK`) and on `android.intent.action.TIME_SET` /
`android.intent.action.TIMEZONE_CHANGED`. There is no per-second timer. They are listened for
only while a ring screen with the clock is up.

#### 5.11.2 Stay on while charging

With `notification_ring_stay_on_charging` on, and the ring (`notification_ring_enabled`) and the
clock both on, the clock comes up whenever the screen goes off while the phone is plugged in and
locked, and stays up until the phone is unplugged, unlocked, touched or a key is pressed.

The switch needs the clock: it is greyed out on the screen, with the note "Needs Show the
clock.", while the clock is off (and shows "Needs Ring on new notifications." while the ring is
off), and it does nothing with either off even if its stored value
is true (a restored backup). It also needs the ring, because it rides on the ring's notification
access: the screen and charger broadcasts below are heard by a receiver the notification listener
registers while it is connected, the one part of the app the system keeps running.

"Plugged in" means any charger, read from the battery's sticky status
(`android.intent.action.BATTERY_CHANGED`, plugged not 0), so a full battery still on the cable
counts. "Locked" means the keyguard is showing or locked (Android's keyguard-locked state; an
unknown state counts as not locked).

**Starting.** The listener's receiver hears `android.intent.action.SCREEN_OFF`,
`SCREEN_ON`, `USER_PRESENT`, `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED`. A start
is attempted at a screen-off with no ring screen up or on its way (with one up that is not
holding the screen, at the first check, 6 s on; with one holding it, never: see "The power key"),
and at a plug-in. The attempt, in order:

1. If any of the three switches is off, stop (nothing else is read).
2. If not plugged in, the screen is on, the clock was put away (below), or a ring screen is up or
   a launch is on its way, stop.
3. If not locked, check again later (below); the clock never holds the screen over a phone that
   is not locked, since that would stretch Android's "lock after screen timeout" grace period into
   a whole night.
4. Pocket check (5.5); if covered (face down on the nightstand), stop.
5. Seed the ring's session with the notifications still in the shade that the policy (5.3) lets
   ring, oldest first, each coloured by 5.4, so the clock's glow and icons say what is waiting.
6. Darken the keyboard (5.8), exactly as for a ring.
7. Launch by the ring's own route (5.6): the same channel and notification id 41, text "Showing
   the clock while charging", full-screen intent to the ring activity with no notification key and
   a charging-clock marker. The activity cancels the announcement as it does for a ring.

A launch counts as "on its way" for 15000 ms (the announcement's own timeout) or until the ring
screen appears, whichever is first.

**Checking again.** A start that finds the phone not yet locked is checked again after 6, 10, 15,
30, 60 and 180 s (cumulatively 6 s, 16 s, 31 s, 61 s, 121 s and 301 s after the screen went off),
which covers every lock delay Android offers up to 5 minutes; after the last check it gives up
until the next screen-off. Each check is one inexact wake-up alarm (Android may run it a few
seconds late; it rounds anything under 5 s up anyway); only one is pending at a time. Any
screen-on, unlock, plug-in or unplug cancels the pending check; every check re-reads every fact.

**Holding.** The ring screen is "held by the charger" when it is not a demo, the three switches
are on, the phone is plugged in and locked, and the clock was not put away; this is decided when
the screen is created and again at every plug-in or unplug it hears (a plug-in ignores
put-away). While held:

- the screen stays on with nothing waiting (a launch for the clock alone is not finished for
  having no notifications);
- the ring's timer (5.7.3) expiring releases nothing;
- the last waiting notification going away puts the glow and icons out and leaves the clock up;
- a new notification joins it (5.2 point 4 applies to the charging clock even with nothing in it:
  a notification arriving while the clock is up, or while its launch is on its way, is added to
  it rather than skipped because the screen is on), restarts the ring's timer and brightens the
  screen to the ring's brightness for the ring's minutes, breathing; then it goes back to the
  floor brightness with the glow held still.

Touch, a key, unlock and screen-off end it exactly as they end a ring (5.7 "Ending").

**Letting go.** Unplugged while showing, the screen stops holding at once (the window's
keep-screen-on is cleared unless the ring's own timer is still running for a fresh notification);
the phone's own screen timeout then turns it off, black to black, at the same floor brightness
(letting go never brightens it). On phones that wake the screen at unplug, the timeout starts
from the unplug. On the emulator with a 15 s timeout the screen was off 29 s after unplugging.

**The power key ("put away").** A screen-off while a ring screen holds keep-screen-on (the
charger holds it, or a ring's own timer is running) can only be the power key, since the window
holds off the timeout. The ring screen reports this itself before it finishes, so it never
depends on which of the two screen-off receivers runs first. It ends the screen (as for any ring)
and puts the clock away: no charging clock starts again until the user wakes the phone themselves (a screen-on
that no ring launch caused), unlocks it, or plugs it in. Pressing power on the nightstand clock
therefore turns it off for good that time; waking the phone to check something and letting the
screen go off again (by the power key or the timeout) brings the clock back. That is the
nightstand behaviour: the clock is there whenever the phone is put down on the charger, and one
press dismisses it until the phone is next used. A notification ring that arrives while put away
still rings, but is an ordinary ring: the charger does not hold it, and its timer lets it go.

A ring screen that had already let go (its minutes over, keep-screen-on released) and is then
ended by the phone's own timeout puts nothing away: the first check of the chain runs 6 s later,
once that screen is gone, and starts the clock if everything holds. The same applies to a second
screen-off while a launch is still on its way.

#### 5.11.3 Burn-in

The clock block (time, date and icons together) moves once a minute, on the minute tick, to the
next position of a fixed 16-step loop around its home position, in dp (x right, y down):
(0,0), (2,1), (4,2), (3,4), (1,3), (−1,4), (−3,3), (−4,1), (−2,0), (−4,−1), (−3,−3), (−1,−4),
(1,−3), (3,−4), (4,−2), (2,−1), then back to the start. The position is the wall-clock minute
(milliseconds since the epoch / 60000) modulo 16, so it does not restart when the screen does. No
position is more than 4 dp from home on either axis and no step moves more than 2 dp per axis,
so the move is not noticed. The ring does not move: it has to stay on the lens. On the charging
clock with old notifications waiting, the glow is held still at a dim level (below) for as long
as the clock is up; at the floor brightness this is the one fixed bright-ish shape on the panel.

#### 5.11.4 Brightness, animation and battery

| State | Window brightness | Keep-screen-on | Glow |
|---|---|---|---|
| Ordinary ring, timer running | the ring's (5.7.2) | yes | breathing (5.7, every frame) |
| Ordinary ring, timer expired | the ring's | released | breathing |
| Charging clock, nothing waiting | 0.01 (the floor) | yes, until unplugged | none |
| Charging clock, notification newer than the ring's minutes | the ring's | yes | breathing |
| Charging clock, older notifications only | 0.01 | yes, until unplugged | held still at breath 0.3 (alpha factor 0.35 + 0.65 x 0.3 = 0.545) |
| Charging clock after unplugging | 0.01 | released (unless the ring's timer still runs) | as before |

The floor, 0.01, is as low as a window may ask: Android clamps any window brightness to the
panel's own minimum, so this is the dimmest level the panel offers; 0 is avoided because some
drivers read it as "backlight off".

CPU: with the glow still or absent, the screen is redrawn only on the minute tick (one draw a
minute) and nothing else runs; the app holds no wake lock of its own (keep-screen-on is the
window's, held by the system for the window). The only timers while the screen is off are the
start checks of 5.11.2, at most six inexact alarms after a screen-off that found the phone not yet
locked. The listener's receiver costs one short wake per screen-on, screen-off, unlock, plug and
unplug, and returns at once when the switch is off.

Battery: the charging clock only runs on the charger, so it costs no battery charge; it costs
panel lifetime (an AMOLED panel lit all night at its minimum brightness, almost all of it black).
The clock alone with an ordinary ring (not charging) adds one text draw per frame to the ring's
existing cost and nothing else.

#### 5.11.5 Settings and the screen

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `notification_ring_clock` | boolean | false | Draw the clock (5.11.1) on every ring screen | Notification ring | "Show the clock", description "A dim clock and date on the ring's black screen, in the style of an always-on display. The Titan has no true always-on display, so the clock is only there while the ring is. With Show app icons on, the waiting apps appear under the date as small grey icons; never what the notifications say." |
| `notification_ring_stay_on_charging` | boolean | false | The charging clock (5.11.2); does nothing unless `notification_ring_clock` and `notification_ring_enabled` are on | Notification ring | "Stay on while charging", description "While the phone is plugged in and locked, the clock comes up whenever the screen goes off and keeps the screen on, at its lowest brightness, until you unplug, unlock, touch the screen or press a key. Press power while it shows and it stays off until you next wake the phone."; greyed out with the note "Needs Show the clock." while the clock is off |

Both rows sit in a "Clock" section between "Behaviour" and "Colour" (5.10). With the clock on,
"Try it" reads "Shows the ring and the clock for a few seconds with PhysiBoard's own icon. Lock
the phone first to see it the way it will really look." and the demo shows the clock (5.7.6;
the demo is never held by the charger). Search finds both rows under "clock", "always on",
"AOD", "nightstand" and "charging".

#### 5.11.6 Lock-screen safety

The clock screen is the ring's activity: shown over the lock screen, never dismissing it. Any
touch or key ends it and leaves the lock screen (or, if the phone was not locked, whatever was
there before, which the charging clock never covers: 5.11.2 step 3, and the screen re-checks the
lock and the charger when it is created; a charging-clock launch that finds the phone unlocked or
unplugged by then finishes at once, even with notifications waiting). It draws no notification content (5.11.1), takes no input but the
ending touch and key, and offers no way past the lock screen. The receivers it registers
are for protected system broadcasts only.

#### 5.11.7 What the emulator showed (2026-10-10, `titan2elite` AVD, Android 14, swipe lock screen)

- "Try it" with the clock on: the ring and the clock, the demo's own icon greyed under the date;
  12-hour time without AM/PM.
- On the charger (`dumpsys battery set ac 1`), power key from the lock screen: the clock came up
  through the full-screen notification (launched by SystemUI on the pending intent) about 0.3 s
  after the screen went off, at window brightness 0.01 with the window's keep-screen-on wake lock,
  seeded with the one notification already in the shade (glow in its colour, its small icon grey).
- A notification posted from the shell while the clock was up: brightness 0.2 (the ring's
  Normal), breathing, a second grey icon, no text anywhere; 130 s later (ring minutes 2) back to
  0.01 and still held.
- Power key on the clock: dark, and still dark 8 s later. Woken to the lock screen, then power
  again: the clock came back.
- Unlocked on the home screen, on the charger, left to time out (15 s): the clock came up about
  4 s after the screen went off.
- Unplugged (`dumpsys battery unplug`) while showing: keep-screen-on released at once,
  brightness stayed 0.01, the screen was off 29 s later on the 15 s timeout.

Not shown on the emulator: a secure lock screen with a lock delay (the retry chain), the
pocket check, the Titan's own lock screen and its full-screen-intent handling, real charger
broadcasts, and how the panel looks at 0.01 in a dark room. Also unproven: a check's alarm
wakes the CPU, but the check itself runs on the ring's worker after the alarm's own hold on the
CPU has ended, so on a phone that suspends at once it may run late, until the CPU next wakes.

#### 5.11.8 Test cases

| # | Input sequence | Expected outcome |
|---|---|---|
| T45 | Time 07:05 and 19:05, 12-hour; 00:00 and 12:30, 12-hour; 07:05 and 00:00, 24-hour | "7:05", "7:05"; "12:00", "12:30"; "07:05", "00:00" |
| T46 | The burn-in loop | 16 distinct positions, each within 4 dp of home per axis, each step at most 2 dp per axis, both signs on both axes |
| T47 | Burn-in offset for minutes 0, 1, 5 and 21, and −1 | (0,0); (2,1); minute 5 equals minute 21; minute −1 is the last position |
| T48 | Held notifications A1, B1, A2 (packages A, B); then B1 removed; then 8 distinct packages | icons [A, B]; [A]; the last 6 |
| T49 | Ring, clock, charging switches: all on; any one off | enabled; not enabled |
| T50 | All on, plugged, dark, locked, not put away, nothing up | start |
| T51 | As T50 but each of: switch off, unplugged, screen on, put away, ring up | no start; not locked alone: check again later; not locked and unplugged: no start |
| T52 | The check-again chain | after 6, 16, 31, 61, 121 and 301 s, then none |
| T53 | Power on the clock; then a screen-on the user caused; then screen off | put away (no start); cleared; start |
| T54 | Put away, then a screen-on within 15 s of a launch or with a ring up; unlock; plug in | still put away; cleared; cleared |
| T55 | Held by the charger: on, plugged, locked, not put away, not demo; then each one false | held; not held |
| T56 | Held: timer expiry; last notification removed; touch, key, screen off, unlock | nothing released, not finished; nothing released, not finished; finished each |
| T57 | Keep-screen-on: held and no timer; not held and timer running; neither | on; on; off |
| T58 | Brightness and breathing per the 5.11.4 table | as the table |
| T59 | Switches on, plugged, locked, screen goes off | charging clock launched, keyboard darkened, nothing held |
| T60 | Shade holds B (posted at 20), A (10), an ongoing one and the app's own | clock seeded with [A, B] |
| T61 | Each of: charging switch off, clock off, unplugged, covered, screen on; screen goes off | nothing launched, keyboard untouched |
| T62 | Not locked at screen-off; first check still not locked; second check locked | checks scheduled at 6000 then 10000 ms; launched at the second check |
| T63 | Never locked | exactly the six checks, then nothing |
| T64 | Not locked at screen-off, then the user wakes the phone | the pending check is cancelled |
| T65 | Clock up (holding); power; plain screen-off; user wakes, screen off. Also: the clock reports its own screen-off and is gone before the listener hears it | one launch only until the wake; then a second launch. Put away either way |
| T66 | Clock put away by power; plug-in with the screen dark and locked | launched again |
| T67 | "Try it" with the clock on, 24-hour set | clock drawn as HH:MM with a date, the minute tick registered, keep-screen-on, brightness 0.2; finished after 8 s; the tick unregistered |
| T68 | "Try it" with the clock off | no clock, no minute tick registered |
| T69 | Charging clock launched, plugged and locked | not finished, keep-screen-on, brightness 0.01, no ring; still so 8 hours on |
| T70 | Charging clock launched, plugged, not locked; again with a notification waiting in its seed | finished at once, both times |
| T71 | Charging clock; a notification; 11 minutes; the notification removed; unplugged | ring at 0.2; held at 0.01 with the glow; clock stays, glow gone; keep-screen-on released, brightness still 0.01, not finished |
| T72 | A notification launches a ring that never appears; 15 s later another notification | a new launch with only the new notification (not a join) |
| T73 | On the charger, a notification ring whose minutes are over (not holding) is ended by the timeout | not put away; a check at 6 s; once the ring screen is gone, the clock is launched |
| T74 | Not locked at screen-off (a check pending); the user wakes the phone; the cancelled check's alarm fires anyway | nothing launched, no further check scheduled |

## 6. The device setup card

Shown as the header of T2E Tools and inside the Smart backlight screen when that switch is on
and the latch is false. It is the one-time pairing every privileged feature depends on.

While visible it re-reads every 1500 ms: whether a pairing key is stored, whether Developer
options are on (`Settings.Global` `development_settings_enabled` = 1), whether wireless
debugging is on (`adb_wifi_enabled` = 1), whether Do Not Disturb is on (`Settings.Global`
`zen_mode` != 0). It also reads the shared verified broker status (re-verified when the key
presence changes).

Arming: whenever the card is visible and no key is stored, it starts the pairing watcher
foreground service once, and stops it once a key appears. The watcher is deliberately NOT
stopped when the card leaves the screen, because the button's whole job is to send the user
away to Android's Wireless debugging page; the watcher stops itself when pairing succeeds.
On first show, if `android.permission.POST_NOTIFICATIONS` is not granted, the system permission
prompt is requested (the pairing code arrives as a notification); a grant while unpaired
re-arms the watcher. The prompt is never a precondition for arming.

Header line (monospace) and icon:

| Condition | Icon | Title |
|---|---|---|
| verified OK | check, primary colour | "Paired" |
| key stored, verifying | warning, error colour | "Checking…" |
| key stored, verified not OK | warning | "Cannot reach the system" |
| no key | warning | "Setup needed" |

Paired (key stored) body, by verified status: OK "The tools below can reach the system.
Survives reboots."; REJECTED "Paired, but your phone is refusing the connection. The pairing
code was probably mistyped, so the app is holding a key your phone never accepted. Re-pair to
fix it."; WIRELESS_DEBUGGING_OFF and NO_SERVICE and NOT_PAIRED reuse the backlight blocker
messages from 3.4; null "Testing whether the tools can reach the system." Then a row with, for
WIRELESS_DEBUGGING_OFF or NO_SERVICE, an "Open Wireless debugging" button (intent action
`android.settings.ADB_WIRELESS_SETTINGS` if it resolves, else
`android.settings.APPLICATION_DEVELOPMENT_SETTINGS`), and always a text button: "Re-pair" when
REJECTED, otherwise "Forget pairing" (both forget the key and invalidate the verdict).

Unpaired body: three numbered steps, either (Developer options off) "Open Settings → About
phone", "Tap Build number seven times", "Come back here", or (on) "Turn on Wireless debugging",
"Tap Pair device with pairing code", "Type the code into the PhysiBoard notification". Then a
warning if applicable, notifications first: "Allow PhysiBoard notifications first. The pairing
code arrives as one." or "Do Not Disturb is on and may hide the pairing code. Turn it off until
you are paired." Then one button: "Open About phone" (Developer options off, opens
`android.settings.DEVICE_INFO_SETTINGS`) or "Open Wireless debugging" (opens the wireless
debugging page as above).

## 7. Titan 2 / Titan 2 Elite hardware facts

Evidence codes: DEVICE = docs/titan2elite/DEVICE.md; BACKLIGHT = docs/titan2elite/BACKLIGHT.md;
CHANGES x.y.z = the changelog entry for that version; commit = the git commit message; ARCHIVE
= the device archive under docs/device-archives; NOTES = the maintainer's session notes kept
outside the repository (cited where no in-repo evidence exists; a rewrite should re-verify on
the phone).

| # | Fact | Evidence |
|---|---|---|
| D1 | Model string "Titan 2", build "Titan 2 Elite_V02.00.02", Android 16. | DEVICE |
| D2 | Display 1080 x 1200 physical (override 1076 x 1200), density 300 dpi, about 574 x 640 dp; wider than any normal phone and shorter than all of them. | DEVICE |
| D3 | The panel is a 4.03 inch AMOLED (manufacturer: 401 ppi) with strongly rounded lower corners; black pixels are off. | ARCHIVE titan2elite README; CHANGES 1.2.0 |
| D4 | Emulator profile: AVD `titan2elite`, 1080 x 1200 at 300 dpi, hardware keyboard on, main keys on. | DEVICE |
| D5 | Kernel keyboard device `TitanKey` at `/dev/input/event5`, i2c 6-0058, vendor/product 0x2533, with an `aw9523_power_ctrl` sysfs attribute. | DEVICE |
| D6 | The key backlight is driven by an AW9523 16-channel I2C LED driver; it supports 256-step dimming but no userspace interface was found, so brightness tiers were never built. | DEVICE; roadmap workstream 3 |
| D7 | Companion input devices: `touchPad` event4 (capacitive touch layer on the keys), `fts_ts` event6 (touchscreen plus gesture keys), `ff_key` event7 (scancode 249). | DEVICE |
| D8 | Key layout files: `/system/usr/keylayout/TitanKey.kl`, `/system/usr/keychars/TitanKey.kcm`. Scancodes: 16-25 Q..P; 30-38 A..L; 14 DEL; 56 ALT_LEFT; 44-50 Z..M; 253 Sym (custom keycode AGUI_SYM); 28 Enter; 42/54 Shift L/R; 158 Back; 102 Home; 57 Space; 580 App switch; 251 Fn (custom keycode FUNC3). | DEVICE; TitanKey.kl |
| D9 | There is no Ctrl key in hardware; Fn to Ctrl is synthesized by the vendor layer per user config. The Fn key never delivers key-up; a hold arrives as auto-repeating Ctrl-left events with scancode 251 every ~50 ms starting ~400 ms in; a quick Fn tap delivers nothing; Fn+X delivers only X with the Ctrl meta bit. | DEVICE |
| D10 | The stock keyboard backlight goes dark 30 s after the last keystroke; the stock timeout UI is capped at 30 s; there is no keep-on option. | BACKLIGHT |
| D11 | The vendor lights the keyboard LEDs on every screen-on and on every keypress. | BACKLIGHT; commit e8d997c |
| D12 | The backlight controller lives in core `/system/framework/services.jar` (not in the agui jars) and is registered on the ambient light sensor `stk3a5x_als`. Its fields show it has brightness, auto-brightness, timeout, current-light and screen-on-sync state. | DEVICE |
| D13 | The hardware node `/sys/devices/platform/keypad_led/keyled_brightness` is SELinux system-only; the controller writes it and the app never needs to. | DEVICE; BACKLIGHT |
| D14 | Master switch `Settings.Global` `agui_keyboard_background_light` (0/1) is writable with WRITE_SECURE_SETTINGS and takes effect live. Other globals `keyboard_led_brightness`, `keyboard_led_auto_switch`, `keyboard_brightness_timeout`, `agui_keyboard_led_timer` accept writes but were all unset on the user device; as plain globals the timeouts are ignored because the real config lives in root-only `/data/system/agui_settings_data.xml`. | DEVICE; BACKLIGHT |
| D15 | The vendor timeout `keyboard_brightness_timeout` written through binder service `agui_functional_service` transaction 2 accepts `-1` = never turn off and `30000` = stock; verified on device 2026-08-21; the value survives reboots. | source comment in the backlight writer; CHANGES 1.0.1 |
| D16 | Transaction 1 of `agui_functional_service` is GET(key) and returns a String16 parcel; confirmed on the Titan 2 Elite 2026-08-31. The literal always-on response was `Result: Parcel(00000000 00000002 0031002d 00000000 '........-.1.....')`. | source comment; parcel parse test |
| D17 | Transaction 7 of `agui_functional_service` is `keyboardLightTest(String)`: "1" holds a lit LED past the timeout (does not light a dark one), "0" leaves test mode; `am broadcast -a agui.action.CLOSE_KEYBOARD_LIGHT` then powers it off. Found by probing codes 1-30. | BACKLIGHT |
| D18 | Writing the vendor settings needs shell uid; a normal app cannot. Shizuku was refused by Play on this device (stale compat flag), which is why the app embeds its own wireless-ADB broker. | BACKLIGHT; CHANGES 0.86 later revisions |
| D19 | The vendor key-config rows (`{key}_programmable_key_enable`, `{key}_programmable_key_function`, `{key}_shortcut_key_enable`, `{key}_{short,long,double}_press_activity/_package`) are plain `Settings.System` rows writable with WRITE_SETTINGS; observed fn=1 means Ctrl, sym=2. | DEVICE |
| D20 | Vendor packages: `com.agui.keyboard` (FUNC3/AGUI_SYM translation layer), `com.agui.shortcutsettings` (Shortcut keys UI), `com.agui.spacebarkey` (drives the capacitive touch layer on the keys, not a sensor). Stock IME `com.iqqijni.bbkeyboard` remains enabled. `show_ime_with_hard_keyboard` = 0. | DEVICE; NOTES |
| D21 | Wireless debugging must be re-enabled after every reboot; its port rotates per reboot and per toggle; it is discoverable via mDNS. | DEVICE; CHANGES 2.0.3 |
| D22 | Android turns wireless debugging off across reboots, so a paired device is routinely unable to connect; "paired" (a key is stored) says nothing about whether anything works. | commit 8ddc87f; commit bd28de8 |
| D23 | The system reports the camera hole as a display cutout whose bounding box is a 123 px square at the top-left corner of the 1080 px wide panel. | source geometry constant; geometry test |
| D24 | The actual lens sits lower and further right than the centre of that box and is smaller than it: fitted ring centre (78, 80) px, radius 46 px, stroke 6 px at 300 dpi, measured 2026-08-26. Panels differ by a few pixels between units (a user's ring sat off the lens). | source comment; commit 2656c2f; CHANGES 1.2.3 |
| D25 | A proximity sensor exists and is used for the pocket check. | ring listener; CHANGES 1.2.0 |
| D26 | A full-screen notification is the one route Android 15+ leaves an app for turning on a dark, locked screen; a direct background activity start is refused. | commit 30f0944 |
| D27 | Firmware on the Titan 2 (non-Elite) archive: EEA V01.00.14, build BP2A.250605.031.A3, MediaTek MT6878, board G71BoardV1, Android 16 SDK 36, physical keyboard name `titan2`; same letter scancodes and Sym 253. | ARCHIVE titan2 snapshot 2026-05-19 |
| D28 | Every Titan 2 Elite is the same phone, so the measured ring fit is the default and not a setting; the fit screen exists only for the few-pixel unit variance. | commit a43e43a; commit 2656c2f |
| D29 | The Quick Settings tile's permission was granted on the maintainer's device over a computer ADB session on 2026-08-19. | roadmap "Quick Settings tile SHIPPED v1" |
| D30 | Always-on display is compiled out: `config_dozeAlwaysOnDisplayAvailable` is false in framework-res and again in the product RRO, so `doze_always_on` = 1 does nothing. Doze itself works: with the screen off, posting a notification wakes the phone within 4 s. The user's phone has `lock_screen_show_notifications` = 0 and `lock_screen_allow_private_notifications` = 0, so a woken lock screen shows no notification content. Verified 2026-08-26. | CHANGES 1.2.0; commit 30f0944; NOTES |
| D31 | Double-tap to wake is compiled out: `double_tap_to_wake` = 1 but `config_supportDoubleTapWake` is false, no touch-gesture kernel nodes exist, and no key (TitanKey.kl, ff_key, gpio-keys, mtk-pmic-keys) carries a WAKE flag. Only the power button wakes the phone; the fingerprint reader is in the power button. Verified 2026-08-25. | docs/plans/2.0-overhaul.md; NOTES |
| D32 | Per-app display density is impossible on Android: `wm density` is global only, the game downscale command refuses non-game apps, and a virtual display with its own density can host only the calling app. The only fake (switch global density on app focus) is too slow through a broker whose every call takes seconds. Verified 2026-08-24. | NOTES |
| D33 | Notification-class vibration is muted when the system's notification vibration is off; dictation cues therefore use plain vibration. | CHANGES 1.0.5 |
| D34 | Screen density on this phone pays off unusually: the screen is short, so every step down buys another line, and no space goes back to a soft keyboard. The density change is bounded to 0.6 x to 1.4 x of the physical density and auto-reverts after 15 s unless confirmed. | density source comment; CHANGES 1.1.0 (details in the toolbox spec) |

## 8. Reset device settings to stock (the parts owned here)

Reset runs five reverts independently and reports each as success, failed, or needs
permission. Three belong to this document:

| Step | What it does | Outcome rules |
|---|---|---|
| Backlight always-on | Queues the stock `30000` write through the broker (asynchronous, gate applies), then sets `smart_backlight_enabled` and `smart_backlight_applied` to false so nothing re-arms | Success if the feature was never on and never applied; else success when a key is stored (the write itself is not awaited), needs permission when not paired |
| Quick Settings master switch | Target = the captured original when captured and not the unset sentinel, else 0. Writes `agui_keyboard_background_light` in-process when WRITE_SECURE_SETTINGS is held, else `settings put global agui_keyboard_background_light <target>` through the broker; clears the capture on success | Success / failed by the write; needs permission when neither route is available |
| Notification ring | Sets `notification_ring_enabled` false. If neither notification access nor full-screen intents are granted, done. Else runs `cmd notification disallow_listener <component>; appops set brobata.physiboard USE_FULL_SCREEN_INTENT default` through the broker | Success when the listener is no longer granted afterwards; needs permission when not paired |

Reset does not revoke WRITE_SECURE_SETTINGS, POST_NOTIFICATION, or the ring's keyboard record.

## 9. Edge cases, quirks and known bugs

| Situation | Behavior | Why |
|---|---|---|
| Quick Settings tile added on a 2.x build | Expected to fail to instantiate (component name does not match the compiled class) | Namespace rename left the class in `brobata.physiboard.physi` while the manifest names `brobata.physiboard`; lint baseline and disabled release lint hide it |
| Reset to stock with the tile never tapped | The master switch is written 0 (keyboard light off), not left unset | "Never captured" maps to 0; the vendor default for an unset key is on |
| Smart backlight switch on, phone never paired | Switch stays on, keyboard keeps stock 30 s, setup card shown, reason `not_paired` recorded | The write needs shell uid |
| Paired, then rebooted | Wireless debugging is off; the always-on value still holds (it is persistent) but any new write is skipped with `wireless_debugging_off`; the screen names the switch to flip | D21, D22 |
| System update or another app resets the vendor timeout | Latch still true; the screen reads the phone when the broker is reachable and shows "no longer holding" with "Apply again" | The latch is one-way |
| Smart backlight write attempted from the IME on every start | Harmless repeat write of the same value; serialized on one worker so overlapping discoveries cannot fail each other | Idempotent by design (CHANGES 1.0.2) |
| Enable ring and dark keyboard, then the app loses WRITE_SECURE_SETTINGS | The dark-keyboard switch stays on but is inert; the row says it is unavailable | Never offer a switch that quietly does nothing |
| Ring darkens the keyboard, system declines the full-screen launch | Keyboard restored by the 20 s orphan timer; announcement expires at 15 s | The ring never took ownership |
| Ring darkens the keyboard, process dies | Record was committed before the write; next process start restores off-thread | Restore must not depend on memory |
| Ring darkens the keyboard, user taps the tile | Tile captures the ring's recorded prior as the original, clears the ring's record, turns the light on; ring's later restore is a no-op | Otherwise Reset to stock would hand the user a dead keyboard |
| Keyboard already off (user's choice) when a ring launches | Nothing recorded, nothing restored; the user's 0 stands | Only what the ring turned off may be turned back on |
| Restore called twice | Second call finds no record and leaves whatever the user set since | No claim left |
| Notification arrives with the screen on and no ring showing | Ignored | The ring answers dark screens only |
| Notification arrives while a ring is showing | Ring recolours to the new app, icons updated (max 3, distinct packages, most recent last), timer restarted | One ring for all waiting apps |
| The waiting notification is dismissed on another device | Ring removes it; empty means the ring ends; otherwise last remaining colour | Ring reflects what is still waiting |
| Phone face down or in a pocket | No ring; skipped after at most 300 ms | Proximity below 5 cm |
| No proximity reading within 300 ms | Rings anyway | A missed ring is cheaper than one that never rings |
| App declares a near-black brand colour | Default colour used (luminance below 0.12) | Invisible on an off panel |
| User picks a dark colour on the wheel | Warned, still allowed; brightness cannot go below 0.15 | Deliberate choice beats a silent floor |
| Timer expires on a real ring | Keep-screen-on released only; the screen stays black until the system timeout | An app cannot switch the screen off; black to black avoids a flash |
| Timer expires on a demo ring | Ring finishes | Nothing to wait for |
| Key press while ring is up | Ring finishes and the key is still delivered onward | Any key means the user is here; the first key is likely lost to whatever is under the ring |
| No cutout insets delivered | Titan fallback geometry (123 px square at origin) | Some launches deliver no insets callback |
| Stored override with stroke 0 | Stroke replaced with 3 dp | A zero stroke draws nothing |
| Grant rows shown while broker not verified OK | Broker button hidden; pairing hint and "Set up pairing" shown | Offering a grant that will silently fail was the 2.0.3 bug |
| DND on during pairing | Card warns; watcher still arms | The code arrives as a notification DND can hide |
| POST_NOTIFICATIONS denied | Card warns and requests it; watcher still arms | Never make the prompt a precondition |
| Clock on, "Show app icons" off | Clock, date, no icons anywhere | Which apps are waiting is shown only by choice (5.11.1) |
| "Stay on while charging" stored true, clock off (a restored backup) | Nothing happens; the row is greyed out | The charging clock needs the clock |
| Screen times out on the charger while the phone is still in its lock delay | Checked again at 6, 16, 31, 61, 121, 301 s; comes up once locked; gives up after 5 minutes | Never hold the screen over an unlocked phone |
| Power pressed on the charging clock | Dark until the user next wakes, unlocks or plugs in | Never fight the user (5.11.2) |
| Phone face down on the charger | No charging clock | Same pocket check as the ring |
| Unplugged while the charging clock shows | Keep-screen-on released at once, brightness stays at the floor; the phone's timeout ends it | Black to black |
| A ring launch the system declines | After 15 s the next notification starts a new ring | Before 3.4 the session stayed "showing" and swallowed every later ring until the process restarted |
| First-run defaults stamp | `smart_backlight_enabled` true, `notification_ring_enabled` true, `notification_ring_minutes` 2, written once per install when not already chosen | Maintainer's dialed-in config |

## 10. Test cases

Each row is a JVM-encodable check. "switch" means `agui_keyboard_background_light`.

| # | Input sequence | Expected outcome |
|---|---|---|
| T1 | Parse `Result: Parcel(\t00000000 00000002 0031002d 00000000 '........-.1.....')` | "-1" |
| T2 | Parse `Result: Parcel(00000000 00000005 00300033 00300030 00000030 '..')` | "30000" |
| T3 | Parse `Result: Parcel(00000000 00000001 00000030 '..')` | "0" |
| T4 | Parse "Result: oops", "", null | unreadable each time |
| T5 | Parse a parcel with count 0; with count 4 and one payload word; with count 0xffff | unreadable each time |
| T6 | Ring enabled, dark-keyboard on, permission held, switch 1; suppress | switch 0, `ring_backlight_prev_captured` true, `ring_backlight_prev` 1 |
| T7 | After T6, restore | switch 1, record cleared |
| T8 | After T6, suppress again, then restore | prior stays 1; switch ends at 1 |
| T9 | Switch 0; suppress; restore | no record; switch stays 0 |
| T10 | Dark-keyboard off; suppress | switch stays 1, no record |
| T11 | Ring disabled; suppress | switch stays 1 |
| T12 | Suppress; restore; set switch 0; restore | switch stays 0 |
| T13 | Permission denied; suppress | switch stays 1, no record |
| T14 | Suppress; permission removed; restore | record kept, switch stays 0 |
| T15 | Policy: own package | skip: own app |
| T16 | Policy: ongoing flag; foreground-service flag | skip: ongoing, both |
| T17 | Policy: group-summary flag | skip: group summary |
| T18 | Policy: not clearable | skip: not clearable |
| T19 | Policy: priority -2 | skip: silent |
| T20 | Policy: plain clearable default-priority message | rings |
| T21 | Colour 0xFF1E88E5 | itself |
| T22 | Colour 0; colour 0xFF101010 | default colour, both |
| T23 | Ring around box (0,0,123,123), gap 6, stroke 4 | centre (61.5, 61.5), radius 69.5, stroke 4 |
| T24 | Titan fallback at 300 dpi, gap 6, stroke 4 | equals T23 |
| T25 | Ring around box (100,0,300,80), gap 0, stroke 0 | centre (200, 40), radius 100 |
| T26 | Is Titan cutout: (0,0,123,123) at 300 dpi; (100,0,300,80) | true; false |
| T27 | Fitted ring at 300 dpi | centre (78, 80), radius 46, stroke 6 |
| T28 | Fitted ring at 600 dpi | centre (156, 160), radius 92, stroke 12 |
| T29 | Brightness name "BRIGHT"; "DIM"; "bogus"; null | 0.6; 0.05; 0.2; 0.2 |
| T30 | Duration prefs: default; set 2 | 10; 2 |
| T31 | Per-app map: set A=red, set B=blue, remove A | {B: blue}; JSON object keyed by package |
| T32 | Tile tap with ring record {prior 1} outstanding and switch 0, nothing captured | `qs_backlight_prev` = 1, captured true, ring record cleared, switch 1 |
| T33 | Tile tap twice with switch 1, nothing captured | first: captured 1, switch 0; second: capture unchanged, switch 1 |
| T34 | Reset: never captured, permission held | switch written 0, capture cleared |
| T35 | Reset: captured 1, permission held | switch written 1 |
| T36 | Reset backlight: enabled false and applied false | success, no broker call needed |
| T37 | Gate: no key stored | reason `not_paired`, no shell call |
| T38 | Gate: key stored, `adb_wifi_enabled` 0 | reason `wireless_debugging_off`, no shell call |
| T39 | Successful always-on write | `smart_backlight_applied` true, read-back stored, step ok |
| T40 | Successful stock write | `smart_backlight_applied` false |
| T41 | Luminance of 0xFF34C759 | at or above 0.12 (not too dark) |
| T42 | Ring receives source B while showing A; then A removed | colour B, icons [A, B] then [B]; ring still up |
| T43 | Ring showing A; A removed | ring finishes |
| T44 | Four distinct packages added | icons show the last 3 |

## 11. Keep / Drop for 3.0

| Item | Verdict | Reasoning |
|---|---|---|
| Always-on backlight via the persistent vendor timeout (transaction 2, `-1`/`30000`) | Keep | The whole feature is one write; nothing else gives a Titan a keyboard that stays lit |
| Read-back via transaction 1 and the "no longer holding" line | Keep | The only way to catch the value being lost; cheap once the broker is connected |
| The one-way `smart_backlight_applied` latch as the readiness signal | Keep, but subordinate to the read-back | It is what survives wireless debugging turning off |
| Light-sensor smart backlight (section 4) | Drop | Superseded; needs the broker every screen-on; the vendor already has an ALS mode nobody has characterised |
| Brightness tiers | Drop | No userspace interface (D6) |
| Quick Settings "Keyboard light" tile | Keep, fixed | Useful; currently broken by the component name; must be verified on the phone |
| Tile's toast with the `pm grant` command | Undecided | With the ring's broker grant in place the manual route is rarely needed, but it is the only path for a user who never pairs |
| Capture of the tile's original value for Reset | Keep, with the never-captured case mapped to "unset" not 0 | Writing 0 on reset turns the light off |
| Notification ring, all of section 5 | Keep | Titan-only feature with no equivalent on this ROM (D30) |
| Ring keyboard-dark with commit-before-write and orphan timer | Keep | Every path proven by tests; the record shape is the contract |
| Full-screen-notification launch route | Keep | The only route (D26) |
| Fit-the-ring screen | Keep | Unit variance is real (D24) |
| Per-app colours and colour wheel | Keep | Small, already shared with cursor colours |
| "Show app icons" | Undecided | Off by default; icons at 42 % height are far from the ring and cost little; keep if cheap |
| Pocket check | Keep | A screen lit in a pocket is worse than no ring |
| The clock and "Stay on while charging" (5.11, 3.4) | Keep, off by default | The nearest thing to an always-on display this ROM allows; unproven on the phone |
| Device setup card | Keep | Every privileged feature depends on it; belongs to the broker spec's pairing flow |
| Smart backlight screen hosting the setup card when unconfigured | Drop | Keep one home for pairing (the hub header); route "Set up pairing" links there instead |
| Reset to stock steps here | Keep | Anything that outlives an uninstall must be undoable |
| Unused vendor globals (`keyboard_led_brightness`, `keyboard_led_auto_switch`, `agui_keyboard_led_timer`, close broadcast) | Drop from the app, keep in the facts | Documented for future spikes only |
| Titan 2 (non-Elite) archive | Keep as facts | 3.0 targets the Elite; the archive shows the non-Elite shares the keymap |

## 12. Provenance

- /home/disdiqqq/projects/pastiera/docs/spec/README.md
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/KeyboardBacklightManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/KeyboardBacklightTileService.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SmartBacklightScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/NotificationRingScreen.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/DeviceSetupCard.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/ColorWheel.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/NotificationRingActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/NotificationRingLauncher.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/NotificationRingListener.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/NotificationRingPolicy.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/NotificationRingSetup.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingAdjustActivity.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingBacklight.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingBrightness.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingGeometry.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingPalette.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingSource.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ring/RingView.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PrivilegedSetup.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PrivilegedDiagnostics.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/EmbeddedAdbShell.kt (status enum, pairing and wireless-debugging checks, discovery timeout only)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/inputmethod/PhysicalKeyboardInputMethodService.kt (backlight start and stop hooks only)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/PhysiBoardApplication.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsManager.kt (backlight, ring and tile capture sections; first-run defaults)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SettingsScreen.kt (T2E Tools hub rows and navigation)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/SystemChangeManager.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/DiagnosticsScreen.kt (privileged section)
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/ColorPickerDialog.kt
- /home/disdiqqq/projects/pastiera/app/src/main/java/brobata/physiboard/toolbox/DisplayDensity.kt (header only)
- /home/disdiqqq/projects/pastiera/app/src/main/AndroidManifest.xml
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/strings.xml
- /home/disdiqqq/projects/pastiera/app/src/main/res/values/themes.xml
- /home/disdiqqq/projects/pastiera/app/build.gradle.kts (lint block)
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/BacklightParcelParseTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/ring/RingGeometryTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/ring/RingBacklightTest.kt
- /home/disdiqqq/projects/pastiera/app/src/test/java/brobata/physiboard/ring/NotificationRingPolicyTest.kt
- /home/disdiqqq/projects/pastiera/docs/titan2elite/DEVICE.md
- /home/disdiqqq/projects/pastiera/docs/titan2elite/BACKLIGHT.md
- /home/disdiqqq/projects/pastiera/docs/titan2elite/TitanKey.kl
- /home/disdiqqq/projects/pastiera/docs/device-archives/README.md
- /home/disdiqqq/projects/pastiera/docs/device-archives/unihertz-titan2/README.md
- /home/disdiqqq/projects/pastiera/docs/device-archives/unihertz-titan2/2026-05-19-eea-v01.00.14-qwertz-on-physical-qwerty.md
- /home/disdiqqq/projects/pastiera/docs/device-archives/unihertz-titan2elite/README.md
- /home/disdiqqq/projects/pastiera/docs/plans/physiboard-roadmap.md (workstream 3, decision log)
- /home/disdiqqq/projects/pastiera/docs/plans/2.0-overhaul.md (double-tap note)
- /home/disdiqqq/projects/pastiera/PHYSIBOARD_CHANGES.md (entries 0.86-physi, 1.0.1, 1.0.2, 1.0.4, 1.0.5, 1.1.0, 1.2.0, 1.2.1, 1.2.3, 2.0.0, 2.0.3, 2.0.6)
- git commit messages 30f0944, e8d997c, 8ddc87f, 2656c2f, a43e43a, d057f81, 2b4beeb, bd28de8, 9fd44fa
- /home/disdiqqq/.claude/projects/-home-disdiqqq-projects-pastiera/memory/aod-not-possible.md, double-tap-to-wake-not-possible.md, per-app-dpi-not-possible.md (outside the repository; cited as NOTES)
