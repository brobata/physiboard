package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS5.11 (`sym_open_on_press`), SS14 cases 88 to 96. */
class SymOpenOnPressTest {

    private val sym = KeyId.Modifier(ModifierKey.SYM)
    private val on = ModifierSettings(symOpenOnPress = true)
    private val pages = SymPagesConfig() // Emoji, Symbols enabled; cycle 0, 1, 2

    private fun down(key: KeyId, t: Long, repeat: Int = 0) = KeyStroke(key, KeyEdge.DOWN, repeat, t)
    private fun up(key: KeyId, t: Long) = KeyStroke(key, KeyEdge.UP, 0, t)

    private fun symDown(state: ModifierState, t: Long, s: ModifierSettings = on, repeat: Int = 0, field: Boolean = true) =
        ModifierMachine.symDown(state, down(sym, t, repeat), s, hasEditableField = field, pages = pages)

    private fun symUp(state: ModifierState, t: Long) = ModifierMachine.symUp(state, up(sym, t), hasEditableField = true, pages = pages)

    @Test
    fun `case 88 - the press shows the next page and the release keeps it`() {
        val pressed = symDown(ModifierState(), 0)
        assertEquals(1, pressed.state.sym.currentPageNumber)
        assertTrue(pressed.state.sym.openedOnPress)
        val released = symUp(pressed.state, 120)
        assertEquals(Action.StateOnly, released.action)
        assertEquals(1, released.state.sym.currentPageNumber)
        assertEquals(120L, released.state.sym.lastTapUpAtMs)
        assertEquals(0, released.state.sym.pageBeforeLastTap)
        assertFalse(released.state.sym.openedOnPress)
    }

    @Test
    fun `case 89 - off, the press changes no page (today's behaviour)`() {
        val pressed = symDown(ModifierState(), 0, s = ModifierSettings())
        assertEquals(0, pressed.state.sym.currentPageNumber)
        assertEquals(1, symUp(pressed.state, 120).state.sym.currentPageNumber)
    }

    @Test
    fun `case 90 - a key under Sym takes the page back before it resolves`() {
        val pressed = symDown(ModifierState(sym = SymSessionState(currentPageNumber = 2)), 0)
        assertEquals(0, pressed.state.sym.currentPageNumber, "Symbols to closed on the way down")
        val keyed = ModifierMachine.onOtherKeyDown(pressed.state, down(KeyId.Letter('Q'), 40))
        assertEquals(2, keyed.sym.currentPageNumber, "the page open before the press is back, so a chord draws from it")
        assertTrue(keyed.sym.pressReverted)
        assertTrue(keyed.sym.togglePending, "the chord still sees Sym held")
        val chorded = ModifierMachine.symChordUsed(keyed)
        val released = symUp(chorded, 150)
        assertEquals(Action.Ignored, released.action)
        assertEquals(2, released.state.sym.currentPageNumber)
    }

    @Test
    fun `case 91 - a key that did not count as a chord still leaves the pages alone at the release`() {
        val pressed = symDown(ModifierState(), 0)
        val keyed = ModifierMachine.onOtherKeyDown(pressed.state, down(KeyId.Letter('Q'), 40))
        val released = symUp(keyed, 150)
        assertEquals(Action.Ignored, released.action)
        assertEquals(0, released.state.sym.currentPageNumber)
        assertFalse(released.state.sym.pressReverted)
    }

    @Test
    fun `case 92 - the double tap's second press takes no step and its release opens the chooser`() {
        val first = symUp(symDown(ModifierState(), 0).state, 60).state
        assertEquals(1, first.sym.currentPageNumber)
        val second = symDown(first, 250)
        assertEquals(1, second.state.sym.currentPageNumber)
        assertTrue(second.state.sym.secondTapPending)
        val released = symUp(second.state, 300)
        assertEquals(Action.RunCommand(KeyCommands.OPEN_SYM_PAGE_CHOOSER), released.action)
        assertEquals(0, released.state.sym.currentPageNumber)
    }

    @Test
    fun `case 93 - with the chooser off, two quick presses step two pages, each on its way down`() {
        val s = on.copy(symDoubleTapChooser = false)
        val first = symUp(symDown(ModifierState(), 0, s).state, 60).state
        val second = symDown(first, 250, s)
        assertEquals(2, second.state.sym.currentPageNumber)
    }

    @Test
    fun `case 94 - the long hold takes the page back and launches the assistant`() {
        val s = on.copy(symLongPressAssistantEnabled = true)
        val pressed = symDown(ModifierState(), 0, s)
        assertEquals(1, pressed.state.sym.currentPageNumber)
        val fired = ModifierMachine.symAssistantTimerFired(pressed.state, 600, s)
        assertEquals(Action.RunCommand(KeyCommands.LAUNCH_ASSISTANT), fired.action)
        assertEquals(0, fired.state.sym.currentPageNumber)
        val released = symUp(fired.state, 700)
        assertEquals(0, released.state.sym.currentPageNumber)
    }

    @Test
    fun `case 95 - Shift during the hold is no chord, and repeats take no extra step`() {
        val pressed = symDown(ModifierState(), 0)
        val repeated = symDown(pressed.state, 400, repeat = 1)
        assertEquals(Action.Ignored, repeated.action)
        assertEquals(1, repeated.state.sym.currentPageNumber)
        val shifted = ModifierMachine.forgetSymTapOnModifierDown(repeated.state, ModifierKey.SHIFT)
        assertEquals(1, symUp(shifted, 500).state.sym.currentPageNumber)
    }

    @Test
    fun `case 96 - without a text box the press opens nothing`() {
        val pressed = symDown(ModifierState(), 0, field = false)
        assertEquals(0, pressed.state.sym.currentPageNumber)
        assertFalse(pressed.state.sym.openedOnPress)
        assertNull(pressed.state.sym.lastTapUpAtMs)
    }

    @Test
    fun `a key with no Sym press open is left alone`() {
        val state = ModifierState(sym = SymSessionState(currentPageNumber = 2))
        assertEquals(state.sym, ModifierMachine.onOtherKeyDown(state, down(KeyId.Letter('Q'), 0)).sym)
        assertEquals(state, ModifierMachine.revertPressStep(state))
    }
}
