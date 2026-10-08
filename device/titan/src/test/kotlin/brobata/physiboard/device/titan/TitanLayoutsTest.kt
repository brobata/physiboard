package brobata.physiboard.device.titan

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.AltState
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlState
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierSettings
import brobata.physiboard.core.keys.ModifierState
import brobata.physiboard.core.keys.ShiftState
import brobata.physiboard.core.keys.ShiftValue
import brobata.physiboard.core.keys.SymPageId
import brobata.physiboard.core.keys.SymSessionState
import brobata.physiboard.core.keys.TypingSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks [TitanLayouts.titan2EliteQwerty] against layers-sym-alt.md's own tables (SS3.2, SS3.3,
 * SS9.1-9.2, SS8.1) and keys-and-modifiers.md SS12.3, then drives a handful of representative
 * keystrokes through `:core:keys`' own [LayerResolver] to prove the data and the resolver agree.
 */
class TitanLayoutsTest {

    private val layout = TitanLayouts.titan2EliteQwerty()
    private val allLetters = ('A'..'Z').map { KeyId.Letter(it) }.toSet()

    // Every physical key present exactly once -------------------------------------------------

    @Test
    fun `the base layout defines exactly the 26 letter keys, each once`() {
        assertEquals(allLetters, layout.baseLayout.entries.keys)
    }

    @Test
    fun `the Alt device layer defines exactly the 26 letter keys, each once`() {
        assertEquals(allLetters, layout.deviceLayer.entries.keys)
    }

    @Test
    fun `the Emoji page defines exactly the 26 letter keys, each once`() {
        assertEquals(allLetters, layout.emojiPage.entries.keys)
    }

    @Test
    fun `the Symbols page defines exactly the 26 letter keys, each once`() {
        assertEquals(allLetters, layout.symbolsPage.entries.keys)
    }

    @Test
    fun `the Ctrl mapping table covers the letter keys, less the one 3_0 unmapped`() {
        // B toggled the software keyboard in 2.x and 3.0 has none, so it is deliberately
        // absent rather than pointed at something that cannot happen.
        assertEquals(allLetters - KeyId.Letter('B'), layout.ctrlMappings.entries.keys)
        assertEquals(CtrlMapping.None, layout.ctrlMappings.mappingFor(KeyId.Letter('B')))
    }

    // Base layout: spec layers-sym-alt.md SS9.1-9.2 (identity, no multi-tap) ------------------

    @Test
    fun `base layout is the identity qwerty map with no multi-tap keys`() {
        for (letter in 'A'..'Z') {
            val entry = layout.baseLayout[KeyId.Letter(letter)]
            assertEquals(letter.lowercaseChar().toString(), entry?.lowercase, "lowercase of $letter")
            assertEquals(letter.toString(), entry?.uppercase, "uppercase of $letter")
            assertTrue(!(entry?.isMultiTap ?: false), "$letter must not be multi-tap (SS9.2: qwerty has 0 multi-tap keys)")
        }
    }

    // Alt layer: spec layers-sym-alt.md SS3.2 titan2elite_qwerty table, D3 --------------------

    @Test
    fun `Alt layer matches the titan2elite_qwerty table`() {
        val expected = mapOf(
            'Q' to "0", 'W' to "1", 'E' to "2", 'R' to "3", 'T' to "(", 'Y' to ")", 'U' to "_", 'I' to "-", 'O' to "+", 'P' to "@",
            'A' to "*", 'S' to "4", 'D' to "5", 'F' to "6", 'G' to "/", 'H' to ":", 'J' to "#", 'K' to "'", 'L' to "\"",
            'Z' to "7", 'X' to "8", 'C' to "9", 'V' to "?", 'B' to "!", 'N' to ",", 'M' to ".",
        )
        for ((letter, value) in expected) {
            assertEquals(value, layout.deviceLayer[KeyId.Letter(letter)], "Alt value for $letter")
        }
    }

    // Sym pages: spec layers-sym-alt.md SS3.3 --------------------------------------------------

    @Test
    fun `Emoji page matches the shipped sym_key_mappings table`() {
        val expected = mapOf(
            'Q' to "😀", 'T' to "😎", 'U' to "❤️", 'P' to "😉",
            'A' to "😢", 'H' to "🤔", 'L' to "😌",
            'Z' to "😔", 'C' to "😅", 'M' to "😳",
        )
        for ((letter, value) in expected) {
            assertEquals(value, layout.emojiPage[KeyId.Letter(letter)]?.lowercase, "Emoji value for $letter")
            assertEquals(null, layout.emojiPage[KeyId.Letter(letter)]?.uppercase, "shipped Sym files carry no uppercase map (SS3.1)")
        }
    }

    @Test
    fun `Symbols page matches the shipped sym_key_mappings_page2 table, spec test case 38`() {
        // spec layers-sym-alt.md SS14 test 38: "S is ;, F is –, J is „, K is “, C is &, O is °, V is ^, Z is », X is «".
        val expected = mapOf(
            'S' to ";", 'F' to "–", 'J' to "„", 'K' to "“",
            'C' to "&", 'O' to "°", 'V' to "^", 'Z' to "»", 'X' to "«",
        )
        for ((letter, value) in expected) {
            assertEquals(value, layout.symbolsPage[KeyId.Letter(letter)]?.lowercase, "Symbols value for $letter")
        }
    }

    // Sym page order: spec layers-sym-alt.md SS1, SS4.1 default order, Device page dropped (SS15) --

    @Test
    fun `Sym page order is Emoji, Symbols, Clipboard, Emoji Picker, GIFs with Emoji and Symbols enabled`() {
        assertEquals(
            listOf(SymPageId.EMOJI, SymPageId.SYMBOLS, SymPageId.CLIPBOARD, SymPageId.EMOJI_PICKER, SymPageId.GIF, SymPageId.CUSTOM_1, SymPageId.CUSTOM_2, SymPageId.CUSTOM_3, SymPageId.FILL),
            layout.symPagesConfig.normalizedOrder,
        )
        // spec test case 1: config default, page 0: Sym tap sequence gives 1, 2, 0.
        assertEquals(listOf(0, 1, 2), layout.symPagesConfig.cycle)
    }

    // Ctrl mappings: spec keys-and-modifiers.md SS12.3 shipped defaults ------------------------

    @Test
    fun `Ctrl mapping table matches the shipped Fn Layer defaults`() {
        assertEquals(CtrlMapping.Keycode(KeyId.Control(ControlKey.ESCAPE)), layout.ctrlMappings.mappingFor(KeyId.Letter('Q')))
        assertEquals(CtrlMapping.NamedAction("select_all"), layout.ctrlMappings.mappingFor(KeyId.Letter('A')))
        assertEquals(CtrlMapping.None, layout.ctrlMappings.mappingFor(KeyId.Letter('G')))
        // 3.0 has no software keyboard, so the key 2.x used to toggle it is unmapped.
        assertEquals(CtrlMapping.None, layout.ctrlMappings.mappingFor(KeyId.Letter('B')))
        assertEquals(CtrlMapping.NamedAction("copy"), layout.ctrlMappings.mappingFor(KeyId.Letter('C')))
    }

    // Variations: spec layers-sym-alt.md SS8.1 -------------------------------------------------

    @Test
    fun `variations are the built-in table in its neutral order`() {
        assertEquals(listOf("à", "á", "â", "ä", "ã", "å", "ā", "ą", "ă", "æ"), layout.variations.listFor('a'))
        assertEquals(listOf("è", "é", "ê", "ë", "ē", "ę", "ě", "ė", "€"), layout.variations.listFor('e'))
        assertEquals(listOf("ß", "ś", "š", "ş", "ș", "$"), layout.variations.listFor('s'))
        assertEquals(emptyList(), layout.variations.listFor('p'))
        assertEquals(listOf("ё", "є"), layout.variations.listFor('е'))
        assertEquals(listOf("₽"), layout.variations.listFor('Р'))
    }

    // Round-trip through the :core:keys resolver, a handful of representative keystrokes ------

    private val settings = ModifierSettings()
    private val resolverSettings = LayerResolver.LayerResolverSettings()
    private val context = LayerResolver.Context()

    private fun down(key: KeyId, shift: Boolean = false): KeyStroke =
        KeyStroke(key = key, edge = KeyEdge.DOWN, repeatCount = 0, timeMs = 0, meta = ModifierFlags(shift = shift))

    @Test
    fun `plain Q types the base layout lowercase, Shift types uppercase`() {
        val plain = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('Q')), layout, settings, resolverSettings, context)
        assertEquals(Action.Commit("q"), plain.action)

        val shiftOneShot = ModifierState(shift = ShiftState(value = ShiftValue.ONE_SHOT))
        val shifted = LayerResolver.resolveKeyDown(shiftOneShot, TypingSessionState(), down(KeyId.Letter('Q')), layout, settings, resolverSettings, context)
        assertEquals(Action.Commit("Q"), shifted.action)
    }

    @Test
    fun `Alt latched plus Q commits the device layer character, spec test 19`() {
        // spec layers-sym-alt.md SS14 test 19: "profile titan2elite_qwerty: Alt held + U commits _".
        val altLatched = ModifierState(alt = AltState(latched = true))
        val resolution = LayerResolver.resolveKeyDown(altLatched, TypingSessionState(), down(KeyId.Letter('U')), layout, settings, resolverSettings, context)
        assertEquals(Action.Commit("_"), resolution.action)
    }

    @Test
    fun `a Sym chord with no page open commits the preferred page's character and does not open a page`() {
        val symHeld = ModifierState(sym = SymSessionState(togglePending = true, chordUsed = false, currentPageNumber = 0))
        val resolution = LayerResolver.resolveKeyDown(symHeld, TypingSessionState(), down(KeyId.Letter('Q')), layout, settings, resolverSettings, context)
        assertEquals(Action.Commit("😀"), resolution.action)
        assertEquals(0, resolution.state.sym.currentPageNumber)
    }

    @Test
    fun `the Symbols page open commits its character for C, spec test 38 cross-check`() {
        val symbolsOpen = ModifierState(sym = SymSessionState(currentPageNumber = SymPageId.SYMBOLS.pageNumber))
        val resolution = LayerResolver.resolveKeyDown(symbolsOpen, TypingSessionState(), down(KeyId.Letter('C')), layout, settings, resolverSettings, context)
        assertEquals(Action.Commit("&"), resolution.action)
    }

    @Test
    fun `Ctrl one-shot plus E resolves the Fn Layer keycode mapping to cursor up`() {
        val ctrlOneShot = ModifierState(ctrl = CtrlState(oneShot = true))
        val resolution = LayerResolver.resolveKeyDown(ctrlOneShot, TypingSessionState(), down(KeyId.Letter('E')), layout, settings, resolverSettings, context)
        assertEquals(Action.Edit(EditEffect.CURSOR_UP), resolution.action)
    }

    @Test
    fun `Ctrl one-shot plus G has no Fn Layer mapping and passes through untouched`() {
        val ctrlOneShot = ModifierState(ctrl = CtrlState(oneShot = true))
        val resolution = LayerResolver.resolveKeyDown(ctrlOneShot, TypingSessionState(), down(KeyId.Letter('G')), layout, settings, resolverSettings, context)
        assertEquals(Action.PassThrough, resolution.action)
    }

    @Test
    fun `every letter the spec names as having accents offers some`() {
        // The spec names these base characters; each must offer at least one variation, or a
        // long press on that key does nothing and the user simply cannot type the accent.
        for (base in "aeioulcnszydgrt") {
            assertTrue(
                layout.variations.listFor(base).isNotEmpty(),
                "lowercase '$base' offers no variations",
            )
            val upper = base.uppercaseChar()
            assertTrue(
                layout.variations.listFor(upper).isNotEmpty(),
                "uppercase '$upper' offers no variations",
            )
        }
    }

    @Test
    fun `an uppercase accent list matches its lowercase one`() {
        // Whatever a key offers, holding Shift must offer the same characters in capitals, so
        // the two lists cannot drift apart as letters are added.
        for (base in "aeioulcnszydgrt") {
            val lower = layout.variations.listFor(base)
            val upper = layout.variations.listFor(base.uppercaseChar())
            assertTrue(lower.size == upper.size, "'$base' and '${base.uppercaseChar()}' differ in length")
            for ((lo, up) in lower.zip(upper)) {
                val expected = if (lo.length == 1 && lo[0].isLetter()) lo.uppercase() else lo
                // The eszett and Turkish dotless i are the two letters whose capital offered is a
                // different character entirely (the capital sharp s, the dotted capital I).
                if (lo == "ß" || lo == "ı") continue
                assertTrue(up == expected, "'$base': $lo should uppercase to $expected but was $up")
            }
        }
    }
}
