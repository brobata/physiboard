package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS9 and SS10 step 7, the accessibility service row. */
class AccessibilityServiceListTest {

    private val dev = "brobata.physiboard.dev3"
    private val ours = "$dev/${AccessibilityServiceList.SERVICE_CLASS}"
    private val other = "cz.mobilesoft.appblock/cz.mobilesoft.appblock.service.LockAccessibilityService"

    @Test
    fun `parse reads the list and the master switch`() {
        val r = AccessibilityServiceList.parse("$other\n1\n")!!
        assertEquals(listOf(other), r.entries)
        assertTrue(r.masterOn)
        assertEquals(AccessibilityServiceList.Reading(emptyList(), false), AccessibilityServiceList.parse("null\nnull\n"))
        assertNull(AccessibilityServiceList.parse("only one line"))
    }

    @Test
    fun `ours is recognised in the full and the short form, and only for this build's package`() {
        assertTrue(AccessibilityServiceList.isOurs(ours, dev))
        assertTrue(AccessibilityServiceList.isOurs("brobata.physiboard/.ime.access.PhysiBoardAccessibilityService", "brobata.physiboard"))
        assertFalse(AccessibilityServiceList.isOurs("brobata.physiboard/${AccessibilityServiceList.SERVICE_CLASS}", dev), "the release build's entry is not the dev build's")
        assertFalse(AccessibilityServiceList.isOurs(other, dev))
        assertTrue(AccessibilityServiceList.isListed("$other:$ours", dev))
    }

    @Test
    fun `turning on appends ours after every other service and switches accessibility on`() {
        val line = AccessibilityServiceList.enableLine(AccessibilityServiceList.Reading(listOf(other), masterOn = true), dev)
        assertEquals("settings put secure enabled_accessibility_services '$other:$ours'; settings put secure accessibility_enabled 1", line)
    }

    @Test
    fun `turning on from nothing, or with ours listed but accessibility off`() {
        assertEquals(
            "settings put secure enabled_accessibility_services '$ours'; settings put secure accessibility_enabled 1",
            AccessibilityServiceList.enableLine(AccessibilityServiceList.Reading(emptyList(), masterOn = false), dev),
        )
        assertEquals(
            "settings put secure enabled_accessibility_services '$other:$ours'; settings put secure accessibility_enabled 1",
            AccessibilityServiceList.enableLine(AccessibilityServiceList.Reading(listOf(other, ours), masterOn = false), dev),
        )
        assertNull(AccessibilityServiceList.enableLine(AccessibilityServiceList.Reading(listOf(ours), masterOn = true), dev), "already on")
    }

    @Test
    fun `an entry that is not a plain component refuses the write rather than risk another app's`() {
        val planted = AccessibilityServiceList.Reading(listOf(other, "x/y'; reboot; '"), masterOn = true)
        assertNull(AccessibilityServiceList.enableLine(planted, dev))
        assertNull(AccessibilityServiceList.disableLine(planted.copy(entries = planted.entries + ours), dev))
    }

    @Test
    fun `the reset removes only ours`() {
        assertEquals(
            "settings put secure enabled_accessibility_services '$other'",
            AccessibilityServiceList.disableLine(AccessibilityServiceList.Reading(listOf(ours, other), masterOn = true), dev),
        )
    }

    @Test
    fun `the reset with nothing else left goes back to a phone with nothing turned on`() {
        assertEquals(
            "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0",
            AccessibilityServiceList.disableLine(AccessibilityServiceList.Reading(listOf(ours), masterOn = true), dev),
        )
    }

    @Test
    fun `the reset does nothing when ours is not listed, including the other build's entry`() {
        val release = "brobata.physiboard/${AccessibilityServiceList.SERVICE_CLASS}"
        assertNull(AccessibilityServiceList.disableLine(AccessibilityServiceList.Reading(listOf(other, release), masterOn = true), dev))
    }
}
