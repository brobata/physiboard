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
        settings: ModifierSettings = this.settings,
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

    // SS7.7, Alt+Backspace (`alt_backspace_delete`) ---------------------------------------------

    private val backspace = KeyId.Control(ControlKey.BACKSPACE)
    private val lineSettings = LayerResolver.LayerResolverSettings(altBackspace = AltBackspaceAction.DELETE_TO_LINE_START)
    private val forwardSettings = LayerResolver.LayerResolverSettings(altBackspace = AltBackspaceAction.DELETE_FORWARD)

    private fun altBackspace(
        state: ModifierState,
        resolver: LayerResolver.LayerResolverSettings,
        meta: ModifierFlags = ModifierFlags(),
        context: LayerResolver.Context = field,
    ) = LayerResolver.resolveKeyDown(state, TypingSessionState(), down(backspace, 0, meta = meta), layout(), settings, resolver, context)

    @Test
    fun `T49b - by default Alt+Backspace deletes one character, as Backspace alone`() {
        val result = altBackspace(ModifierState(), resolverSettings, meta = ModifierFlags(alt = true))
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD), result.action)
    }

    @Test
    fun `T49c - held Alt deletes to the start of the line`() {
        val result = altBackspace(ModifierState(), lineSettings, meta = ModifierFlags(alt = true))
        assertEquals(Action.Edit(EditEffect.DELETE_TO_LINE_START), result.action)
    }

    @Test
    fun `T49d - a tapped Alt deletes to the start of the line and is spent`() {
        val result = altBackspace(ModifierState(alt = AltState(oneShot = true)), lineSettings)
        assertEquals(Action.Edit(EditEffect.DELETE_TO_LINE_START), result.action)
        assertFalse(result.state.alt.oneShot)
    }

    @Test
    fun `T49e - a locked Alt deletes to the start of the line and stays locked`() {
        val result = altBackspace(ModifierState(alt = AltState(latched = true)), lineSettings)
        assertEquals(Action.Edit(EditEffect.DELETE_TO_LINE_START), result.action)
        assertTrue(result.state.alt.latched)
    }

    @Test
    fun `T49f - with a selection Alt+Backspace is an ordinary Backspace and Alt is still spent`() {
        val result = altBackspace(ModifierState(alt = AltState(oneShot = true)), lineSettings, context = field.copy(hasSelection = true))
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD), result.action)
        assertFalse(result.state.alt.oneShot)
    }

    @Test
    fun `T49g - without Alt the line choice changes nothing`() {
        val result = altBackspace(ModifierState(), lineSettings)
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD), result.action)
    }

    @Test
    fun `T49h - Ctrl+Alt+Backspace stays Ctrl's, whatever the Alt choice`() {
        for (ctrl in listOf(ModifierState(ctrl = CtrlState(oneShot = true)), ModifierState())) {
            val meta = if (ctrl.ctrl.oneShot) ModifierFlags(alt = true) else ModifierFlags(alt = true, ctrl = true)
            val expected = altBackspace(ctrl, resolverSettings, meta = meta).action
            assertEquals(expected, altBackspace(ctrl, lineSettings, meta = meta).action)
        }
    }

    @Test
    fun `T49i - Shift's forward delete wins over the line delete, and leaves a tapped Alt armed`() {
        val both = lineSettings.copy(shiftBackspaceDelete = true)
        val result = altBackspace(ModifierState(alt = AltState(oneShot = true)), both, meta = ModifierFlags(shift = true))
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_FORWARD), result.action)
        assertTrue(result.state.alt.oneShot)
    }

    @Test
    fun `T49j - with Shift's row off, Alt+Shift+Backspace deletes to the start of the line`() {
        val result = altBackspace(ModifierState(), lineSettings, meta = ModifierFlags(alt = true, shift = true))
        assertEquals(Action.Edit(EditEffect.DELETE_TO_LINE_START), result.action)
    }

    @Test
    fun `T49k - the forward choice deletes forward and spends a tapped Alt`() {
        val result = altBackspace(ModifierState(alt = AltState(oneShot = true)), forwardSettings)
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_FORWARD), result.action)
        assertFalse(result.state.alt.oneShot)
    }

    @Test
    fun `T49l - Shift+Backspace and Ctrl+Backspace are unchanged by the line choice`() {
        val shift = ModifierFlags(shift = true)
        assertEquals(altBackspace(ModifierState(), resolverSettings, meta = shift).action, altBackspace(ModifierState(), lineSettings, meta = shift).action)
        val ctrl = ModifierFlags(ctrl = true)
        assertEquals(altBackspace(ModifierState(), resolverSettings, meta = ctrl).action, altBackspace(ModifierState(), lineSettings, meta = ctrl).action)
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
        val result = resolve(state, down(KeyId.Control(ControlKey.SPACE), 0, meta = ModifierFlags(ctrl = true)), context = field.copy(canSwitchLayout = true))
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
    fun `case 13 - Enter with auto-close closes the page`() {
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
    fun `an unmapped punctuation key with no layout entry falls back to its own glyph`() {
        // Defect 4 (the same family as Space/Enter/Backspace): the Titan's base layout maps only
        // letters, so an ordinary period, comma or digit key must still reach `:core:text`, not
        // vanish as Action.PassThrough the way it used to.
        val result = resolve(ModifierState(), down(KeyId.Punctuation(PunctuationKey.MINUS), 0))
        assertEquals(Action.Commit("-"), result.action)
    }

    @Test
    fun `an ordinary period commits its default glyph instead of passing through`() {
        val result = resolve(ModifierState(), down(KeyId.Punctuation(PunctuationKey.PERIOD), 0))
        assertEquals(Action.Commit("."), result.action)
    }

    @Test
    fun `an ordinary comma commits its default glyph instead of passing through`() {
        val result = resolve(ModifierState(), down(KeyId.Punctuation(PunctuationKey.COMMA), 0))
        assertEquals(Action.Commit(","), result.action)
    }

    @Test
    fun `an ordinary digit commits its default glyph instead of passing through`() {
        val result = resolve(ModifierState(), down(KeyId.Digit('5'), 0))
        assertEquals(Action.Commit("5"), result.action)
    }

    @Test
    fun `a control key with no character of its own still passes through`() {
        // TAB carries no text (unlike Space, Enter and Backspace, which get their own baseline
        // action in withBaselineControlAction): CharacterResolution.defaultCharacter is null for
        // every KeyId.Control, so this remains a genuine pass-to-app, not the defect-4 gap.
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.TAB), 0))
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

    // Defect 1: an ordinary Space, Enter or Backspace must reach :core:text's smart rules
    // (text-input.md SS5-SS8), not fall out as Action.PassThrough just because none of them has a
    // base-layout entry. -----------------------------------------------------------------------

    @Test
    fun `an ordinary Space commits a plain space instead of passing through`() {
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.SPACE), 0))
        assertEquals(Action.Commit(" "), result.action)
    }

    @Test
    fun `an ordinary Enter resolves to a newline edit instead of passing through`() {
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.ENTER), 0))
        assertEquals(Action.Edit(EditEffect.NEWLINE), result.action)
    }

    @Test
    fun `an ordinary Backspace resolves to a backward-delete edit instead of passing through`() {
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.BACKSPACE), 0))
        assertEquals(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD), result.action)
    }

    @Test
    fun `Ctrl held with no mapping still leaves Enter passed through, not turned into a newline`() {
        val ctrlOneShot = ModifierState(ctrl = CtrlState(oneShot = true))
        val result = resolve(ctrlOneShot, down(KeyId.Control(ControlKey.ENTER), 0))
        assertEquals(Action.PassThrough, result.action)
    }

    @Test
    fun `no editable field leaves an ordinary Enter passed through, not turned into a newline`() {
        val noField = LayerResolver.Context(hasEditableField = false)
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.ENTER), 0), context = noField)
        assertEquals(Action.PassThrough, result.action)
    }

    // Review finding A8 (2026-09-24): Ctrl+Space with nothing to switch to. ---------------------

    @Test
    fun `A8 - Fn+Space with no other layout to switch to is not a chord and is forwarded like any unmapped Fn key (spec SS7-3 step 1)`() {
        val result = resolve(ModifierState(), down(KeyId.Control(ControlKey.SPACE), 0, meta = ModifierFlags(ctrl = true)))
        assertEquals(Action.ForwardAsCtrlCombo(KeyId.Control(ControlKey.SPACE)), result.action)
    }

    @Test
    fun `A8 - Ctrl one-shot then Space with no other layout passes Space to the app and consumes the one-shot (spec SS7-3 step 2)`() {
        val result = resolve(ModifierState(ctrl = CtrlState(oneShot = true)), down(KeyId.Control(ControlKey.SPACE), 0))
        assertEquals(Action.PassThrough, result.action)
        assertFalse(result.state.ctrl.oneShot)
    }

    @Test
    fun `A8 - Ctrl+Space with another layout available runs the switch and clears Ctrl (spec SS7-5, T57 shape)`() {
        val result = resolve(ModifierState(ctrl = CtrlState(oneShot = true)), down(KeyId.Control(ControlKey.SPACE), 0), context = field.copy(canSwitchLayout = true))
        assertEquals(Action.RunCommand(KeyCommands.SWITCH_LAYOUT), result.action)
        assertFalse(result.state.ctrl.oneShot)
    }

    @Test
    fun `ctrl_space_layout_switch off - Ctrl+Space with another layout available passes Space through instead of switching (spec SS7-5)`() {
        val result = resolve(
            ModifierState(ctrl = CtrlState(oneShot = true)), down(KeyId.Control(ControlKey.SPACE), 0),
            settings = settings.copy(ctrlSpaceLayoutSwitch = false), context = field.copy(canSwitchLayout = true),
        )
        assertEquals(Action.PassThrough, result.action)
    }

    @Test
    fun `alt_enter_layout_switch on - Alt+Enter with another layout available runs the switch and clears Alt (spec SS7-5)`() {
        val result = resolve(
            ModifierState(alt = AltState(oneShot = true)), down(KeyId.Control(ControlKey.ENTER), 0),
            settings = settings.copy(altEnterLayoutSwitch = true), context = field.copy(canSwitchLayout = true),
        )
        assertEquals(Action.RunCommand(KeyCommands.SWITCH_LAYOUT), result.action)
        assertEquals(AltState(), result.state.alt)
        assertTrue(result.typing.consumeEnterRepeatsUntilUp)
    }

    @Test
    fun `alt_enter_layout_switch off (the default) - Alt+Enter is a plain newline that clears the Alt one-shot (spec SS6-4)`() {
        val result = resolve(ModifierState(alt = AltState(oneShot = true)), down(KeyId.Control(ControlKey.ENTER), 0), context = field.copy(canSwitchLayout = true))
        assertEquals(Action.Edit(EditEffect.NEWLINE), result.action)
    }

    @Test
    fun `alt_enter_layout_switch on but only one layout installed - Alt+Enter is a plain newline (spec SS7-5)`() {
        val result = resolve(ModifierState(alt = AltState(oneShot = true)), down(KeyId.Control(ControlKey.ENTER), 0), settings = settings.copy(altEnterLayoutSwitch = true))
        assertEquals(Action.Edit(EditEffect.NEWLINE), result.action)
    }

    @Test
    fun `Alt+Enter chord - Enter repeats are swallowed until the key-up, which is consumed, and a fresh Enter is a newline again (spec SS7-5)`() {
        val chordSettings = settings.copy(altEnterLayoutSwitch = true)
        val ctx = field.copy(canSwitchLayout = true)
        val enter = KeyId.Control(ControlKey.ENTER)
        val first = resolve(ModifierState(alt = AltState(oneShot = true)), down(enter, 0), settings = chordSettings, context = ctx)
        val repeat = resolve(first.state, down(enter, 400, repeat = 1), settings = chordSettings, context = ctx, typing = first.typing)
        assertEquals(Action.Ignored, repeat.action)
        val released = LayerResolver.resolveKeyUp(repeat.state, repeat.typing, KeyStroke(enter, KeyEdge.UP, 0, 500))
        assertEquals(Action.Ignored, released.action)
        assertFalse(released.typing.consumeEnterRepeatsUntilUp)
        val fresh = resolve(released.state, down(enter, 900), settings = chordSettings, context = ctx, typing = released.typing)
        assertEquals(Action.Edit(EditEffect.NEWLINE), fresh.action)
    }

    @Test
    fun `A8 - a Space repeat while Fn stays held does not switch layouts again and again`() {
        val ctx = field.copy(canSwitchLayout = true)
        val first = resolve(ModifierState(), down(KeyId.Control(ControlKey.SPACE), 0, meta = ModifierFlags(ctrl = true)), context = ctx)
        assertEquals(Action.RunCommand(KeyCommands.SWITCH_LAYOUT), first.action)
        val repeat = resolve(first.state, down(KeyId.Control(ControlKey.SPACE), 450, repeat = 1, meta = ModifierFlags(ctrl = true)), context = ctx, typing = first.typing)
        assertEquals(Action.Ignored, repeat.action)
    }
}
