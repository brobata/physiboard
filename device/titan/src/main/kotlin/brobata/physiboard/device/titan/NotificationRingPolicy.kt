package brobata.physiboard.device.titan

/** The facts about one posted notification the ring policy needs. spec: device-backlight-ring.md SS5.3. */
data class NotificationRingCandidate(
    val packageName: String,
    val isOngoingOrForegroundService: Boolean,
    val isGroupSummary: Boolean,
    val isClearable: Boolean,
    val priority: Int,
)

/** Why a notification never rings. spec: SS5.3's table, evaluated in this order, first match wins. */
enum class RingSkipReason { OWN_APP, ONGOING, GROUP_SUMMARY, NOT_CLEARABLE, SILENT }

/**
 * Which posted notifications qualify for the notification ring. This exists only because the ring
 * itself only exists on the Titan (there is no always-on display to fall back to, D30 in
 * device-backlight-ring.md); a device with AOD would never need this policy at all.
 *
 * spec: device-backlight-ring.md SS5.3.
 */
object NotificationRingPolicy {

    private const val OWN_PACKAGE = "brobata.physiboard"

    /** spec: SS5.3 ("priority is minimum (-2) or lower"). */
    private const val SILENT_PRIORITY_THRESHOLD = -2

    /** Returns the reason to skip, or null when the notification rings. */
    fun evaluate(candidate: NotificationRingCandidate): RingSkipReason? = when {
        candidate.packageName == OWN_PACKAGE -> RingSkipReason.OWN_APP
        candidate.isOngoingOrForegroundService -> RingSkipReason.ONGOING
        candidate.isGroupSummary -> RingSkipReason.GROUP_SUMMARY
        !candidate.isClearable -> RingSkipReason.NOT_CLEARABLE
        candidate.priority <= SILENT_PRIORITY_THRESHOLD -> RingSkipReason.SILENT
        else -> null
    }
}
