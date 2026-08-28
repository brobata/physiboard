package brobata.physiboard.ring

import android.app.Notification

/**
 * Decides which notifications are allowed to light the ring.
 *
 * Kept free of Android services so it can be tested as plain code: the listener hands it the
 * few facts it needs and acts on the answer. The rules are conservative on purpose — the ring
 * lights the screen, and a ring that lights for a download progress bar or a "running in the
 * background" notice is worse than no ring at all.
 */
object NotificationRingPolicy {

    /** The facts about a notification that the decision depends on. */
    data class Candidate(
        val packageName: String,
        val flags: Int,
        val isClearable: Boolean,
        val priority: Int,
        val color: Int
    )

    /** Why a notification was ignored. */
    enum class Skip { OWN_APP, ONGOING, GROUP_SUMMARY, NOT_CLEARABLE, SILENT }

    /** The green of the ring in the reference photo; used when an app declares no colour. */
    const val DEFAULT_COLOR: Int = 0xFF34C759.toInt()

    /** Null when the notification should light the ring; otherwise the reason it should not. */
    fun skipReason(candidate: Candidate, ownPackage: String): Skip? = when {
        candidate.packageName == ownPackage -> Skip.OWN_APP
        candidate.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0 ->
            Skip.ONGOING
        candidate.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> Skip.GROUP_SUMMARY
        !candidate.isClearable -> Skip.NOT_CLEARABLE
        candidate.priority <= Notification.PRIORITY_MIN -> Skip.SILENT
        else -> null
    }

    /**
     * The colour the ring should take for a notification. Apps that set a brand colour get
     * it; apps that set none, or set one too dark to read on a black screen, get the default.
     */
    fun ringColor(notificationColor: Int, fallback: Int = DEFAULT_COLOR): Int {
        if (notificationColor == 0) return fallback
        return if (relativeLuminance(notificationColor) < 0.12) fallback else notificationColor
    }

    /** WCAG relative luminance, written out so the test suite does not need android.graphics. */
    private fun relativeLuminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
