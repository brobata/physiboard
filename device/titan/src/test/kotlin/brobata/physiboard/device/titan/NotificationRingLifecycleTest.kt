package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.2, SS5.7.1, SS10 test cases T42-T44. */
class NotificationRingLifecycleTest {

    private fun source(key: String, pkg: String, color: Int, at: Long) = RingSource(key, pkg, color, at)

    @Test
    fun `T42 - a second source recolours and stacks icons, removing the first leaves the second showing`() {
        val started = NotificationRingLifecycle.onNotificationPosted(
            RingSessionState(), source("a", "pkg.a", 0xA, 0), screenInteractive = false, pocketCovered = false,
        )
        var state = RingSessionState(assertIs<RingArrivalDecision.Start>(started).sources)

        val arrival = NotificationRingLifecycle.onNotificationPosted(state, source("b", "pkg.b", 0xB, 10), screenInteractive = false, pocketCovered = false)
        val updated = assertIs<RingArrivalDecision.UpdateExisting>(arrival)
        state = RingSessionState(updated.sources)
        assertEquals(0xB, NotificationRingLifecycle.currentColorArgb(state))
        assertEquals(listOf("pkg.a", "pkg.b"), NotificationRingLifecycle.icons(state))

        state = NotificationRingLifecycle.onNotificationRemoved(state, "a")
        assertEquals(listOf("pkg.b"), NotificationRingLifecycle.icons(state))
        assertTrue(!NotificationRingLifecycle.isFinished(state))
    }

    @Test
    fun `T43 - removing the only source finishes the ring`() {
        var state = RingSessionState(listOf(source("a", "pkg.a", 0xA, 0)))
        state = NotificationRingLifecycle.onNotificationRemoved(state, "a")
        assertTrue(NotificationRingLifecycle.isFinished(state))
    }

    @Test
    fun `T44 - four distinct packages show only the last three icons`() {
        val state = RingSessionState(
            listOf(
                source("1", "pkg.1", 0x1, 0),
                source("2", "pkg.2", 0x2, 1),
                source("3", "pkg.3", 0x3, 2),
                source("4", "pkg.4", 0x4, 3),
            ),
        )
        assertEquals(listOf("pkg.2", "pkg.3", "pkg.4"), NotificationRingLifecycle.icons(state))
    }

    @Test
    fun `an interactive screen or a covered pocket both skip starting a new ring`() {
        val interactive = NotificationRingLifecycle.onNotificationPosted(RingSessionState(), source("a", "pkg.a", 0xA, 0), screenInteractive = true, pocketCovered = false)
        assertIs<RingArrivalDecision.Skip>(interactive)
        val covered = NotificationRingLifecycle.onNotificationPosted(RingSessionState(), source("a", "pkg.a", 0xA, 0), screenInteractive = false, pocketCovered = true)
        assertIs<RingArrivalDecision.Skip>(covered)
    }

    @Test
    fun `a real ring's timer expiry only releases keep-screen-on, a demo ring finishes`() {
        val real = NotificationRingLifecycle.onEndTrigger(RingEndTrigger.TIMER_EXPIRY, isDemoMode = false)
        assertEquals(RingEndDecision(finishes = false, releasesKeepScreenOn = true), real)
        val demo = NotificationRingLifecycle.onEndTrigger(RingEndTrigger.TIMER_EXPIRY, isDemoMode = true)
        assertEquals(RingEndDecision(finishes = true, releasesKeepScreenOn = true), demo)
    }

    @Test
    fun `touch, key down, screen off and unlock all finish the ring outright`() {
        for (trigger in listOf(RingEndTrigger.TOUCH, RingEndTrigger.KEY_DOWN, RingEndTrigger.SCREEN_OFF, RingEndTrigger.UNLOCK, RingEndTrigger.LAST_SOURCE_REMOVED)) {
            val decision = NotificationRingLifecycle.onEndTrigger(trigger, isDemoMode = false)
            assertEquals(RingEndDecision(finishes = true, releasesKeepScreenOn = true), decision)
        }
    }
}
