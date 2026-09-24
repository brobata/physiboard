package brobata.physiboard.device.privileged.setup

import brobata.physiboard.device.titan.BacklightWrite
import brobata.physiboard.device.titan.KeyboardBacklight

/**
 * Who the running app is, as the shell lines need it. The applicationId differs between the
 * release install (`brobata.physiboard`) and the sideload build (`brobata.physiboard.dev3`), and
 * the ring listener compiles in this module's package, so neither may be a literal: a grant
 * addressed to the wrong package lands nowhere and reports success (the 2.x tile bug in another
 * form, device-backlight-ring.md SS2.2 "Known bug").
 */
data class AppIdentity(val packageName: String, val ringListenerComponent: String)

/**
 * Every shell line this module sends, rendered in one place so a test can pin the exact text
 * and no call site builds a command by hand.
 *
 * spec: broker-privileged-toolbox.md SS7 (the setup pass), SS9 (every system-level write),
 * SS10 (the reverts); device-backlight-ring.md SS3.1 (the vendor transactions), SS5.9 (the
 * ring's grants).
 */
object ShellLines {
    private const val VENDOR = KeyboardBacklight.VENDOR_SERVICE_NAME
    private const val TIMEOUT_KEY = KeyboardBacklight.VENDOR_TIMEOUT_KEY

    /** spec: device-backlight-ring.md SS3.1 (SET is transaction 2, a String16 key and value). */
    fun vendorTimeout(valueMs: Int): String =
        "service call $VENDOR ${KeyboardBacklight.VENDOR_TRANSACTION_SET} s16 \"$TIMEOUT_KEY\" s16 \"$valueMs\""

    /** spec: device-backlight-ring.md SS3.1 (GET is transaction 1). */
    val vendorTimeoutRead: String =
        "service call $VENDOR ${KeyboardBacklight.VENDOR_TRANSACTION_GET} s16 \"$TIMEOUT_KEY\""

    /** spec: SS9 row `agui_keyboard_background_light` through the broker (the reset's fallback route). */
    fun masterSwitchPut(value: Int): String = "settings put global ${KeyboardBacklight.MASTER_SWITCH_KEY} $value"

    /** The 3.0 reset for a never-captured tile value: unset, not 0 (device-backlight-ring.md SS11 Keep/Drop). */
    val masterSwitchDelete: String = "settings delete global ${KeyboardBacklight.MASTER_SWITCH_KEY}"

    /** spec: SS7 step 2. */
    fun overlayGrant(identity: AppIdentity): String = "appops set ${identity.packageName} SYSTEM_ALERT_WINDOW allow"

    /** spec: SS7 step 3, one line for the three grants. */
    fun ringGrants(identity: AppIdentity): String = joined(
        "cmd notification allow_listener ${identity.ringListenerComponent}",
        "appops set ${identity.packageName} USE_FULL_SCREEN_INTENT allow",
        "appops set ${identity.packageName} POST_NOTIFICATION allow",
    )

    /** spec: SS10 step 5. */
    fun ringRevoke(identity: AppIdentity): String = joined(
        "cmd notification disallow_listener ${identity.ringListenerComponent}",
        "appops set ${identity.packageName} USE_FULL_SCREEN_INTENT default",
    )

    /** spec: SS7 step 4; `pm grant` prints nothing on success, so the outcome is a permission re-check. */
    fun secureSettingsGrant(identity: AppIdentity): String = "pm grant ${identity.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** spec: SS10 steps 1 and 4 (`settings put system` through the broker). */
    fun systemPut(key: String, value: String): String = "settings put system $key $value"

    /** The one rendering of a `:device:titan` decision as shell text. */
    fun render(write: BacklightWrite): String = when (write) {
        is BacklightWrite.VendorTimeoutMs -> vendorTimeout(write.valueMs)
        is BacklightWrite.MasterSwitch -> masterSwitchPut(write.value)
    }

    /** spec: SS6 ("Callers that batch several settings writes join them with `; ` into one line so one discovery serves all of them"). */
    fun joined(vararg lines: String): String = lines.joinToString("; ")
}

/**
 * The validation every side-key value passes before it is written or restored. The restore path
 * feeds values read back from `Settings.System` into a shell line, and any app holding
 * `WRITE_SETTINGS` could otherwise plant shell metacharacters there.
 *
 * spec: broker-privileged-toolbox.md SS9 ("Side-key values are validated..."); T49.
 */
object SideKeyValue {
    private const val MAX_LENGTH = 256
    private val SAFE = Regex("[A-Za-z0-9_][A-Za-z0-9_.$]*")

    fun isSafe(value: String): Boolean = value.length <= MAX_LENGTH && SAFE.matches(value)
}
