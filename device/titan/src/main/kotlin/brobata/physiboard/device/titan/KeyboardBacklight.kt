package brobata.physiboard.device.titan

/**
 * A write this module has decided should happen to the phone. Never performed here: the module
 * cannot touch a file, a binder service, or `Settings`; a later module (the broker or an
 * in-process `Settings` call) executes it. Keeping the effect as data is what makes the decision
 * logic in [KeyboardBacklight] testable on the JVM.
 *
 * spec: device-backlight-ring.md SS2, SS3.1, SS5.8.
 */
sealed class BacklightWrite {
    /**
     * `Settings.Global agui_keyboard_background_light`, writable in-process with
     * WRITE_SECURE_SETTINGS and effective live. spec: SS2 point 3, D14.
     */
    data class MasterSwitch(val value: Int) : BacklightWrite() {
        init {
            require(value == 0 || value == 1) { "master switch is 0 or 1, was $value" }
        }
    }

    /**
     * The vendor's own settings store, `agui_functional_service` transaction 2 (SET), key
     * `keyboard_brightness_timeout`. `-1` never turns the light off; the stock value is `30000`
     * ms. Plain `settings put global` has no effect on this key: it must go through the binder
     * service. spec: SS2 point 4, SS3.1, D14, D15.
     */
    data class VendorTimeoutMs(val valueMs: Int) : BacklightWrite()
}

/** One outstanding "the ring turned the keyboard off on the user's behalf" record. spec: SS5.8. */
data class BacklightSuppressionRecord(val priorSwitchValue: Int, val capturedAtMs: Long)

/** What should happen when the notification ring wants the keyboard dark. spec: SS5.8 "Suppress". */
sealed class BacklightSuppressionDecision {
    data class Suppress(val record: BacklightSuppressionRecord, val write: BacklightWrite.MasterSwitch) : BacklightSuppressionDecision()

    /** Nothing to do: a precondition failed, a suppression is already outstanding, or the user already had the light off. */
    object Skip : BacklightSuppressionDecision()
}

/** What should happen when a suppression needs to be undone. spec: SS5.8 "Restore". */
sealed class BacklightRestoreDecision {
    data class Restore(val write: BacklightWrite.MasterSwitch) : BacklightRestoreDecision()

    /** No record exists; a no-op, "no claim left" (SS9 edge case table). */
    object NoRecord : BacklightRestoreDecision()

    /** The record must survive so a later permission grant can still heal it. */
    object KeepRecordPermissionMissing : BacklightRestoreDecision()
}

/**
 * The keyboard backlight's vendor facts and the pure decisions this module is allowed to make
 * about it: which write applies the Smart backlight switch, and when the notification ring
 * should darken or restore the keyboard.
 *
 * spec: device-backlight-ring.md SS2 (the phone's stock behaviour and the vendor keys), SS3 (Smart
 * backlight), SS5.8 (keeping the keyboard dark for the ring), D6, D10-D19.
 */
object KeyboardBacklight {

    // Vendor facts: keys, transactions, sysfs. Documented here, never written by this module. -----

    const val MASTER_SWITCH_KEY = "agui_keyboard_background_light"

    /** Unset reads as on. spec: SS2 point 3. */
    const val MASTER_SWITCH_UNSET_VALUE = 1

    const val VENDOR_SERVICE_NAME = "agui_functional_service"
    const val VENDOR_TIMEOUT_KEY = "keyboard_brightness_timeout"

    /** GET(key). spec: SS3.1, D16. */
    const val VENDOR_TRANSACTION_GET = 1

    /** SET(key, value). spec: SS3.1, D15. */
    const val VENDOR_TRANSACTION_SET = 2

    const val ALWAYS_ON_TIMEOUT_MS = -1
    const val STOCK_TIMEOUT_MS = 30_000

    /** SELinux system-only; the app never writes it, kept here only as a documented fact. D13. */
    const val KEYLED_BRIGHTNESS_SYSFS_PATH = "/sys/devices/platform/keypad_led/keyled_brightness"

    /** Vendor keys the app reads or writes nowhere. Recorded for the fact table only, per Keep/Drop ("drop from the app, keep in the facts"). */
    object UnusedVendorKeys {
        const val LED_BRIGHTNESS = "keyboard_led_brightness"
        const val LED_AUTO_SWITCH = "keyboard_led_auto_switch"
        const val LED_TIMER = "agui_keyboard_led_timer"
        const val CLOSE_KEYBOARD_LIGHT_BROADCAST = "agui.action.CLOSE_KEYBOARD_LIGHT"

        /** `keyboardLightTest(String)`, the removed light-sensor feature's hold mode. D17. Dropped for 3.0 (section 4 Keep/Drop). */
        const val VENDOR_TRANSACTION_KEYBOARD_LIGHT_TEST = 7
    }

    /** How long an outstanding suppression may wait for the ring to take ownership before healing itself. spec: SS5.8 point 4. */
    const val SUPPRESSION_ORPHAN_TIMEOUT_MS = 20_000L

    // Smart backlight: which timeout value the switch implies. spec: SS3.1, SS3.3. -----------------

    /** spec: SS3.1 ("Always on" writes -1; "Stock" writes the vendor's own 30000). */
    fun timeoutWriteFor(smartBacklightEnabled: Boolean): BacklightWrite.VendorTimeoutMs =
        BacklightWrite.VendorTimeoutMs(if (smartBacklightEnabled) ALWAYS_ON_TIMEOUT_MS else STOCK_TIMEOUT_MS)

    /**
     * spec: SS3.4 ("when the broker is reachable and the phone reported a value other than -1:
     * ... the backlight will time out until it is applied again"). The read-back is a raw device
     * string (from [BacklightParcel.parse]); anything other than the always-on sentinel while the
     * feature should be on means a system update or another app reset it.
     */
    fun isTimeoutStale(smartBacklightEnabled: Boolean, deviceReadValue: String?): Boolean =
        smartBacklightEnabled && deviceReadValue != null && deviceReadValue != ALWAYS_ON_TIMEOUT_MS.toString()

    // The ring's "keep the keyboard dark" lever. spec: SS5.8. -----------------------------------

    /**
     * spec: SS5.8 "Suppress". Skipped entirely when the ring is off, `notification_ring_keyboard_dark`
     * is off, the permission is missing, or a suppression is already outstanding; also a no-op
     * (recording nothing) when the switch already reads 0, because that is the user's own choice
     * and there is nothing of ours to put back later.
     */
    fun decideSuppression(
        ringEnabled: Boolean,
        keyboardDarkEnabled: Boolean,
        hasWriteSecureSettingsPermission: Boolean,
        alreadySuppressed: Boolean,
        currentSwitchValue: Int?,
        nowMs: Long,
    ): BacklightSuppressionDecision {
        if (!ringEnabled || !keyboardDarkEnabled || !hasWriteSecureSettingsPermission || alreadySuppressed) {
            return BacklightSuppressionDecision.Skip
        }
        val effective = currentSwitchValue ?: MASTER_SWITCH_UNSET_VALUE
        if (effective == 0) return BacklightSuppressionDecision.Skip

        val record = BacklightSuppressionRecord(priorSwitchValue = effective, capturedAtMs = nowMs)
        return BacklightSuppressionDecision.Suppress(record, BacklightWrite.MasterSwitch(value = 0))
    }

    /**
     * spec: SS5.8 "Restore". A no-op when nothing was recorded; when the permission has since been
     * revoked the record is kept rather than dropped, "a later grant can heal it".
     */
    fun decideRestore(record: BacklightSuppressionRecord?, hasWriteSecureSettingsPermission: Boolean): BacklightRestoreDecision = when {
        record == null -> BacklightRestoreDecision.NoRecord
        !hasWriteSecureSettingsPermission -> BacklightRestoreDecision.KeepRecordPermissionMissing
        else -> BacklightRestoreDecision.Restore(BacklightWrite.MasterSwitch(value = record.priorSwitchValue))
    }

    /** spec: SS5.8 point 4: an outstanding suppression the ring never claimed heals itself after the orphan timeout. */
    fun isOrphaned(suppressedAtMs: Long, ringTookOwnership: Boolean, nowMs: Long): Boolean =
        !ringTookOwnership && (nowMs - suppressedAtMs) >= SUPPRESSION_ORPHAN_TIMEOUT_MS
}
