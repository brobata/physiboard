# Titan 2 Elite keyboard backlight — how the "always on in the dark" feature works

*Reverse-engineered and verified on-device 2026-08-19 (build V02.00.02). Narrative
version: published artifact "Cracking the Titan Backlight".*

## Problem
Stock backlight dies 30s after last keystroke; the stock timeout setting caps at 30s.
No "keep on" option exists in the UI.

## Dead ends (what a normal app / adb-shell CANNOT do)
- `settings put global agui_keyboard_background_light 1` — master on/off; the 30s timer still wins.
- `keyboard_brightness_timeout` / `agui_keyboard_led_timer` — ignored; real config lives in
  root-only `/data/system/agui_settings_data.xml` which the settings keys only fall back to.
- Stock timeout UI — hard-capped at 30s.
- `/sys/devices/platform/keypad_led/keyled_brightness` — SELinux system-only.

## The mechanism
Vendor `KeyboardLightController` (in core `/system/framework/services.jar`) exposes
`keyboardLightTest(String)` on binder service **`agui_functional_service`, transaction 7**:
- **`"1"` = enter test mode = HOLD** the LED on, bypassing the 30s timeout.
  It does NOT power on a dark LED — it only holds a lit one.
- **`"0"` = leave test mode**, then broadcast **`agui.action.CLOSE_KEYBOARD_LIGHT`** powers it off.

The vendor already lights the LED on screen-on and on keypress. So the policy is:
arm the hold whenever **screen-on + ambient lux below threshold**; the vendor's own
screen-on/keypress lights it, and test mode keeps it lit. Bright room or screen-off →
leave test mode + close broadcast; stock 30s behavior resumes.

### Verified shell commands
```sh
# ON  (hold a lit LED past 30s)
service call agui_functional_service 7 s16 1
# OFF (release + power down)
service call agui_functional_service 7 s16 0
am broadcast -a agui.action.CLOSE_KEYBOARD_LIGHT
```
Transaction 7 was found empirically (interface AIDL not on-device): probing codes 1–30 with
`service call … N s16 1`, code 7 was the one returning a clean boolean `true`.

## Privilege / Shizuku
The binder call needs shell uid; a normal app can't. Reached via **Shizuku** (`Shizuku.newProcess`,
root-free), off the IME main thread. Play Store refuses Shizuku on this device (stale compat flag) —
sideload from `RikkaApps/Shizuku` GitHub releases. Shizuku stops on reboot; restart via the app's
"Start via Wireless debugging", or over adb:
```sh
adb shell "$(dirname $(pm path moe.shizuku.privileged.api|sed s/package://))/lib/arm64/libshizuku.so"
```
(The `app_process … ServerStarter` route aborts; the `libshizuku.so` starter works.)
Shizuku app-permission is server-tracked, granted via `Shizuku.requestPermission` (in-app button),
NOT via `pm grant`.

## Implementation
- `app/src/main/java/it/palsoftware/pastiera/inputmethod/KeyboardBacklightManager.kt` — screen
  receiver + light sensor + lux threshold; arms/releases the hold; shell calls off-thread; no-op
  without Shizuku or on non-Titan devices.
- Wired in `PhysicalKeyboardInputMethodService` onCreate/onDestroy/onKeyActivity.
- `SmartBacklightScreen.kt` — enable toggle, darkness-threshold slider with live lux readout,
  Shizuku status + grant button. Settings under Appearance. Default off.
