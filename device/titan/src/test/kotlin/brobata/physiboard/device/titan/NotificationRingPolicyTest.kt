package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: device-backlight-ring.md SS5.3, SS10 test cases T15-T20. */
class NotificationRingPolicyTest {

    private fun candidate(
        packageName: String = "com.example.app",
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
        clearable: Boolean = true,
        priority: Int = 0,
    ) = NotificationRingCandidate(packageName, ongoing, groupSummary, clearable, priority)

    @Test
    fun `T15 - the app's own package never rings`() {
        assertEquals(RingSkipReason.OWN_APP, NotificationRingPolicy.evaluate(candidate(packageName = "brobata.physiboard")))
    }

    @Test
    fun `T16 - ongoing and foreground-service notifications never ring`() {
        assertEquals(RingSkipReason.ONGOING, NotificationRingPolicy.evaluate(candidate(ongoing = true)))
    }

    @Test
    fun `T17 - a group summary never rings, its child does`() {
        assertEquals(RingSkipReason.GROUP_SUMMARY, NotificationRingPolicy.evaluate(candidate(groupSummary = true)))
    }

    @Test
    fun `T18 - a notification the user cannot dismiss never rings`() {
        assertEquals(RingSkipReason.NOT_CLEARABLE, NotificationRingPolicy.evaluate(candidate(clearable = false)))
    }

    @Test
    fun `T19 - minimum priority never rings`() {
        assertEquals(RingSkipReason.SILENT, NotificationRingPolicy.evaluate(candidate(priority = -2)))
    }

    @Test
    fun `T20 - a plain clearable default-priority notification rings`() {
        assertNull(NotificationRingPolicy.evaluate(candidate()))
    }
}
