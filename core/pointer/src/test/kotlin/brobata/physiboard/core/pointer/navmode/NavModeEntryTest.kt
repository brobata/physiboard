package brobata.physiboard.core.pointer.navmode

import brobata.physiboard.core.keys.CtrlState
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.ModifierMachine
import brobata.physiboard.core.keys.ModifierSettings
import brobata.physiboard.core.keys.ModifierState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: trackpad-caret-nav.md SS10, test cases T52-T55 (nav mode entry/exit with no field). */
class NavModeEntryTest {

    private val settings = ModifierSettings()
    private val ctrlKey = KeyId.Modifier(ModifierKey.CTRL)

    private fun down(timeMs: Long) = KeyStroke(ctrlKey, KeyEdge.DOWN, 0, timeMs, ModifierFlags())
    private fun up(timeMs: Long) = KeyStroke(ctrlKey, KeyEdge.UP, 0, timeMs, ModifierFlags())

    @Test
    fun `T52 - a consecutive Ctrl double tap with no field latches nav mode`() {
        val afterDown1 = NavModeEntry.onCtrlDown(ModifierState(), down(0), settings)
        assertEquals(NavModeTransition.NONE, afterDown1.transition)
        val afterUp1 = NavModeEntry.onCtrlUp(afterDown1.state, up(50), settings)

        val afterDown2 = NavModeEntry.onCtrlDown(afterUp1.state, down(400), settings)
        assertTrue(afterDown2.consumed)
        assertEquals(NavModeTransition.ENTERED, afterDown2.transition)
        assertTrue(afterDown2.state.ctrl.latched)
        assertTrue(afterDown2.state.ctrl.latchFromNavMode)
    }

    @Test
    fun `T53 - a non-modifier key between the two taps breaks the double tap`() {
        val afterDown1 = NavModeEntry.onCtrlDown(ModifierState(), down(0), settings)
        val afterUp1 = NavModeEntry.onCtrlUp(afterDown1.state, up(50), settings)
        val otherKeyStroke = KeyStroke(KeyId.Letter('A'), KeyEdge.DOWN, 0, 100, ModifierFlags())
        val afterOther = ModifierMachine.onOtherKeyDown(afterUp1.state, otherKeyStroke)

        val afterDown2 = NavModeEntry.onCtrlDown(afterOther, down(200), settings)
        assertEquals(NavModeTransition.NONE, afterDown2.transition)
        assertFalse(afterDown2.state.ctrl.latched)
    }

    @Test
    fun `T54 - a second tap outside the 500ms window does not latch`() {
        val afterDown1 = NavModeEntry.onCtrlDown(ModifierState(), down(0), settings)
        val afterUp1 = NavModeEntry.onCtrlUp(afterDown1.state, up(50), settings)

        val afterDown2 = NavModeEntry.onCtrlDown(afterUp1.state, down(600), settings)
        assertEquals(NavModeTransition.NONE, afterDown2.transition)
        assertFalse(afterDown2.state.ctrl.latched)
    }

    @Test
    fun `T55 - a Ctrl down while nav mode is latched turns it off`() {
        val navOn = ModifierState(ctrl = CtrlState(latched = true, latchFromNavMode = true))
        val result = NavModeEntry.onCtrlDown(navOn, down(1000), settings)
        assertTrue(result.consumed)
        assertEquals(NavModeTransition.EXITED, result.transition)
        assertFalse(result.state.ctrl.latched)
        assertFalse(result.state.ctrl.latchFromNavMode)
    }
}
