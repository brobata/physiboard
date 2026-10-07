package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: layers-sym-alt.md SS4, SS14 test cases 1-3, 5-7. */
class SymPagesConfigTest {

    private fun tapSequence(config: SymPagesConfig, taps: Int): List<Int> {
        var page = 0
        val results = mutableListOf<Int>()
        repeat(taps) {
            page = config.nextPage(page)
            results += page
        }
        return results
    }

    @Test
    fun `case 1 - default config cycles Emoji then Symbols then closed`() {
        assertEquals(listOf(1, 2, 0), tapSequence(SymPagesConfig(), 3))
    }

    @Test
    fun `case 2 - the factory baseline cycles Emoji Picker then Symbols then closed`() {
        val baseline = SymPagesConfig(
            emojiEnabled = false,
            symbolsEnabled = true,
            clipboardEnabled = false,
            emojiPickerEnabled = true,
            order = listOf(SymPageId.EMOJI_PICKER, SymPageId.SYMBOLS, SymPageId.CLIPBOARD, SymPageId.EMOJI),
        )
        assertEquals(listOf(4, 2, 0), tapSequence(baseline, 3))
    }

    @Test
    fun `case 7 - restoring a page no longer in the cycle falls back to the first enabled page`() {
        // Default config: Emoji (1) and Symbols (2) enabled, Clipboard (3) disabled.
        val config = SymPagesConfig()
        assertEquals(1, config.restorePage(3), "clipboard is disabled, so restoring it falls back to the first enabled page")
    }

    @Test
    fun `restoring a page still in the cycle reopens it as is`() {
        assertEquals(2, SymPagesConfig().restorePage(2))
    }

    @Test
    fun `restoring page 0 (nothing pending) opens nothing`() {
        assertEquals(0, SymPagesConfig().restorePage(0))
    }

    @Test
    fun `case 3 - a custom order normalises with the missing ids appended and cycles accordingly`() {
        val config = SymPagesConfig(
            emojiEnabled = true,
            symbolsEnabled = true,
            order = listOf(SymPageId.SYMBOLS, SymPageId.EMOJI),
        )
        assertEquals(
            listOf(SymPageId.SYMBOLS, SymPageId.EMOJI, SymPageId.CLIPBOARD, SymPageId.EMOJI_PICKER, SymPageId.GIF),
            config.normalizedOrder,
        )
        assertEquals(listOf(2, 1, 0), tapSequence(config, 3))
    }

    @Test
    fun `case 5 - reading the current page evicts a disabled Emoji page but leaves Symbols alone`() {
        val emojiDisabled = SymPagesConfig(emojiEnabled = false, symbolsEnabled = true)
        assertEquals(2, emojiDisabled.consistentPage(1))

        val symbolsDisabled = SymPagesConfig(emojiEnabled = true, symbolsEnabled = false)
        assertEquals(2, symbolsDisabled.consistentPage(2))
    }

    @Test
    fun `case 6 - a direct-open button toggles its page regardless of the cycle`() {
        val config = SymPagesConfig()
        assertEquals(0, config.directOpen(SymPageId.CLIPBOARD, currentPageNumber = 3))
        assertEquals(3, config.directOpen(SymPageId.CLIPBOARD, currentPageNumber = 1))
    }

    @Test
    fun `no page is enabled - Sym never opens anything`() {
        val allOff = SymPagesConfig(emojiEnabled = false, symbolsEnabled = false, clipboardEnabled = false, emojiPickerEnabled = false)
        assertEquals(listOf(0, 0, 0), tapSequence(allOff, 3))
    }
}
