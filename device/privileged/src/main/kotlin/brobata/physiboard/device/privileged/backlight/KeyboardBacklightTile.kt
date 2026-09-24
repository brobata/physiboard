package brobata.physiboard.device.privileged.backlight

import brobata.physiboard.core.settings.DeviceCaptures
import brobata.physiboard.device.titan.KeyboardBacklight

/** What one tap of the Quick Settings tile does: the value to write and the captures as they stand afterwards. */
data class TileTap(val writeValue: Int, val captures: DeviceCaptures)

/**
 * The Quick Settings "Keyboard light" tile's decisions, pure so the capture rules are pinned on
 * the JVM: the original is captured once and never overwritten (it is what reset-to-stock
 * restores), and a ring that currently holds the switch off hands its record to the tile so the
 * user is never left with a dead keyboard after a reset.
 *
 * spec: device-backlight-ring.md SS2.2 (steps 1, 3a to 3c); T32, T33.
 */
object KeyboardBacklightTile {

    /** spec: SS2.2 step 1 ("active when ... reads 1 (or is unset); inactive when it reads 0"). */
    fun isActive(currentValue: Int?): Boolean = (currentValue ?: KeyboardBacklight.MASTER_SWITCH_UNSET_VALUE) != 0

    /** spec: SS2.2 steps 3a to 3c. */
    fun decideTap(currentValue: Int?, captures: DeviceCaptures): TileTap {
        val captured = when {
            captures.qsBacklightPrevCaptured -> captures
            // 3b: a ring has the switch off on the user's behalf; its recorded prior is the real
            // original, and the ring's later restore then finds nothing to do.
            captures.ringBacklightPrevCaptured -> captures.copy(
                qsBacklightPrevCaptured = true,
                qsBacklightPrev = captures.ringBacklightPrev,
                ringBacklightPrevCaptured = false,
                ringBacklightPrev = null,
            )
            // 3a: the live value, or null as "the system has no value".
            else -> captures.copy(qsBacklightPrevCaptured = true, qsBacklightPrev = currentValue)
        }
        // 3c: the opposite of the current reading (1 becomes 0, 0 or unset becomes 1).
        val next = if (currentValue == 1) 0 else 1
        return TileTap(next, captured)
    }

    /** The tile's subtitle. spec: SS2.2 step 1. */
    fun subtitle(hasWriteSecureSettings: Boolean): String = if (hasWriteSecureSettings) "" else NEEDS_GRANT_SUBTITLE

    const val LABEL = "Keyboard light"
    const val NEEDS_GRANT_SUBTITLE = "Needs ADB grant"

    /** spec: SS2.2 step 2, shown as a long toast; [packageName] is the running app's own. */
    fun grantToast(packageName: String): String =
        "PhysiBoard needs WRITE_SECURE_SETTINGS. Grant once via ADB: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
