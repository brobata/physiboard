package brobata.physiboard.device.privileged.ring

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.FakeTimer
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.backlight.FakeMasterSwitch
import brobata.physiboard.device.privileged.setup.FakePermissionProbe
import brobata.physiboard.device.titan.KeyboardBacklight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.8; test cases T6 to T14 run against the real store, switch and timer. */
class RingBacklightTest {

    private val store = InMemoryDeviceStateStore(Settings())
    private val switch = FakeMasterSwitch(value = 1)
    private val permissions = FakePermissionProbe(writeSecureSettings = true)
    private val timer = FakeTimer()
    private val backlight = RingBacklight(store, switch, permissions, timer, FakeClock())

    private val captures get() = store.snapshot().captures

    @Test
    fun `T6 - suppress with everything in place writes 0 and records prior 1 before the write`() {
        assertTrue(backlight.suppress())
        assertEquals(0, switch.value)
        assertTrue(captures.ringBacklightPrevCaptured)
        assertEquals(1, captures.ringBacklightPrev)
        assertEquals(KeyboardBacklight.SUPPRESSION_ORPHAN_TIMEOUT_MS, timer.scheduledDelayMs)
    }

    @Test
    fun `T7 - restore after suppress writes 1 back and clears the record`() {
        backlight.suppress()
        backlight.restore()
        assertEquals(1, switch.value)
        assertFalse(captures.ringBacklightPrevCaptured)
        assertNull(captures.ringBacklightPrev)
        assertFalse(timer.isArmed)
    }

    @Test
    fun `T8 - a second suppress never overwrites the record, the switch ends at 1`() {
        backlight.suppress()
        assertFalse(backlight.suppress())
        assertEquals(1, captures.ringBacklightPrev)
        backlight.restore()
        assertEquals(1, switch.value)
    }

    @Test
    fun `T9 - switch already 0 - suppress records nothing and restore leaves 0`() {
        switch.value = 0
        assertFalse(backlight.suppress())
        assertFalse(captures.ringBacklightPrevCaptured)
        backlight.restore()
        assertEquals(0, switch.value)
        assertTrue(switch.writes.isEmpty())
    }

    @Test
    fun `T10 - keep-the-keyboard-dark off - switch stays 1, no record`() {
        store.update { it.copy(device = it.device.copy(ringKeyboardDark = false)) }
        assertFalse(backlight.suppress())
        assertEquals(1, switch.value)
        assertFalse(captures.ringBacklightPrevCaptured)
    }

    @Test
    fun `T11 - ring disabled - switch stays 1`() {
        store.update { it.copy(device = it.device.copy(ringEnabled = false)) }
        assertFalse(backlight.suppress())
        assertEquals(1, switch.value)
    }

    @Test
    fun `T12 - suppress, restore, user sets 0, restore again - stays 0`() {
        backlight.suppress()
        backlight.restore()
        switch.value = 0
        backlight.restore()
        assertEquals(0, switch.value)
    }

    @Test
    fun `T13 - permission denied - suppress does nothing`() {
        permissions.writeSecureSettings = false
        assertFalse(backlight.suppress())
        assertEquals(1, switch.value)
        assertFalse(captures.ringBacklightPrevCaptured)
    }

    @Test
    fun `T14 - suppress, permission removed, restore keeps the record and the switch stays 0`() {
        backlight.suppress()
        permissions.writeSecureSettings = false
        switch.permissionHeld = false
        backlight.restore()
        assertTrue(captures.ringBacklightPrevCaptured, "a later grant can heal it")
        assertEquals(1, captures.ringBacklightPrev)
        assertEquals(0, switch.value)
        permissions.writeSecureSettings = true
        switch.permissionHeld = true
        backlight.restore()
        assertEquals(1, switch.value)
        assertFalse(captures.ringBacklightPrevCaptured)
    }

    @Test
    fun `a failed switch write clears the record it had just committed`() {
        switch.permissionHeld = false
        assertFalse(backlight.suppress())
        assertFalse(captures.ringBacklightPrevCaptured)
        assertFalse(timer.isArmed)
    }

    @Test
    fun `the orphan timer restores a suppression no ring ever claimed`() {
        backlight.suppress()
        timer.fire()
        assertEquals(1, switch.value)
        assertFalse(captures.ringBacklightPrevCaptured)
    }

    @Test
    fun `a ring taking ownership cancels the orphan timer, and its teardown restores`() {
        backlight.suppress()
        backlight.takeOwnership()
        assertFalse(timer.isArmed)
        timer.fire()
        assertEquals(0, switch.value, "the ring owns the restore now")
        backlight.restore()
        assertEquals(1, switch.value)
    }

    @Test
    fun `a process that died mid-ring restores from the persisted record at its next start`() {
        backlight.suppress()
        // A new process: same store, no timer, no ownership.
        val revived = RingBacklight(store, switch, permissions, FakeTimer(), FakeClock())
        revived.restore()
        assertEquals(1, switch.value)
        assertFalse(captures.ringBacklightPrevCaptured)
    }
}
