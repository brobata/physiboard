package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: keys-and-modifiers.md SS13.1 (the system status bar icon and its precedence), trackpad-caret-nav.md SS5.7, dictation.md SS9. */
class StatusBarModifierIconTest {

    private val off = ModifierIconState.OFF
    private val active = ModifierIconState.ACTIVE
    private val locked = ModifierIconState.LOCKED

    private fun choose(shift: ModifierIconState = off, ctrl: ModifierIconState = off, alt: ModifierIconState = off, sym: Boolean = false, nav: Boolean = false, dictation: Boolean = false) =
        StatusBarModifierIcon.choose(shift, ctrl, alt, symPageOpen = sym, navModeActive = nav, dictationListening = dictation)

    @Test
    fun `all three modifiers off and no Sym page shows no icon`() {
        assertEquals(StatusBarIcon.NONE, choose())
    }

    @Test
    fun `each single state has its own icon`() {
        assertEquals(StatusBarIcon.SHIFT, choose(shift = active))
        assertEquals(StatusBarIcon.CAPS_LOCK, choose(shift = locked))
        assertEquals(StatusBarIcon.ALT, choose(alt = active))
        assertEquals(StatusBarIcon.ALT_LOCKED, choose(alt = locked))
        assertEquals(StatusBarIcon.CTRL, choose(ctrl = active))
        assertEquals(StatusBarIcon.CTRL_LOCKED, choose(ctrl = locked))
        assertEquals(StatusBarIcon.SYM, choose(sym = true))
        assertEquals(StatusBarIcon.NAV, choose(nav = true))
        assertEquals(StatusBarIcon.DICTATION, choose(dictation = true))
    }

    @Test
    fun `a listening dictation session wins over nav mode and every modifier`() {
        assertEquals(StatusBarIcon.DICTATION, choose(shift = active, ctrl = locked, sym = true, nav = true, dictation = true))
    }

    @Test
    fun `nav mode wins over every modifier and a Sym page`() {
        assertEquals(StatusBarIcon.NAV, choose(shift = locked, ctrl = locked, alt = locked, sym = true, nav = true))
    }

    @Test
    fun `Ctrl beats Alt, Alt beats Shift, any modifier beats Sym`() {
        assertEquals(StatusBarIcon.CTRL, choose(shift = locked, ctrl = active, alt = locked))
        assertEquals(StatusBarIcon.ALT, choose(shift = locked, alt = active))
        assertEquals(StatusBarIcon.SHIFT, choose(shift = active, sym = true))
    }

    @Test
    fun `within one modifier locked beats active`() {
        assertEquals(StatusBarIcon.CTRL_LOCKED, choose(ctrl = locked, alt = active))
        assertEquals(StatusBarIcon.ALT_LOCKED, choose(alt = locked, shift = active))
    }

    @Test
    fun `every one of the 26 non-empty modifier combinations shows some modifier icon`() {
        val states = listOf(off, active, locked)
        val combos = states.flatMap { s -> states.flatMap { c -> states.map { a -> Triple(s, c, a) } } }
            .filterNot { (s, c, a) -> s == off && c == off && a == off }
        assertEquals(26, combos.size)
        combos.forEach { (s, c, a) ->
            val icon = choose(shift = s, ctrl = c, alt = a)
            assertTrue(icon.isModifierState && icon != StatusBarIcon.SYM && icon != StatusBarIcon.NAV, "($s, $c, $a) -> $icon")
        }
    }

    @Test
    fun `only modifier, Sym and nav states are modifier states`() {
        assertFalse(StatusBarIcon.NONE.isModifierState)
        assertFalse(StatusBarIcon.DICTATION.isModifierState)
        StatusBarIcon.entries.filter { it != StatusBarIcon.NONE && it != StatusBarIcon.DICTATION }.forEach { assertTrue(it.isModifierState, it.name) }
    }

    @Test
    fun `Shift caps lock is locked, one-shot and held are active`() {
        assertEquals(locked, StatusBarModifierIcon.shiftState(ShiftValue.CAPS, physicallyPressed = false))
        assertEquals(active, StatusBarModifierIcon.shiftState(ShiftValue.ONE_SHOT, physicallyPressed = false))
        assertEquals(active, StatusBarModifierIcon.shiftState(ShiftValue.OFF, physicallyPressed = true))
        assertEquals(off, StatusBarModifierIcon.shiftState(ShiftValue.OFF, physicallyPressed = false))
    }

    @Test
    fun `Ctrl or Alt latched beats one-shot or held, which beats off`() {
        assertEquals(locked, StatusBarModifierIcon.latchableState(latched = true, oneShot = true, physicallyPressed = true))
        assertEquals(active, StatusBarModifierIcon.latchableState(latched = false, oneShot = true, physicallyPressed = false))
        assertEquals(active, StatusBarModifierIcon.latchableState(latched = false, oneShot = false, physicallyPressed = true))
        assertEquals(off, StatusBarModifierIcon.latchableState(latched = false, oneShot = false, physicallyPressed = false))
    }
}
