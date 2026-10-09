package brobata.physiboard.app.settings.ui

import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.settings.CustomSymPage
import brobata.physiboard.core.settings.KeyPrefs
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SymPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: app-shell.md SS22.4 (undo instead of confirm) and its test rows. */
class UndoModelTest {

    @Test
    fun `the slot gives its restore once, and only inside the window`() {
        val slot = UndoSlot<String>()
        slot.offer("a", "Deleted", restore = "before", nowMs = 1_000)
        assertEquals("before", slot.take(nowMs = 1_000 + UndoSlot.WINDOW_MS - 1))
        assertNull(slot.take(nowMs = 1_000 + UndoSlot.WINDOW_MS - 1), "taken once")

        slot.offer("a", "Deleted", restore = "before", nowMs = 0)
        assertNull(slot.take(nowMs = UndoSlot.WINDOW_MS), "8 s later the action stands")
    }

    @Test
    fun `a burst of the same action undoes back to where the burst started`() {
        val slot = UndoSlot<String>()
        slot.offer("sym-pages", "Moved GIF up", restore = "order0", nowMs = 0)
        slot.offer("sym-pages", "Moved GIF up", restore = "order1", nowMs = 3_000)
        val last = slot.offer("sym-pages", "Moved GIF down", restore = "order2", nowMs = 9_000)
        assertEquals("Moved GIF down", last.message)
        assertEquals("order0", slot.take(nowMs = 9_500), "each move extended the window; the first prior state wins")
    }

    @Test
    fun `a different action replaces the pending one, and a stale dismiss does not clear the new one`() {
        val slot = UndoSlot<String>()
        val first = slot.offer("dictionary-teh", "Deleted", restore = "teh", nowMs = 0)
        slot.offer("dictionary-adn", "Deleted", restore = "adn", nowMs = 100)
        slot.dismiss(first.offeredAtMs)
        assertEquals("adn", slot.take(nowMs = 200))
    }

    @Test
    fun `a settings section restore puts back only that section`() {
        val before = Settings().let { it.copy(typing = it.typing.copy(removeSpaceBefore = ".,", spaceBeforeNextText = "!")) }
        val restore = SettingsSection.PUNCTUATION_SPACING.restoreFrom(before)
        // The reset, then an unrelated change while the snackbar is up.
        val reset = before.copy(typing = before.typing.copy(removeSpaceBefore = "", spaceBeforeNextText = ""))
        val meanwhile = reset.copy(keys = reset.keys.copy(navModeEnabled = !reset.keys.navModeEnabled))
        val undone = restore(meanwhile)
        assertEquals(".,", undone.typing.removeSpaceBefore)
        assertEquals("!", undone.typing.spaceBeforeNextText)
        assertEquals(meanwhile.keys.navModeEnabled, undone.keys.navModeEnabled, "the other change survives")
    }

    @Test
    fun `the Fn layer switches come back exactly`() {
        val before = Settings().let { it.copy(keys = it.keys.copy(navModeEnabled = true, navModeCtrlHoldEnabled = false, layoutAwareCtrlShortcuts = false)) }
        val defaults = KeyPrefs()
        val reset = before.copy(keys = before.keys.copy(navModeEnabled = defaults.navModeEnabled, navModeCtrlHoldEnabled = defaults.navModeCtrlHoldEnabled, layoutAwareCtrlShortcuts = defaults.layoutAwareCtrlShortcuts))
        assertEquals(before, SettingsSection.FN_LAYER_SWITCHES.restoreFrom(before)(reset))
    }

    @Test
    fun `Sym pages - order, a page switched off, a cleared My page and a reset layer`() {
        val base = Settings()
        val before = base.copy(
            symPages = base.symPages.copy(
                pages = base.symPages.pages.copy(order = base.symPages.pages.order.reversed(), gifEnabled = true),
                customPages = List(CustomSymPage.COUNT) { i -> CustomSymPage(name = "P$i", mappings = mapOf("KEYCODE_A" to "α$i")) },
                customEmojiPage = mapOf("KEYCODE_Q" to "🙂"),
            ),
        )
        val shuffled = before.copy(symPages = before.symPages.copy(pages = before.symPages.pages.copy(order = listOf(SymPage.GIF) + before.symPages.pages.order.filter { it != SymPage.GIF }, gifEnabled = false)))
        assertEquals(before, SettingsSection.SYM_PAGE_ORDER.restoreFrom(before)(shuffled))

        val cleared = before.copy(symPages = before.symPages.copy(customPages = before.symPages.customPages.mapIndexed { i, p -> if (i == 1) p.copy(mappings = emptyMap()) else p }))
        val renamedMeanwhile = cleared.copy(symPages = cleared.symPages.copy(customPages = cleared.symPages.customPages.mapIndexed { i, p -> if (i == 1) p.copy(name = "Greek") else p }))
        val undone = SettingsSection.customPageKeys(1).restoreFrom(before)(renamedMeanwhile)
        assertEquals(mapOf("KEYCODE_A" to "α1"), undone.symPages.customPages[1].mappings)
        assertEquals("Greek", undone.symPages.customPages[1].name, "Clear page never touched the name, so neither does Undo")

        val layerReset = before.copy(symPages = before.symPages.copy(customEmojiPage = emptyMap()))
        assertEquals(before, SettingsSection.EMOJI_LAYER.restoreFrom(before)(layerReset))
    }

    @Test
    fun `one letter's accents, including a letter that had none of its own`() {
        val before = Settings().let { it.copy(keys = it.keys.copy(customVariations = mapOf("e" to listOf("é", "è"), "a" to listOf("á")))) }
        val resetE = before.copy(keys = before.keys.copy(customVariations = before.keys.customVariations - "e"))
        assertEquals(before, SettingsSection.variationsFor('e').restoreFrom(before)(resetE))
        // Undo of a letter that was on the built-in list removes the stored copy again.
        val edited = before.copy(keys = before.keys.copy(customVariations = before.keys.customVariations + ("o" to listOf("ö"))))
        assertEquals(before, SettingsSection.variationsFor('o').restoreFrom(before)(edited))
        assertEquals(before, SettingsSection.ALL_VARIATIONS.restoreFrom(before)(before.copy(keys = before.keys.copy(customVariations = emptyMap()))))
    }

    @Test
    fun `a deleted personal word comes back with its count and last use`() {
        val word = PersonalWord("Brobata", frequency = 7, lastUsedMillis = 1_700_000_000_000)
        val store = UserWordStore.of(listOf(WordFrequency("kitchen", 3)), listOf(word, PersonalWord("Tripleseat", 2, 5)))
        val afterDelete = store.withPersonalWordRemoved("Brobata")
        val restored = DictionaryUndo.restorePersonal(afterDelete, word)
        assertEquals(store.personalWords().toSet(), restored.personalWords().toSet())
        assertEquals(store.defaultWords(), restored.defaultWords())
        // Re-added by hand in the meantime: the newer entry is kept.
        val readded = afterDelete.withPersonalWordAdded("Brobata", nowMillis = 9)
        assertEquals(readded.personalWords().toSet(), DictionaryUndo.restorePersonal(readded, word).personalWords().toSet())
    }

    @Test
    fun `a deleted default word goes back in its old place`() {
        val words = listOf(WordFrequency("a", 1), WordFrequency("b", 2), WordFrequency("c", 3))
        val removed = words[1]
        assertEquals(words, DictionaryUndo.restoreDefault(words - removed, removed, 1))
        assertEquals(listOf(WordFrequency("a", 1), removed), DictionaryUndo.restoreDefault(listOf(WordFrequency("a", 1)), removed, 5), "clamped to the end")
        assertEquals(words, DictionaryUndo.restoreDefault(words, removed, 0), "already there: unchanged")
    }
}
