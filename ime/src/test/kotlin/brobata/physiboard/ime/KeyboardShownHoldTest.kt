package brobata.physiboard.ime

import brobata.physiboard.core.keys.StatusBarIcon
import brobata.physiboard.ime.ModifierHoldPolicy.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** keys-and-modifiers.md SS13.1 and dictation.md SS6.8: the shared keyboard-shown hold and when the status icon takes its share. */
class KeyboardShownHoldTest {

    private val dictation = ShownHoldOwner.DICTATION
    private val icon = ShownHoldOwner.MODIFIER_ICON

    @Test
    fun `one owner releasing never drops the other's hold`() {
        val hold = KeyboardShownHold()
        assertTrue(hold.acquire(dictation))
        assertTrue(hold.acquire(icon))
        assertFalse(hold.release(icon), "dictation still holds: no hide")
        assertTrue(hold.isHeld)
        assertTrue(hold.release(dictation), "the last owner may hide")
        assertFalse(hold.isHeld)
    }

    @Test
    fun `the order of release does not matter`() {
        val hold = KeyboardShownHold()
        hold.acquire(icon)
        hold.acquire(dictation)
        assertFalse(hold.release(dictation))
        assertTrue(hold.holds(icon))
        assertTrue(hold.release(icon))
    }

    @Test
    fun `acquiring twice counts once and releasing an owner that never held does nothing`() {
        val hold = KeyboardShownHold()
        assertTrue(hold.acquire(dictation))
        assertFalse(hold.acquire(dictation))
        assertFalse(hold.release(icon))
        assertTrue(hold.isHeld)
        assertTrue(hold.release(dictation))
        assertFalse(hold.release(dictation), "a second release is not a second hide")
    }

    @Test
    fun `a modifier icon takes the hold at once and lets it go only after the delay`() {
        val policy = ModifierHoldPolicy()
        assertEquals(Decision.ACQUIRE, policy.onIcon(StatusBarIcon.SHIFT))
        assertEquals(Decision.CANCEL_RELEASE, policy.onIcon(StatusBarIcon.CAPS_LOCK))
        assertEquals(Decision.SCHEDULE_RELEASE, policy.onIcon(StatusBarIcon.NONE))
        assertTrue(policy.held)
        assertEquals(Decision.RELEASE, policy.onReleaseDue(StatusBarIcon.NONE))
        assertFalse(policy.held)
    }

    @Test
    fun `Shift again before the delay keeps the same hold, no second show`() {
        val policy = ModifierHoldPolicy()
        policy.onIcon(StatusBarIcon.SHIFT)
        policy.onIcon(StatusBarIcon.NONE)
        assertEquals(Decision.CANCEL_RELEASE, policy.onIcon(StatusBarIcon.SHIFT))
        assertEquals(Decision.NONE, policy.onReleaseDue(StatusBarIcon.SHIFT))
        assertTrue(policy.held)
    }

    @Test
    fun `dictation's icon is not a modifier state, so the icon's share goes while dictation holds its own`() {
        val policy = ModifierHoldPolicy()
        policy.onIcon(StatusBarIcon.CAPS_LOCK)
        assertEquals(Decision.SCHEDULE_RELEASE, policy.onIcon(StatusBarIcon.DICTATION))
        assertEquals(Decision.RELEASE, policy.onReleaseDue(StatusBarIcon.DICTATION))
        assertEquals(Decision.ACQUIRE, policy.onIcon(StatusBarIcon.CAPS_LOCK), "the session ended with caps lock on")
    }

    @Test
    fun `Back releases at once and the same state does not take the hold again`() {
        val policy = ModifierHoldPolicy()
        policy.onIcon(StatusBarIcon.CAPS_LOCK)
        assertEquals(Decision.RELEASE, policy.onBack(StatusBarIcon.CAPS_LOCK))
        assertEquals(Decision.NONE, policy.onBack(StatusBarIcon.CAPS_LOCK), "a second Back costs nothing")
        assertEquals(Decision.NONE, policy.onIcon(StatusBarIcon.CAPS_LOCK))
        assertEquals(Decision.ACQUIRE, policy.onIcon(StatusBarIcon.ALT), "a different state takes it again")
    }

    @Test
    fun `after Back the next field takes the hold again`() {
        val policy = ModifierHoldPolicy()
        policy.onIcon(StatusBarIcon.CAPS_LOCK)
        policy.onBack(StatusBarIcon.CAPS_LOCK)
        assertEquals(Decision.ACQUIRE, policy.onFieldStarted(StatusBarIcon.CAPS_LOCK))
        assertEquals(Decision.REASSERT, policy.onFieldStarted(StatusBarIcon.CAPS_LOCK))
    }

    @Test
    fun `Back with nothing held and a field with nothing shown do nothing`() {
        val policy = ModifierHoldPolicy()
        assertEquals(Decision.NONE, policy.onBack(StatusBarIcon.NONE))
        assertEquals(Decision.NONE, policy.onFieldStarted(StatusBarIcon.NONE))
        assertEquals(Decision.NONE, policy.onIcon(StatusBarIcon.DICTATION))
    }

    @Test
    fun `a field that starts with the state already gone schedules the release`() {
        val policy = ModifierHoldPolicy()
        policy.onIcon(StatusBarIcon.SYM)
        assertEquals(Decision.SCHEDULE_RELEASE, policy.onFieldStarted(StatusBarIcon.NONE))
    }
}
