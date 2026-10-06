package brobata.physiboard.ime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The order of one dictionary load ([DictionaryLoadSequence]): the dictionary reaches the keyboard
 * before the word-pair table is read, the caller always hears about the dictionary (so its
 * in-flight marker clears), and a table that cannot be read or does not fit in memory costs only
 * the sentence context.
 */
class DictionaryLoadSequenceTest {

    private val events = mutableListOf<String>()

    private fun load(readDictionary: () -> String?, readContextModel: () -> String?) = DictionaryLoadSequence.run(
        readDictionary = readDictionary,
        readContextModel = { events += "table read"; readContextModel() },
        // The main thread's queue, run in order as it would be.
        post = { it() },
        onDictionary = { events += "dictionary $it" },
        onContextModel = { events += "table $it" },
        onFailure = { what, error -> events += "failed $what ${error::class.simpleName}" },
    )

    @Test
    fun `the dictionary is handed over before the table is read`() {
        load(readDictionary = { "en" }, readContextModel = { "pairs" })
        assertEquals(listOf("dictionary en", "table read", "table pairs"), events)
    }

    @Test
    fun `a table that runs out of memory costs the context, not the dictionary or the thread`() {
        load(readDictionary = { "en" }, readContextModel = { throw OutOfMemoryError("4.7 MB of arrays") })
        assertEquals(listOf("dictionary en", "table read", "failed context OutOfMemoryError"), events)
    }

    @Test
    fun `a table that fails to read or parse is no context`() {
        load(readDictionary = { "en" }, readContextModel = { throw IllegalStateException("bad offsets") })
        assertEquals(listOf("dictionary en", "table read", "failed context IllegalStateException"), events)
        events.clear()
        load(readDictionary = { "en" }, readContextModel = { null })
        assertEquals(listOf("dictionary en", "table read"), events)
    }

    @Test
    fun `a dictionary that fails is still reported, as null, and no table is read`() {
        load(readDictionary = { null }, readContextModel = { "pairs" })
        assertEquals(listOf("dictionary null"), events)
        events.clear()
        load(readDictionary = { throw OutOfMemoryError() }, readContextModel = { "pairs" })
        assertEquals(listOf("failed dictionary OutOfMemoryError", "dictionary null"), events)
    }
}
