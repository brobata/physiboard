# Unihertz Titan 2 Elite — device profile (captured over ADB 2026-08-19)

Model: `Titan 2` (build `Titan 2 Elite_V02.00.02`), Android 16.

## Display
- Physical: **1080×1200** (override 1076×1200), density **300** → **~574dp × 640dp** window.
- Wide-short, near-square. Wider than any normal phone (360–430dp), shorter than all of them.
- Emulator profile: AVD `titan2elite` (hw.lcd 1080×1200 @ 300, hw.keyboard=yes, hw.mainKeys=yes).

## Physical keyboard
- Kernel device: **`TitanKey`** `/dev/input/event5`, i2c `6-0058`, vendor/product `0x2533`.
  Has `aw9523_power_ctrl` sysfs attr (AW9523 = 16-ch I2C LED driver / GPIO expander → key backlight).
- Companion devices: `touchPad` event4 (capacitive touch layer on keys), `fts_ts` event6 (touchscreen
  + gesture keys), `ff_key` event7 (scancode 0x00f9 = 249).
- Layout: `/system/usr/keylayout/TitanKey.kl` + `/system/usr/keychars/TitanKey.kcm`
  (copies in this dir).

### Scancode map (from TitanKey.kl)
| Scan | Android key |
|---|---|
| 16–25 | Q W E R T Y U I O P |
| 30–38, 14 | A S D F G H J K L, DEL |
| 56 | ALT_LEFT |
| 44–50 | Z X C V B N M |
| **253** | **AGUI_SYM** (Sym key, custom keycode) |
| 28 | ENTER |
| 42 / 54 | SHIFT_LEFT / SHIFT_RIGHT |
| 158 | BACK (hardware key) |
| 102 | HOME (hardware key) |
| 57 | SPACE |
| 580 | APP_SWITCH (hardware key) |
| **251** | **FUNC3** (the Fn key, custom keycode) |

No Ctrl in hardware — Fn→Ctrl is synthesized by the vendor layer from FUNC3 per user config.

### Fn event delivery model (measured 2026-08-19, kernel getevent + Pastiera recorder)
- Kernel reports Fn (0xfb) DOWN/UP instantly and normally.
- The vendor layer NEVER delivers the app-side initial down (`repeat=0`) or any KEY_UP for Fn.
- Holding Fn delivers auto-repeat KEYCODE_CTRL_LEFT events (`scan=251`, `repeat=1,2,…`,
  first one flagged FLAG_LONG_PRESS) every ~50ms, starting ~400ms into the hold
  (KeyRepeatTimeout=400ms, KeyRepeatDelay=50ms).
- A quick Fn tap delivers NOTHING to apps. Quick Fn+X chords deliver only the X key with
  `META_CTRL_ON` set — no Fn event at all.
- Consequence: app-side Fn hold detection must count the repeat burst (Pastiera patch v3,
  commit 5dbc1c9), not track down/up or repeatCount==0.

## Vendor key-config (plain `settings system` table — writable with WRITE_SETTINGS)
Schema per key `{fn,func1,func2,home,recent,shift_r,sym,...}`:
- `{key}_programmable_key_enable` = 0/1
- `{key}_programmable_key_function` = int enum (observed: fn=1 → Ctrl, sym=2)
- `{key}_shortcut_key_enable` = 0/1
- `{key}_{short,long,double}_press_activity` / `..._package` (e.g. `NoOperate`,
  `shortcut_function_home`, or a real activity like Gemini's)

### Keyboard backlight (spike results 2026-08-19)
Controller: `com.agui.server.functional.KeyboardLightController` in core `/system/framework/services.jar`
(copy pulled to scratchpad; NOT in agui-services.jar). Registered on ALS sensor `stk3a5x_als`.
Fields prove capability: `mLedBrightness`, `mAutoBrightnessAdjust`, `mBrightnessTimeout`,
`mCurrentLightValue`, `mScreenOnSync`, `mKeyboardLightTimer`, `mPowerOffRunnable`.
Hardware node: `/sys/devices/platform/keypad_led/keyled_brightness` (SELinux system-only; the
controller writes it — we never need to).

**All control keys are `Settings.Global` (writable with WRITE_SECURE_SETTINGS, granted to
brobata.physiboard):**
- `agui_keyboard_background_light` (0/1 master — confirmed live toggle works)
- `keyboard_led_brightness` (level; write accepted, visual confirmation pending)
- `keyboard_led_auto_switch` (0/1 — presumably ALS auto mode; write accepted)
- `keyboard_brightness_timeout`, `agui_keyboard_led_timer` (timeouts; unset on device = code defaults)
- Broadcast action exists: `agui.action.CLOSE_KEYBOARD_LIGHT`

All four keys were unset on the user device (defaults in code). Smart-backlight feature can be
built entirely on these keys — no root, no sysfs access needed.

## Vendor packages (system_ext)
- `com.agui.keyboard` — AguiKeyBoardShortcut.apk: FUNC3/AGUI_SYM translation layer.
- `com.agui.shortcutsettings` — AguiShortcutKey.apk: the "Shortcut keys" settings UI
  (`.ui.EntryAppActivity`, `.ui.SelectFunctionActivity`).
- `com.agui.spacebarkey` — AguiSpaceBarKey.apk.
- Stock IME still enabled: `com.iqqijni.bbkeyboard` (Kika). `show_ime_with_hard_keyboard=0`.

APK copies in this dir are for local interop analysis only — do not commit or redistribute
(dir should stay untracked; add to .gitignore if docs/ ever gets committed).

## ADB access
- Wireless debugging: `adb connect <device-ip>:<port>` (the port rotates per reboot/toggle;
  Wireless debugging must be re-enabled after each reboot). Discoverable via `adb mdns services`.
