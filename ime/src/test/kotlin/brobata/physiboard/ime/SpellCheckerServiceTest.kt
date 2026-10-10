package brobata.physiboard.ime

import android.os.Looper
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The system spell checker's session (autocorrect-suggestions.md SS18) as Android calls it: real
 * [TextInfo], [SuggestionsInfo] and [SentenceSuggestionsInfo], the shipped English dictionary and
 * word-pair table put in the process-wide store the keyboard shares.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpellCheckerServiceTest {

    private companion object {
        val assets: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
            File(checkNotNull(dir), "app/src/main/assets/dictionaries")
        }
        val dictionary: DictionaryIndex by lazy { checkNotNull(DictionaryIndex.fromPbdBytes(File(assets, "en.pbd").readBytes())) }
        val model: ContextModel by lazy { checkNotNull(ContextModel.read(File(assets, "en.bigrams").readBytes())) }
        val english = checkNotNull(LanguageCode.of("en"))
    }

    private lateinit var shared: SharedDictionaries

    @Before
    fun setUp() {
        SharedDictionaries.resetForTest()
        shared = SharedDictionaries.get(RuntimeEnvironment.getApplication())
        shared.install(english, dictionary, model, UserWordStore.of(emptyList(), listOf(PersonalWord("zorbulon", 1, 0L))))
    }

    @After
    fun tearDown() = SharedDictionaries.resetForTest()

    private fun session(locale: String = "en_US") = PhysiBoardSpellCheckerService.PhysiBoardSpellSession(shared, locale).apply { onCreate() }

    private fun check(text: String, session: PhysiBoardSpellCheckerService.PhysiBoardSpellSession = session()): SentenceSuggestionsInfo =
        session.onGetSentenceSuggestionsMultiple(arrayOf(TextInfo(text, 7, 42)), 5).single()

    /** The entry covering [word] in [text]'s answer. */
    private fun entry(result: SentenceSuggestionsInfo, text: String, word: String): SuggestionsInfo {
        val start = text.indexOf(word)
        val index = (0 until result.suggestionsCount).single { result.getOffsetAt(it) == start && result.getLengthAt(it) == word.length }
        return result.getSuggestionsInfoAt(index)
    }

    private fun SuggestionsInfo.has(attribute: Int) = suggestionsAttributes and attribute != 0
    private fun SuggestionsInfo.suggestions() = (0 until suggestionsCount).map { getSuggestionAt(it) }

    @Test
    fun `the service hands out PhysiBoard's session`() {
        val service = Robolectric.buildService(PhysiBoardSpellCheckerService::class.java).create().get()
        assertTrue(service.createSession() is PhysiBoardSpellCheckerService.PhysiBoardSpellSession)
    }

    @Test
    fun `a typo is underlined with the engine's correction first, matched to the request`() {
        val text = "I will recieve it"
        val result = check(text)
        assertEquals(4, result.suggestionsCount)
        val typo = entry(result, text, "recieve")
        assertTrue(typo.has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO))
        assertTrue(typo.has(SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS))
        assertFalse(typo.has(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
        assertEquals("receive", typo.suggestions().first())
        assertTrue(typo.suggestionsCount <= 5)
        assertEquals(7, typo.cookie)
        assertEquals(42, typo.sequence)
        val known = entry(result, text, "will")
        assertTrue(known.has(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
        assertFalse(known.has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO))
        assertEquals(42, known.sequence)
    }

    @Test
    fun `a mix-up is a grammar error with its twin`() {
        val text = "The dog wagged it's tail"
        val mixup = entry(check(text), text, "it's")
        assertTrue(mixup.has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR))
        assertFalse(mixup.has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO))
        assertEquals(listOf("its"), mixup.suggestions())
    }

    @Test
    fun `personal words, names and URLs are not underlined`() {
        val text = "Ask Mennad about zorbulon at https://exampel.com/teh"
        val result = check(text)
        for (i in 0 until result.suggestionsCount) {
            val info = result.getSuggestionsInfoAt(i)
            assertFalse(text.substring(result.getOffsetAt(i), result.getOffsetAt(i) + result.getLengthAt(i)), info.has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO))
        }
        assertTrue(entry(result, text, "zorbulon").has(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
    }

    @Test
    fun `a single word is checked on its own`() {
        val info = session().onGetSuggestions(TextInfo("recieve", 3, 9), 5)
        assertTrue(info.has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO))
        assertEquals("receive", info.suggestions().first())
        assertEquals(3, info.cookie)
        assertEquals(9, info.sequence)
        assertTrue(session().onGetSuggestions(TextInfo("receive", 3, 9), 5).has(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
    }

    @Test
    fun `a language without a dictionary flags nothing and still answers for the whole text`() {
        val german = session("de_DE")
        // The load of the missing German dictionary finishes, with nothing, on the main thread.
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            if (shared.snapshot.isReady(checkNotNull(LanguageCode.of("de")))) return@repeat
            Thread.sleep(20)
        }
        val text = "Das ist ein Tset"
        val result = check(text, german)
        assertEquals(1, result.suggestionsCount)
        assertEquals(0, result.getOffsetAt(0))
        assertEquals(text.length, result.getLengthAt(0))
        assertEquals(0, result.getSuggestionsInfoAt(0).suggestionsAttributes)
        assertEquals(42, result.getSuggestionsInfoAt(0).sequence)
    }

    @Test
    fun `blank text gets one empty answer, never none`() {
        val result = check("   ")
        assertEquals(1, result.suggestionsCount)
        assertEquals(0, result.getSuggestionsInfoAt(0).suggestionsAttributes)
    }

    @Test
    fun `a personal word added later is known at once`() {
        val text = "we use blorptastic daily"
        assertTrue(entry(check(text), text, "blorptastic").has(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO))
        checkNotNull(shared.snapshot.userWords)
        shared.editUserWords({ it.withPersonalWordAdded("blorptastic", 0L) }) {}
        assertTrue(entry(check(text), text, "blorptastic").has(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY))
    }
}
