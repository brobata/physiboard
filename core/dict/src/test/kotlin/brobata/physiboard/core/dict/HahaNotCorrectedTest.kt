package brobata.physiboard.core.dict

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The maintainer's "haha" kept arriving as "hama" (2026-09-28). Both are in the shipped list. */
class HahaNotCorrectedTest {

    private fun shippedIndex(): DictionaryIndex {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "app/src/main/assets/dictionaries/en.pbd").isFile) dir = dir.parentFile
        val file = File(assertNotNull(dir, "could not find the repo root"), "app/src/main/assets/dictionaries/en.pbd")
        return assertNotNull(DictionaryIndex.fromPbdBytes(file.readBytes()), "en.pbd failed to parse")
    }

    /**
     * The corpus the word list is built from drops common chat words while keeping obscure real
     * ones, so "haha" was absent and "hama" present, and autocorrect did as it was told. The
     * shipped default user words carry the missing ones; a user word is known and is never
     * corrected away (dictionaries-languages.md SS7).
     */
    @Test
    fun `the words the corpus drops are shipped as default user words`() {
        val defaults = defaultUserWords()
        for (word in listOf("haha", "hehe", "yep", "nope", "gonna", "hmm", "lol", "fuck")) {
            assertTrue(defaults.contains(word), "$word is missing from the shipped default user words")
        }
    }

    private fun defaultUserWords(): Set<String> {
        var dir: File? = File(".").absoluteFile
        val rel = "app/src/main/assets/common/dictionaries/user_defaults.json"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        val text = File(assertNotNull(dir, "could not find the repo root"), rel).readText()
        return text.split("\"w\":\"").drop(1).map { it.substringBefore('"').lowercase() }.toSet()
    }

    @Test
    fun `what the ranking offers for haha, for the record`() {
        val index = shippedIndex()
        val entries = mutableListOf<WordFrequency>()
        index.entriesForExactKey("haha", limit = 8, into = entries)
        println("exact-key entries for haha: " + entries.map { "${it.word}=${it.frequency}" })
        val neighbours = mutableListOf<WordFrequency>()
        index.entriesForExactKey("hama", limit = 8, into = neighbours)
        println("exact-key entries for hama: " + neighbours.map { "${it.word}=${it.frequency}" })
    }
}
