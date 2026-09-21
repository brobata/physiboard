package brobata.physiboard.device.titan

/**
 * The numbered Titan 2 / Titan 2 Elite hardware facts as data, so no other module ever needs to
 * hard-code a Unihertz constant. spec: keys-and-modifiers.md SS19; device-backlight-ring.md SS7.
 * Facts that belong to another module's own spec (broker pairing, D18/D21/D22; screen density,
 * D34) are left out of this table and live where they are used.
 */
object DeviceFacts {

    // Firmware the rest of the facts were checked against. D1 (backlight-ring), D14 (keys).
    const val MODEL_STRING = "Titan 2"
    const val BUILD_FINGERPRINT_SAMPLE = "Titan 2 Elite_V02.00.02"
    const val ANDROID_VERSION = 16

    // The built-in keyboard's input device identity. D2 (keys), D5 (backlight-ring).
    const val KEYBOARD_DEVICE_NAME = "TitanKey"
    const val KEYBOARD_DEVICE_PATH = "/dev/input/event5"
    const val KEYBOARD_I2C_ADDRESS = "6-0058"

    /** The spec gives one combined "vendor/product" value; it is not documented as two separate ids. D2, D5. */
    const val KEYBOARD_VENDOR_PRODUCT_ID = 0x2533

    const val KEYBOARD_POWER_CTRL_SYSFS_ATTR = "aw9523_power_ctrl"

    // Companion input devices sharing the phone with the keyboard. D13 (keys), D7 (backlight-ring).
    const val TOUCH_PAD_DEVICE_NAME = "touchPad" // event4: capacitive touch layer on the keys
    const val TOUCHSCREEN_DEVICE_NAME = "fts_ts" // event6: touchscreen plus gesture keys
    const val ORANGE_KEY_DEVICE_NAME = "ff_key" // event7: also carries Power (D13)

    // Screen geometry. D15 (keys), D2/D3 (backlight-ring).
    const val SCREEN_WIDTH_PX = 1080
    const val SCREEN_WIDTH_OVERRIDE_PX = 1076
    const val SCREEN_HEIGHT_PX = 1200
    const val SCREEN_DENSITY_DPI = 300
    const val SCREEN_WIDTH_DP_APPROX = 574
    const val SCREEN_HEIGHT_DP_APPROX = 640
    const val PANEL_SIZE_INCHES = 4.03
    const val PANEL_PPI = 401

    // Vendor packages and the one system setting that disables an on-screen keyboard. D12 (keys), D20 (backlight-ring).
    const val VENDOR_KEYBOARD_TRANSLATION_PACKAGE = "com.agui.keyboard"
    const val VENDOR_SHORTCUT_SETTINGS_PACKAGE = "com.agui.shortcutsettings"
    const val VENDOR_SPACEBAR_PACKAGE = "com.agui.spacebarkey"
    const val STOCK_IME_PACKAGE = "com.iqqijni.bbkeyboard"

    /** `show_ime_with_hard_keyboard` reads 0 on this device. D12 (keys), D20 (backlight-ring). */
    const val SHOW_IME_WITH_HARD_KEYBOARD = false

    // Things this ROM makes impossible, recorded so a later module never offers them.
    /** `config_dozeAlwaysOnDisplayAvailable` is compiled false; `doze_always_on` does nothing. D30. */
    const val ALWAYS_ON_DISPLAY_SUPPORTED = false

    /** `config_supportDoubleTapWake` is compiled false; no key or touch node carries a WAKE flag. D31. */
    const val DOUBLE_TAP_TO_WAKE_SUPPORTED = false

    /** `wm density` is global only; the only fake (switch on app focus) is too slow through the broker. D32. */
    const val PER_APP_DENSITY_SUPPORTED = false

    /** The AW9523 LED driver supports 256-step dimming but no userspace interface was ever found. D6 (backlight-ring). */
    const val KEYBOARD_BACKLIGHT_DIMMING_SUPPORTED = false
}
