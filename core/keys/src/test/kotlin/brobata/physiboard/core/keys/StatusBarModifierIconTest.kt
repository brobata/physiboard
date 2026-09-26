package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: keys-and-modifiers.md SS13.1 (the system status bar icon), trackpad-caret-nav.md SS5.7 (nav mode wins the same slot). */
class StatusBarModifierIconTest {

    private val off = ModifierIconState.OFF
    private val active = ModifierIconState.ACTIVE
    private val locked = ModifierIconState.LOCKED

    @Test
    fun `all three modifiers off and no Sym page shows no icon`() {
        assertEquals(StatusBarIcon.None, StatusBarModifierIcon.choose(off, off, off, symPageOpen = false, navModeActive = false))
    }

    @Test
    fun `all off with a Sym page open shows the Sym icon`() {
        assertEquals(StatusBarIcon.Sym, StatusBarModifierIcon.choose(off, off, off, symPageOpen = true, navModeActive = false))
    }

    @Test
    fun `nav mode wins over every other state, even a Sym page`() {
        assertEquals(StatusBarIcon.Nav, StatusBarModifierIcon.choose(active, locked, off, symPageOpen = true, navModeActive = true))
        assertEquals(StatusBarIcon.Nav, StatusBarModifierIcon.choose(off, off, off, symPageOpen = false, navModeActive = true))
    }

    @Test
    fun `one active modifier selects the modifier combination, not the Sym fallback`() {
        val result = StatusBarModifierIcon.choose(active, off, off, symPageOpen = true, navModeActive = false)
        assertEquals(StatusBarIcon.Modifiers(active, off, off), result)
    }

    @Test
    fun `every one of the 26 non-empty combinations is distinct and none is None`() {
        val states = listOf(off, active, locked)
        val combos = states.flatMap { s -> states.flatMap { c -> states.map { a -> Triple(s, c, a) } } }
            .filterNot { (s, c, a) -> s == off && c == off && a == off }
        assertEquals(26, combos.size)
        val icons = combos.map { (s, c, a) -> StatusBarModifierIcon.choose(s, c, a, symPageOpen = false, navModeActive = false) }
        assertEquals(26, icons.toSet().size, "every non-empty combination must map to its own distinct icon value")
        icons.forEach { assertEquals(true, it is StatusBarIcon.Modifiers) }
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
