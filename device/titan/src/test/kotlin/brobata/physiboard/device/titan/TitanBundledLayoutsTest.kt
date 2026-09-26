package brobata.physiboard.device.titan

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierSettings
import brobata.physiboard.core.keys.ModifierState
import brobata.physiboard.core.keys.TypingSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks [TitanLayouts.bundled] against layers-sym-alt.md SS9.2's table of eighteen bundled
 * layout names: the id list matches the table exactly, every layout's base layer covers the 26
 * letter keys once each, and every layout's multi-tap key count matches the table's own "Multi-tap
 * keys" column. Section 9.2's own rule ("ALT, SYM and Ctrl mappings remain based on physical key
 * position") is also checked: every layout shares [TitanLayouts.titan2EliteQwerty]'s Alt, Sym and
 * Ctrl tables, differing only in its base layout.
 */
class TitanBundledLayoutsTest {

    private val allLetters = ('A'..'Z').map { KeyId.Letter(it) }.toSet()
    private val bundled = TitanLayouts.bundled()
    private val qwerty = TitanLayouts.titan2EliteQwerty()

    /** SS9.2's table, name to its own "Multi-tap keys" column. */
    private val expectedMultiTapCounts: Map<String, Int> = mapOf(
        "qwerty" to 0,
        "qwertz" to 0,
        "azerty" to 0,
        "german_multitap_qwertz" to 5,
        "turkish_multitap" to 7,
        "norwegian_multitap_qwerty" to 2,
        "arabic" to 10,
        "armenian_phonetic" to 9,
        "bulgarian_phonetic" to 0,
        "bulgarian_phonetic_traditional" to 4,
        "Cyrillic_Translite" to 7,
        "greek" to 7,
        "russian_jcuken" to 7,
        "russian_standard" to 4,
        "russian_translit" to 6,
        "serbian_cyrillic" to 4,
        "ukrainian" to 6,
        "vietnamese_telex_qwerty" to 0,
    )

    @Test
    fun `bundled lists exactly SS9_2's eighteen names, no more, no fewer`() {
        assertEquals(expectedMultiTapCounts.keys, bundled.map { it.first }.toSet())
        assertEquals(18, bundled.size)
    }

    @Test
    fun `every bundled layout's base layer defines exactly the 26 letter keys, each once`() {
        for ((id, _, layout) in bundled) {
            assertEquals(allLetters, layout.baseLayout.entries.keys, "$id base layout key set")
        }
    }

    @Test
    fun `every bundled layout's multi-tap key count matches SS9_2's table`() {
        for ((id, _, layout) in bundled) {
            val multiTapCount = layout.baseLayout.entries.values.count { it.isMultiTap }
            assertEquals(expectedMultiTapCounts.getValue(id), multiTapCount, "$id multi-tap key count")
        }
    }

    @Test
    fun `every bundled layout reuses qwerty's Alt, Sym and Ctrl tables, SS9_2's own rule`() {
        for ((id, _, layout) in bundled) {
            assertEquals(qwerty.deviceLayer, layout.deviceLayer, "$id Alt layer")
            assertEquals(qwerty.emojiPage, layout.emojiPage, "$id Emoji page")
            assertEquals(qwerty.symbolsPage, layout.symbolsPage, "$id Symbols page")
            assertEquals(qwerty.ctrlMappings, layout.ctrlMappings, "$id Ctrl mappings")
        }
    }

    @Test
    fun `qwertz swaps Y and Z, SS9_2's own rule, and vietnamese_telex_qwerty is the plain identity`() {
        val qwertz = TitanLayouts.titanQwertz()
        assertEquals("z", qwertz.baseLayout[KeyId.Letter('Y')]?.lowercase)
        assertEquals("y", qwertz.baseLayout[KeyId.Letter('Z')]?.lowercase)
        for (letter in "ABCDEFGHIJKLMNOPQRSTUVWX") {
            assertEquals(letter.lowercaseChar().toString(), qwertz.baseLayout[KeyId.Letter(letter)]?.lowercase, "qwertz $letter unchanged")
        }
        val telex = TitanLayouts.titanVietnameseTelexQwerty()
        assertEquals(qwerty.baseLayout, telex.baseLayout)
    }

    @Test
    fun `azerty swaps Q-A and W-Z and puts a comma on M`() {
        val azerty = TitanLayouts.titanAzerty()
        assertEquals("a", azerty.baseLayout[KeyId.Letter('Q')]?.lowercase)
        assertEquals("q", azerty.baseLayout[KeyId.Letter('A')]?.lowercase)
        assertEquals("z", azerty.baseLayout[KeyId.Letter('W')]?.lowercase)
        assertEquals("w", azerty.baseLayout[KeyId.Letter('Z')]?.lowercase)
        assertEquals(",", azerty.baseLayout[KeyId.Letter('M')]?.lowercase)
    }

    @Test
    fun `german_multitap_qwertz reaches umlauts and eszett on a second tap`() {
        val layout = TitanLayouts.titanGermanMultiTapQwertz()
        val a = layout.baseLayout[KeyId.Letter('A')]!!
        assertTrue(a.isMultiTap)
        assertEquals("ä", a.taps[1].lowercase)
        assertEquals("ß", layout.baseLayout[KeyId.Letter('S')]!!.taps[1].lowercase)
    }

    @Test
    fun `turkish_multitap's I key gives dotless i first, dotted i on a second tap`() {
        val layout = TitanLayouts.titanTurkishMultiTap()
        val entry = layout.baseLayout[KeyId.Letter('I')]!!
        assertEquals("ı", entry.lowercase)
        assertEquals("I", entry.uppercase)
        assertEquals("i", entry.taps[1].lowercase)
        assertEquals("İ", entry.taps[1].uppercase)
    }

    @Test
    fun `russian_translit maps Latin sounds to Cyrillic letters`() {
        val layout = TitanLayouts.titanRussianTranslit()
        assertEquals("а", layout.baseLayout[KeyId.Letter('A')]?.lowercase)
        assertEquals("м", layout.baseLayout[KeyId.Letter('M')]?.lowercase)
        assertEquals("ц", layout.baseLayout[KeyId.Letter('C')]?.lowercase)
        assertEquals("ч", layout.baseLayout[KeyId.Letter('C')]?.taps?.get(1)?.lowercase)
    }

    // Round-trip through the real resolver, for one derived Latin layout and one derived
    // non-Latin layout, proving the data and `:core:keys`' own LayerResolver agree -----------

    private val settings = ModifierSettings()
    private val resolverSettings = LayerResolver.LayerResolverSettings()
    private val context = LayerResolver.Context()

    private fun down(key: KeyId): KeyStroke =
        KeyStroke(key = key, edge = KeyEdge.DOWN, repeatCount = 0, timeMs = 0, meta = ModifierFlags())

    @Test
    fun `plain Y on qwertz commits z through the real resolver`() {
        val qwertz = TitanLayouts.titanQwertz()
        val resolution = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('Y')), qwertz, settings, resolverSettings, context)
        assertEquals(Action.Commit("z"), resolution.action)
    }

    @Test
    fun `plain Q on the Russian standard layout commits the Cyrillic short i through the real resolver`() {
        val russian = TitanLayouts.titanRussianStandard()
        val resolution = LayerResolver.resolveKeyDown(ModifierState(), TypingSessionState(), down(KeyId.Letter('Q')), russian, settings, resolverSettings, context)
        assertEquals(Action.Commit("й"), resolution.action)
    }
}
