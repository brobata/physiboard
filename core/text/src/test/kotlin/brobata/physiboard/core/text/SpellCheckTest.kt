package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The system spell checker's judgement ([SpellCheck], autocorrect-suggestions.md §18) on the
 * shipped English dictionary and word-pair table.
 */
class SpellCheckTest {

    private companion object {
        fun assets(): File {
            System.getProperty("physiboard.assets.dictionaries")?.let { return File(it) }
            var dir: File? = File(".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
            return File(checkNotNull(dir), "app/src/main/assets/dictionaries")
        }

        val dictionary: DictionaryIndex by lazy { assertNotNull(DictionaryIndex.fromPbdBytes(File(assets(), "en.pbd").readBytes())) }
        val model: ContextModel by lazy { assertNotNull(ContextModel.read(File(assets(), "en.bigrams").readBytes())) }
        val defaults: List<WordFrequency> by lazy {
            UserWordFileCodec.decodeDefaultWords(File(assets().parentFile, "common/dictionaries/user_defaults.json").readText())
        }
    }

    private fun resources(
        personal: List<String> = emptyList(),
        withModel: Boolean = true,
        systemWords: Set<String> = emptySet(),
    ) = SpellResources(
        dictionaries = listOf(dictionary),
        userWords = UserWordStore.of(defaults, personal.map { PersonalWord(it, 1, 0L) }),
        contextModel = if (withModel) model else null,
        lengthChangeAllowance = LengthChangeAllowance.ENGLISH,
        systemWords = systemWords,
    )

    /** The finding for [word] (its first occurrence) in [text]. */
    private fun finding(text: String, word: String, r: SpellResources = resources()): SpellFinding {
        val start = text.indexOf(word)
        return SpellCheck.checkSentence(text, r, limit = 5).single { it.start == start && it.end == start + word.length }
    }

    private fun flagged(text: String, r: SpellResources = resources()): List<String> =
        SpellCheck.checkSentence(text, r, limit = 5).filter { it.kind == SpellKind.TYPO || it.kind == SpellKind.MIXUP }.map { text.substring(it.start, it.end) }

    @Test
    fun `correct text flags nothing and every word is known`() {
        val text = "The quick brown fox jumps over the lazy dog."
        val findings = SpellCheck.checkSentence(text, resources(), limit = 5)
        assertEquals(9, findings.size)
        assertTrue(findings.all { it.kind == SpellKind.KNOWN }, findings.toString())
    }

    @Test
    fun `a typo is flagged with the noisy channel's correction first, cased as typed`() {
        val f = finding("I will recieve it tomorow", "recieve")
        assertEquals(SpellKind.TYPO, f.kind)
        assertEquals("receive", f.suggestions.first())
        assertTrue(f.confident)
        assertEquals("tomorrow", finding("I will recieve it tomorow", "tomorow").suggestions.first())
        assertEquals("The", finding("Teh dog barked", "Teh").suggestions.first())
        assertTrue(finding("Teh dog barked", "Teh").suggestions.size <= 5)
    }

    @Test
    fun `the word before steers the ranking`() {
        // Both are one slip from "fro"; after "back and" the table knows which.
        val f = finding("back and fprth", "fprth")
        assertEquals(SpellKind.TYPO, f.kind)
        assertEquals("forth", f.suggestions.first())
    }

    @Test
    fun `a missing apostrophe is offered`() {
        val f = finding("I dont know", "dont")
        assertEquals(SpellKind.TYPO, f.kind)
        assertEquals("don't", f.suggestions.first())
    }

    @Test
    fun `personal and default words are never flagged`() {
        assertEquals(SpellKind.TYPO, finding("we use zorbulon daily", "zorbulon").kind)
        assertEquals(SpellKind.KNOWN, finding("we use zorbulon daily", "zorbulon", resources(personal = listOf("zorbulon"))).kind)
        assertEquals(SpellKind.KNOWN, finding("haha that was gonna happen", "haha").kind)
        assertEquals(SpellKind.KNOWN, finding("haha that was gonna happen", "gonna").kind)
    }

    @Test
    fun `a word added to Android's own dictionary is known`() {
        assertEquals(SpellKind.TYPO, finding("the blorptastic show", "blorptastic").kind)
        assertEquals(SpellKind.KNOWN, finding("the blorptastic show", "blorptastic", resources(systemWords = setOf("blorptastic"))).kind)
    }

    @Test
    fun `a capitalised unknown word mid-sentence is a name, not a typo`() {
        assertEquals(SpellKind.UNFLAGGED, finding("I met Mennad yesterday", "Mennad").kind)
        assertEquals(SpellKind.UNFLAGGED, finding("Ziri went home", "Ziri").kind)
        assertEquals(emptyList(), flagged("Then Skura and Rima left for Tizi Ouzou."))
    }

    @Test
    fun `without a table a capitalised unknown word is never flagged and a lowercase one still is`() {
        val r = resources(withModel = false)
        assertEquals(SpellKind.UNFLAGGED, finding("Teh dog", "Teh", r).kind)
        val f = finding("the dgo barked", "dgo", r)
        assertEquals(SpellKind.TYPO, f.kind)
        assertTrue("dog" in f.suggestions, f.suggestions.toString())
        assertFalse(f.confident)
    }

    @Test
    fun `URLs, addresses, numbers, hashtags and mentions are never flagged`() {
        val text = "see https://exampel.com/teh or mail jon@exampel.org at 4pm #throwbackthursdy @jonnny site.com/recieve"
        assertEquals(emptyList(), flagged(text))
        val skipped = SpellCheck.checkSentence(text, resources(), limit = 5).filter { it.kind == SpellKind.SKIPPED }
        assertEquals(6, skipped.size, skipped.toString())
    }

    @Test
    fun `acronyms, inner capitals and other scripts are not judged`() {
        assertEquals(SpellKind.SKIPPED, finding("the XKCDQ team", "XKCDQ").kind)
        assertEquals(SpellKind.SKIPPED, finding("my iPhonr broke", "iPhonr").kind)
        assertEquals(SpellKind.SKIPPED, finding("he said привет", "привет").kind)
    }

    @Test
    fun `regional spellings, inflections, contractions and short words are left`() {
        assertEquals(emptyList(), flagged("I realised the neighbours travelled"))
        assertEquals(emptyList(), flagged("The millennials rehydrated"))
        assertEquals(emptyList(), flagged("you must've seen Sami's dog and the players' ball"))
        assertEquals(SpellKind.UNFLAGGED, finding("so xq it", "xq").kind)
    }

    @Test
    fun `a lowercase word the dictionary only has capitalised gets its capital`() {
        // The shipped English list is all lowercase; a list that capitalises names (as others do) gets the keyboard's case repair.
        val list = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("Paris", 200), WordFrequency("see", 220), WordFrequency("bill", 150), WordFrequency("Bill", 120)))
        val r = SpellResources(listOf(list), UserWordStore.empty(), null, LengthChangeAllowance.ENGLISH)
        val f = finding("see paris", "paris", r)
        assertEquals(SpellKind.TYPO, f.kind)
        assertEquals(listOf("Paris"), f.suggestions)
        assertTrue(f.confident)
        assertEquals(SpellKind.KNOWN, finding("see Paris", "Paris", r).kind)
        // Spelled lowercase somewhere in the list: left.
        assertEquals(SpellKind.KNOWN, finding("see bill", "bill", r).kind)
        // The user's own lowercase word, or one added to Android's dictionary, is theirs.
        assertEquals(SpellKind.KNOWN, finding("see paris", "paris", r.copy(userWords = UserWordStore.empty().withPersonalWordAdded("paris", 0L))).kind)
        assertEquals(SpellKind.KNOWN, finding("see paris", "paris", r.copy(systemWords = setOf("paris"))).kind)
    }

    @Test
    fun `a mix-up is flagged as grammar only when the engine is sure`() {
        val text = "The dog wagged it's tail"
        val f = finding(text, "it's")
        assertEquals(SpellKind.MIXUP, f.kind)
        assertEquals(listOf("its"), f.suggestions)
        // Correct uses of the same words are left alone.
        assertEquals(emptyList(), flagged("It's time to go. The dog wagged its tail."))
        // Nothing after it: no judgement.
        assertEquals(SpellKind.KNOWN, finding("I know it's", "it's").kind)
        // More than one space between the words is not the shape the fix reads.
        assertEquals(SpellKind.KNOWN, finding("The dog wagged it's  tail", "it's").kind)
    }

    @Test
    fun `curly apostrophes read as straight ones and suggestions keep the user's style`() {
        assertEquals(emptyList(), flagged("It isn’t here and she doesn’t know."))
        assertEquals(emptyList(), flagged("The players’ bus was late, wasn’t it? We weren’t sure you’d come."))
        val f = finding("I dont think so", "dont")
        assertEquals("don't", f.suggestions.first())
        val mixup = finding("The dog wagged it’s tail", "it’s")
        assertEquals(SpellKind.MIXUP, mixup.kind)
        val typo = finding("I wouldn’ recieve", "recieve")
        assertEquals("receive", typo.suggestions.first())
        assertEquals("doesn’t", finding("she doesn’y know", "doesn’y").suggestions.firstOrNull { it.startsWith("doesn") })
    }

    @Test
    fun `a mix-up keeps the case the user typed`() {
        val f = finding("Your welcome to come", "Your")
        assertEquals(SpellKind.MIXUP, f.kind)
        assertEquals(listOf("You're"), f.suggestions)
    }

    @Test
    fun `a single word is judged without context`() {
        val r = resources()
        assertEquals(SpellKind.TYPO, SpellCheck.checkWord("recieve", r, 5).kind)
        assertEquals(SpellKind.KNOWN, SpellCheck.checkWord("receive", r, 5).kind)
        assertEquals(SpellKind.SKIPPED, SpellCheck.checkWord("https://exampel.com", r, 5).kind)
        assertEquals(SpellKind.SKIPPED, SpellCheck.checkWord("two words", r, 5).kind)
        assertEquals(SpellKind.UNFLAGGED, SpellCheck.checkWord("Mennad", r, 5).kind)
    }

    @Test
    fun `the limit caps suggestions and the cache returns the same verdicts`() {
        val r = resources()
        val text = "teh cat and teh dog"
        assertTrue(SpellCheck.checkSentence(text, r, limit = 1).filter { it.kind == SpellKind.TYPO }.all { it.suggestions.size <= 1 })
        val cache = HashMap<String, SpellFinding>()
        val first = SpellCheck.checkSentence(text, r, limit = 5, cache = cache)
        val second = SpellCheck.checkSentence(text, r, limit = 5, cache = cache)
        assertEquals(first, second)
        assertEquals(first, SpellCheck.checkSentence(text, r, limit = 5))
        assertTrue(cache.isNotEmpty())
    }

    @Test
    fun `with no dictionary nothing is flagged`() {
        val r = SpellResources(emptyList(), UserWordStore.empty(), null, 0, SpellResources.scriptFor(LanguageCode.of("de").toString()))
        assertEquals(emptyList(), flagged("hallo welt, wie gehts", r))
    }

    @Test
    fun `script follows the language`() {
        assertEquals(Character.UnicodeScript.CYRILLIC, SpellResources.scriptFor("uk"))
        assertEquals(Character.UnicodeScript.GREEK, SpellResources.scriptFor("el"))
        assertEquals(Character.UnicodeScript.LATIN, SpellResources.scriptFor("de"))
    }
}
