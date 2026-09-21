package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserWordStoreTest {

    @Test
    fun `T-an empty store knows nothing`() {
        val store = UserWordStore.empty()
        assertFalse(store.isKnown("PhysiBoard"))
        assertEquals(0, store.frequencyOf("PhysiBoard"))
        assertNull(store.sourceOf("PhysiBoard"))
    }

    @Test
    fun `T-default words are known and sourced as default`() {
        val store = UserWordStore.of(defaultWords = listOf(WordFrequency("PhysiBoard", 30)))

        assertTrue(store.isKnown("physiboard"))
        assertEquals(30, store.frequencyOf("PhysiBoard"))
        assertEquals(WordSource.DEFAULT_USER, store.sourceOf("PhysiBoard"))
    }

    @Test
    fun `T-adding a new personal word starts it at frequency 1`() {
        val store = UserWordStore.empty().withPersonalWordAdded("Parenzo", nowMillis = 1000L)

        assertTrue(store.isKnown("parenzo"))
        assertEquals(1, store.frequencyOf("Parenzo"))
        assertEquals(WordSource.PERSONAL, store.sourceOf("Parenzo"))
        assertEquals(listOf(PersonalWord("Parenzo", 1, 1000L)), store.personalWords())
    }

    @Test
    fun `T-adding an existing personal word increments frequency and refreshes last used`() {
        val once = UserWordStore.empty().withPersonalWordAdded("Parenzo", nowMillis = 1000L)
        val twice = once.withPersonalWordAdded("PARENZO", nowMillis = 2000L)

        val entry = twice.personalWords().single()
        assertEquals(2, entry.frequency)
        assertEquals(2000L, entry.lastUsedMillis)
    }

    @Test
    fun `T-personal words take precedence over default words with the same spelling`() {
        val store = UserWordStore
            .of(defaultWords = listOf(WordFrequency("BlackBerry", 25)))
            .withPersonalWordAdded("BlackBerry", nowMillis = 1L)

        assertEquals(WordSource.PERSONAL, store.sourceOf("BlackBerry"))
        assertEquals(1, store.frequencyOf("BlackBerry"))
    }

    @Test
    fun `T-removing a personal word makes it unknown again`() {
        val store = UserWordStore.empty()
            .withPersonalWordAdded("Parenzo", nowMillis = 1L)
            .withPersonalWordRemoved("parenzo")

        assertFalse(store.isKnown("Parenzo"))
        assertTrue(store.personalWords().isEmpty())
    }

    @Test
    fun `T-removing a word that was never added is a no-op`() {
        val store = UserWordStore.empty().withPersonalWordAdded("Parenzo", nowMillis = 1L)
        val unchanged = store.withPersonalWordRemoved("nothing")

        assertEquals(store.personalWords(), unchanged.personalWords())
    }

    @Test
    fun `T-removing a personal word never touches default words`() {
        val store = UserWordStore
            .of(defaultWords = listOf(WordFrequency("PhysiBoard", 30)))
            .withPersonalWordRemoved("PhysiBoard")

        assertTrue(store.isKnown("PhysiBoard"))
        assertEquals(WordSource.DEFAULT_USER, store.sourceOf("PhysiBoard"))
    }
}
