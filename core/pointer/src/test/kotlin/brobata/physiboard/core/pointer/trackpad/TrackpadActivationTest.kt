package brobata.physiboard.core.pointer.trackpad

import brobata.physiboard.core.pointer.OverlayAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: trackpad-caret-nav.md SS10, test cases T1-T13 (the trigger-key subset owned by [TrackpadActivation]). */
class TrackpadActivationTest {

    private val holdSettings = TrackpadActivationSettings(triggerKey = TriggerKey.SPACE, activationMode = ActivationMode.HOLD)

    @Test
    fun `T1 - a quick tap under the hold threshold replays down then up and never opens`() {
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SPACE, 0, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        assertTrue(down.consumed)
        assertEquals(TrackpadActivationEffect.NONE, down.effect)

        val up = TrackpadActivation.onKeyUp(down.state, TrackpadPhysicalKey.SPACE, 100, holdSettings)
        assertTrue(up.consumed)
        assertTrue(up.effect.replayTriggerDownAndUp)
        assertFalse(up.effect.openOverlayHold)
        assertEquals(TrackpadPhase.IDLE, up.state.phase)
    }

    @Test
    fun `T2 - the timer opens the overlay at 250ms and the later up closes it`() {
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SPACE, 0, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        val fired = TrackpadActivation.onHoldTimerFired(down.state, 250, holdSettings, OverlayAvailability.AVAILABLE)
        assertEquals(TrackpadPhase.ACTIVE_HOLD, fired.state.phase)
        assertTrue(fired.effect.openOverlayHold)

        val up = TrackpadActivation.onKeyUp(fired.state, TrackpadPhysicalKey.SPACE, 300, holdSettings)
        assertTrue(up.consumed)
        assertTrue(up.effect.closeOverlay)
        assertFalse(up.effect.replayTriggerDownAndUp)
    }

    @Test
    fun `T3 - another key during the pending hold aborts the chord and replays the trigger alone`() {
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SPACE, 0, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        val other = TrackpadActivation.onKeyDown(down.state, null, 0, 50, false, holdSettings, OverlayAvailability.AVAILABLE)
        assertFalse(other.consumed)
        assertTrue(other.effect.replayTriggerDownOnly)
        assertEquals(TrackpadPhase.ABORTED, other.state.phase)

        val up = TrackpadActivation.onKeyUp(other.state, TrackpadPhysicalKey.SPACE, 120, holdSettings)
        assertFalse(up.consumed)
    }

    @Test
    fun `T4 - a trigger auto-repeat while pending is consumed`() {
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SPACE, 0, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        val repeat = TrackpadActivation.onKeyDown(down.state, TrackpadPhysicalKey.SPACE, 2, 10, false, holdSettings, OverlayAvailability.AVAILABLE)
        assertTrue(repeat.consumed)
        assertEquals(TrackpadPhase.PENDING, repeat.state.phase)
    }

    @Test
    fun `T5 - a Space down carrying Ctrl or Alt meta is never a trigger`() {
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SPACE, 0, 0, true, holdSettings, OverlayAvailability.AVAILABLE)
        assertFalse(down.consumed)
        assertEquals(TrackpadPhase.IDLE, down.state.phase)
    }

    @Test
    fun `T6 - a missing permission toasts, aborts and replays instead of opening`() {
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SPACE, 0, 0, false, holdSettings, OverlayAvailability.PERMISSION_MISSING)
        val fired = TrackpadActivation.onHoldTimerFired(down.state, 250, holdSettings, OverlayAvailability.PERMISSION_MISSING)
        assertTrue(fired.consumed)
        assertTrue(fired.effect.showPermissionToast)
        assertTrue(fired.effect.replayTriggerDownOnly)
        assertEquals(TrackpadPhase.ABORTED, fired.state.phase)

        val up = TrackpadActivation.onKeyUp(fired.state, TrackpadPhysicalKey.SPACE, 300, holdSettings)
        assertFalse(up.consumed)
    }

    private val doubleTapSettings = TrackpadActivationSettings(triggerKey = TriggerKey.SHIFT_EITHER, activationMode = ActivationMode.DOUBLE_TAP)

    @Test
    fun `T7 - double tap opens sticky on a consecutive tap of the same trigger key`() {
        val firstDown = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SHIFT_LEFT, 0, 0, false, doubleTapSettings, OverlayAvailability.AVAILABLE)
        assertFalse(firstDown.consumed)

        val firstUp = TrackpadActivation.onKeyUp(firstDown.state, TrackpadPhysicalKey.SHIFT_LEFT, 40, doubleTapSettings)
        assertFalse(firstUp.consumed)

        val secondDown = TrackpadActivation.onKeyDown(firstUp.state, TrackpadPhysicalKey.SHIFT_LEFT, 0, 300, false, doubleTapSettings, OverlayAvailability.AVAILABLE)
        assertTrue(secondDown.consumed)
        assertEquals(TrackpadPhase.ACTIVE_STICKY, secondDown.state.phase)
        assertTrue(secondDown.effect.openOverlaySticky)
    }

    @Test
    fun `T8 - a second tap outside the 400ms window is just another first tap`() {
        val firstUp = TrackpadActivationState(lastReleaseAtMs = 40, lastReleaseKey = TrackpadPhysicalKey.SHIFT_LEFT)
        val secondDown = TrackpadActivation.onKeyDown(firstUp, TrackpadPhysicalKey.SHIFT_LEFT, 0, 500, false, doubleTapSettings, OverlayAvailability.AVAILABLE)
        assertFalse(secondDown.consumed)
        assertEquals(TrackpadPhase.IDLE, secondDown.state.phase)

        val secondUp = TrackpadActivation.onKeyUp(secondDown.state, TrackpadPhysicalKey.SHIFT_LEFT, 540, doubleTapSettings)
        assertEquals(540L, secondUp.state.lastReleaseAtMs)
    }

    @Test
    fun `T9 - the other Shift key's down is a first tap, not a double tap`() {
        val firstUp = TrackpadActivationState(lastReleaseAtMs = 40, lastReleaseKey = TrackpadPhysicalKey.SHIFT_LEFT)
        val secondDown = TrackpadActivation.onKeyDown(firstUp, TrackpadPhysicalKey.SHIFT_RIGHT, 0, 300, false, doubleTapSettings, OverlayAvailability.AVAILABLE)
        assertFalse(secondDown.consumed)
        assertEquals(TrackpadPhase.IDLE, secondDown.state.phase)
    }

    @Test
    fun `T10 - single tap opens sticky on the very first fresh down`() {
        val settings = TrackpadActivationSettings(triggerKey = TriggerKey.SYM, activationMode = ActivationMode.SINGLE_TAP)
        val down = TrackpadActivation.onKeyDown(TrackpadActivationState(), TrackpadPhysicalKey.SYM, 0, 0, false, settings, OverlayAvailability.AVAILABLE)
        assertTrue(down.consumed)
        assertEquals(TrackpadPhase.ACTIVE_STICKY, down.state.phase)
        assertTrue(down.effect.openOverlaySticky)
    }

    @Test
    fun `T11 - Back closes a sticky overlay`() {
        val sticky = TrackpadActivationState(phase = TrackpadPhase.ACTIVE_STICKY)
        val back = TrackpadActivation.onKeyDown(sticky, TrackpadPhysicalKey.BACK, 0, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        assertTrue(back.consumed)
        assertTrue(back.effect.closeOverlay)
        assertEquals(TrackpadPhase.IDLE, back.state.phase)
    }

    @Test
    fun `T12 - an ordinary letter under a sticky overlay is not consumed`() {
        val sticky = TrackpadActivationState(phase = TrackpadPhase.ACTIVE_STICKY)
        val letter = TrackpadActivation.onKeyDown(sticky, null, 0, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        assertFalse(letter.consumed)
        assertEquals(TrackpadPhase.ACTIVE_STICKY, letter.state.phase)
    }

    @Test
    fun `T13 - a trigger auto-repeat while the hold overlay is active is consumed and stays open`() {
        val active = TrackpadActivationState(phase = TrackpadPhase.ACTIVE_HOLD)
        val repeat = TrackpadActivation.onKeyDown(active, TrackpadPhysicalKey.SPACE, 3, 0, false, holdSettings, OverlayAvailability.AVAILABLE)
        assertTrue(repeat.consumed)
        assertEquals(TrackpadPhase.ACTIVE_HOLD, repeat.state.phase)
    }
}
