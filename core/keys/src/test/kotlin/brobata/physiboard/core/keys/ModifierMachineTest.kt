package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: keys-and-modifiers.md SS21, test cases T1-T32 (the subset owned by [ModifierMachine]). */
class ModifierMachineTest {

    private val settings = ModifierSettings()
    private val shift = KeyId.Modifier(ModifierKey.SHIFT)
    private val ctrl = KeyId.Modifier(ModifierKey.CTRL)
    private val alt = KeyId.Modifier(ModifierKey.ALT)

    private fun down(key: KeyId, timeMs: Long, repeat: Int = 0, meta: ModifierFlags = ModifierFlags()) =
        KeyStroke(key, KeyEdge.DOWN, repeat, timeMs, meta)

    private fun up(key: KeyId, timeMs: Long, meta: ModifierFlags = ModifierFlags()) =
        KeyStroke(key, KeyEdge.UP, 0, timeMs, meta)

    // Alt+Shift layout chord. spec: keys-and-modifiers.md SS7.5 ------------------------------

    @Test
    fun `alt_shift_layout_switch on - Shift down while Alt is held runs the switch and clears both (spec SS7-5)`() {
        val chord = settings.copy(altShiftLayoutSwitch = true)
        val (held, _) = ModifierMachine.altDown(ModifierState(), down(alt, 0), chord)
        val (after, action) = ModifierMachine.shiftDown(held, down(shift, 100, meta = ModifierFlags(alt = true)), chord, canSwitchLayout = true)
        assertEquals(Action.RunCommand(KeyCommands.SWITCH_LAYOUT), action)
        assertEquals(ShiftState(), after.shift)
        assertEquals(AltState(), after.alt)
    }

    @Test
    fun `alt_shift_layout_switch on - Alt down while Shift is held runs the switch too, either order (spec SS7-5)`() {
        val chord = settings.copy(altShiftLayoutSwitch = true)
        val (held, _) = ModifierMachine.shiftDown(ModifierState(), down(shift, 0), chord)
        val (after, action) = ModifierMachine.altDown(held, down(alt, 100, meta = ModifierFlags(shift = true)), chord, canSwitchLayout = true)
        assertEquals(Action.RunCommand(KeyCommands.SWITCH_LAYOUT), action)
        assertEquals(ShiftState(), after.shift)
        assertEquals(AltState(), after.alt)
    }

    @Test
    fun `alt_shift_layout_switch off (the default) - Shift down while Alt is held is an ordinary Shift press`() {
        val (held, _) = ModifierMachine.altDown(ModifierState(), down(alt, 0), settings)
        val (after, action) = ModifierMachine.shiftDown(held, down(shift, 100, meta = ModifierFlags(alt = true)), settings, canSwitchLayout = true)
        assertEquals(Action.PassThrough, action)
        assertTrue(after.shift.pressed)
        assertTrue(after.alt.physicallyPressed)
    }

    @Test
    fun `alt_shift_layout_switch on but only one layout installed - the chord does not fire (spec SS7-5)`() {
        val chord = settings.copy(altShiftLayoutSwitch = true)
        val (held, _) = ModifierMachine.altDown(ModifierState(), down(alt, 0), chord)
        val (_, action) = ModifierMachine.shiftDown(held, down(shift, 100, meta = ModifierFlags(alt = true)), chord)
        assertEquals(Action.PassThrough, action)
    }

    // T1, T2 -------------------------------------------------------------------------------

    @Test
    fun `T1 - a Shift tap arms one-shot`() {
        val (s1, _) = ModifierMachine.shiftDown(ModifierState(), down(shift, 0), settings)
        val (s2, action) = ModifierMachine.shiftUp(s1, up(shift, 50), settings)
        assertEquals(ShiftValue.ONE_SHOT, s2.shift.value)
        assertFalse(s2.shift.pressed)
        assertEquals(Action.PassThrough, action)
    }

    @Test
    fun `T2 - the letter after a Shift one-shot consumes it`() {
        val (s1, _) = ModifierMachine.shiftDown(ModifierState(), down(shift, 0), settings)
        val (s2, _) = ModifierMachine.shiftUp(s1, up(shift, 50), settings)
        val resolution = LayerResolver.resolveKeyDown(
            s2, TypingSessionState(), down(KeyId.Letter('A'), 100), qwertyLayout(), settings, defaultResolverSettings(), editableField(),
        )
        assertEquals(Action.Commit("A"), resolution.action)
        assertEquals(ShiftValue.OFF, resolution.state.shift.value)
    }

    // T3, T4, T5, T6, T7, T8 -----------------------------------------------------------------

    @Test
    fun `T3 - a quick double tap of Shift latches caps and the layer`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 50), settings).state
        s = ModifierMachine.shiftDown(s, down(shift, 200), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 250), settings).state
        assertEquals(ShiftValue.CAPS, s.shift.value)
        assertTrue(s.shift.layerLatched)
    }

    @Test
    fun `T4 - a third Shift tap clears caps and the layer latch and is consumed`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 50), settings).state
        s = ModifierMachine.shiftDown(s, down(shift, 200), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 250), settings).state

        val (s2, action) = ModifierMachine.shiftDown(s, down(shift, 400), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
        assertFalse(s2.shift.layerLatched)
        assertEquals(Action.StateOnly, action)
    }

    @Test
    fun `T5 - a non-consecutive Shift tap does not pair for a double tap`() {
        // An intervening key breaks the consecutive-tap memory (SS5.1), so the second Shift down
        // at t=200 (only 200ms after the first, well inside the 500ms window) must NOT be treated
        // as a double tap. Left as ONE_SHOT (untouched by the intervening key at this, the
        // modifier machine's own level; a real letter would also consume it, see T2), a plain tap
        // toggles it back OFF rather than latching CAPS the way a true double tap would.
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 50), settings).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Letter('A'), 100))
        val (s2, _) = ModifierMachine.shiftDown(s, down(shift, 200), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
    }

    // -----------------------------------------------------------------------------------------
    // Regression (device report, trackpad wiring): shiftDown() has no memory of shiftUp() at all
    // -- it only compares this down's time against the PREVIOUS down's time and whether the
    // previous key was also Shift. So two shiftDown() calls back to back, with no shiftUp() ever
    // in between and effectively no time apart, are read as a real double tap and latch Caps Lock,
    // even though the physical key never came up and went back down. This is exactly what
    // `:ime`'s KeyboardSession.interceptForTrackpad used to produce: while the trackpad's trigger
    // (Space, by default) was PENDING, a Shift key-down chorded with it (ordinary when
    // capitalising the next word right after a space) got cached into the same single-slot field
    // meant for the trigger's own down, one statement before the trigger's chord-abort logic
    // replayed whatever that field held; that replay fed the modifier machine one shiftDown for a
    // key that then arrived a second time through its own, completely normal delivery a moment
    // later, both carrying the identical event timestamp. Fixed by only caching a key for trigger
    // replay when it actually matches the configured trigger (KeyboardSession no longer caches a
    // Shift/Sym/Back down that is not the trigger itself), but that fix lives in `:ime` where a
    // real KeyEvent cannot be constructed in a plain JVM test; this pins the `:core:keys` half of
    // the hazard so nothing upstream can reintroduce a double shiftDown() for one physical press.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `A7 - a second Shift down while Shift is already pressed changes nothing (spec SS5-1, two Shifts normalise to one key)`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 1_000), settings).state
        assertEquals(ShiftValue.ONE_SHOT, s.shift.value, "the first, real down arms a plain one-shot")

        // No shiftUp() call here: the physical key was never released. Holding one Shift and
        // pressing the other, or a replayed down, both arrive exactly like this.
        val (s2, action) = ModifierMachine.shiftDown(s, down(shift, 1_050), settings)
        assertEquals(s, s2, "a single physical Shift press must never be able to latch Caps Lock on its own")
        assertEquals(Action.PassThrough, action)
    }

    @Test
    fun `T6 - an intentional Shift hold with nothing typed restores the prior state`() {
        val (s1, _) = ModifierMachine.shiftDown(ModifierState(), down(shift, 0), settings)
        val (s2, action) = ModifierMachine.shiftUp(s1, up(shift, 400), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
        assertFalse(s2.shift.pressed)
        assertEquals(Action.PassThrough, action)
    }

    @Test
    fun `T7 - a letter typed during a Shift hold prevents the hold restore`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Letter('A'), 100))
        val resolution = LayerResolver.resolveKeyDown(
            s, TypingSessionState(), down(KeyId.Letter('A'), 100, meta = ModifierFlags(shift = true)),
            qwertyLayout(), settings, defaultResolverSettings(), editableField(),
        )
        assertEquals(Action.Commit("A"), resolution.action)
        s = resolution.state
        val (s2, _) = ModifierMachine.shiftUp(s, up(shift, 400), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
    }

    @Test
    fun `T8 - a second Shift down outside the double-tap window toggles one-shot off`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 50), settings).state
        val (s2, _) = ModifierMachine.shiftDown(s, down(shift, 600), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
    }

    // T9-T11, T16, T17-T19 -------------------------------------------------------------------

    @Test
    fun `T9 - a Ctrl tap arms one-shot and records the release time`() {
        val (s1, _) = ModifierMachine.ctrlDown(ModifierState(), down(ctrl, 0), settings)
        val (s2, _) = ModifierMachine.ctrlUp(s1, up(ctrl, 50), settings)
        assertTrue(s2.ctrl.oneShot)
        assertFalse(s2.ctrl.pressed)
        assertEquals(50L, s2.ctrl.lastReleaseAtMs)
    }

    @Test
    fun `T10 - a consecutive Ctrl tap within the window latches`() {
        var s = ModifierState()
        s = ModifierMachine.ctrlDown(s, down(ctrl, 0), settings).state
        s = ModifierMachine.ctrlUp(s, up(ctrl, 50), settings).state
        val (s2, _) = ModifierMachine.ctrlDown(s, down(ctrl, 200), settings)
        assertTrue(s2.ctrl.latched)
        assertFalse(s2.ctrl.oneShot)
    }

    @Test
    fun `T11 - a further Ctrl tap on a latched Ctrl un-latches it`() {
        var s = ModifierState()
        s = ModifierMachine.ctrlDown(s, down(ctrl, 0), settings).state
        s = ModifierMachine.ctrlUp(s, up(ctrl, 50), settings).state
        s = ModifierMachine.ctrlDown(s, down(ctrl, 200), settings).state
        s = ModifierMachine.ctrlUp(s, up(ctrl, 250), settings).state
        val (s2, _) = ModifierMachine.ctrlDown(s, down(ctrl, 400), settings)
        assertFalse(s2.ctrl.latched)
        assertFalse(s2.ctrl.oneShot)
    }

    @Test
    fun `T16 - Ctrl one-shot clears after being used as a held chord`() {
        var s = ModifierState()
        s = ModifierMachine.ctrlDown(s, down(ctrl, 0), settings).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Letter('A'), 100))
        s = s.copy(ctrl = s.ctrl.copy()) // A down carried Ctrl meta but is otherwise irrelevant to this unit test
        val (s2, _) = ModifierMachine.ctrlUp(s, up(ctrl, 150), settings)
        assertFalse(s2.ctrl.oneShot)
    }

    @Test
    fun `T17 - an Alt tap arms one-shot and the down is consumed`() {
        val (s1, downAction) = ModifierMachine.altDown(ModifierState(), down(alt, 0), settings)
        assertEquals(Action.Ignored, downAction)
        val (s2, _) = ModifierMachine.altUp(s1, up(alt, 50), settings)
        assertTrue(s2.alt.oneShot)
    }

    @Test
    fun `T18 - the letter after an Alt one-shot commits the device layer value and clears it`() {
        var s = ModifierState()
        s = ModifierMachine.altDown(s, down(alt, 0), settings).state
        s = ModifierMachine.altUp(s, up(alt, 50), settings).state
        val resolution = LayerResolver.resolveKeyDown(
            s, TypingSessionState(), down(KeyId.Letter('Q'), 100), qwertyLayout(), settings, defaultResolverSettings(), editableField(),
        )
        assertEquals(Action.Commit("0"), resolution.action)
        assertFalse(resolution.state.alt.oneShot)
    }

    @Test
    fun `T19 - a quick double tap of Alt latches the modifier and the layer`() {
        var s = ModifierState()
        s = ModifierMachine.altDown(s, down(alt, 0), settings).state
        s = ModifierMachine.altUp(s, up(alt, 50), settings).state
        s = ModifierMachine.altDown(s, down(alt, 200), settings).state
        s = ModifierMachine.altUp(s, up(alt, 250), settings).state
        assertTrue(s.alt.latched)
        assertTrue(s.alt.layerLatched)
    }

    @Test
    fun `T20 - Space clears an Alt one-shot and Ctrl+Space chord table is untouched here`() {
        var s = ModifierState()
        s = ModifierMachine.altDown(s, down(alt, 0), settings).state
        s = ModifierMachine.altUp(s, up(alt, 50), settings).state
        s = ModifierMachine.altDown(s, down(alt, 200), settings).state
        s = ModifierMachine.altUp(s, up(alt, 250), settings).state
        val resolution = LayerResolver.resolveKeyDown(
            s, TypingSessionState(), down(KeyId.Control(ControlKey.SPACE), 300),
            qwertyLayout(), settings, defaultResolverSettings(), editableField(),
        )
        assertEquals(Action.Commit(" "), resolution.action)
        assertFalse(resolution.state.alt.latched)
        assertFalse(resolution.state.alt.oneShot)
    }

    @Test
    fun `T22 amended (A6) - Alt down with Ctrl meta is an ordinary Alt press, the Alt+Ctrl dictation chord is dropped (spec SS22 undecided, Fn hold already dictates)`() {
        val (s2, action) = ModifierMachine.altDown(ModifierState(), down(alt, 0, meta = ModifierFlags(ctrl = true)), settings)
        assertEquals(Action.Ignored, action)
        assertTrue(s2.alt.pressed)
        assertTrue(s2.alt.oneShot)
    }

    @Test
    fun `A6 - Ctrl down with Alt meta is an ordinary Ctrl press, never a dictation toggle`() {
        val (s2, action) = ModifierMachine.ctrlDown(ModifierState(), down(ctrl, 0, meta = ModifierFlags(alt = true)), settings)
        assertEquals(Action.PassThrough, action)
        assertTrue(s2.ctrl.pressed)
        assertTrue(s2.ctrl.oneShot)
    }

    // Sym: T23-T28 -----------------------------------------------------------------------------

    private val sym = KeyId.Modifier(ModifierKey.SYM)

    @Test
    fun `T23 - a Sym tap cycles the page and consumes both events`() {
        var s = ModifierState()
        val (s1, downAction) = ModifierMachine.symDown(s, down(sym, 0), settings, hasEditableField = true)
        assertEquals(Action.Ignored, downAction)
        s = s1
        val (s2, upAction) = ModifierMachine.symUp(s, up(sym, 50), hasEditableField = true, pages = SymPagesConfig())
        assertEquals(1, s2.sym.currentPageNumber)
        assertEquals(Action.StateOnly, upAction)
    }

    @Test
    fun `T26 - Sym pressed while Alt is held clears all Alt state`() {
        val altHeld = ModifierState(alt = AltState(pressed = true, physicallyPressed = true, oneShot = true))
        val (s, action) = ModifierMachine.symDown(altHeld, down(sym, 0, meta = ModifierFlags(alt = true)), settings, hasEditableField = true)
        assertEquals(AltState(), s.alt)
        assertEquals(Action.Ignored, action)
    }

    @Test
    fun `T27 - a 600ms Sym hold launches the assistant and swallows the release`() {
        val assistantSettings = settings.copy(symLongPressAssistantEnabled = true)
        var s = ModifierState()
        s = ModifierMachine.symDown(s, down(sym, 0), assistantSettings, hasEditableField = true).state
        val (fired, action) = ModifierMachine.symAssistantTimerFired(s, 600, assistantSettings)
        assertEquals(Action.RunCommand(KeyCommands.LAUNCH_ASSISTANT), action)
        val (afterUp, upAction) = ModifierMachine.symUp(fired, up(sym, 620), hasEditableField = true, pages = SymPagesConfig())
        assertEquals(0, afterUp.sym.currentPageNumber)
        assertEquals(Action.Ignored, upAction)
    }

    @Test
    fun `T28 - a chord before the assistant timer fires cancels it`() {
        val assistantSettings = settings.copy(symLongPressAssistantEnabled = true)
        var s = ModifierState()
        s = ModifierMachine.symDown(s, down(sym, 0), assistantSettings, hasEditableField = true).state
        assertTrue(ModifierMachine.symAssistantTimerFired(s, 100, assistantSettings).action == Action.Ignored)
        s = ModifierMachine.symChordUsed(s)
        assertEquals(null, s.sym.assistantArmedAtMs)
    }

    // Fn burst: T29-T32 --------------------------------------------------------------------------

    private val fn = KeyId.Modifier(ModifierKey.FN)
    private val fnSettings = settings.copy(fnLongPressSpeechEnabled = true)

    @Test
    fun `T29 - five Fn repeats within the reset window trigger dictation and clear Ctrl and Alt`() {
        var s = ModifierState(ctrl = CtrlState(pressed = true), alt = AltState(pressed = true))
        var lastAction: Action = Action.Ignored
        for ((i, t) in listOf(400L, 450L, 500L, 550L, 600L).withIndex()) {
            val result = ModifierMachine.fnKeyDown(s, down(fn, t, repeat = i + 1), fnSettings)
            s = result.state
            lastAction = result.action
        }
        assertEquals(Action.RunCommand(KeyCommands.TOGGLE_DICTATION), lastAction)
        assertEquals(CtrlState(), s.ctrl)
        assertEquals(AltState(), s.alt)
    }

    @Test
    fun `T30 - a chord key during the burst blocks it without resetting the count`() {
        var s = ModifierState()
        for ((i, t) in listOf(400L, 450L).withIndex()) {
            s = ModifierMachine.fnKeyDown(s, down(fn, t, repeat = i + 1), fnSettings).state
        }
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Letter('E'), 470, meta = ModifierFlags(ctrl = true)))
        assertTrue(s.fnBurst.blocked)
        val result = ModifierMachine.fnKeyDown(s, down(fn, 520, repeat = 3), fnSettings)
        assertEquals(Action.StateOnly, result.action)
    }

    @Test
    fun `T31 - a burst that resets before reaching five never triggers`() {
        var s = ModifierState()
        for ((i, t) in listOf(400L, 450L, 500L).withIndex()) {
            s = ModifierMachine.fnKeyDown(s, down(fn, t, repeat = i + 1), fnSettings).state
        }
        assertEquals(3, s.fnBurst.count)
        // 200ms with no further Fn event; the next Fn event arrives after the reset window.
        val result = ModifierMachine.fnKeyDown(s, down(fn, 710, repeat = 1), fnSettings)
        assertEquals(1, result.state.fnBurst.count)
        assertEquals(Action.StateOnly, result.action)
    }

    @Test
    fun `T32 - with the speech setting off a held Fn arms Ctrl on the first repeat and never releases`() {
        val (s, action) = ModifierMachine.fnKeyDown(ModifierState(), down(fn, 400, repeat = 1), settings)
        assertTrue(s.ctrl.pressed)
        assertTrue(s.ctrl.oneShot)
        assertEquals(Action.PassThrough, action)
    }

    // T60 --------------------------------------------------------------------------------------

    @Test
    fun `T60 - finishing input while Ctrl is latched preserves it as a nav-mode latch`() {
        val state = ModifierState(
            shift = ShiftState(value = ShiftValue.CAPS, layerLatched = true),
            ctrl = CtrlState(latched = true),
            alt = AltState(latched = true, layerLatched = true),
        )
        val reset = ModifierMachine.fullReset(state, preserveNavModeLatch = true)
        assertEquals(ShiftValue.OFF, reset.shift.value)
        assertFalse(reset.shift.layerLatched)
        assertFalse(reset.alt.latched)
        assertFalse(reset.alt.layerLatched)
        assertTrue(reset.ctrl.latched)
        assertTrue(reset.ctrl.latchFromNavMode)
    }

    // Review findings A3, A4 (2026-09-24): the layer latch and the one-shot after a chorded hold.

    @Test
    fun `A3 - a second Shift tap whose down is outside 500 ms latches neither caps nor the layer (spec SS2, one double-tap window)`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 250), settings).state
        s = ModifierMachine.shiftDown(s, down(shift, 520), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 560), settings).state
        assertEquals(ShiftValue.OFF, s.shift.value)
        assertFalse(s.shift.layerLatched, "a layer latch with Shift OFF would uppercase every letter with no badge to say why")
    }

    @Test
    fun `A3 - the Shift layer latch is set exactly when the double tap latched caps (spec SS5-6, a normal double tap sets both)`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 250), settings).state
        s = ModifierMachine.shiftDown(s, down(shift, 480), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 760), settings).state
        assertEquals(ShiftValue.CAPS, s.shift.value)
        assertTrue(s.shift.layerLatched)
    }

    @Test
    fun `A3 - the Alt layer latch follows the Alt latch with the same window (spec SS5-6 mirrors Shift)`() {
        var s = ModifierState()
        s = ModifierMachine.altDown(s, down(alt, 0), settings).state
        s = ModifierMachine.altUp(s, up(alt, 50), settings).state
        s = ModifierMachine.altDown(s, down(alt, 540), settings).state
        s = ModifierMachine.altUp(s, up(alt, 560), settings).state
        assertTrue(s.alt.latched, "release-to-down is 490 ms, inside the window")
        assertTrue(s.alt.layerLatched)
    }

    @Test
    fun `A4 - Shift held while Backspace is pressed leaves no one-shot armed for the next letter (the Ctrl rule of spec SS5-4, given to Shift)`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Control(ControlKey.BACKSPACE), 100))
        val (s2, _) = ModifierMachine.shiftUp(s, up(shift, 200), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
    }

    @Test
    fun `A4 - Shift held while Space is pressed leaves no one-shot armed, even after a long hold`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Control(ControlKey.SPACE), 100))
        val (s2, _) = ModifierMachine.shiftUp(s, up(shift, 900), settings)
        assertEquals(ShiftValue.OFF, s2.shift.value)
    }

    @Test
    fun `A4 - caps lock survives a chorded hold, only the one-shot clears (as Ctrl's latch does)`() {
        var s = ModifierState()
        s = ModifierMachine.shiftDown(s, down(shift, 0), settings).state
        s = ModifierMachine.shiftUp(s, up(shift, 50), settings).state
        s = ModifierMachine.shiftDown(s, down(shift, 200), settings).state
        s = ModifierMachine.onOtherKeyDown(s, down(KeyId.Control(ControlKey.BACKSPACE), 300))
        val (s2, _) = ModifierMachine.shiftUp(s, up(shift, 400), settings)
        assertEquals(ShiftValue.CAPS, s2.shift.value)
    }

    // Test fixtures ------------------------------------------------------------------------------

    private fun qwertyLayout(): LayoutDescription = LayoutDescription(
        baseLayout = LayoutMap(emptyMap()),
        deviceLayer = DeviceLayerMap(mapOf(KeyId.Letter('Q') to "0")),
        emojiPage = SymPageMap(),
        symbolsPage = SymPageMap(),
        ctrlMappings = CtrlMappingTable(),
    )

    private fun defaultResolverSettings() = LayerResolver.LayerResolverSettings()

    private fun editableField() = LayerResolver.Context(hasEditableField = true)
}
