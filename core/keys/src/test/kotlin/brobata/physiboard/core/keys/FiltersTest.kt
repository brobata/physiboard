package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** spec: keys-and-modifiers.md SS21, test cases T33-T41. */
class FiltersTest {

    private val a = KeyId.Letter('A')
    private val s = KeyId.Letter('S')
    private val shift = KeyId.Modifier(ModifierKey.SHIFT)

    private fun down(key: KeyId, timeMs: Long, repeat: Int = 0, deviceId: Int = 1) =
        KeyStroke(key, KeyEdge.DOWN, repeat, timeMs, deviceId = deviceId)

    private fun up(key: KeyId, timeMs: Long, deviceId: Int = 1) =
        KeyStroke(key, KeyEdge.UP, 0, timeMs, deviceId = deviceId)

    // Bounce filter: T33-T37 -------------------------------------------------------------------

    @Test
    fun `T33 - a second down and up within the delay are both consumed`() {
        val settings = BounceKeySettings(enabled = true, delayMs = 80)
        var state = BounceFilterState()

        val d1 = BounceFilter.onKeyDown(state, down(a, 0), settings); state = d1.first
        assertEquals(FilterVerdict.Accept, d1.second)
        val u1 = BounceFilter.onKeyUp(state, up(a, 20)); state = u1.first
        assertEquals(FilterVerdict.Accept, u1.second)

        val d2 = BounceFilter.onKeyDown(state, down(a, 50), settings); state = d2.first
        assertIs<FilterVerdict.Reject>(d2.second)
        val u2 = BounceFilter.onKeyUp(state, up(a, 70))
        assertIs<FilterVerdict.Reject>(u2.second)
    }

    @Test
    fun `T34 - a second down after the delay is accepted`() {
        val settings = BounceKeySettings(enabled = true, delayMs = 80)
        var state = BounceFilterState()
        state = BounceFilter.onKeyDown(state, down(a, 0), settings).first
        state = BounceFilter.onKeyUp(state, up(a, 20)).first
        val d2 = BounceFilter.onKeyDown(state, down(a, 100), settings)
        assertEquals(FilterVerdict.Accept, d2.second)
    }

    @Test
    fun `T35 - a system repeat is never filtered`() {
        val settings = BounceKeySettings(enabled = true, delayMs = 80)
        var state = BounceFilterState()
        state = BounceFilter.onKeyDown(state, down(a, 0), settings).first
        val repeat = BounceFilter.onKeyDown(state, down(a, 60, repeat = 1), settings)
        assertEquals(FilterVerdict.Accept, repeat.second)
    }

    @Test
    fun `T36 - a disabled category is never filtered`() {
        val settings = BounceKeySettings(enabled = true, delayMs = 80, modifierKeysEnabled = false)
        var state = BounceFilterState()
        state = BounceFilter.onKeyDown(state, down(shift, 0), settings).first
        state = BounceFilter.onKeyUp(state, up(shift, 10)).first
        val d2 = BounceFilter.onKeyDown(state, down(shift, 30), settings)
        assertEquals(FilterVerdict.Accept, d2.second)
    }

    @Test
    fun `T37 - a rejected down while the first press is still held does not swallow its eventual up`() {
        val settings = BounceKeySettings(enabled = true, delayMs = 80)
        var state = BounceFilterState()
        state = BounceFilter.onKeyDown(state, down(a, 0), settings).first // held, no up yet
        val d2 = BounceFilter.onKeyDown(state, down(a, 30), settings)
        state = d2.first
        assertIs<FilterVerdict.Reject>(d2.second)
        val u = BounceFilter.onKeyUp(state, up(a, 200))
        assertEquals(FilterVerdict.Accept, u.second)
    }

    // Accidental-press filter: T38-T41 -----------------------------------------------------------

    @Test
    fun `T38 - a second key held while the first is down is suppressed, the first's up is delivered`() {
        val settings = AccidentalPressSettings(enabled = true)
        var state = AccidentalPressFilterState()
        state = AccidentalPressFilter.onKeyDown(state, down(a, 0), settings).first

        val sDown = AccidentalPressFilter.onKeyDown(state, down(s, 10), settings)
        state = sDown.first
        assertIs<FilterVerdict.Reject>(sDown.second)

        val sUp = AccidentalPressFilter.onKeyUp(state, up(s, 20))
        state = sUp.first
        assertIs<FilterVerdict.Reject>(sUp.second)

        val aUp = AccidentalPressFilter.onKeyUp(state, up(a, 30))
        assertEquals(FilterVerdict.Accept, aUp.second)
    }

    @Test
    fun `T39 - a key pressed after the first was released is accepted`() {
        val settings = AccidentalPressSettings(enabled = true)
        var state = AccidentalPressFilterState()
        state = AccidentalPressFilter.onKeyDown(state, down(a, 0), settings).first
        state = AccidentalPressFilter.onKeyUp(state, up(a, 10)).first
        val sDown = AccidentalPressFilter.onKeyDown(state, down(s, 20), settings)
        assertEquals(FilterVerdict.Accept, sDown.second)
    }

    @Test
    fun `T40 - a modifier key is never suppressed and never counts as held`() {
        val settings = AccidentalPressSettings(enabled = true)
        var state = AccidentalPressFilterState()
        state = AccidentalPressFilter.onKeyDown(state, down(a, 0), settings).first
        val shiftDown = AccidentalPressFilter.onKeyDown(state, down(shift, 10), settings)
        assertEquals(FilterVerdict.Accept, shiftDown.second)
    }

    @Test
    fun `T41 - keys on different devices do not interact`() {
        val settings = AccidentalPressSettings(enabled = true)
        var state = AccidentalPressFilterState()
        state = AccidentalPressFilter.onKeyDown(state, down(a, 0, deviceId = 1), settings).first
        val sDown = AccidentalPressFilter.onKeyDown(state, down(s, 10, deviceId = 2), settings)
        assertEquals(FilterVerdict.Accept, sDown.second)
    }
}
