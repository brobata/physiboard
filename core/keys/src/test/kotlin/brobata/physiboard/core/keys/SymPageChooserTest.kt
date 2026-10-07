package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS5.10 (the double tap and the chooser), SS14 cases 42-53. */
class SymPageChooserTest {

    private val sym = KeyId.Modifier(ModifierKey.SYM)
    private val settings = ModifierSettings()
    private val pages = SymPagesConfig() // Emoji, Symbols enabled; cycle 0, 1, 2

    private fun down(key: KeyId, t: Long, repeat: Int = 0) = KeyStroke(key, KeyEdge.DOWN, repeat, t)
    private fun up(key: KeyId, t: Long) = KeyStroke(key, KeyEdge.UP, 0, t)

    private fun tap(state: ModifierState, downAt: Long, upAt: Long, s: ModifierSettings = settings): ModifierMachine.Result {
        val afterDown = ModifierMachine.symDown(state, down(sym, downAt), s, hasEditableField = true).state
        return ModifierMachine.symUp(afterDown, up(sym, upAt), hasEditableField = true, pages = pages)
    }

    // The trigger ------------------------------------------------------------------------------

    @Test
    fun `case 42 - one tap still cycles one page`() {
        val r = tap(ModifierState(), 0, 60)
        assertEquals(Action.StateOnly, r.action)
        assertEquals(1, r.state.sym.currentPageNumber)
    }

    @Test
    fun `case 43 - a second tap within 300 ms opens the chooser and takes back the first step`() {
        val first = tap(ModifierState(), 0, 60).state
        val second = tap(first, 300, 350)
        assertEquals(Action.RunCommand(KeyCommands.OPEN_SYM_PAGE_CHOOSER), second.action)
        assertEquals(0, second.state.sym.currentPageNumber, "the page open before the double tap (none) is back")
    }

    @Test
    fun `case 44 - from an open page the double tap leaves that page open under the chooser`() {
        val onSymbols = ModifierState(sym = SymSessionState(currentPageNumber = 2))
        val first = tap(onSymbols, 0, 50).state
        assertEquals(0, first.sym.currentPageNumber)
        val second = tap(first, 120, 170)
        assertEquals(Action.RunCommand(KeyCommands.OPEN_SYM_PAGE_CHOOSER), second.action)
        assertEquals(2, second.state.sym.currentPageNumber)
    }

    @Test
    fun `case 45 - a second tap later than 300 ms is just another cycle step`() {
        val first = tap(ModifierState(), 0, 60).state
        val second = tap(first, 361, 400)
        assertEquals(Action.StateOnly, second.action)
        assertEquals(2, second.state.sym.currentPageNumber)
    }

    @Test
    fun `case 46 - a third quick tap after the chooser opened starts a new streak, not another chooser`() {
        val first = tap(ModifierState(), 0, 40).state
        val second = tap(first, 100, 140).state
        val third = tap(second, 200, 240)
        assertEquals(Action.StateOnly, third.action, "the chooser consumed the streak")
        assertEquals(1, third.state.sym.currentPageNumber)
    }

    @Test
    fun `case 47 - a key typed between the two taps makes them two taps`() {
        var s = tap(ModifierState(), 0, 40).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Letter('A'), 80))
        val second = tap(s, 120, 160)
        assertEquals(Action.StateOnly, second.action)
        assertEquals(2, second.state.sym.currentPageNumber)
    }

    @Test
    fun `case 47b - Shift, Ctrl or Alt pressed between the taps also makes them two taps`() {
        for (modifier in listOf(ModifierKey.SHIFT, ModifierKey.CTRL, ModifierKey.ALT)) {
            var s = tap(ModifierState(), 0, 40).state
            s = ModifierMachine.forgetSymTapOnModifierDown(s, modifier)
            assertEquals(Action.StateOnly, tap(s, 120, 160).action, "$modifier between the taps")
        }
        val s = tap(ModifierState(), 0, 40).state
        assertEquals(s, ModifierMachine.forgetSymTapOnModifierDown(s, ModifierKey.SYM), "Sym itself is the second tap, not a break")
    }

    @Test
    fun `case 48 - a chord on the second press opens no chooser and no page`() {
        val first = tap(ModifierState(), 0, 40).state
        var s = ModifierMachine.symDown(first, down(sym, 100), settings, hasEditableField = true).state
        s = ModifierMachine.symChordUsed(s)
        val r = ModifierMachine.symUp(s, up(sym, 200), hasEditableField = true, pages = pages)
        assertEquals(Action.Ignored, r.action)
        assertEquals(1, r.state.sym.currentPageNumber)
    }

    @Test
    fun `case 49 - with sym_double_tap_chooser off, two quick taps step two pages as before`() {
        val off = settings.copy(symDoubleTapChooser = false)
        val first = tap(ModifierState(), 0, 40, off).state
        val second = tap(first, 100, 140, off)
        assertEquals(Action.StateOnly, second.action)
        assertEquals(2, second.state.sym.currentPageNumber)
    }

    @Test
    fun `case 50 - the assistant hold on the second press wins and the chooser stays shut`() {
        val assistant = settings.copy(symLongPressAssistantEnabled = true)
        val first = tap(ModifierState(), 0, 40, assistant).state
        val held = ModifierMachine.symDown(first, down(sym, 100), assistant, hasEditableField = true).state
        val fired = ModifierMachine.symAssistantTimerFired(held, 700, assistant)
        assertEquals(Action.RunCommand(KeyCommands.LAUNCH_ASSISTANT), fired.action)
        val r = ModifierMachine.symUp(fired.state, up(sym, 800), hasEditableField = true, pages = pages)
        assertEquals(Action.Ignored, r.action)
    }

    @Test
    fun `without a field Sym taps never open the chooser`() {
        var s = ModifierMachine.symDown(ModifierState(), down(sym, 0), settings, hasEditableField = false).state
        s = ModifierMachine.symUp(s, up(sym, 40), hasEditableField = false, pages = pages).state
        s = ModifierMachine.symDown(s, down(sym, 100), settings, hasEditableField = false).state
        val r = ModifierMachine.symUp(s, up(sym, 140), hasEditableField = false, pages = pages)
        assertEquals(Action.Ignored, r.action)
    }

    // The chooser ------------------------------------------------------------------------------

    @Test
    fun `case 51 - every page is listed in cycle order, enabled or not, picker modes after the picker`() {
        val baseline = SymPagesConfig(
            emojiEnabled = false, symbolsEnabled = true, clipboardEnabled = false, emojiPickerEnabled = true, gifEnabled = false,
            order = listOf(SymPageId.EMOJI_PICKER, SymPageId.SYMBOLS, SymPageId.CLIPBOARD, SymPageId.EMOJI),
        )
        val rows = SymPageChooser.entries(baseline)
        assertEquals(
            listOf(
                SymChooserTarget.EMOJI_PICKER, SymChooserTarget.KAOMOJI, SymChooserTarget.UNICODE_SYMBOLS,
                SymChooserTarget.SYMBOLS, SymChooserTarget.CLIPBOARD, SymChooserTarget.EMOJI, SymChooserTarget.GIF,
            ),
            rows.map { it.target },
        )
        assertEquals(listOf(true, true, true, true, false, false, false), rows.map { it.inCycle })
    }

    @Test
    fun `case 52 - each letter opens its page, Shift is ignored, Back and Sym dismiss`() {
        val expected = mapOf('E' to SymChooserTarget.EMOJI, 'S' to SymChooserTarget.SYMBOLS, 'C' to SymChooserTarget.CLIPBOARD,
            'P' to SymChooserTarget.EMOJI_PICKER, 'K' to SymChooserTarget.KAOMOJI, 'U' to SymChooserTarget.UNICODE_SYMBOLS, 'G' to SymChooserTarget.GIF)
        for ((letter, target) in expected) {
            assertEquals(SymPageChooser.KeyOutcome.Open(target), SymPageChooser.onKeyDown(KeyId.Letter(letter), isRepeat = false))
        }
        assertEquals(SymChooserTarget.GIF, SymPageChooser.targetFor('g'))
        assertEquals(SymPageChooser.KeyOutcome.Swallow, SymPageChooser.onKeyDown(KeyId.Modifier(ModifierKey.SHIFT), isRepeat = false))
        assertEquals(SymPageChooser.KeyOutcome.Dismiss, SymPageChooser.onKeyDown(KeyId.Control(ControlKey.BACK), isRepeat = false))
        assertEquals(SymPageChooser.KeyOutcome.Dismiss, SymPageChooser.onKeyDown(sym, isRepeat = false))
        assertEquals(SymPageChooser.KeyOutcome.Swallow, SymPageChooser.onKeyDown(KeyId.Letter('E'), isRepeat = true))
    }

    @Test
    fun `case 53 - any other key closes the chooser and does what it always does`() {
        assertNull(SymPageChooser.targetFor('X'))
        assertEquals(SymPageChooser.KeyOutcome.CloseAndPassOn, SymPageChooser.onKeyDown(KeyId.Letter('X'), isRepeat = false))
        assertEquals(SymPageChooser.KeyOutcome.CloseAndPassOn, SymPageChooser.onKeyDown(KeyId.Control(ControlKey.SPACE), isRepeat = false))
        assertEquals(SymPageChooser.KeyOutcome.CloseAndPassOn, SymPageChooser.onKeyDown(KeyId.Modifier(ModifierKey.ALT), isRepeat = false))
        assertEquals(SymPageChooser.KeyOutcome.CloseAndPassOn, SymPageChooser.onKeyDown(KeyId.Modifier(ModifierKey.FN), isRepeat = false), "Fn arrives as a repeat the chooser never consumed")
    }

    @Test
    fun `the GIF page is page 6, a panel, last in the default order and off by default`() {
        assertEquals(6, SymPageId.GIF.pageNumber)
        assertEquals(SymPageId.GIF, SymPageId.DEFAULT_ORDER.last { !it.isCustom }, "last of the shipped pages, before the user's own")
        assertEquals(listOf(0, 1, 2), SymPagesConfig().cycle)
        assertEquals(listOf(0, 1, 2, 6), SymPagesConfig(gifEnabled = true).cycle)
        assertEquals(SymPageId.GIF, SymPageId.forPageNumber(6))
        assertNull(SymPageId.forPageNumber(5), "the Device page stays dropped")
    }

    // The user's own pages, layers-sym-alt.md SS4.6 -------------------------------------------

    @Test
    fun `case 54 - the user's own pages are pages 7 to 9, key layers, off by default`() {
        assertEquals(listOf(7, 8, 9), SymPageId.CUSTOM.map { it.pageNumber })
        assertTrue(SymPageId.CUSTOM.all { it.isKeyLayer && it.isCustom })
        assertEquals(listOf(0, 1, 2), SymPagesConfig().cycle)
        assertEquals(listOf(0, 1, 2, 8), SymPagesConfig(custom2Enabled = true).cycle)
    }

    @Test
    fun `case 55 - a page of the user's own is listed only when set up, under its own name`() {
        val config = SymPagesConfig(custom1Enabled = true)
        val rows = SymPageChooser.entries(config, mapOf(SymPageId.CUSTOM_1 to "Polish", SymPageId.CUSTOM_3 to "  "))
        val custom = rows.filter { it.target.page.isCustom }
        assertEquals(listOf(SymChooserTarget.CUSTOM_1, SymChooserTarget.CUSTOM_3), custom.map { it.target })
        assertEquals(listOf("Polish", "My page 3"), custom.map { it.label })
        assertEquals(listOf(true, false), custom.map { it.inCycle })
        assertEquals(listOf('M', 'N', 'B'), listOf(SymChooserTarget.CUSTOM_1, SymChooserTarget.CUSTOM_2, SymChooserTarget.CUSTOM_3).map { it.letter })
    }

    @Test
    fun `case 56 - the letter of a page that is not listed closes the chooser and types`() {
        val listed = setOf(SymChooserTarget.EMOJI, SymChooserTarget.CUSTOM_1)
        assertEquals(SymPageChooser.KeyOutcome.Open(SymChooserTarget.CUSTOM_1), SymPageChooser.onKeyDown(KeyId.Letter('M'), isRepeat = false, listed = listed))
        assertEquals(SymPageChooser.KeyOutcome.CloseAndPassOn, SymPageChooser.onKeyDown(KeyId.Letter('N'), isRepeat = false, listed = listed))
    }

    @Test
    fun `case 57 - a Sym chord draws from the first switched-on key layer, the user's own pages included`() {
        val myPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("ą")))
        val layout = LayoutDescription(
            baseLayout = LayoutMap(),
            deviceLayer = DeviceLayerMap(),
            emojiPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("😀"))),
            symbolsPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("~"))),
            ctrlMappings = CtrlMappingTable(),
            symPagesConfig = SymPagesConfig(emojiEnabled = false, symbolsEnabled = true, custom1Enabled = true, order = listOf(SymPageId.CUSTOM_1, SymPageId.SYMBOLS)),
            customPages = mapOf(SymPageId.CUSTOM_1 to myPage),
        )
        val symDown = ModifierMachine.symDown(ModifierState(), down(sym, 0), settings, hasEditableField = true).state
        val chord = LayerResolver.resolveKeyDown(symDown, TypingSessionState(), down(KeyId.Letter('Q'), 10), layout, settings, LayerResolver.LayerResolverSettings(), LayerResolver.Context())
        assertEquals(Action.Commit("ą"), chord.action)
    }

    @Test
    fun `case 58 - a key on an open page of the user's own types its text`() {
        val layout = LayoutDescription(
            baseLayout = LayoutMap(),
            deviceLayer = DeviceLayerMap(),
            emojiPage = SymPageMap(),
            symbolsPage = SymPageMap(),
            ctrlMappings = CtrlMappingTable(),
            customPages = mapOf(SymPageId.CUSTOM_2 to SymPageMap(mapOf(KeyId.Letter('A') to SymPageEntry("你好")))),
        )
        val open = ModifierState().let { it.copy(sym = it.sym.copy(currentPageNumber = 8)) }
        val r = LayerResolver.resolveKeyDown(open, TypingSessionState(), down(KeyId.Letter('A'), 0), layout, settings, LayerResolver.LayerResolverSettings(), LayerResolver.Context())
        assertEquals(Action.Commit("你好"), r.action)
        assertEquals(0, r.state.sym.currentPageNumber, "sym_auto_close closes it like any key layer")
    }
}
