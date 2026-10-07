package brobata.physiboard.ime

import android.os.Looper
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * The rules [SharedDictionaries] took over from the keyboard when the spell checker began sharing
 * its dictionaries: a load overtaken by a reload never lands, a reader on another thread waits a
 * bounded time and never on the main thread, and a reload leaves nothing able to pin the old data.
 * `de` is used because no German dictionary exists in a test, so its load always ends as failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SharedDictionariesTest {

    private val german = checkNotNull(LanguageCode.of("de"))
    private val english = checkNotNull(LanguageCode.of("en"))
    private lateinit var shared: SharedDictionaries

    @Before
    fun setUp() {
        SharedDictionaries.resetForTest()
        shared = SharedDictionaries.get(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = SharedDictionaries.resetForTest()

    /** Runs the main looper until [done] holds, giving the loader's own thread time to post back. */
    private fun drainUntil(timeoutMillis: Long = 5_000, done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a load overtaken by a reload never lands, and the next request loads afresh`() {
        shared.request(german)
        shared.reloadAll()
        val afterReload = shared.snapshot.generation
        // Give the overtaken load every chance to post its result.
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("the overtaken load must not mark German failed", german in shared.snapshot.failed)
        assertFalse(german in shared.snapshot.complete)
        assertEquals(afterReload, shared.snapshot.generation)

        shared.request(german)
        drainUntil { german in shared.snapshot.complete }
        assertTrue("a fresh request in the new generation finishes", german in shared.snapshot.failed)
    }

    @Test
    fun `waiting off the main thread gives up within its timeout when nothing finishes`() {
        // No user words and no load: German can never become ready.
        val result = AtomicReference<Boolean>()
        val elapsed = AtomicReference<Long>()
        val reader = Thread {
            val start = System.nanoTime()
            result.set(shared.awaitReady(german, 200))
            elapsed.set((System.nanoTime() - start) / 1_000_000)
        }
        reader.start()
        reader.join(5_000)
        assertFalse("the reader thread must not hang", reader.isAlive)
        assertEquals(false, result.get())
        assertTrue("gave up after ${elapsed.get()} ms", elapsed.get() in 150L..2_000L)
    }

    @Test
    fun `waiting on the main thread never blocks`() {
        val start = System.nanoTime()
        assertFalse(shared.awaitReady(german, 5_000))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 100)
    }

    @Test
    fun `a reader waiting off the main thread wakes when the language becomes ready`() {
        val result = AtomicReference<Boolean>()
        val reader = Thread { result.set(shared.awaitReady(english, 5_000)) }
        reader.start()
        Thread.sleep(50)
        shared.install(english, DictionaryIndex.build(english, listOf(WordFrequency("word", 200))), null, UserWordStore.empty())
        reader.join(5_000)
        assertFalse(reader.isAlive)
        assertEquals(true, result.get())
    }

    @Test
    fun `every publish stamps a new version, so a reader can drop its cache without holding the old snapshot`() {
        shared.install(english, DictionaryIndex.build(english, listOf(WordFrequency("word", 200))), null, UserWordStore.empty())
        val before = shared.snapshot.version
        shared.reloadAll()
        shadowOf(Looper.getMainLooper()).idle()
        val after = shared.snapshot
        assertNotEquals(before, after.version)
        assertFalse("a reload drops the old dictionary", english in after.dictionaries)
    }
}
