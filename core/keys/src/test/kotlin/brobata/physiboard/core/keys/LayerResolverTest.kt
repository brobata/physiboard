package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * spec: keys-and-modifiers.md SS21 (T12-T15, T21, T49-T59); layers-sym-alt.md SS14
 * (chords/session cases 8-18, Alt cases 19-23).
 */
class LayerResolverTest {

    private val settings = ModifierSettings()
    private val resolverSettings = LayerResolver.LayerResolverSettings()
    private val field = LayerResolver.Context(hasEditableField = true)
    private val numericField = LayerResolver.Context(hasEditableField = true, isNumericField = true)

    private fun down(key: KeyId, timeMs: Long = 0, repeat: Int = 0, meta: ModifierFlags = ModifierFlags()) =
        KeyStroke(key, KeyEdge.DOWN, repeat, timeMs, meta)

    private fun letter(c: Char) = KeyId.Letter(c)

    private fun titanElitePlus26LetterMap(): DeviceLayerMap = DeviceLayerMap(
        mapOf(
            letter('Q') to "0", letter('W') to "1", letter('E') to "2", letter('R') to "3",
            letter('V') to "?",
        ),
    )

    private fun shippedEmojiPage(): SymPageMap = SymPageMap(
        mapOf(letter('C') to SymPageEntry("😅"), letter('Q') to SymPageEntry("😀")),
    )

    private fun shippedSymbolsPage(): SymPageMap = SymPageMap(
        mapOf(letter('C') to SymPageEntry("&")),
    )

    private fun ctrlMappingDefaults(): CtrlMappingTable = CtrlMappingTable(
        mapOf(
            letter('A') to CtrlMapping.NamedAction("select_all"),
            letter('V') to CtrlMapping.NamedAction("paste"),
            letter('Z') to CtrlMapping.NamedAction("undo"),
            letter('N') to CtrlMapping.NamedAction("move_word_left"),
            letter('E') to CtrlMapping.Keycode(KeyId.Control(ControlKey.DPAD_UP)),
        ),
    )

    private fun layout(
        deviceLayer: DeviceLayerMap = titanElitePlus26LetterMap(),
        ctrlMappings: CtrlMappingTable = ctrlMappingDefaults(),
        symPagesConfig: SymPagesConfig = SymPagesConfig(),
        baseLayout: LayoutMap = LayoutMap(),
    ) = LayoutDescription(
        baseLayout = baseLayout,
        deviceLayer = deviceLayer,
        emojiPage = shippedEmojiPage(),
        symbolsPage = shippedSymbolsPage(),
        ctrlMappings = ctrlMappings,
        symPagesConfig = symPagesConfig,
    )

    private fun resolve(
        state: ModifierState,
        stroke: KeyStroke,
        l: LayoutDescription = layout(),
        context: LayerResolver.Context = field,
        typing: TypingSessionState = TypingSessionState(),
    ) = LayerResolver.resolveKeyDown(state, typing, stroke, l, settings, resolverSettings, context)

    // T12-T15: Ctrl resolution ------------------------------------------------------------------

    @Test
    fun `T12 - a Ctrl one-shot chord runs the Fn Layer mapping and consumes the one-shot`() {
        val state = ModifierState(ctrl = CtrlState(oneShot = true))
        val result = resolve(state, down(letter('A'), 100))
        assertEquals(Action.Edit(EditEffect.SELECT_ALL), result.action)
        assertFalse(result.state.ctrl.oneShot)
    }

    @Test
    fun `T13 - a physical Ctrl combo forwards to the app by default`() {
        val result = resolve(ModifierState(), down(letter('A'), 0, meta = ModifierFlags(ctrl = true)))
        assertEquals(Action.ForwardAsCtrlCombo(letter('A')), result.action)
    }

    @Test
    fun `T14 - nav_mode_ctrl_hold_enabled runs the mapping for a held Ctrl combo`() {
        val navHoldSettings = settings.copy(navModeCtrlHoldEnabled = true)
        val result = LayerResolver.resolveKeyDown(
            ModifierState(), TypingSessionState(), down(letter('A'), 0, meta = ModifierFlags(ctrl = true)),
            layout(), navHoldSettings, resolverSettings, field,
        )
        assertEquals(Action.Edit(EditEffect.SELECT_ALL), result.action)
    }

    @Test
    fun `T15 - layout-aware Ctrl shortcuts forward using the printed letter, not the physical key`() {
        // QWERTZ: physical Y key prints 'z'.
        val qwertz = LayoutMap(mapOf(letter('Y') to LetterEntry("z", "Z")))
        val layoutAware = settings.copy(layoutAwareCtrlShortcuts = true)
        val result = LayerResolver.resolveKeyDown(
            ModifierState(), TypingSessionState(), down(letter('Y'), 0, meta = ModifierFlags(ctrl = true)),
            layout(baseLayout = qwertz), layoutAware, resolverSettings, field,
        )
        assertEquals(Action.ForwardAsCtrlCombo(letter('Z')), result.action)
    }

    // T21, T49, T50, T59 -------------------------------------------------------------------------

    @Test
    fun `T21 - a numeric field commits the device layer character without Alt`() {
        val result = resolve(ModifierState(), down(letter('Q'), 0), context = numericField)
        assertEquals(Action.Commit("0"), result.action)
    }

    @Test
    fun `T21b - a numeric field with a Ctrl one-shot does not commit the device layer character`() {
        val result = resolve(ModifierState(ctrl = CtrlState(oneShot = true)), down(letter('Q'), 0), context = numericField)
        assertFalse(result.action == Action.Commit("0"))
    }

    @Test
    fun `T49 - Shift-Backspace with no selection deletes the character after the caret`() {
        val shiftDeleteSettings = LayerResolver.LayerResolverSettings(shiftBackspaceDelete = true)
        val result = LayerResolver.resolveKeyDown(
            ModifierState(), TypingSessionState(), down(KeyId.Control(ControlKey.BACKSPACE), 0, meta = ModifierFlags(shift = true)),
            layout(), settings, shiftDeleteSettings, field,
        )
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_FORWARD), result.action)
    }

    @Test
    fun `T50 - a forward-delete alternative is not intercepted when there is a selection`() {
        val shiftDeleteSettings = LayerResolver.LayerResolverSettings(shiftBackspaceDelete = true)
        val selectionField = field.copy(hasSelection = true)
        val result = LayerResolver.resolveKeyDown(
            ModifierState(), TypingSessionState(), down(KeyId.Control(ControlKey.BACKSPACE), 0, meta = ModifierFlags(shift = true)),
            layout(), settings, shiftDeleteSettings, selectionField,
        )
        assertFalse(result.action == Action.Edit(EditEffect.DELETE_CHAR_FORWARD))
    }

    @Test
    fun `T59 - a numeric field still pastes on a Ctrl-held V instead of typing the Alt digit`() {
        val result = resolve(ModifierState(), down(letter('V'), 0, meta = ModifierFlags(ctrl = true)), context = numericField)
        assertEquals(Action.Edit(EditEffect.PASTE), result.action)
    }

    // T56-T58 --------------------------------------------------------------------------------

    @Test
    fun `T56 - Enter clears a Shift one-shot before anything else`() {
        val result = resolve(ModifierState(shift = ShiftState(value = ShiftValue.ONE_SHOT)), down(KeyId.Control(ControlKey.ENTER), 0))
        assertEquals(ShiftValue.OFF, result.state.shift.value)
    }

    @Test
    fun `T58 - Ctrl+Space clears a nav-mode latch and cancels the nav notification`() {
        val state = ModifierState(ctrl = CtrlState(latched = true, latchFromNavMode = true))
        val result = resolve(state, down(KeyId.Control(ControlKey.SPACE), 0, meta = ModifierFlags(ctrl = true)))
        assertFalse(result.state.ctrl.latched)
        assertEquals(
            Action.Multiple(listOf(Action.RunCommand(KeyCommands.EXIT_NAV_MODE), Action.RunCommand(KeyCommands.SWITCH_LAYOUT))),
            result.action,
        )
    }

    // layers-sym-alt.md SS14 chords/session: cases 8-18 -----------------------------------------

    @Test
    fun `case 8 - Sym+C runs the copy edit shortcut and leaves the page closed`() {
        val symHeld = ModifierState(sym = SymSessionState(togglePending = true))
        val result = resolve(symHeld, down(letter('C'), 0))
        assertEquals(Action.Edit(EditEffect.COPY), result.action)
        val (afterUp, _) = ModifierMachine.symUp(result.state, KeyStroke(KeyId.Modifier(ModifierKey.SYM), KeyEdge.UP, 0, 10), true, SymPagesConfig())
        assertEquals(0, afterUp.sym.currentPageNumber)
    }

    @Test
    fun `case 9 - Sym+C with edit shortcuts off commits the emoji`() {
        val symHeld = ModifierState(sym = SymSessionState(togglePending = true))
        val noEditShortcuts = settings.copy(symEditShortcutsEnabled = false)
        val result = LayerResolver.resolveKeyDown(symHeld, TypingSessionState(), down(letter('C'), 0), layout(), noEditShortcuts, resolverSettings, field)
        assertEquals(Action.Commit("😅"), result.action)
    }

    @Test
    fun `case 10 - Sym+C with symbols first in the order commits the ampersand`() {
        val symHeld = ModifierState(sym = SymSessionState(togglePending = true))
        val noEditShortcuts = settings.copy(symEditShortcutsEnabled = false)
        val symbolsFirst = layout(symPagesConfig = SymPagesConfig(order = listOf(SymPageId.SYMBOLS, SymPageId.EMOJI)))
        val result = LayerResolver.resolveKeyDown(symHeld, TypingSessionState(), down(letter('C'), 0), symbolsFirst, noEditShortcuts, resolverSettings, field)
        assertEquals(Action.Commit("&"), result.action)
    }

    @Test
    fun `case 11 - a shifted Sym chord uses the page's uppercase entry`() {
        val symHeld = ModifierState(sym = SymSessionState(togglePending = true))
        val noEditShortcuts = settings.copy(symEditShortcutsEnabled = false)
        val customSymbols = layout(
            symPagesConfig = SymPagesConfig(order = listOf(SymPageId.SYMBOLS, SymPageId.EMOJI)),
        ).let { it.copy(symbolsPage = SymPageMap(mapOf(letter('C') to SymPageEntry("&", "§")))) }
        val result = LayerResolver.resolveKeyDown(
            symHeld, TypingSessionState(), down(letter('C'), 0, meta = ModifierFlags(shift = true)),
            customSymbols, noEditShortcuts, resolverSettings, field,
        )
        assertEquals(Action.Commit("§"), result.action)
    }

    @Test
    fun `case 12 - a Sym chord commits and auto-closes the page it just opened`() {
        var s = ModifierState()
        s = ModifierMachine.symDown(s, KeyStroke(KeyId.Modifier(ModifierKey.SYM), KeyEdge.DOWN, 0, 0), settings, true).state
        s = ModifierMachine.symUp(s, KeyStroke(KeyId.Modifier(ModifierKey.SYM), KeyEdge.UP, 0, 10), true, SymPagesConfig()).state
        assertEquals(1, s.sym.currentPageNumber)

        s = ModifierMachine.symDown(s, KeyStroke(KeyId.Modifier(ModifierKey.SYM), KeyEdge.DOWN, 0, 20), settings, true).state
        val result = resolve(s, down(letter('Q'), 30))
        assertEquals(Action.Commit("😀"), result.action)
        assertEquals(0, result.state.sym.currentPageNumber)
    }

    @Test
    fun `case 12b - with auto-close off the page stays open after a chord commit`() {
        val autoCloseOff = settings.copy(symAutoCloseEnabled = false)
        var s = ModifierState(sym = SymSessionState(currentPageNumber = 1))
        s = ModifierMachine.symDown(s, KeyStroke(KeyId.Modifier(ModifierKey.SYM), KeyEdge.DOWN, 0, 20), autoCloseOff, true).state
        val result = LayerResolver.resolveKeyDown(s, TypingSessionState(), down(letter('Q'), 30), layout(), autoCloseOff, resolverSettings, field)
        assertEquals(1, result.state.sym.currentPageNumber)
    }

    @Test
    fun `case 13 - Enter with auto-close closes the page and still reaches the app`() {
        val s = ModifierState(sym = SymSessionState(currentPageNumber = 1))
        val result = resolve(s, down(KeyId.Control(ControlKey.ENTER), 0))
        assertEquals(0, result.state.sym.currentPageNumber)
    }

    @Test
    fun `case 13b - Enter with auto-close off leaves the page open`() {
        val autoCloseOff = settings.copy(symAutoCloseEnabled = false)
        val s = ModifierState(sym = SymSessionState(currentPageNumber = 1))
        val result = LayerResolver.resolveKeyDown(s, TypingSessionState(), down(KeyId.Control(ControlKey.ENTER), 0), layout(), autoCloseOff, resolverSettings, field)
        assertEquals(1, result.state.sym.currentPageNumber)
    }

    @Test
    fun `case 14 - with Ctrl held the page is bypassed and Ctrl resolution runs`() {
        val s = ModifierState(sym = SymSessionState(currentPageNumber = 2))
        val result = resolve(s, down(letter('C'), 0, meta = ModifierFlags(ctrl = true)))
        assertEquals(2, result.state.sym.currentPageNumber)
        assertTrue(result.action is Action.ForwardAsCtrlCombo)
    }

    @Test
    fun `case 15 - Back closes an open Sym page and is consumed`() {
        val s = ModifierState(sym = SymSessionState(currentPageNumber = 2))
        val result = resolve(s, down(KeyId.Control(ControlKey.BACK), 0))
        assertEquals(0, result.state.sym.currentPageNumber)
        assertEquals(Action.Ignored, result.action)
    }

    // layers-sym-alt.md SS14 Alt: cases 19-23 -----------------------------------------------------

    @Test
    fun `case 19 - Alt held plus U commits the device layer underscore on the Elite profile`() {
        val eliteLayout = layout(deviceLayer = DeviceLayerMap(mapOf(letter('U') to "_")))
        val result = LayerResolver.resolveKeyDown(
            ModifierState(alt = AltState(oneShot = true)), TypingSessionState(), down(letter('U'), 0),
            eliteLayout, settings, resolverSettings, field,
        )
        assertEquals(Action.Commit("_"), result.action)
    }

    @Test
    fun `case 20 - an Alt one-shot then Space commits a plain space and clears the one-shot`() {
        val result = resolve(ModifierState(alt = AltState(oneShot = true)), down(KeyId.Control(ControlKey.SPACE), 0))
        assertEquals(Action.Commit(" "), result.action)
        assertFalse(result.state.alt.oneShot)
    }

    @Test
    fun `case 21 - a numeric field types the device layer digit without Alt, but not under a Ctrl one-shot`() {
        val eliteLayout = layout(deviceLayer = DeviceLayerMap(mapOf(letter('Q') to "0")))
        val plain = resolve(ModifierState(), down(letter('Q'), 0), eliteLayout, numericField)
        assertEquals(Action.Commit("0"), plain.action)
        val withCtrl = resolve(ModifierState(ctrl = CtrlState(oneShot = true)), down(letter('Q'), 0), eliteLayout, numericField)
        assertFalse(withCtrl.action == Action.Commit("0"))
    }

    // Sharp-S multi-tap exception, base plain-key resolution --------------------------------------

    @Test
    fun `an unmapped punctuation key with no layout entry passes through`() {
        val result = resolve(ModifierState(), down(KeyId.Punctuation(PunctuationKey.MINUS), 0))
        assertEquals(Action.PassThrough, result.action)
    }

    @Test
    fun `a plain letter with no layout entry falls back to its own glyph`() {
        val result = resolve(ModifierState(), down(letter('A'), 0))
        assertEquals(Action.Commit("a"), result.action)
    }

    @Test
    fun `caps lock commits an uppercase layout letter`() {
        val caps = ModifierState(shift = ShiftState(value = ShiftValue.CAPS))
        val result = resolve(caps, down(letter('A'), 0))
        assertEquals(Action.Commit("A"), result.action)
    }

    @Test
    fun `caps lock with Shift held gives lowercase`() {
        val caps = ModifierState(shift = ShiftState(value = ShiftValue.CAPS))
        val result = resolve(caps, down(letter('A'), 0, meta = ModifierFlags(shift = true)))
        assertEquals(Action.Commit("a"), result.action)
    }

    @Test
    fun `swipe-to-delete is ignored when the setting is off`() {
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.SWIPE_TO_DELETE), 0))
        assertEquals(Action.Ignored, result.action)
    }

    @Test
    fun `swipe-to-delete deletes the last word when enabled`() {
        val enabled = LayerResolver.LayerResolverSettings(swipeToDeleteEnabled = true)
        val result = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Control(ControlKey.SWIPE_TO_DELETE), 0), layout(), settings, enabled, field)
        assertEquals(Action.Edit(EditEffect.DELETE_WORD_BACKWARD), result.action)
    }

    @Test
    fun `no editable field passes every non-modifier stroke through`() {
        val noField = LayerResolver.Context(hasEditableField = false)
        val result = resolve(ModifierState(), down(letter('A'), 0), context = noField)
        assertEquals(Action.PassThrough, result.action)
        assertNull(result.typing.pendingLongPress)
    }
}
