package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: autocorrect-suggestions.md SS6.1, SS6.3; dictionaries-languages.md SS7. */
class UserWordFileCodecTest {

    @Test
    fun `personal words round trip through encode and decode`() {
        val words = listOf(PersonalWord("Pastiera", 3, 1000L), PersonalWord("brobata", 1, 2000L))
        val decoded = UserWordFileCodec.decodePersonalWords(UserWordFileCodec.encodePersonalWords(words))
        assertEquals(words, decoded)
    }

    @Test
    fun `decoding a blank or unparsable personal file yields an empty list`() {
        assertEquals(emptyList(), UserWordFileCodec.decodePersonalWords(null))
        assertEquals(emptyList(), UserWordFileCodec.decodePersonalWords(""))
        assertEquals(emptyList(), UserWordFileCodec.decodePersonalWords("not json"))
        assertEquals(emptyList(), UserWordFileCodec.decodePersonalWords("{}"))
    }

    @Test
    fun `a malformed personal word entry is skipped, not thrown`() {
        val text = """[{"w":"good","f":2,"u":10},{"f":5,"u":10},{"w":"","f":1,"u":1},"not an object"]"""
        val decoded = UserWordFileCodec.decodePersonalWords(text)
        assertEquals(listOf(PersonalWord("good", 2, 10L)), decoded)
    }

    @Test
    fun `a personal entry missing f or u defaults them`() {
        val decoded = UserWordFileCodec.decodePersonalWords("""[{"w":"solo"}]""")
        assertEquals(listOf(PersonalWord("solo", 1, 0L)), decoded)
    }

    @Test
    fun `default words round trip and default a missing frequency to 1, per SS7`() {
        val encoded = UserWordFileCodec.encodeDefaultWords(listOf(WordFrequency("PhysiBoard", 30)))
        assertEquals(listOf(WordFrequency("PhysiBoard", 30)), UserWordFileCodec.decodeDefaultWords(encoded))
        assertEquals(listOf(WordFrequency("BlackBerry", 1)), UserWordFileCodec.decodeDefaultWords("""[{"w":"BlackBerry"}]"""))
    }

    @Test
    fun `a new word is valid only when its trimmed form is non blank, per SS6-3`() {
        assertTrue(isValidNewDictionaryWord("hello"))
        assertTrue(isValidNewDictionaryWord("  hello  "))
        assertFalse(isValidNewDictionaryWord(""))
        assertFalse(isValidNewDictionaryWord("   "))
    }

    @Test
    fun `an upgrade adds the default words this build gained`() {
        val asset = listOf(WordFrequency("PhysiBoard", 30), WordFrequency("haha", 30), WordFrequency("yep", 30))
        val stored = listOf(WordFrequency("PhysiBoard", 30))
        val seeded = setOf("physiboard")
        assertEquals(
            listOf(WordFrequency("haha", 30), WordFrequency("yep", 30)),
            UserWordFileCodec.defaultWordsToMergeIn(asset, stored, seeded),
        )
    }

    @Test
    fun `a default word the user deleted is not raised from the dead`() {
        val asset = listOf(WordFrequency("PhysiBoard", 30), WordFrequency("haha", 30))
        // "PhysiBoard" was seeded before and is gone from the stored file: the user deleted it.
        val merged = UserWordFileCodec.defaultWordsToMergeIn(asset, emptyList(), setOf("physiboard"))
        assertEquals(listOf(WordFrequency("haha", 30)), merged)
    }

    @Test
    fun `an install that never recorded what it seeded gains every word it is missing`() {
        val asset = listOf(WordFrequency("PhysiBoard", 30), WordFrequency("haha", 30))
        val stored = listOf(WordFrequency("PhysiBoard", 30))
        assertEquals(listOf(WordFrequency("haha", 30)), UserWordFileCodec.defaultWordsToMergeIn(asset, stored, emptySet()))
    }

    @Test
    fun `a word already stored is left at the frequency the user gave it`() {
        val asset = listOf(WordFrequency("haha", 30))
        val stored = listOf(WordFrequency("haha", 99))
        assertTrue(UserWordFileCodec.defaultWordsToMergeIn(asset, stored, emptySet()).isEmpty())
    }

    @Test
    fun `seeded spellings survive a round trip and are matched without regard to case`() {
        val encoded = UserWordFileCodec.encodeSeededSpellings(listOf(WordFrequency("PhysiBoard", 30), WordFrequency("haha", 30)))
        assertEquals(setOf("physiboard", "haha"), UserWordFileCodec.decodeSeededSpellings(encoded))
        assertEquals(emptySet<String>(), UserWordFileCodec.decodeSeededSpellings(null))
        assertEquals(emptySet<String>(), UserWordFileCodec.decodeSeededSpellings("not json"))
    }
}
