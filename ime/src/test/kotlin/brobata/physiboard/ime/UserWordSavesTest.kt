package brobata.physiboard.ime

import android.os.Looper
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordFileCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * dictionaries-languages.md SS7: the keyboard's own add or delete of a personal word is applied to
 * the file as it is when the save runs, never its in-memory list written over it, and a read of
 * the files that started before the save cannot undo the edit in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserWordSavesTest {

    private val app = RuntimeEnvironment.getApplication()
    private val personalFile = File(app.filesDir, UserWordFileCodec.PERSONAL_WORDS_FILE_NAME)
    private lateinit var shared: SharedDictionaries

    @Before
    fun setUp() {
        personalFile.delete()
        SharedDictionaries.resetForTest()
        shared = SharedDictionaries.get(app)
    }

    @After
    fun tearDown() {
        SharedDictionaries.resetForTest()
        personalFile.delete()
    }

    private fun drainUntil(timeoutMillis: Long = 5_000, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun writeFile(vararg words: String) =
        personalFile.writeText(UserWordFileCodec.encodePersonalWords(words.map { PersonalWord(it, 1, 0L) }))

    private fun fileWords(): Set<String> = UserWordFileCodec.decodePersonalWords(personalFile.readText()).map { it.word }.toSet()

    private fun memoryWords(): Set<String> = shared.snapshot.userWords?.personalWords()?.map { it.word }?.toSet().orEmpty()

    @Test
    fun `a keyboard save keeps a word the settings screen saved after the keyboard last read the file`() {
        shared.reloadUserWords()
        drainUntil { shared.snapshot.userWords != null }
        // The Personal dictionary screen saves "brobata"; the keyboard's copy has not caught up.
        writeFile("brobata")

        val saved = AtomicReference<Boolean?>(null)
        shared.editUserWords({ it.withPersonalWordAdded("titan", 1L) }) { saved.set(it) }
        drainUntil { saved.get() != null && memoryWords() == setOf("brobata", "titan") }

        assertEquals(true, saved.get())
        assertEquals(setOf("brobata", "titan"), fileWords())
        assertEquals("the file is read back once the save lands", setOf("brobata", "titan"), memoryWords())
    }

    @Test
    fun `a keyboard delete removes only that word from the file`() {
        writeFile("brobata", "titan", "spoony")
        shared.reloadUserWords()
        drainUntil { memoryWords().size == 3 }
        writeFile("brobata", "titan", "spoony", "olive")

        val saved = AtomicReference<Boolean?>(null)
        shared.editUserWords({ it.withPersonalWordRemoved("spoony") }) { saved.set(it) }
        drainUntil { saved.get() != null && memoryWords() == setOf("brobata", "titan", "olive") }

        assertEquals(setOf("brobata", "titan", "olive"), fileWords())
    }

    @Test
    fun `a read that started before the keyboard's save cannot undo the edit in memory`() {
        writeFile("brobata")
        shadowOf(Looper.getMainLooper()).idle()
        // A read starts (the settings screen's broadcast) and finishes: its result, without
        // "titan", is waiting on the main thread when the keyboard makes its edit.
        shared.reloadUserWords()
        val deadline = System.currentTimeMillis() + 5_000
        while (shadowOf(Looper.getMainLooper()).isIdle && System.currentTimeMillis() < deadline) Thread.sleep(5)

        val saved = AtomicReference<Boolean?>(null)
        shared.editUserWords({ it.withPersonalWordAdded("titan", 1L) }) { saved.set(it) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the stale read did not land over the edit", memoryWords().contains("titan"))

        drainUntil { saved.get() != null && memoryWords() == setOf("brobata", "titan") }

        assertEquals(setOf("brobata", "titan"), memoryWords())
        assertEquals(setOf("brobata", "titan"), fileWords())
    }
}
