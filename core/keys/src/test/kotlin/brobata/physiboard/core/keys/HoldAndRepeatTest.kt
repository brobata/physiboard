package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: keys-and-modifiers.md SS21 T42-T48; layers-sym-alt.md SS14 long-press cases 24-33. */
class HoldAndRepeatTest {

    private val settings = ModifierSettings()
    private val resolverSettings = LayerResolver.LayerResolverSettings()
    private val field = LayerResolver.Context(hasEditableField = true)

    private fun down(key: KeyId, timeMs: Long, repeat: Int = 0, meta: ModifierFlags = ModifierFlags()) =
        KeyStroke(key, KeyEdge.DOWN, repeat, timeMs, meta)

    private val kKey = KeyId.Letter('K')

    private fun multiTapLayout(longPress: LongPressSettings = LongPressSettings()): LayoutDescription = LayoutDescription(
        baseLayout = LayoutMap(
            mapOf(
                kKey to LetterEntry(
                    "a", "A",
                    taps = listOf(Tap("a", "A"), Tap("b", "B"), Tap("c", "C")),
                ),
            ),
        ),
        deviceLayer = DeviceLayerMap(mapOf(KeyId.Letter('Q') to "0")),
        emojiPage = SymPageMap(),
        symbolsPage = SymPageMap(),
        ctrlMappings = CtrlMappingTable(),
        longPress = longPress,
    )

    // T42-T45: multi-tap ---------------------------------------------------------------------

    @Test
    fun `T42 - the first tap of a multi-tap key commits its first entry`() {
        val result = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(kKey, 0), multiTapLayout(), settings, resolverSettings, field)
        assertEquals(Action.Commit("a"), result.action)
        assertEquals(0, result.typing.multiTapCycle?.tapIndex)
    }

    @Test
    fun `T43 - a second tap within the window replaces the first with the next entry`() {
        val first = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(kKey, 0), multiTapLayout(), settings, resolverSettings, field)
        val second = LayerResolver.resolveKeyDown(first.state, first.typing, down(kKey, 200), multiTapLayout(), settings, resolverSettings, field)
        assertEquals(Action.ReplaceRecent(1, "b"), second.action)
    }

    @Test
    fun `T44 - a tap after the window starts a new cycle and keeps the earlier text`() {
        val first = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(kKey, 0), multiTapLayout(), settings, resolverSettings, field)
        val second = LayerResolver.resolveKeyDown(first.state, first.typing, down(kKey, 600), multiTapLayout(), settings, resolverSettings, field)
        assertEquals(Action.Commit("a"), second.action)
        assertEquals(0, second.typing.multiTapCycle?.tapIndex)
    }

    @Test
    fun `T45 - a repeat of a multi-tap key is consumed without changing the text`() {
        val first = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(kKey, 0), multiTapLayout(), settings, resolverSettings, field)
        val repeat = LayerResolver.resolveKeyDown(first.state, first.typing, down(kKey, 100, repeat = 1), multiTapLayout(), settings, resolverSettings, field)
        assertEquals(Action.Ignored, repeat.action)
    }

    @Test
    fun `sharp-S exception - an uppercase resolution never multi-taps a key whose taps include capital sharp S`() {
        val sharpSLayout = LayoutDescription(
            baseLayout = LayoutMap(
                mapOf(KeyId.Letter('S') to LetterEntry("s", "S", taps = listOf(Tap("s", "S"), Tap("ß", "ẞ")))),
            ),
            deviceLayer = DeviceLayerMap(),
            emojiPage = SymPageMap(),
            symbolsPage = SymPageMap(),
            ctrlMappings = CtrlMappingTable(),
        )
        val caps = ModifierState(shift = ShiftState(value = ShiftValue.CAPS))
        val result = LayerResolver.resolveKeyDown(caps, TypingSessionState(), down(KeyId.Letter('S'), 0), sharpSLayout, settings, resolverSettings, field)
        assertEquals(Action.Commit("S"), result.action)
        assertEquals(null, result.typing.multiTapCycle)
    }

    // T46-T48: long press ----------------------------------------------------------------------

    private fun altLongPressLayout(thresholdMs: Long = 300): LayoutDescription = LayoutDescription(
        baseLayout = LayoutMap(),
        deviceLayer = DeviceLayerMap(mapOf(KeyId.Letter('A') to "#")),
        emojiPage = SymPageMap(),
        symbolsPage = SymPageMap(),
        ctrlMappings = CtrlMappingTable(),
        longPress = LongPressSettings(mode = LongPressMode.ALT, thresholdMs = thresholdMs),
    )

    @Test
    fun `T46 - releasing before the threshold cancels the long press and keeps the letter`() {
        val l = altLongPressLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("a"), down0.action)
        assertTrue(down0.typing.pendingLongPress != null)

        val up = LayerResolver.resolveKeyUp(down0.state, down0.typing, KeyStroke(KeyId.Letter('A'), KeyEdge.UP, 0, 100))
        assertEquals(Action.Ignored, up.action)
        assertEquals(null, up.typing.pendingLongPress)
    }

    @Test
    fun `T47 - the timer firing while the key is still down replaces the letter with the Alt value`() {
        val l = altLongPressLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 300, l)
        assertEquals(Action.ReplaceRecent(1, "#"), fired?.action)
    }

    @Test
    fun `T48 - shift mode replaces the letter with its layout uppercase`() {
        val l = altLongPressLayout().copy(longPress = LongPressSettings(mode = LongPressMode.SHIFT, thresholdMs = 300))
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 300, l)
        assertEquals(Action.ReplaceRecent(1, "A"), fired?.action)
    }

    @Test
    fun `a repeat of a key with a pending long press is swallowed`() {
        val l = altLongPressLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val repeat = LayerResolver.resolveKeyDown(down0.state, down0.typing, down(KeyId.Letter('A'), 50, repeat = 1), l, settings, resolverSettings, field)
        assertEquals(Action.Ignored, repeat.action)
    }

    @Test
    fun `long_press_threshold is clamped to the 50 to 1000 range`() {
        assertEquals(1000, LongPressSettings(thresholdMs = 2000).clampedThresholdMs)
        assertEquals(50, LongPressSettings(thresholdMs = 10).clampedThresholdMs)
    }

    // layers-sym-alt.md SS14 case 24-28: variations mode ----------------------------------------

    private fun variationsLayout(): LayoutDescription = LayoutDescription(
        baseLayout = LayoutMap(mapOf(KeyId.Letter('U') to LetterEntry("u", "U"))),
        deviceLayer = DeviceLayerMap(),
        emojiPage = SymPageMap(),
        symbolsPage = SymPageMap(),
        ctrlMappings = CtrlMappingTable(),
        variations = VariationTable(mapOf('u' to listOf("ü"), 'U' to listOf("Ü"))),
        longPress = LongPressSettings(mode = LongPressMode.VARIATIONS, thresholdMs = 50),
    )

    @Test
    fun `case 24 - a variations long press replaces the committed letter with its first variation`() {
        val l = variationsLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('U'), 0), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("u"), down0.action)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l)
        assertEquals(Action.ReplaceRecent(1, "ü"), fired?.action)
    }

    @Test
    fun `case 26 - a variations long press with one-shot Shift replaces with the uppercase variation`() {
        val l = variationsLayout()
        val shiftOneShot = ModifierState(shift = ShiftState(value = ShiftValue.ONE_SHOT))
        val down0 = LayerResolver.resolveKeyDown(shiftOneShot, TypingSessionState(), down(KeyId.Letter('U'), 0), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("U"), down0.action)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l)
        assertEquals(Action.ReplaceRecent(1, "Ü"), fired?.action)
    }

    @Test
    fun `case 24b - the variations long press leaves a letter the app already changed alone`() {
        val l = variationsLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('U'), 0), l, settings, resolverSettings, field)
        assertEquals(Action.Ignored, LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l, textBeforeCaret = "hello")?.action)
        assertEquals(Action.ReplaceRecent(1, "ü"), LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l, textBeforeCaret = "hu")?.action)
        assertEquals(Action.ReplaceRecent(1, "ü"), LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l, textBeforeCaret = null)?.action, "an unreadable field is trusted")
        assertEquals(Action.ReplaceRecent(1, "ü"), LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l, textBeforeCaret = "")?.action, "a web field's empty read is trusted")
    }

    @Test
    fun `case 24c - a field that takes no accents never arms a variations long press`() {
        val l = variationsLayout()
        val email = field.copy(variationsAllowed = false)
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('U'), 0), l, settings, resolverSettings, email)
        assertEquals(Action.Commit("u"), down0.action)
        assertEquals(null, down0.typing.pendingLongPress)
    }

    @Test
    fun `case 24d - the variation list for the chooser follows the case of the press`() {
        val l = variationsLayout().copy(variations = VariationTable(mapOf('u' to listOf("ü", "ú"), 'U' to listOf("Ü", "Ú"))))
        val pending = LongPress.Pending(KeyId.Letter('U'), 0, 50, LongPressMode.VARIATIONS, shiftEffective = true, committedText = "U")
        assertEquals(listOf("Ü", "Ú"), LongPress.variationsFor(pending, l))
        assertEquals(emptyList(), LongPress.variationsFor(pending.copy(mode = LongPressMode.ALT), l))
    }

    @Test
    fun `case 31 - sym mode with emoji before symbols replaces with the emoji entry`() {
        val l = LayoutDescription(
            baseLayout = LayoutMap(),
            deviceLayer = DeviceLayerMap(),
            emojiPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("😀"))),
            symbolsPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("~"))),
            ctrlMappings = CtrlMappingTable(),
            symPagesConfig = SymPagesConfig(order = listOf(SymPageId.EMOJI, SymPageId.SYMBOLS)),
            longPress = LongPressSettings(mode = LongPressMode.SYM, thresholdMs = 50),
        )
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('Q'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l)
        assertEquals(Action.ReplaceRecent(1, "😀"), fired?.action)
    }

    @Test
    fun `case 31b - sym_symbols mode always uses the symbols entry`() {
        val l = LayoutDescription(
            baseLayout = LayoutMap(),
            deviceLayer = DeviceLayerMap(),
            emojiPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("😀"))),
            symbolsPage = SymPageMap(mapOf(KeyId.Letter('Q') to SymPageEntry("~"))),
            ctrlMappings = CtrlMappingTable(),
            symPagesConfig = SymPagesConfig(order = listOf(SymPageId.EMOJI, SymPageId.SYMBOLS)),
            longPress = LongPressSettings(mode = LongPressMode.SYM_SYMBOLS, thresholdMs = 50),
        )
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('Q'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 60, l)
        assertEquals(Action.ReplaceRecent(1, "~"), fired?.action)
    }

    @Test
    fun `a key with no long-press support behaves as a plain key with normal auto-repeat`() {
        val l = LayoutDescription(
            baseLayout = LayoutMap(),
            deviceLayer = DeviceLayerMap(),
            emojiPage = SymPageMap(),
            symbolsPage = SymPageMap(),
            ctrlMappings = CtrlMappingTable(),
            longPress = LongPressSettings(mode = LongPressMode.ALT, thresholdMs = 50),
        )
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('Z'), 0), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("z"), down0.action)
        assertFalse(down0.typing.pendingLongPress != null)
        val repeat = LayerResolver.resolveKeyDown(down0.state, down0.typing, down(KeyId.Letter('Z'), 50, repeat = 1), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("z"), repeat.action)
    }

    // Review findings A1, A2, A5 (2026-09-24): what a held key does after its long press fires,
    // what the tick leaves behind, and what another key does to an armed press. ----------------

    @Test
    fun `A1 - repeats after a fired long press are consumed until the key comes up (spec SS8-1, the timer decides, not the repeats)`() {
        val l = altLongPressLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 300, l)
        assertEquals(Action.ReplaceRecent(1, "#"), fired?.action)

        val repeat = LayerResolver.resolveKeyDown(fired!!.state, fired.typing, down(KeyId.Letter('A'), 400, repeat = 1), l, settings, resolverSettings, field)
        assertEquals(Action.Ignored, repeat.action, "the held key must not type its base letter after the replacement")
        val laterRepeat = LayerResolver.resolveKeyDown(repeat.state, repeat.typing, down(KeyId.Letter('A'), 450, repeat = 2), l, settings, resolverSettings, field)
        assertEquals(Action.Ignored, laterRepeat.action)

        val up = LayerResolver.resolveKeyUp(laterRepeat.state, laterRepeat.typing, KeyStroke(KeyId.Letter('A'), KeyEdge.UP, 0, 500))
        assertEquals(Action.Ignored, up.action, "spec SS1-4 step 13: the up of a tracked press is consumed")
        val fresh = LayerResolver.resolveKeyDown(up.state, up.typing, down(KeyId.Letter('A'), 600), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("a"), fresh.action, "a new press after the up types normally again")
    }

    @Test
    fun `A1 - a tick never fires twice for one press`() {
        val l = altLongPressLayout()
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(down0.state, down0.typing, 300, l)!!
        assertEquals(null, LayerResolver.resolveLongPressTick(fired.state, fired.typing, 900, l))
    }

    @Test
    fun `A1 - a held key with Alt latched repeats through the Alt layer, not the base layout (spec SS8-1 re-enters the normal path)`() {
        val l = altLongPressLayout()
        val altLatched = ModifierState(alt = AltState(latched = true))
        val down0 = LayerResolver.resolveKeyDown(altLatched, TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("#"), down0.action)
        val repeat = LayerResolver.resolveKeyDown(down0.state, down0.typing, down(KeyId.Letter('A'), 450, repeat = 1), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("#"), repeat.action)
    }

    @Test
    fun `A1 - a held key with Ctrl physically held repeats as the Ctrl combo, not the base letter (spec SS7-3 step 1)`() {
        val l = altLongPressLayout()
        val ctrlMeta = ModifierFlags(ctrl = true)
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('S'), 0, meta = ctrlMeta), l, settings, resolverSettings, field)
        assertEquals(Action.ForwardAsCtrlCombo(KeyId.Letter('S')), down0.action)
        val repeat = LayerResolver.resolveKeyDown(down0.state, down0.typing, down(KeyId.Letter('S'), 450, repeat = 1, meta = ctrlMeta), l, settings, resolverSettings, field)
        assertEquals(Action.ForwardAsCtrlCombo(KeyId.Letter('S')), repeat.action)
    }

    @Test
    fun `A1 - a repeat of a multi-tap key with no open cycle is still consumed (spec SS8-1, holding must not churn the variants)`() {
        val repeat = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(kKey, 500, repeat = 1), multiTapLayout(), settings, resolverSettings, field)
        assertEquals(Action.Ignored, repeat.action)
        assertEquals(null, repeat.typing.multiTapCycle)
    }

    @Test
    fun `A2 - a fired long press leaves every modifier exactly as it was (spec SS8-3 names no modifier change)`() {
        val l = altLongPressLayout()
        val busy = ModifierState(
            shift = ShiftState(value = ShiftValue.CAPS, layerLatched = true),
            ctrl = CtrlState(latched = true, latchFromNavMode = true),
            alt = AltState(pressed = true, physicallyPressed = true),
            sym = SymSessionState(currentPageNumber = 2),
        )
        val down0 = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val fired = LayerResolver.resolveLongPressTick(busy, down0.typing, 300, l)
        assertEquals(busy, fired?.state)
    }

    @Test
    fun `A5 - a different key going down cancels the armed long press so the tick cannot eat the new key`() {
        val l = altLongPressLayout(thresholdMs = 500)
        val aDown = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val spaceDown = LayerResolver.resolveKeyDown(aDown.state, aDown.typing, down(KeyId.Control(ControlKey.SPACE), 80), l, settings, resolverSettings, field)
        assertEquals(Action.Commit(" "), spaceDown.action)
        assertEquals(null, spaceDown.typing.pendingLongPress)
        assertEquals(null, LayerResolver.resolveLongPressTick(spaceDown.state, spaceDown.typing, 500, l))
    }

    @Test
    fun `A5 - a repeat of the still-held key after another key cancelled its long press types the letter and arms nothing new`() {
        val l = altLongPressLayout(thresholdMs = 500)
        val aDown = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('A'), 0), l, settings, resolverSettings, field)
        val sDown = LayerResolver.resolveKeyDown(aDown.state, aDown.typing, down(KeyId.Letter('S'), 80), l, settings, resolverSettings, field)
        val aRepeat = LayerResolver.resolveKeyDown(sDown.state, sDown.typing, down(KeyId.Letter('A'), 400, repeat = 1), l, settings, resolverSettings, field)
        assertEquals(Action.Commit("a"), aRepeat.action)
        assertEquals(null, aRepeat.typing.pendingLongPress, "spec SS8-2: eligibility is computed on the key-down, never on a repeat")
    }
}
