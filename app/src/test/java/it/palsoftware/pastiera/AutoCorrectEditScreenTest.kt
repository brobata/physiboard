package it.palsoftware.pastiera

import it.palsoftware.pastiera.core.suggestions.UserDictionaryStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoCorrectEditScreenTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun addReplacementToUserDictionaryIfNeeded_addsMissingReplacement() {
        val context = RuntimeEnvironment.getApplication()
        val store = UserDictionaryStore()

        val added = addReplacementToUserDictionaryIfNeeded(context, store, " pamphlet ")

        assertTrue(added)
        store.loadUserEntries(context)
        assertTrue(store.getSnapshot().any { it.word == "pamphlet" })
    }

    @Test
    fun addReplacementToUserDictionaryIfNeeded_doesNotDuplicateExistingWord() {
        val context = RuntimeEnvironment.getApplication()
        val store = UserDictionaryStore()
        store.addWord(context, "Pamphlet")

        val added = addReplacementToUserDictionaryIfNeeded(context, store, "pamphlet")

        assertFalse(added)
        store.loadUserEntries(context)
        assertTrue(store.getSnapshot().count { it.word.equals("pamphlet", ignoreCase = true) } == 1)
    }

    @Test
    fun addReplacementToUserDictionaryIfNeeded_ignoresPunctuationOnlyReplacement() {
        val context = RuntimeEnvironment.getApplication()
        val store = UserDictionaryStore()

        val added = addReplacementToUserDictionaryIfNeeded(context, store, "?!")

        assertFalse(added)
        store.loadUserEntries(context)
        assertFalse(store.getSnapshot().any { it.word == "?!" })
    }
}
