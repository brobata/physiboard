package it.palsoftware.pastiera.ring

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ring turns the screen on. Every notification it wrongly answers is a screen lit in a
 * pocket, so the exclusions are the part worth pinning down.
 */
class NotificationRingPolicyTest {

    private val own = "brobata.physiboard"

    private fun candidate(
        pkg: String = "com.example.chat",
        flags: Int = 0,
        clearable: Boolean = true,
        priority: Int = Notification.PRIORITY_DEFAULT,
        color: Int = 0
    ) = NotificationRingPolicy.Candidate(pkg, flags, clearable, priority, color)

    @Test
    fun `an ordinary message rings`() {
        assertNull(NotificationRingPolicy.skipReason(candidate(), own))
    }

    @Test
    fun `PhysiBoard's own notifications never ring`() {
        assertEquals(
            NotificationRingPolicy.Skip.OWN_APP,
            NotificationRingPolicy.skipReason(candidate(pkg = own), own)
        )
    }

    @Test
    fun `ongoing and foreground-service notifications are ignored`() {
        assertEquals(
            NotificationRingPolicy.Skip.ONGOING,
            NotificationRingPolicy.skipReason(candidate(flags = Notification.FLAG_ONGOING_EVENT), own)
        )
        assertEquals(
            NotificationRingPolicy.Skip.ONGOING,
            NotificationRingPolicy.skipReason(candidate(flags = Notification.FLAG_FOREGROUND_SERVICE), own)
        )
    }

    @Test
    fun `group summaries are ignored so a thread rings once`() {
        assertEquals(
            NotificationRingPolicy.Skip.GROUP_SUMMARY,
            NotificationRingPolicy.skipReason(candidate(flags = Notification.FLAG_GROUP_SUMMARY), own)
        )
    }

    @Test
    fun `things the user could not dismiss are ignored`() {
        assertEquals(
            NotificationRingPolicy.Skip.NOT_CLEARABLE,
            NotificationRingPolicy.skipReason(candidate(clearable = false), own)
        )
    }

    @Test
    fun `minimum-priority notifications are ignored`() {
        assertEquals(
            NotificationRingPolicy.Skip.SILENT,
            NotificationRingPolicy.skipReason(candidate(priority = Notification.PRIORITY_MIN), own)
        )
    }

    @Test
    fun `an app's brand colour is used when it is readable on black`() {
        val blue = 0xFF1E88E5.toInt()
        assertEquals(blue, NotificationRingPolicy.ringColor(blue))
    }

    @Test
    fun `no colour, or a colour too dark for a black screen, falls back to the default`() {
        assertEquals(NotificationRingPolicy.DEFAULT_COLOR, NotificationRingPolicy.ringColor(0))
        assertEquals(NotificationRingPolicy.DEFAULT_COLOR, NotificationRingPolicy.ringColor(0xFF101010.toInt()))
    }
}
