# Embedded ADB broker, privileged setup, and the toolbox

This document covers everything PhysiBoard does with shell privilege on the Titan 2 Elite: how
it obtains that privilege (a one-time Wireless debugging pairing with an ADB client embedded in
the app), how it verifies and reports the state of that pairing, the pass that applies every
privileged change after pairing, the diagnostics that record what happened, every system
setting the app writes and how each is undone, and the four toolbox screens built on the same
access: Remove bloat, Screen density, System tweaks, and Key mapping. It closes with three
device findings that were made with the same shell access but are not implemented in the app.

The mechanism is used by other subsystems documented elsewhere (the keyboard backlight and the
notification ring, the screen trackpad's overlay grant, the Fn to Ctrl remap, the orange side
key). Those documents describe the features; this one describes the privileged plumbing they
share and the exact system writes they make.

## 1. Vocabulary

- **Broker**: the app's own wireless-ADB client. It talks to the phone's own `adbd` over
  loopback (127.0.0.1) exactly as a desktop `adb` would, using a key pair the phone was taught
  during pairing. No root, no Shizuku app, no external device.
- **Paired**: a private key is stored in the app's preferences. This is a weak claim: a key is
  minted the moment a pairing is attempted, so "paired" alone does not mean the phone accepts
  the app.
- **Verified**: the broker has actually connected and run a trivial command within the last
  10 seconds. This is the only readiness signal any screen is allowed to show the user.
- **Privileged setup pass**: the idempotent sequence that applies every privileged change the
  enabled features need. Runs at pairing success, at every IME start, and whenever the Smart
  backlight screen sees the feature enabled with a key stored.
- **Toolbox** ("T2E Tools" in the UI): the hub screen that hosts the device setup card and the
  device-level tools.

## 2. The vendored ADB client: interface only

The pairing and connection code is Apache-licensed Shizuku code kept verbatim in 3.0. The
rewrite does not reimplement it. What the app asks of it:

| Operation | Input | Output | Failure modes |
|---|---|---|---|
| Discover a service | an mDNS service type: `_adb-tls-pairing._tcp` (pairing) or `_adb-tls-connect._tcp` (connect); an observer for the port | the TCP port, delivered once per resolved service whose host address matches one of the phone's own interfaces and whose port is in use; -1 when the service disappears | nothing is ever delivered if Wireless debugging is off or the phone is not advertising; a second concurrent discovery of the same type fails silently (both callers see nothing) |
| Pair | host 127.0.0.1, the discovered pairing port, the 6-digit code the user typed, the app's key | true when the phone accepted the key | connection refused (pairing port gone), wrong code, key store unreadable, any other throwable |
| Connect and run a shell line | host 127.0.0.1, the discovered connect port, the app's key, one shell command string | the command's stdout and stderr bytes, streamed; the call returns when the shell closes | TCP connect timeout 5000 ms; read timeout 10000 ms per read; TLS handshake refused when the phone does not trust the key; any throwable |
| Load or mint the key | the app's key store and the key name `physiboard` | an RSA-2048 key pair | the stored ciphertext cannot be decrypted (key store exception; see section 4.4) |

The app never inspects packets, never opens a port itself, and never talks to any host other
than 127.0.0.1. Wireless debugging (and its mDNS advertisement) exists only from Android 11; on
anything older discovery returns nothing immediately.

## 3. Where the pairing lives in the UI

The **device setup card** sits at the top of the T2E Tools hub and is also shown on the Smart
keyboard backlight screen while that feature is switched on but not yet configured. The card is
one surface with several states; the same shared verdict drives all of them (section 5).

The hub itself (title "T2E Tools", intro "Titan-specific tools. These change the phone itself
rather than the keyboard, so anything here that outlives an uninstall can be undone with Reset
device settings to stock.") lists, in order: Smart keyboard backlight, Remove bloat, Screen
density, System tweaks, Notification ring, Screen trackpad, Key mapping. Remove bloat, Screen
density and System tweaks each offer a "Set up pairing" button when the broker is not usable;
that button navigates to the Smart keyboard backlight screen. The hub is reachable from the home
screen's T2E Tools button and by the deep links `DESTINATION_TOOLBOX` and
`DESTINATION_REMOVE_BLOAT` (the latter opens the hub with Remove bloat pushed on top, so Back
lands on the hub).

## 4. The one-time pairing flow

### 4.1 What the user sees, in order

1. The card opens in the **"Setup needed"** state (warning icon). It shows three numbered
   steps. If Developer options are off (`development_settings_enabled` in `global` is not 1):
   "Open Settings → About phone", "Tap Build number seven times", "Come back here", and a button
   "Open About phone" (fires `android.settings.DEVICE_INFO_SETTINGS`). If Developer options are
   on: "Turn on Wireless debugging", "Tap Pair device with pairing code", "Type the code into
   the PhysiBoard notification", and a button "Open Wireless debugging" (tries
   `android.settings.ADB_WIRELESS_SETTINGS`, falls back to
   `android.settings.APPLICATION_DEVELOPMENT_SETTINGS`). The button label is the same whether
   Wireless debugging is already on or not.
2. Below the steps, one warning line may appear in error color: "Allow PhysiBoard
   notifications first — the pairing code arrives as one." when the notification permission is
   missing (Android 13+), else "Do Not Disturb is on and may hide the pairing code. Turn it off
   until you are paired." when `zen_mode` in `global` is non-zero. The card requests the
   notification permission once on first appearance; if granted while unpaired, the pairing
   watcher is re-armed so it runs under the new permission.
3. **The watcher arms itself the moment the card appears** while unpaired, before the user
   taps anything, and is deliberately not stopped when the user leaves the screen. It is a
   foreground service of type `connectedDevice` on the notification channel "ADB pairing"
   (importance high, silent, no badge, no bubbles, bypass-DND requested; Android only honours
   the bypass if the app has notification-policy access, which it does not, hence the warning
   above). Its notification reads "Searching for pairing service…" with a "Stop" action.
4. The user turns on Wireless debugging and taps "Pair device with pairing code". Android shows
   a 6-digit code and starts advertising `_adb-tls-pairing._tcp`. The watcher's discovery
   resolves the port and replaces its notification with **"Pairing service found"** carrying an
   inline reply action "Enter pairing code" (reply label "Pairing code"). The discovered port is
   embedded in that action, because the service may be killed before the user replies.
5. The user types the code into the notification. The notification becomes "Pairing…". The app
   then loads or mints its key, pairs on 127.0.0.1 at the discovered port with that code, and
   reports:
   - success: notification "Paired successfully" / "This device can now connect to wireless
     debugging."; the watcher stops discovery, the foreground notification is removed, the
     cached verdict is invalidated, and the privileged setup pass runs immediately with reason
     `pairing_succeeded`;
   - failure: notification "Pairing failed" with one of "Cannot connect to the pairing port."
     (connection refused), "The pairing code is wrong.", "Failed to access the ADB key store.",
     or the raw stack trace of any other error. The service stops itself either way.
6. Once a key is stored the card's own 1.5 s poll notices, stops the watcher if it armed it,
   and the card moves to the paired states of section 5.

If the watcher's foreground start is refused by the OS (background start restriction on
Android 12+), the same notification is posted as an ordinary notification instead. The service
restarts with its last intent if killed.

The Smart keyboard backlight screen embeds this same card whenever its toggle is on and the
persistent "configured once" flag is false, so a user who starts from the backlight is never
sent elsewhere to pair.

### 4.2 Key storage, so pairing survives updates

- File: `/data/data/brobata.physiboard/shared_prefs/embedded_adb.xml`, one string entry under
  the key `adbkey`.
- Value: Base64 (no line wrap) of an AES-256-GCM ciphertext of the PKCS#8 private key: a
  12-byte IV, then the ciphertext, then a 16-byte tag, with the 16-byte AAD `adbkey` padded
  with zeros. The AES key lives in the Android Keystore under the alias
  `_adbkey_encryption_key_` (GCM, no padding, 256-bit, created on first use).
- The public key the phone learns carries the name `physiboard`; the same name must be used
  for pairing and for every later connect.
- Consequence: the pairing survives app updates and reboots because the applicationId and the
  preferences file do not change. It does not survive clearing app data, and it does not
  survive a Keystore reset (the ciphertext then cannot be decrypted; see 4.4). It is not part
  of the app's backup file: nothing in the backup writer reads this preferences file.

The rebuild plan requires 3.0 to keep this exact file and format so an upgrade does not lose
the pairing; losing it is the failure users report first.

### 4.3 "Paired" is only "a key is stored"

The paired predicate is nothing more than "the `adbkey` entry exists". A key is minted and
stored before the pairing code is checked. Two rules follow:

- A pairing attempt that fails (wrong code, refused port, any error, or the phone answering
  "not paired") discards the key **only if no key existed before the attempt**. A key that
  predates the attempt may back a pairing that still works and is left alone.
- No screen may show "ready" from the paired predicate alone. Readiness is the verified
  verdict of section 5.

### 4.4 A stored key that cannot be read

If decrypting the stored ciphertext fails (Keystore reset, corrupt entry), loading the key
throws rather than silently minting a new one, because the phone still trusts the old public
key and a new one would leave the app "paired" against a key every connection refuses.

- On a normal broker call: the stored entry is removed, the call fails with the key-store
  error, and the app reads as unpaired, so the setup card offers to pair again.
- During a pairing attempt: the unreadable entry is removed and a fresh key is minted, because
  the phone is about to be told the new public key anyway.

### 4.5 Forgetting a pairing

"Forget pairing" (or "Re-pair" when the verdict is REJECTED) on the setup card, and "Re-pair"
on the Smart backlight screen, remove the `adbkey` entry, clear the broker's last error and
last result, invalidate the cached verdict, and (on the backlight screen only) clear the
backlight "configured once" flag. The card returns to "Setup needed" and re-arms the watcher on
its next poll tick.

## 5. The verified broker status

### 5.1 The five verdicts and how they are reached

A verification is a real connection attempt. In order:

1. No `adbkey` entry: **NOT_PAIRED**.
2. `adb_wifi_enabled` in `global` is not 1: **WIRELESS_DEBUGGING_OFF**. (Android turns Wireless
   debugging off across every reboot, so this is the routine failure on a paired phone.)
3. Discovery of `_adb-tls-connect._tcp` yields no port within 8000 ms: **NO_SERVICE**.
4. Connect and run `echo physiboard_verify`: success is **OK**, any failure is **REJECTED** (the
   phone is advertising but refuses the key; the only cause on a Titan is a pairing that never
   completed, typically a mistyped code).

A verification can take up to about 8 s of discovery plus a 5 s connect plus reads, all off the
main thread, and holds the broker lock (section 6) while it runs.

### 5.2 One verdict, shared

There is exactly one verdict in the process. Every screen that shows readiness (the home
screen's T2E Tools tile, the device setup card, the Smart backlight screen, the Remove bloat
screen, the Notification ring screen) reads the same value, so two screens can never disagree.

- The last verdict is persisted (`privileged_broker_status`, `privileged_broker_status_at`)
  and used to seed the first display so a screen never opens blank.
- A verdict is treated as fresh for **10 000 ms**; a request inside that window returns the
  cached one. Requests that arrive while a check is in flight wait for that check rather than
  starting their own.
- Screens force a re-check when they open, when their refresh key changes (for example after a
  re-pair), and whenever the polled `adb_wifi_enabled` value flips; that global is polled every
  **1500 ms** while such a screen is visible.
- Forgetting a pairing, and the end of any pairing attempt, invalidate the cache so the stale
  verdict cannot stand for the remaining window.

### 5.3 What each surface shows

**Device setup card**, once a key is stored:

| Verdict | Title | Body | Buttons |
|---|---|---|---|
| null (checking) | "Checking…" | "Testing whether the tools can reach the system." | Forget pairing |
| OK | "Paired" (check icon) | "The tools below can reach the system. Survives reboots." | Forget pairing |
| WIRELESS_DEBUGGING_OFF | "Cannot reach the system" | "Wireless debugging is off, so the backlight setting cannot be applied. Android turns it off after a restart — turn it back on in Developer options." | Open Wireless debugging, Forget pairing |
| NO_SERVICE | "Cannot reach the system" | "Wireless debugging is on but the phone is not advertising it. Turn it off and on again in Developer options." | Open Wireless debugging, Forget pairing |
| REJECTED | "Cannot reach the system" | "Paired, but your phone is refusing the connection — the pairing code was probably mistyped, so the app is holding a key your phone never accepted. Re-pair to fix it." | Re-pair |
| NOT_PAIRED (key vanished mid-check) | "Cannot reach the system" | "Not paired. Set up wireless debugging below to apply the backlight setting." | Forget pairing |

Wireless debugging being off does not invalidate a pairing, so that case is sent to the switch,
not told to pair again.

**Home screen T2E Tools tile**: raises its attention badge only when the verdict is present and
not OK. A null verdict (check not landed) never raises it, so the badge does not flash at every
launch.

**Smart backlight screen**, when enabled and configured once: a verified non-OK verdict outranks
the cheap blocker check and is shown in monospace error text prefixed "! ", with the
strings above (REJECTED uses "The phone refused the pairing. This happens when a pairing code
was mistyped: the app kept a key your phone never accepted. Re-pair to fix it."), plus buttons
"Re-pair" and "Check again" (label "Checking…" and disabled while a check is in flight). With
an OK verdict it shows "✓ Always on — set up once, survives reboots." in the primary color. It
also asks the device for the real backlight timeout whenever the verdict is OK and, if the
device does not hold the always-on value, shows "! Your phone is no longer holding the always-on
setting — a system update or another app reset it. The backlight will time out until it is
applied again." with an "Apply again" button. Under all of that, the last recorded backlight
failure, if any, is shown as "Last attempt failed: <reason>".

**Remove bloat**: refuses to list anything unless the verdict is OK; shows "Needs the same
wireless-debugging pairing as the keyboard backlight." and "Set up pairing". Screen density and
System tweaks decide from whether their first read through the broker returned anything, not
from the verdict, and show the same notice and button when it did not.

## 6. Running a privileged command

Every privileged action is one shell line sent through the broker, executed as follows:

1. Take the broker lock. All discovery-plus-shell work in the process is serialized. This is
   not an optimisation: two overlapping mDNS discoveries for the same service type both fail
   silently, so two privileged steps started together (the backlight write and the overlay
   grant at IME start) would each see "no service found".
2. If no key is stored: fail with the message "Not paired yet — set up wireless debugging
   first."
3. Discover `_adb-tls-connect._tcp`; give up after **8000 ms** with "No adb-tls-connect service
   found. Is wireless debugging on?"
4. Load the key (section 4.4), connect with a **5000 ms** TCP timeout and **10 000 ms** read
   timeout, run the line, collect all output.
5. On success, remember the output as the last result and clear the last error; on any error
   remember "<exception simple name>: <message>" as the last error.

The call never throws. It blocks for the whole sequence and must never run on the main thread.
Callers that batch several settings writes join them with `; ` into one line so one discovery
serves all of them. The port is rediscovered on every call: the phone's Wireless debugging port
rotates on every toggle and reboot (and, on a charge-only supply, about once a second; section
19), so nothing is ever cached about it.

The last error and last result live only in memory; the process that holds them restarts
constantly (it is the IME), which is why outcomes are also persisted (section 8).

## 7. The privileged setup pass

One pass applies everything the enabled features need. It runs, with a reason string for the
log, at: pairing success (`pairing_succeeded`), IME service start (`ime_start`), and whenever
the Smart backlight screen observes the feature enabled while a key is stored
(`backlight_screen`). Every step is idempotent, runs off the main thread, and never throws, so
the pass can run any number of times.

Before any step: if the cheap blocker check fails (no key stored: `not_paired`; or
`adb_wifi_enabled` is not 1: `wireless_debugging_off`), every step's outcome is recorded as
failed with that reason and the pass returns. This exists because the release build strips
every log level below error, so a silent early return would leave no trace at all.

Otherwise, in this order:

1. **Backlight always-on**, only if `smart_backlight_enabled` is true. Sends
   `service call agui_functional_service 2 s16 "keyboard_brightness_timeout" s16 "-1"` on a
   single-thread executor (so it queues behind any earlier backlight write). On success sets
   `smart_backlight_applied` true, immediately reads the value back with
   `service call agui_functional_service 1 s16 "keyboard_brightness_timeout"` and records the
   observed value, and records the step outcome `ok`; on failure records the broker's last
   error (or `shell_failed`). Reverting sends the same write with `"30000"` and sets the
   applied flag false. The read-back parses the reply parcel: word 0 is the exception code,
   word 1 the character count (accepted only when 1 to 64), and each following word packs two
   little-endian UTF-16 units; the quoted ASCII rendering beside it is ignored because it
   prints non-ASCII as dots.
2. **Overlay grant** for the screen trackpad: no-op if "Display over other apps" is already
   granted; otherwise sends `appops set brobata.physiboard SYSTEM_ALERT_WINDOW allow` and
   re-checks the permission. The first time the grant succeeds while `screen_trackpad_enabled`
   is false, the trackpad is switched on, so the pairing is genuinely the only step the user
   takes.
3. **Notification ring grants**, only if `notification_ring_enabled` is true: no-op if
   notification access, the full-screen-intent permission (Android 14+) and notifications are
   all already in place; otherwise one line
   `cmd notification allow_listener <listener component>; appops set brobata.physiboard USE_FULL_SCREEN_INTENT allow; appops set brobata.physiboard POST_NOTIFICATION allow`.
4. **Ring backlight grant**, only if the ring is enabled: no-op if the app already holds
   `WRITE_SECURE_SETTINGS` (recorded as ok); otherwise
   `pm grant brobata.physiboard android.permission.WRITE_SECURE_SETTINGS`, then the outcome is
   decided by re-checking the permission, not by the shell's exit status, because `pm grant`
   prints nothing on success.

Steps 2 to 4 use "a key is stored" as their pre-flight, not the verified verdict; they are
tolerant of failing. Only steps 1 and 4 record outcomes (section 8); steps 2 and 3 log only,
which in a release build means nothing is recorded for them.

## 8. Privileged diagnostics

Outcomes are persisted in the main preferences so they survive IME restarts and reach the debug
export. For each step name in {`backlight`, `overlay_grant`, `notification_ring`,
`ring_backlight`}: `privileged_<step>_ok` (boolean), `privileged_<step>_reason` (string; one of
`ok`, `not_paired`, `wireless_debugging_off`, `shell_failed`, or a free-text error), and
`privileged_<step>_at` (epoch ms; 0 or absent means never run). The last observed backlight
value is `privileged_backlight_device_value` (string, may be null) with
`privileged_backlight_device_value_at`; the last verdict is `privileged_broker_status` (the
verdict name) with `privileged_broker_status_at`.

The debug export's `[privileged]` section prints, one per line: `broker_paired`,
`wireless_debugging_enabled`, `broker_blocker` (`none` when clear), `backlight_enabled`,
`backlight_applied_flag`, `backlight_device_value` (`never read` if none),
`backlight_device_value_at` (`n/a` if none), `overlay_permission_granted`,
`notification_listener_granted`, `notification_ring_enabled`, `screen_trackpad_enabled`,
`trackpad_provider`, `ime_enabled`, `ime_selected`, then either
`last_outcomes=(no privileged step has run)` or one `last_<step>=ok|failed reason='…' at=<time>`
per recorded step.

## 9. Every system-level write the app makes

All of these live in the OS or vendor layer and survive uninstalling the app. Android gives an
app no uninstall hook, so the supported path is "Reset device settings to stock" (section 10)
before uninstalling. "Direct" means written with the app's own permission (`WRITE_SETTINGS`
for `system`, `WRITE_SECURE_SETTINGS` for `global`/`secure` once granted); "broker" means a
`settings put` line through the shell.

| Namespace | Key | Value written | Why | Route | Original captured? |
|---|---|---|---|---|---|
| system | `fn_programmable_key_enable` | 1 | Fn to Ctrl remap (vendor reads this before any app sees the key) | direct, else broker | yes: `fn_ctrl_prev_captured`, `fn_ctrl_prev_enable` (unset sentinel = Int.MIN_VALUE) |
| system | `fn_programmable_key_function` | 1 (inferred to mean Ctrl) | same | direct, else broker | yes: `fn_ctrl_prev_function` |
| system | `func1_long_press_package` | `brobata.physiboard` | Orange side key long press opens the assistant already listening | direct, else broker | yes: `side_key_original_captured`, `side_key_original_package` |
| system | `func1_long_press_activity` | the app's assistant trigger activity class name | same | direct, else broker | yes: `side_key_original_activity` |
| system | `func1_shortcut_key_enable` | 1 | the vendor ignores every slot unless this is on; stock already has it on | direct, else broker | no |
| global | `agui_keyboard_background_light` | 0 or 1 | the Quick Settings backlight tile flips the vendor master switch; the notification ring writes 0 before lighting and restores after | direct (needs the granted WRITE_SECURE_SETTINGS) | yes for the tile: `qs_backlight_prev_captured`, `qs_backlight_prev` (unset sentinel = Int.MIN_VALUE) |
| vendor binder store | `keyboard_brightness_timeout` | -1 (always on) or 30000 (stock) | keyboard backlight past the 30 s cap | broker only (`service call agui_functional_service 2`) | no: stock is the known constant 30000 |
| global | `window_animation_scale`, `transition_animation_scale`, `animator_duration_scale` | 0, 0.5 or 1.0 | System tweaks: animation speed | broker | no: stock is 1.0 |
| secure | `notification_history_enabled` | 1, or deleted | System tweaks | broker | no: stock is unset |
| secure | `one_handed_enabled` | 1, or deleted | System tweaks | broker | no: stock is unset |
| window manager | display density override | any value in the safe range, or reset | Screen density | broker (`wm density N` / `wm density reset`) | the revert is always "reset", never a number |
| package manager | per-user enabled/installed state of catalog packages | disabled / uninstalled for user 0 | Remove bloat | broker | yes: the removal journal (section 12.6) |
| app ops and grants | `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATION` for the app; notification listener allow-list; `WRITE_SECURE_SETTINGS` runtime grant | allow / granted | trackpad overlay, notification ring, ring backlight | broker | no |

Side-key values are validated before they are written or restored: only strings up to 256
characters matching `[A-Za-z0-9_][A-Za-z0-9_.$]*` are accepted, because the restore path feeds
values read back from `Settings.System` into a shell line and any app holding `WRITE_SETTINGS`
could otherwise plant shell metacharacters there. A malformed current value is not captured
(no restore point is promised); a malformed captured value is dropped instead of restored.
Package names are validated the same way (`[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*`) and must
additionally be in the catalog.

## 10. Reset device settings to stock

Lives on the main settings list (the old Advanced screen is gone as of 2.0), row "Reset device
settings to stock" with description "Undo the system-wide changes PhysiBoard made — the Fn key
mapping and keyboard backlight — restoring your device to stock. Do this BEFORE uninstalling;
uninstalling alone won't undo them." Tapping opens a dialog "Reset device settings to stock?" /
"This restores the Fn key mapping and keyboard backlight to your device's stock settings. Your
PhysiBoard preferences are kept. You can re-apply these features anytime." with "Reset to
stock" and "Cancel". While running, the row shows a spinner and is not tappable.

Five reverts run independently (one failing never skips the others), off the main thread, each
never throwing:

1. **Fn to Ctrl**: write the captured originals to `fn_programmable_key_enable` and
   `fn_programmable_key_function`; an unset or never-captured original becomes 0. Direct if
   `WRITE_SETTINGS` is held, else through the broker as two `settings put system` lines, else
   NEEDS_PERMISSION. The capture is cleared only on success.
2. **Backlight**: always send the stock timeout write (`"30000"`), set `smart_backlight_enabled`
   and `smart_backlight_applied` false. If neither flag was true there was nothing at the vendor
   level to undo: SUCCESS. Otherwise SUCCESS if a key is stored (the write was queued), else
   NEEDS_PERMISSION. Note that the write is queued and its result is not awaited.
3. **QS tile backlight**: write the captured original of `agui_keyboard_background_light` (or 0
   when never captured or unset). Direct if `WRITE_SECURE_SETTINGS` is held, else broker, else
   NEEDS_PERMISSION. Capture cleared on success.
4. **Orange side key**: set `side_key_assistant` false, then restore the captured long-press
   package and activity (nothing captured: SUCCESS without writing; malformed capture: dropped,
   SUCCESS). Direct with `WRITE_SETTINGS`, else broker, else NEEDS_PERMISSION.
5. **Notification ring**: set `notification_ring_enabled` false. If neither notification access
   nor the full-screen permission is held: SUCCESS. Else, with no key stored:
   NEEDS_PERMISSION; otherwise send
   `cmd notification disallow_listener <component>; appops set brobata.physiboard USE_FULL_SCREEN_INTENT default`
   and report SUCCESS only if notification access is gone afterwards.

Result snackbar: all five SUCCESS gives "Device settings restored to stock."; any
NEEDS_PERMISSION gives "Grant PhysiBoard "Modify system settings", or pair wireless debugging,
then try again."; otherwise "Some settings were restored. A reboot may be needed for changes to
fully apply."

Not covered by reset to stock, despite the plan and the 1.1.0 change note saying "Restore all
is wired into Reset device settings to stock": the bloat journal (packages stay disabled),
display density, the three animation scales and the two secure toggles, the `WRITE_SECURE_SETTINGS`
grant, the overlay grant, and `func1_shortcut_key_enable`. Each of the toolbox screens has its
own "back to stock" control instead.

## 11. Toolbox screens: common behavior

- Each screen loads through the broker on entry and shows an indeterminate progress bar while
  the round trip runs (up to 8 s of discovery plus the command).
- Every action disables the controls while a broker call is in flight, then reloads.
- Results are read back from the phone, never assumed; the screens show what the phone reports.
- None of the toolbox writes are captured for reset-to-stock; each screen offers its own reset.

## 12. Remove bloat

### 12.1 Gating

- Off any device that is not a Titan 2 Elite (device profile check), the screen shows only
  "This list was written for the Titan 2 Elite and stays switched off on other phones, because
  it removes packages by name." and every mutation is refused with "This catalog is for the
  Titan 2 Elite only". Nothing is inert on the Titan 2 non-Elite by this check; it is the
  profile that decides.
- Unless the verified verdict is OK, the screen shows the not-paired notice and "Set up
  pairing" and nothing else.
- The catalog records the firmware it was verified against, `V02.00.02` (the build's
  incremental version). The app carries a check for "is the running firmware that one" but
  nothing calls it; there is no warning on a different firmware. The mitigation that does exist
  is the unrecognised-package list (12.5).

### 12.2 The catalog

28 entries (the 1.1.0 change note says 29; the plan's draft list also had `com.iqqijni.bbkeyboard`
as optional and `com.android.fmradio` as useful, both of which were dropped: the first moved to
the denylist, the second is excluded by the `com.android.` pattern). Tier order is the display
order: Factory tools, then Vendor features, then Drives hardware, alphabetical by label within a
tier.

| Package | Label | Tier | Presets |
|---|---|---|---|
| `com.bhpme.AgingTest` | Aging Test | Factory | Factory tools |
| `com.agui.app.apninfocollector` | APN Info Collector | Factory | Privacy, Factory tools |
| `com.agui.batterystatsdumper` | Battery Stats Dumper | Factory | Privacy, Factory tools |
| `com.agui.calibration` | Calibration | Factory | Factory tools |
| `com.devices116` | Devices116 | Factory | Factory tools |
| `com.agui.factorytest` | Factory Test | Factory | Factory tools |
| `com.example.feedback` | Feedback | Factory | Factory tools |
| `com.swatch.gps` | GPS Test | Factory | Factory tools |
| `com.agui.app.imei` | IMEI Tool | Factory | Factory tools |
| `com.debug.loggerui` | MediaTek Logger | Factory | Privacy, Factory tools, Background killers |
| `com.agui.app.memtester` | Memory Tester | Factory | Factory tools |
| `com.agui.appblock` | App Block | Vendor | Background killers |
| `com.agui.frozen` | App Freezer | Vendor | Background killers |
| `com.agui.applock` | App Lock | Vendor | none |
| `com.agold.autopoweronoff` | Auto Power On/Off | Vendor | Background killers |
| `com.agui.bedtimesetting` | Bedtime | Vendor | Vendor extras |
| `com.agui.callrecord` | Call Recorder | Vendor | Privacy, Vendor extras |
| `com.agold.cyclocomputer` | Cyclocomputer | Vendor | Vendor extras |
| `com.agui.game` | Game Mode | Vendor | Vendor extras |
| `com.agui.aguigrabageclear` | Garbage Clear | Vendor | Background killers |
| `com.agui.nfc` | NFC Tools | Vendor | Vendor extras |
| `com.agui.providers.pedometer` | Pedometer | Vendor | Vendor extras |
| `com.agui.systemmanager` | Phone Manager | Vendor | Background killers |
| `com.agui.privatespace` | Private Space | Vendor | none |
| `com.agui.rotationcontrol` | Rotation Control | Vendor | Vendor extras |
| `com.agui.studentmodel` | Student Mode | Vendor | none |
| `com.agui.toolbox` | Toolbox | Vendor | Vendor extras |
| `com.tiqiaa.icontrol` | IR Remote | Drives hardware | none |

Each row shows the label, a one-line summary (for example Phone Manager: "Vendor battery and
memory manager. Android already does all of this in the system server — Doze, App Standby,
Adaptive Battery — and this duplicates it with its own opinions about killing background
apps."; IR Remote: "Drives the infrared blaster. Removing it makes that hardware unusable."),
its state, and its buttons. Tier headers: "Factory tools" / "Test and calibration apps left over
from the production line. Nothing uses them."; "Vendor features" / "Real features that
duplicate something Android already does, or that you may simply not want."; "Drives
hardware" / "Listed so you can see them, but removing these makes real hardware stop working."

### 12.3 Presets

Shown as cards above the list, in this order, each listing only the entries still Active and
hidden entirely when none are; the button reads "Disable all N" and disables (never
uninstalls) each active entry in turn, in one coroutine, with one broker round trip per
package:

1. **Android Auto stabilizer** (Background killers), badge "In testing": 6 packages
   (`com.debug.loggerui`, `com.agui.systemmanager`, `com.agui.aguigrabageclear`,
   `com.agui.frozen`, `com.agui.appblock`, `com.agold.autopoweronoff`). Description states
   plainly that this is the most likely cause of Android Auto dropping, not a proven one.
2. **Factory and lab tools**: the 11 Factory-tier packages.
3. **Vendor apps and games**: 9 packages (Game Mode, Toolbox, Bedtime, Call Recorder,
   Pedometer, Rotation Control, NFC Tools, Cyclocomputer; the description explains App Lock,
   Private Space and Student Mode are left out on purpose and IR Remote is never touched).
4. **Sends data onward** (Privacy): Battery Stats Dumper, APN Info Collector, MediaTek Logger,
   Call Recorder. Presets overlap deliberately.

### 12.4 Protected packages

Refused everywhere, enforced at the last gate before a name reaches the shell and not only in
the UI. By name: `com.agui.shortcutsettings` (owns the orange side key the app rebinds),
`com.agui.settings` (hosts the vendor keyboard and backlight configuration), `com.agui.update`
(the OTA client), `com.agui.spacebarkey` (the spacebar is also the fingerprint sensor and the
trackpad trigger), `com.agui.esim.service`, `com.agui.keyboard`, `com.agui.overlay.kika`,
`com.agold.networkmanager.service`, `com.agold.networkmanager.ui`,
`com.agui.systemui.fixed_status_bar_icon_size`, `com.agui.internal.fixed_status_bar_icon_size`,
`com.iqqijni.bbkeyboard` (the only other keyboard on a stock phone; if PhysiBoard is ever
disabled the phone falls back to it, and removing it can leave a phone that cannot type). By
pattern, case-insensitive: anything containing `overlay` (RRO resource packages, not apps),
anything starting `com.android.` or `com.google.android.`, anything containing `telephony`,
`dialer`, the word `sms`, `systemui` or `launcher`. And anything not in the catalog at all.

### 12.5 The census

On entry and after every action, one broker line
`echo __E__; pm list packages -e; echo __D__; pm list packages -d; echo __U__; pm list packages -u`
is run and split on the three markers into enabled, disabled and everything-including-
uninstalled (each line stripped of its `package:` prefix). For every catalog entry: in
disabled = **Disabled**; else in enabled = **Active**; else in the everything set =
**Uninstalled**; else **Absent** (the row is not shown). Additionally every package in the
everything set that starts with one of the vendor namespaces `com.agui.`, `com.agold.`,
`com.bhpme.`, `com.swatch.`, `com.devices`, `com.debug.`, `com.iqqijni.`, `com.tiqiaa.` and is
neither catalogued nor protected (by name or pattern) is listed, sorted, at the bottom under
"N vendor apps not in this list" with the explanation that they came from a later firmware
or this is not a Titan 2 Elite, shown but never touched.

Reading through the shell rather than the package manager API is deliberate: a disabled or
uninstalled-for-user package is awkward to classify locally without hidden flags, and the
broker is required for this screen anyway.

### 12.6 Actions, the journal, and restore

| State | Buttons |
|---|---|
| Active | Disable, Uninstall |
| Disabled | Restore, Uninstall |
| Uninstalled | Restore |

- **Disable** sends `pm disable-user --user 0 <pkg>`. Default action everywhere: gone from the
  drawer and not running, trivially reversible, nothing deleted.
- **Uninstall** first shows "Uninstall <label>?" / "Disabling is usually enough: it hides the app
  and stops it running, and is instantly reversible. Uninstalling removes it for this user —
  PhysiBoard can still restore it because the app stays in system storage, but a firmware
  update may bring it back, and if wireless debugging is ever unavailable you cannot undo it
  from here." with Uninstall / Cancel; then sends `pm uninstall --user 0 <pkg>`.
- Before either command runs, a journal record is written: `{"pkg": …, "prev": "ACTIVE" |
  "DISABLED" | "UNINSTALLED" | "ABSENT", "action": "DISABLED" | "UNINSTALLED", "at": epoch ms}`
  in a JSON array under the key `removal_journal` in the preferences file
  `/data/data/brobata.physiboard/shared_prefs/physiboard_toolbox.xml`. One record per package:
  acting again on the same package replaces its record rather than stacking. If the command
  fails the record is removed again (it never happened, so the journal must not claim it did).
  An unreadable journal reads as empty; a record with an unknown `prev` reads as ACTIVE and an
  unknown `action` as DISABLED.
- **Restore** sends two lines, always both, regardless of what the journal says the previous
  state was: `cmd package install-existing --user 0 <pkg>` then `pm enable --user 0 <pkg>`
  (a package can be both uninstalled-for-user and disabled, and reinstalling does not
  re-enable). Success if either line succeeded; the journal record is then forgotten.
- **Restore all** appears as "N package(s) changed by PhysiBoard" with a "Restore all" button
  whenever the journal is non-empty, and restores each journal record in turn. Restore does not
  depend on the catalog: a package disabled two releases ago whose entry was since retired
  still comes back.
- Outcomes: NotPaired shows the not-paired notice; Refused shows its reason ("Not a package
  name", "This package is protected", the wrong-device text); Failed shows the broker's last
  error or "Command failed" / "Restore failed".

The journal is not in the app's backup file.

## 13. Screen density

Intro: "Lower density fits more on screen; higher makes everything bigger. The Titan's screen
is short, so a small reduction buys a surprising number of extra lines — and with a physical
keyboard, none of that space goes back to an on-screen one."

- **Read**: `wm density`. The output's "Physical density: N" is the stock value; an "Override
  density: N" line is present only when an override is in force, so its absence is the signal
  that nothing has been changed. Current = override if present else physical. A read that
  fails (no key stored, broker failure, unparseable) shows the not-paired notice.
- **Range**: 60 % to 140 % of the physical density, integer-truncated (300 dpi stock gives 180
  to 420). Below roughly 60 % the system UI lays out in ways nothing was designed for; above
  140 % the keyboard's own rows stop fitting. A value outside the range is refused with
  "Outside the safe range" even if a caller tries.
- **Slider**: snaps to multiples of 5; the label reads "<chosen> dpi (stock is <physical>)" with
  a line "Everything smaller, more fits on screen" / "Everything larger, less fits on screen" /
  "The density this screen shipped with". Apply is enabled only when the chosen value differs
  from the current one and nothing is in flight. "Back to stock" appears only when an override
  is in force.
- **Apply**: the revert is armed **before** the command lands, as JSON
  `{"id": "display_density", "apply": "wm density <N>", "revert": "wm density reset"}` under the
  key `pending_revert` in the same `physiboard_toolbox` preferences file; then `wm density N`
  is sent. If the command fails the pending record is removed and the error shown. The revert
  is always "reset", never the previous number, so chained changes never leave the undo
  pointing at another override.
- **Countdown**: on success a dialog "Can you still read this?" / "Reverting to the stock density
  in N seconds unless you keep it. If the screen is unusable, just wait — this undoes itself."
  counts down from **15** in 1 s steps. It cannot be dismissed by tapping outside. "Keep it"
  removes the pending record. "Undo now", or reaching zero, sends the recorded revert and
  removes the record only if that succeeded. The slider and buttons are disabled while the
  countdown runs.
- **Back to stock**: `wm density reset` with no countdown (always safe); on success the pending
  record is cleared and the slider snaps to the physical value.
- **Pending revert after process death**: the record is persisted precisely so a change that
  outlives the app (crash, or a reboot inside the window) can still be undone. Nothing in the
  app reads it back except the density screen's own countdown, so today an armed change whose
  process died becomes permanent until the user opens the screen and taps "Back to stock". The
  intended behavior (and the 3.0 behavior) is to check for a pending record at IME start and
  send its revert.

Per-app density is not possible (D8).

## 14. System tweaks

Intro: "Android supports all of these; Unihertz just never surfaced them. Each one applies
straight away and every one can be put back." No countdown: none of these can make the phone
unusable.

- **Read**: one line `settings get global window_animation_scale; settings get secure
  notification_history_enabled; settings get secure one_handed_enabled`; blank lines are
  dropped; the first line is parsed as a float (unparseable = 1.0) and snapped to the nearest of
  Off (0), Fast (0.5), Normal (1.0); each toggle is on only if its line is exactly `1` (the
  string `null`, which the shell prints for a never-written key, is the shipped state, not an
  error). A failed read (no key stored or broker failure) shows the not-paired notice.
- **Animation speed**: three chips Off / Fast / Normal. Selecting one writes all three globals
  `window_animation_scale`, `transition_animation_scale`, `animator_duration_scale` to 0, 0.5
  or 1.0 in one line. Only the first is read back; the three are assumed to move together.
- **Notification history**: switch; on writes `settings put secure notification_history_enabled 1`,
  off sends `settings delete secure notification_history_enabled`. Description: "Keeps a log of
  notifications you already dismissed, so a swiped-away message is still findable. Standard on
  Pixel."
- **One-handed mode**: same with `one_handed_enabled`. Description: "Pull the top of the screen
  down into reach with a swipe on the navigation bar."
- Turning a toggle off deletes the key rather than writing 0, because these ship unset and
  "back to stock" means absent, not an explicit false.
- **"Put all of these back to stock"**: one line writing the three scales to 1.0 and deleting
  both secure keys.

## 15. Key mapping

A read-only inventory reachable from the hub, intro "Bindings live in two places that never
talk to each other: rows the firmware reads before any app sees the key, and PhysiBoard's own
handling. This is both, per key — including the ones bound to nothing." It needs no broker:
`Settings.System` is readable without permission. It is read once when the screen is entered.
Rows, in order, each with label, binding text in the primary color and hardware text in small
type; rows with an editor show an arrow and navigate:

| Key | Hardware text | Binding text | Opens |
|---|---|---|---|
| Fn | "scancode 251 (FUNC3)" | "Acts as Ctrl" when `fn_programmable_key_enable` = 1 and `fn_programmable_key_function` = 1, else "Fn layer"; plus " · hold to dictate" if Fn long-press speech is on; plus " · long press opens <activity tail>" from `fn_long_press_activity` when not remapped | Fn layer (nav mode) screen |
| Sym | "scancode 253 (AGUI_SYM)" | "Symbol and emoji pages"; " · hold for the assistant" if Sym long-press assistant is on; " · hold for the trackpad" if the trackpad is on with Sym as trigger | Voice screen |
| Orange side key | "ff_key 249" | "tap: <tail of func1_short_press_activity>" · "double: <tail of func1_double_press_activity>" · "hold: the assistant, listening" when `func1_long_press_package` is `brobata.physiboard`, else "hold: <tail of func1_long_press_activity>", else "hold: nothing" | Voice screen |
| Space | "keyboard matrix, also the fingerprint sensor" | "Space · hold for the trackpad" if the trackpad is on with Space as trigger, else "Space" | Screen trackpad screen |
| Right Shift | "keyboard matrix" | "Vendor remapping enabled" if `shift_r_programmable_key_enable` = 1 else "Types Shift" | none |
| Home | "scancode 102" | same rule with `home_programmable_key_enable`, else "Home" | none |
| Recent apps | "navigation key" | same rule with `recent_programmable_key_enable`, else "Recent apps" | none |
| Back | "scancode 158" | "Back" | none |
| Volume up / down | "gpio-keys 115 / 114" | "Volume" | none |
| Power | "ff_key 116" | "Power and screen lock" | none |

Activity values are shown as the segment after the last dot. Footnote: "Keys with an arrow can
be changed. The rest are fixed by the phone or the hardware."

## 16. Finding: Wi-Fi drops and Adaptive Connectivity (not implemented)

The Titan 2 drops Wi-Fi mid-session because Adaptive Connectivity scores the link at 38 to 45
and hands the connection to LTE; `dumpsys wifi` shows `SCORE_BREACH` and
`FRAMEWORK_DISCONNECT reason=DISCONNECT_UNWANTED`, and a score under 50 is the tell. Three
rows, all previously unset, were written by hand over adb on 2026-08-28 and stop it:
`adaptive_connectivity_enabled` = 0 in **`secure`** (the one that matters; reading it from
`global` returns null and misleads), `network_avoid_bad_wifi` = 0 in `global`,
`wifi_watchdog_poor_network_test_enabled` = 0 in `global`. Undo with `settings delete` on each,
or Settings → Network & internet → Adaptive connectivity → On. These survive uninstall like
every secure/global write. The app has no screen for this; it is a candidate System tweak. A
justified low score (2.4 GHz-only SSID, high retry rate in `cmd wifi status`) should be ruled
out before blaming the scorer.

## 17. Finding: the Android Auto USB chooser (not implemented, pending road test)

In a real USB host (a 2025 Silverado head unit) the "Use USB for" chooser kept appearing on the
lock screen and killed Android Auto. `dumpsys usb`'s event log showed, three times identically:
CONNECTED, DISCONNECTED 6 ms later, CONNECTED, CONFIGURED, `ACCESSORY=GETPROTOCOL`, then
`USB_ACCESSORY_HANDSHAKE` exactly 10.00 s later, which is the accessory-handshake timeout
constant, not a negotiation. Cause: MTP was pinned as the screen-unlocked USB function
(`current_functions=MTP`, `screen_unlocked_functions=MTP`), so the switch to accessory mode
never happened. Fix applied 2026-08-31 by hand: `svc usb setScreenUnlockedFunctions` with no
argument (charging only), the same binder call as Developer options → Default USB configuration
→ "No data transfer"; it persists in the USB service's own pinned preferences, not in `settings`,
so `settings list` shows nothing. Revert: `svc usb setScreenUnlockedFunctions mtp`. Side effect:
a computer connection defaults to charging only and File Transfer must be picked by hand; adb is
unaffected because `persist.sys.usb.config` is already `adb` and was not touched. Not
road-verified: the check is a drive with Android Auto on and a `GETPROTOCOL` event with no 10 s
handshake behind it. Not the cable, not the app (the app only reads `adb_wifi_enabled` and never
touches USB), and not the bind loop below.

## 18. Finding: the adbd USB bind loop on charge-only supplies (not implemented)

On a dumb charger (`AC powered: true`, `USB powered: false`, no data link) with USB debugging
off, `sys.usb.config` is `mtp` with no adb function and `persist.sys.usb.config` is empty;
`adbd` opens `/dev/usb-ffs/adb/ep0`, times out waiting for `FUNCTIONFS_BIND`, tears down its
transport and restarts roughly once per second, and every restart regenerates the Wireless
debugging port. Measured: about 30 timeouts per 30 s and a constantly changing adbd pid with USB
debugging off; 0 and a stable pid with it on. The fix is to turn **USB debugging on** in
Developer options: `sys.usb.config` becomes `mtp,adb`, the function binds, adbd settles.
Rebooting does not fix it while `adb_enabled` is 0. Do not write `persist.sys.usb.config` on a
daily driver. This applies to charge-only supplies only; on a real host the advice inverts
(section 17). Even with both fixes the maintainer's port still rotated several times per
session in September 2026, so the broker's rediscover-on-every-call design stands.

## 19. Titan-specific facts

| # | Fact | Evidence |
|---|---|---|
| D1 | The vendor keyboard-backlight timeout is stored through the binder service `agui_functional_service`, transaction 2 = SET(key, value), transaction 1 = GET(key); key `keyboard_brightness_timeout`, `-1` means never turn off, `30000` is the stock 30 s; the value survives reboots. GET returns a String16 parcel. | source comments dated 2026-08-21 (values) and 2026-08-31 (GET); 1.0.1 change note |
| D2 | Android turns Wireless debugging off across every reboot, and the advertised port rotates on every toggle and reboot. | docs/titan2elite device profile "ADB access"; 2.x change notes on the backlight saying why it stopped |
| D3 | Two overlapping NsdManager discoveries of the same service type both fail silently. | 1.0.4-era change note "Broker calls are now serialized — overlapping mDNS discoveries failed silently" |
| D4 | The vendor programmable-key table is a plain `Settings.System` table: `{key}_programmable_key_enable`, `{key}_programmable_key_function` (fn=1 observed to mean Ctrl), `{key}_shortcut_key_enable`, `{key}_{short,long,double}_press_activity` / `_package` for keys fn, func1, func2, home, recent, shift_r, sym; writable with `WRITE_SETTINGS`. | device profile "Vendor key-config"; toolbox plan wave 2 probe table |
| D5 | The orange side key is `ff_key` scancode 249 (`func1`); the vendor launches the configured package/activity directly and the key never reaches an input method. Stock long press points at the Gemini entry activity. | device profile; side-key source comment |
| D6 | The Quick Settings backlight master switch is `Settings.Global` `agui_keyboard_background_light` (0/1), live-toggle confirmed; writing it needs `WRITE_SECURE_SETTINGS`. | device profile "Keyboard backlight (spike results 2026-08-19)" |
| D7 | Firmware `V02.00.02` (build incremental), Android 16; every Titan 2 Elite ships the same packages, so the 28-entry catalog and the vendor namespace list were inventoried on one device on 2026-08-24 and treated as constant. | catalog source comment; toolbox plan "inventoried on-device 2026-08-24" |
| D8 | Per-app density is impossible: `wm density` is global only; `cmd game custom --downscale` is games-only and did not exist on this build; a virtual display with its own density can host only the calling app's activities without `ADD_TRUSTED_DISPLAY` (signature/privileged). The global-switch fake (switch density on app focus) was rejected because each broker call is seconds-scale, every switch reflows the launcher and status bar, and a drop of Wireless debugging mid-override strands the user. | memory note per-app-dpi-not-possible, checked 2026-08-24 on V02.00.02 |
| D9 | Stock display: physical 1080x1200 (override 1076x1200), density 300; animation scales all 1.0; `one_handed_enabled` and `notification_history_enabled` both unset. | device profile "Display"; toolbox plan wave 2 probe table |
| D10 | No notification LED (`/sys/class/leds/` empty), no battery charge-limit node, no fingerprint tunables. | toolbox plan wave 2 probe table |
| D11 | `com.agui.systemmanager` had 0 running processes with Doze enabled and the hibernation API live: it duplicates AOSP power management rather than providing it. | toolbox plan "On com.agui.systemmanager" |
| D12 | The spacebar is also the fingerprint sensor, owned by `com.agui.spacebarkey`. `dumpsys fingerprint` showed accept 197 / reject 117 / acquire 441 / lockout 3 (37 % reject). | toolbox plan; denylist comment |
| D13 | `screen_off_timeout` was 2147483647 on the maintainer's device (screen never sleeps). | toolbox plan "Findings worth surfacing" |
| D14 | Adaptive Connectivity drops Wi-Fi for LTE at link scores 38 to 45; `adaptive_connectivity_enabled` lives in `secure`. | memory note titan-wifi-drops-adaptive-connectivity, 2026-08-28 |
| D15 | MTP pinned as the screen-unlocked USB function makes a head unit's AOAP handshake time out at exactly 10 s; `svc usb setScreenUnlockedFunctions` clears it and persists outside `settings`. Pending road test. | memory note titan-android-auto-usb-chooser, 2026-08-31 |
| D16 | On a charge-only supply with USB debugging off, adbd crash-loops on `FUNCTIONFS_BIND` about once per second and the wireless port rotates with it; USB debugging on stops it. | memory note titan-adbd-usb-bind-loop, 2026-08-28 |
| D17 | A foreground service started from `BOOT_COMPLETED` crashes on this ROM, which is why privileged re-application lives in the IME (the process that survives boot) rather than a boot receiver. | toolbox plan decision log; 1.0.1 removal of the auto re-arm service |
| D18 | The vendored pairing client needs the hidden-API exemption to reach the conscrypt keying-material export on API 28+. | vendored glue comment |

## 20. Settings

Main preferences file unless stated. "n/a" in the screen column means the key is state, not a
control.

| Preference key | Type | Default | What it changes | Screen | Label |
|---|---|---|---|---|---|
| `adbkey` (file `embedded_adb`) | string (Base64 AES-GCM blob) | absent | the broker's private key; presence = "paired" | Device setup card (Forget pairing removes it) | n/a |
| `smart_backlight_enabled` | boolean | false (Titan baseline stamps true) | whether the setup pass writes the always-on timeout | Smart keyboard backlight | "Smart backlight" / "Keeps the keyboard backlight on whenever the screen is on. A one-time setup below — it survives reboots." |
| `smart_backlight_applied` | boolean | false | one-way "configured once" latch; true after a successful always-on write, false after a revert; gates the collapsed ready line | n/a | n/a |
| `notification_ring_enabled` | boolean | false (baseline true) | whether the setup pass grants the ring's permissions and the secure-settings grant | Notification ring | (see ring document) |
| `screen_trackpad_enabled` | boolean | false (baseline true) | switched on automatically by the first successful overlay grant | Screen trackpad | (see trackpad document) |
| `side_key_assistant` | boolean | false (baseline true) | whether the orange key long press is bound to the assistant; reset to stock sets false | Voice | (see side key document) |
| `fn_ctrl_prev_captured` | boolean | false | original Fn values were captured | n/a | n/a |
| `fn_ctrl_prev_enable` | int | Int.MIN_VALUE (unset) | original `fn_programmable_key_enable` | n/a | n/a |
| `fn_ctrl_prev_function` | int | Int.MIN_VALUE | original `fn_programmable_key_function` | n/a | n/a |
| `qs_backlight_prev_captured` | boolean | false | original tile value was captured | n/a | n/a |
| `qs_backlight_prev` | int | Int.MIN_VALUE | original `agui_keyboard_background_light` | n/a | n/a |
| `side_key_original_captured` | boolean | false | original side-key pair was captured | n/a | n/a |
| `side_key_original_package` | string | null | original `func1_long_press_package` | n/a | n/a |
| `side_key_original_activity` | string | null | original `func1_long_press_activity` | n/a | n/a |
| `privileged_backlight_ok` / `_reason` / `_at` | boolean / string / long | absent | last backlight step outcome | Smart backlight ("Last attempt failed: …"), debug export | n/a |
| `privileged_overlay_grant_ok` / `_reason` / `_at` | boolean / string / long | absent | reserved; only written by the blocker short-circuit | debug export | n/a |
| `privileged_notification_ring_ok` / `_reason` / `_at` | boolean / string / long | absent | reserved; only written by the blocker short-circuit | debug export | n/a |
| `privileged_ring_backlight_ok` / `_reason` / `_at` | boolean / string / long | absent | last secure-settings grant outcome | debug export | n/a |
| `privileged_backlight_device_value` / `_at` | string / long | absent | timeout last read back from the device | debug export | n/a |
| `privileged_broker_status` / `_at` | string (verdict name) / long | absent | last verified verdict, seeds every screen | all readiness surfaces | n/a |
| `removal_journal` (file `physiboard_toolbox`) | JSON array string | absent | packages changed and their prior state | Remove bloat ("Restore all") | "N package(s) changed by PhysiBoard" |
| `pending_revert` (file `physiboard_toolbox`) | JSON object string | absent | an armed density change awaiting confirmation | Screen density | n/a |

Controls with no preference of their own (they read and write the phone): the density slider,
Apply, Keep it, Undo now, Back to stock; the animation chips Off / Fast / Normal; the switches
Notification history and One-handed mode; "Put all of these back to stock"; every Disable /
Uninstall / Restore / "Disable all N" / "Restore all"; "Reset device settings to stock".

## 21. Edge cases, quirks, known bugs

| Situation | Behavior | Why |
|---|---|---|
| User types the wrong pairing code on a phone never paired before | "Pairing failed / The pairing code is wrong."; the key minted for the attempt is discarded; the app still reads as unpaired | a key is minted before the code is checked; keeping it made the app claim "paired" forever against a key the phone never accepted (fixed in 2.x) |
| Wrong code on a phone that already has a working key | failure notification; the existing key is kept | it may back a pairing that still works |
| Key stored, phone rejects the connection | verdict REJECTED; card says the code was probably mistyped and offers Re-pair | nothing short of connecting can tell a good key from a bad one |
| Phone reboots | verdict WIRELESS_DEBUGGING_OFF; card sends the user to the switch, not to pairing; the backlight keeps working because the vendor value persists | Android turns Wireless debugging off across reboots; the pairing itself is intact |
| Wireless debugging on but nothing advertised | NO_SERVICE with "turn it off and on again" | the mDNS advertisement sometimes does not come up with the switch |
| Two privileged steps start at once | the second waits; both succeed | the broker lock; overlapping discoveries fail silently |
| Setup pass runs with no key or debugging off | every step recorded failed with the blocker reason; nothing attempted | the release build strips every log level below error; a silent return left no trace |
| User enables the backlight before pairing | the screen embeds the setup card; when a key appears the pass runs with reason `backlight_screen` | "enable first, pair second" must work |
| Backlight write succeeded once, later reset by an update | "configured" latch still true; the screen reads the device when the verdict is OK and shows "Apply again" | the latch is one-way and cannot notice loss |
| Screen density applied, app killed inside the 15 s window | the override stays; the pending record remains on disk but nothing sends it | no IME-start check exists despite the record being persisted for that purpose; known gap |
| Density chosen outside 60 % to 140 % | refused, "Outside the safe range" | the slider cannot reach it, but the gate is independent of the UI |
| Density slider dragged | value snaps to a multiple of 5 | usable steps |
| Uninstall a package, then Wireless debugging becomes unavailable | cannot be undone from the app; the dialog warned of this | restore needs the broker |
| Restore on a package the journal says was disabled only | both install-existing and enable are sent | reinstalling does not re-enable; a package can be in both states |
| Journal record for a package no longer in the catalog | Restore all still restores it | undo must not depend on the catalog |
| Firmware differs from V02.00.02 | no warning; unrecognised vendor packages are listed but untouched | the firmware check exists but is never called |
| Non-Titan device opens Remove bloat | the wrong-device notice only; every mutation refused | catalog removes packages by name |
| Reset to stock without WRITE_SETTINGS and without a key | Fn, side key, QS tile report NEEDS_PERMISSION; snackbar asks for "Modify system settings" or pairing | two routes, neither available |
| Reset to stock with a key stored but debugging off | backlight step reports SUCCESS although the queued 30000 write cannot land; Fn/side key/QS steps fail as FAILED | the backlight step only checks that a key exists; the flags are cleared regardless so nothing re-arms it |
| Reset to stock does not touch the bloat journal, density, tweaks or the grants | they stay as set | each toolbox screen has its own reset; the 1.1.0 note promising otherwise is wrong |
| Backup and restore | the pairing key, journal and pending revert are not in the backup | they live in separate preferences files the backup writer does not read |
| `pm grant` for WRITE_SECURE_SETTINGS | outcome judged by re-checking the permission, not the shell result | the command prints nothing on success |
| Notification permission denied on Android 13+ | the watcher still arms; the code never arrives; the card warns | the code is delivered as a notification, but arming must not wait for the permission |
| Do Not Disturb on during pairing | the card warns; the channel asks to bypass DND but Android ignores that without policy access | the pairing code is time-limited |
| Foreground service start refused (background restriction) | the same notification is posted as a plain notification | the watcher must still be visible |
| Setup card left while unpaired | the watcher keeps running | stopping it as the user walks to Wireless debugging is how they arrive at "Pair device" with nothing listening |
| Vendor side-key slot holds a value with shell metacharacters | not captured, never written; restore drops such a capture | the restore path feeds these strings to a shell |
| Broker port rotates mid-session | the next call rediscovers; a call in flight fails with a socket error and is recorded | nothing about the port is cached |
| Android below 11 | discovery returns null immediately; everything reports NO_SERVICE / no key | Wireless debugging does not exist |

## 22. Test cases

Encodable without a device: catalog rules, journal and pending-revert serialization, parsing
of `wm density` and the census output, the parcel parser, the verdict decision table, the
density range, the animation snapping, and the reset-to-stock outcome aggregation.

| # | Input | Expected |
|---|---|---|
| T1 | removable? for each of the 12 denylisted names | false for all |
| T2 | removable? for `com.google.android.projection.gearhead.agui.overlay`, `com.agui.google.android.wifi.resources.overlay`, `com.android.systemui`, `com.android.phone`, `com.google.android.gms`, `com.android.dialer`, `com.android.mms` | false for all |
| T3 | removable? for `com.whatsapp`, `brobata.physiboard`, empty string, `com.agui.somethingInvented` | false |
| T4 | removable? for every one of the 28 catalog entries | true; and no entry is also denied |
| T5 | catalog entries | 28 unique package names; order is Factory, Vendor, Hardware tiers, alphabetical by label within a tier |
| T6 | entries in the Background killers preset | exactly the 6 packages listed in 12.3 |
| T7 | vendor namespace? for `com.agui.x`, `com.devices116`, `com.tiqiaa.icontrol`, `com.whatsapp` | true, true, true, false |
| T8 | catalogued? for `com.agui.overlay.foo`, `com.android.fmradio`, `com.agui.newthing` | true (pattern), true (pattern), false |
| T9 | census text `__E__\npackage:a\npackage:b\n__D__\npackage:c\n__U__\npackage:a\npackage:b\npackage:c\npackage:d\n` with catalog {a, c, d, e} | a Active, c Disabled, d Uninstalled, e Absent |
| T10 | disable a package with prior state ACTIVE, command succeeds | journal has one record {pkg, prev ACTIVE, action DISABLED, at > 0} |
| T11 | disable, command fails | journal has no record; result Failed with the broker's last error |
| T12 | disable the same package twice | one journal record, the later timestamp |
| T13 | journal string `[{"pkg":"x","prev":"BOGUS","action":"BOGUS","at":5}]` | one record, prev ACTIVE, action DISABLED |
| T14 | journal string `not json` | empty list |
| T15 | mutate on a non-Titan profile | Refused "This catalog is for the Titan 2 Elite only" before any journal write |
| T16 | mutate with name `com.agui.game; rm -rf /` | Refused "Not a package name" |
| T17 | `wm density` output `Physical density: 300` | physical 300, current 300, not overridden |
| T18 | output `Physical density: 300\nOverride density: 260` | physical 300, current 260, overridden |
| T19 | range for physical 300 | 180..420 |
| T20 | apply 170 with physical 300 | Failed "Outside the safe range"; nothing armed |
| T21 | apply 260, command succeeds | pending record `{"id":"display_density","apply":"wm density 260","revert":"wm density reset"}` present; Applied |
| T22 | apply 260, command fails | no pending record; Failed |
| T23 | keep | pending record removed |
| T24 | revert now with a pending record, revert command succeeds | record removed, true |
| T25 | revert now with no pending record | true, nothing sent |
| T26 | tweak read lines `0.5`, `null`, `1` | animation Fast, history off, one-handed on |
| T27 | tweak read line `0.7` | Fast (nearest of 0, 0.5, 1) |
| T28 | tweak read with empty output | null state (screen shows not-paired notice) |
| T29 | set toggle off | the line sent is `settings delete secure <key>`, not a write of 0 |
| T30 | reset all tweaks | one line: three `settings put global … 1.0` then two `settings delete secure …` |
| T31 | parcel `Result: Parcel(00000000 00000002 0031002d '....-.1....')` | `-1` |
| T32 | parcel `Result: Parcel(00000000 00000005 00300033 00300030 00000030 '…')` | `30000` |
| T33 | parcel with count 0, or count 65, or fewer words than the count needs | null |
| T34 | verdict decision: no key | NOT_PAIRED without discovery |
| T35 | key, `adb_wifi_enabled`=0 | WIRELESS_DEBUGGING_OFF without discovery |
| T36 | key, debugging on, discovery null | NO_SERVICE |
| T37 | key, debugging on, port found, connect refused | REJECTED |
| T38 | key, debugging on, port found, echo succeeds | OK |
| T39 | verdict cached 5 s ago, non-forced refresh | no new check |
| T40 | verdict cached 11 s ago | new check |
| T41 | invalidate, then refresh | new check regardless of age |
| T42 | pairing attempt with no prior key fails | key entry removed |
| T43 | pairing attempt with a prior key fails | key entry kept |
| T44 | reset result all SUCCESS | "Device settings restored to stock." |
| T45 | reset result with one NEEDS_PERMISSION and others SUCCESS | needs-permission message |
| T46 | reset result with one FAILED, none NEEDS_PERMISSION | partial message |
| T47 | Fn revert with captured enable=1, function=UNSET | writes enable 1, function 0 |
| T48 | Fn revert never captured | writes 0 and 0 |
| T49 | side-key safe value? for `com.google.android.apps.bard.MainActivity`, `a$b`, `x; rm`, 257-char string | true, true, false, false |
| T50 | blocker with key and debugging on | null; with key and debugging off | `wireless_debugging_off`; no key | `not_paired` |
| T51 | key mapping Fn row with enable=1, function=1, speech on | "Acts as Ctrl · hold to dictate" |
| T52 | orange key row with package = app and short press `com.x.FooActivity` | "tap: FooActivity · hold: the assistant, listening" |

## 23. Keep / Drop for 3.0

| Item | Verdict | Reasoning |
|---|---|---|
| Vendored ADB pairing and connect client | keep | Apache, unchanged; the only root-free route to shell |
| `embedded_adb` file, `adbkey` key, encryption scheme, key name `physiboard` | keep, byte-for-byte | losing the pairing on upgrade is the first complaint |
| Pairing from a notification with inline reply | keep | Android's pairing dialog and the app cannot be on screen together |
| Watcher armed on card appearance, not on a button | keep | the button's job is to leave the app |
| Verified verdict shared process-wide, 10 s freshness, 1.5 s polling of `adb_wifi_enabled` | keep | two screens disagreeing was a real bug |
| Broker lock serializing discovery | keep | overlapping discovery fails silently |
| Rediscover the port on every call | keep | the port rotates, sometimes once a second |
| Privileged setup pass at pairing, IME start and backlight screen | keep | the IME is what survives boot on this ROM |
| Persisted step outcomes and debug export section | keep | release builds have no logs |
| Record outcomes for the overlay and ring steps too | keep, and fix | today they only log |
| Pending density revert checked at IME start | keep, and fix | the record exists for this and nothing reads it |
| Reset to stock | keep | the only uninstall hook |
| Reset to stock also restoring the bloat journal, density and tweaks | undecided | promised by the 1.1.0 note, never built; harmless to add |
| Backlight step reporting SUCCESS on a queued write | drop | await the write and report the truth |
| Remove bloat, catalog, denylist, patterns, journal, presets | keep | the reason the toolbox exists; the denylist is the tripwire |
| Firmware pin warning | keep, and wire it | the check exists unused |
| Screen density with the 15 s auto-revert | keep | the one safety model that works when the screen is unreadable |
| System tweaks (animation, history, one-handed) | keep | one settings line each |
| Adaptive Connectivity rows as a fourth tweak | undecided | proven on one phone; would need the same delete-to-restore shape |
| USB unlocked-function fix as a tweak | undecided | pending road test; touches a store `settings` cannot see |
| Key mapping inventory | keep | read-only, no broker, cheap |
| Shizuku-app status probe (Connected / NotAuthorized / NotConnected) | drop | 2.x still compiles a probe for the separate Shizuku app; nothing on a Titan uses it since 0.86 |
| Android below 11 guards | drop | the Titan 2 Elite ships Android 16 |
| Non-Titan device gate on the catalog | keep | costs nothing and the catalog removes by name |
| Backup of the pairing key and journal | drop (leave out) | the key is device-bound to the Keystore anyway; the journal describes one phone |

## 24. Provenance

- app/src/main/java/brobata/physiboard/inputmethod/EmbeddedAdbShell.kt
- app/src/main/java/brobata/physiboard/inputmethod/PrivilegedSetup.kt
- app/src/main/java/brobata/physiboard/inputmethod/PrivilegedDiagnostics.kt
- app/src/main/java/brobata/physiboard/inputmethod/KeyboardBacklightManager.kt
- app/src/main/java/brobata/physiboard/inputmethod/ScreenTrackpadSetup.kt (overlay grant only)
- app/src/main/java/brobata/physiboard/inputmethod/DeviceSpecific.kt (device profile check only)
- app/src/main/java/brobata/physiboard/ShizukuStatus.kt
- app/src/main/java/brobata/physiboard/ui/BrokerStatus.kt
- app/src/main/java/brobata/physiboard/SystemChangeManager.kt
- app/src/main/java/brobata/physiboard/VendorSideKeyManager.kt
- app/src/main/java/brobata/physiboard/NavModeSettingsScreen.kt (Fn to Ctrl apply and revert only)
- app/src/main/java/brobata/physiboard/KeyboardBacklightTileService.kt (write path only)
- app/src/main/java/brobata/physiboard/ring/NotificationRingSetup.kt
- app/src/main/java/brobata/physiboard/ring/RingBacklight.kt (grant only)
- app/src/main/java/brobata/physiboard/toolbox/BloatCatalog.kt
- app/src/main/java/brobata/physiboard/toolbox/PackageRemover.kt
- app/src/main/java/brobata/physiboard/toolbox/RemovalJournal.kt
- app/src/main/java/brobata/physiboard/toolbox/RevertibleChange.kt
- app/src/main/java/brobata/physiboard/toolbox/DisplayDensity.kt
- app/src/main/java/brobata/physiboard/toolbox/SystemTweaks.kt
- app/src/main/java/brobata/physiboard/toolbox/KeyInventory.kt
- app/src/main/java/brobata/physiboard/BloatRemoverScreen.kt
- app/src/main/java/brobata/physiboard/DisplayDensityScreen.kt
- app/src/main/java/brobata/physiboard/SystemTweaksScreen.kt
- app/src/main/java/brobata/physiboard/KeyMappingScreen.kt
- app/src/main/java/brobata/physiboard/SmartBacklightScreen.kt
- app/src/main/java/brobata/physiboard/DeviceSetupCard.kt
- app/src/main/java/brobata/physiboard/SettingsAdvancedSection.kt
- app/src/main/java/brobata/physiboard/MaintenanceSettingsRows.kt
- app/src/main/java/brobata/physiboard/SettingsScreen.kt (toolbox hub and navigation only)
- app/src/main/java/brobata/physiboard/MainActivity.kt (home tile badge only)
- app/src/main/java/brobata/physiboard/DiagnosticsScreen.kt (privileged export section only)
- app/src/main/java/brobata/physiboard/SettingsManager.kt (keys, sentinels, Titan baseline only)
- app/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt
- app/src/main/java/moe/shizuku/manager/adb/EmbeddedAdbInit.kt
- app/src/main/java/moe/shizuku/manager/adb/AdbMdns.kt
- app/src/main/java/moe/shizuku/manager/adb/AdbClient.kt (timeouts only)
- app/src/main/java/moe/shizuku/manager/adb/AdbKey.kt (storage format only)
- app/src/main/java/moe/shizuku/manager/adb/AdbPairingClient.kt (constants only)
- app/src/main/java/moe/shizuku/manager/adb/AdbException.kt
- app/src/main/java/moe/shizuku/manager/adb/NOTICE
- app/src/main/AndroidManifest.xml (permissions and the pairing service)
- app/src/main/res/values/strings.xml
- app/src/main/res/values/adb_pairing_strings.xml
- app/src/test/java/brobata/physiboard/toolbox/BloatCatalogTest.kt
- docs/spec/README.md
- docs/plans/t2e-toolbox.md
- docs/plans/rebuild-from-scratch.md
- docs/titan2elite/DEVICE.md
- docs/titan2elite/BACKLIGHT.md
- PHYSIBOARD_CHANGES.md
- ~/.claude/projects/-home-disdiqqq-projects-pastiera/memory/per-app-dpi-not-possible.md
- ~/.claude/projects/-home-disdiqqq-projects-pastiera/memory/titan-wifi-drops-adaptive-connectivity.md
- ~/.claude/projects/-home-disdiqqq-projects-pastiera/memory/titan-android-auto-usb-chooser.md
- ~/.claude/projects/-home-disdiqqq-projects-pastiera/memory/titan-adbd-usb-bind-loop.md
- ~/.claude/projects/-home-disdiqqq-projects-pastiera/memory/phone-adb-over-tailscale.md
