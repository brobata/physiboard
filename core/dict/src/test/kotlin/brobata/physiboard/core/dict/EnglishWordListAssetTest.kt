package brobata.physiboard.core.dict

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises [DictionaryIndex] against the real bundled English list built by
 * `scripts/build_dictionary.py` (see docs/spec/autocorrect-suggestions.md §11 for the recipe and
 * docs/dictionaries.md for why only English is built this way today), rather than a handful of
 * hand-picked entries: membership, prefix and neighbour queries all need to work on the actual
 * 80,000-entry shape, not just a small fixture.
 */
class EnglishWordListAssetTest {

    private val index: DictionaryIndex? by lazy {
        assetBytes()?.let { DictionaryIndex.fromPbdBytes(it) }
    }

    @Test
    fun `T-the bundled English asset parses and is the expected size`() {
        val loaded = assertNotNull(index, "app/src/main/assets/dictionaries/en.pbd is missing or failed to parse; run scripts/build_dictionary.py")
        assertEquals(LanguageCode.of("en"), loaded.language)
        // 80,000 built, less the slurs in scripts/blocklists/en.txt.
        assertEquals(79_961, loaded.wordCount)
    }

    @Test
    fun `T-ordinary English words are known, including the ones the old list once missed`() {
        val loaded = assertNotNull(index)
        // spec: autocorrect-suggestions.md SS10, the exact words a coverage regression in the
        // old English list dropped. Regressing on any of these means the coverage bug is back.
        val onceMissing = listOf(
            "salve", "gaunt", "glean", "lithe", "canny", "dowdy", "flout",
            "imbue", "jostle", "loathe", "shirk", "spurn", "vex", "ember",
        )
        for (word in onceMissing) assertTrue(loaded.contains(word), "expected '$word' to be a known word")
    }

    @Test
    fun `T-common misspellings the lexicon filter exists to exclude are not known words`() {
        val loaded = assertNotNull(index)
        val misspellings = listOf("alot", "teh", "thier", "untill", "definately", "seperate", "occured", "recieve", "goverment", "wierd")
        for (typo in misspellings) assertFalse(loaded.contains(typo), "expected '$typo' NOT to be a known word")
    }

    @Test
    fun `T-no word in the slur blocklist is a known word, while ordinary profanity is`() {
        val loaded = assertNotNull(index)
        val blocklist = File(assertNotNull(repoRoot()), "scripts/blocklists/en.txt").readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
        assertTrue(blocklist.size >= 30, "scripts/blocklists/en.txt looks empty or unreadable")
        for (word in blocklist) assertFalse(loaded.contains(word), "'$word' is on the blocklist but still in en.pbd; run build_dictionary.py --apply-blocklist")
        for (word in listOf("fuck", "shit", "damn")) assertTrue(loaded.contains(word), "profanity is ordinary language and stays: '$word'")
    }

    @Test
    fun `T-prefix lookup finds completions on the real list`() {
        val loaded = assertNotNull(index)
        val results = mutableListOf<WordFrequency>()
        loaded.prefixLookup("keyboa", limit = 20, into = results)
        assertTrue(results.any { it.word == "keyboard" }, "expected a completion for 'keyboa' -> 'keyboard', got $results")
    }

    @Test
    fun `T-neighbour lookup finds a one-edit correction on the real list`() {
        val loaded = assertNotNull(index)
        val results = mutableListOf<ScoredCandidate>()
        // "recieve" is one transposition from "receive" (OSA distance 1) and is itself excluded
        // from the list by the lexicon filter, so this is the fuzzy-match path, not membership.
        loaded.neighbours("recieve", maxDistance = 2, limit = 5, into = results)
        assertTrue(results.any { it.word == "receive" }, "expected 'recieve' to neighbour 'receive', got $results")
    }

    private fun assetBytes(): ByteArray? = repoRoot()
        ?.resolve("app/src/main/assets/dictionaries/en.pbd")
        ?.takeIf { it.isFile }
        ?.readBytes()

    /** Walks upward from the working directory to find the repo root (marked by settings.gradle.kts). */
    private fun repoRoot(): File? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        return null
    }
}
