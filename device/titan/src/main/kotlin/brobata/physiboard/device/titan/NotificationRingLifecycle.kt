package brobata.physiboard.device.titan

/** One notification currently keeping the ring lit. spec: device-backlight-ring.md SS5.2, SS5.7.1 point 4. */
data class RingSource(val notificationKey: String, val packageName: String, val colorArgb: Int, val addedAtMs: Long)

/** Every notification the ring currently reflects, oldest first. An empty list means the ring is not showing. */
data class RingSessionState(val sources: List<RingSource> = emptyList())

/** What a newly posted, policy-qualifying notification should do to the ring. spec: SS5.2. */
sealed class RingArrivalDecision {
    /** The ring is already up: recolour to the new source, refresh icons, restart the timer. spec: SS5.2 point 4. */
    data class UpdateExisting(val sources: List<RingSource>) : RingArrivalDecision()

    /** No ring is up and the conditions to start one are met. spec: SS5.2 points 5-8. */
    data class Start(val sources: List<RingSource>) : RingArrivalDecision()

    /** The ring only answers a dark, uncovered screen. spec: SS5.2 points 5-6. */
    data class Skip(val reason: String) : RingArrivalDecision()
}

/** What ended the ring, or merely paused it. spec: SS5.7 "Ending". */
enum class RingEndTrigger { TOUCH, KEY_DOWN, SCREEN_OFF, UNLOCK, LAST_SOURCE_REMOVED, TIMER_EXPIRY }

/** [releasesKeepScreenOn] is true whenever the trigger should let the phone's own timeout take over. */
data class RingEndDecision(val finishes: Boolean, val releasesKeepScreenOn: Boolean)

/**
 * The notification ring's lifecycle as pure decisions over its waiting sources: what a new or
 * removed notification does to the ring, and what a given ending trigger does to it. Nothing here
 * touches a window, a sensor or a timer; a later module executes what is returned.
 *
 * spec: device-backlight-ring.md SS5.2 (arrival), SS5.7 (ending, icons).
 */
object NotificationRingLifecycle {

    /** spec: SS5.7.1 point 4 ("at most the last 3"). */
    const val MAX_ICONS = 3

    fun onNotificationPosted(state: RingSessionState, source: RingSource, screenInteractive: Boolean, pocketCovered: Boolean): RingArrivalDecision {
        if (state.sources.isNotEmpty()) {
            val updated = state.sources.filterNot { it.notificationKey == source.notificationKey } + source
            return RingArrivalDecision.UpdateExisting(updated)
        }
        if (screenInteractive) return RingArrivalDecision.Skip("screen_interactive")
        if (pocketCovered) return RingArrivalDecision.Skip("pocket_covered")
        return RingArrivalDecision.Start(listOf(source))
    }

    /** spec: SS5.2 ("For every removed notification..."), SS9 edge case ("Ring showing A; A removed -> ring finishes"). */
    fun onNotificationRemoved(state: RingSessionState, notificationKey: String): RingSessionState =
        state.copy(sources = state.sources.filterNot { it.notificationKey == notificationKey })

    fun isFinished(state: RingSessionState): Boolean = state.sources.isEmpty()

    /** spec: SS5.2 ("the ring takes the colour of the most recently added remaining source"). */
    fun currentColorArgb(state: RingSessionState): Int? = state.sources.lastOrNull()?.colorArgb

    /** spec: SS5.7.1 point 4 ("the distinct waiting packages, at most the last 3"). */
    fun icons(state: RingSessionState): List<String> = state.sources.map { it.packageName }.distinct().takeLast(MAX_ICONS)

    /**
     * spec: SS5.7 "Ending": touch, a key down, screen off and unlock all finish the ring outright;
     * expiry on a real ring only releases keep-screen-on so the phone's own timeout ends it dark
     * to dark, but on a demo ring (SS5.7.6) expiry finishes it directly.
     */
    fun onEndTrigger(trigger: RingEndTrigger, isDemoMode: Boolean): RingEndDecision = when (trigger) {
        RingEndTrigger.TIMER_EXPIRY -> RingEndDecision(finishes = isDemoMode, releasesKeepScreenOn = true)
        else -> RingEndDecision(finishes = true, releasesKeepScreenOn = true)
    }
}
