package brobata.physiboard.ime

import brobata.physiboard.core.dict.Bigram
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.dict.NgramStore
import brobata.physiboard.core.strip.SuggestionRow
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.PunctuationKey
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.TextInputResources
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * spec: autocorrect-suggestions.md SS4, "Test cases" T50 (this module's slice: the strip fed from
 * [KeyboardPipeline], not the pure ranking `:core:text` already proves).
 */
class NextWordSuggestionsPipelineTest {

    private val layout = TitanLayouts.titan2EliteQwerty()
    private val english = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("bin", 200), WordFrequency("the", 150)))

    private fun pipeline() = KeyboardPipeline(layout = layout, resources = TextInputResources(dictionaries = listOf(english))).also {
        it.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.example.chat"))
    }

    private fun snapshot(text: String, nowMs: Long) = EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = nowMs)

    private fun press(pipeline: KeyboardPipeline, key: KeyId, text: String, nowMs: Long): String {
        val result = pipeline.onKeyStroke(KeyStroke(key, KeyEdge.DOWN, 0, nowMs), snapshot(text, nowMs))
        val committed = result.ops.filterIsInstance<EditorOp.CommitText>().joinToString("") { it.text }
        pipeline.onKeyStroke(KeyStroke(key, KeyEdge.UP, 0, nowMs + 1), snapshot(text + committed, nowMs + 1))
        return text + committed
    }

    private fun type(pipeline: KeyboardPipeline, word: String, startAt: Long): String {
        var text = ""
        var t = startAt
        for (c in word) {
            text = press(pipeline, KeyId.Letter(c.uppercaseChar()), text, t)
            t += 10
        }
        return text
    }

    private fun model(pipeline: KeyboardPipeline) = pipeline.stripModel(clipboardCount = 0, dictationActive = false, dictionaryInstalled = true, subtypeLocale = "en")

    @Test
    fun `an empty current word shows starter words rather than going blank`() {
        val pipeline = pipeline()
        assertEquals(listOf("bin", "the"), pipeline.nextWordSuggestions())
        // Also proves the strip itself is fed from this, not left blank (SS4).
        val row = assertIs<SuggestionRow.Slots>(model(pipeline).row)
        assertTrue("bin" in row.texts, "expected the strip's own slots to carry a next-word prediction, got ${row.texts}")
    }

    @Test
    fun `typing a word ich then Space learns the bigram and offers bin as the next word`() {
        val pipeline = pipeline()
        val text = type(pipeline, "ich", startAt = 100)
        press(pipeline, KeyId.Control(ControlKey.SPACE), text, 500)

        assertTrue("bin" in pipeline.nextWordSuggestions(), "expected 'bin' among next-word predictions, got ${pipeline.nextWordSuggestions()}")
    }

    @Test
    fun `learning the same pair a second time ranks it first over an untouched starter`() {
        val pipeline = pipeline()
        var text = type(pipeline, "ich", startAt = 100)
        text = press(pipeline, KeyId.Control(ControlKey.SPACE), text, 200)
        text = type(pipeline, "bin", startAt = 300)
        press(pipeline, KeyId.Control(ControlKey.SPACE), text, 400)

        text = type(pipeline, "ich", startAt = 500)
        press(pipeline, KeyId.Control(ControlKey.SPACE), text, 600)

        assertEquals("bin", pipeline.nextWordSuggestions().first())
    }

    @Test
    fun `typing any letter replaces predictions with current-word suggestions`() {
        val pipeline = pipeline()
        var text = type(pipeline, "ich", startAt = 100)
        text = press(pipeline, KeyId.Control(ControlKey.SPACE), text, 200)
        assertTrue(pipeline.nextWordSuggestions().isNotEmpty())

        press(pipeline, KeyId.Letter('T'), text, 300)
        // Once the current word is non-empty, nextWordSuggestions (the empty-word path) yields
        // nothing: the strip has switched to ordinary current-word suggestions instead (SS4).
        assertEquals(emptyList(), pipeline.nextWordSuggestions())
    }

    @Test
    fun `a hard boundary resets the context to sentence start`() {
        val pipeline = pipeline()
        pipeline.onNgramStoreLoaded(listOf(Bigram("en", NgramStore.SENTENCE_START, "the", count = 5, lastUsedMillis = 1)))
        var text = type(pipeline, "ich", startAt = 100)
        text = press(pipeline, KeyId.Control(ControlKey.SPACE), text, 200)
        text = type(pipeline, "bin", startAt = 300)
        text = press(pipeline, KeyId.Punctuation(PunctuationKey.PERIOD), text, 400)

        assertEquals("the", pipeline.nextWordSuggestions().first(), "a hard boundary should reset the next-word context to sentence start")
    }

    @Test
    fun `a bigram learned this session persists through onBigramLearned`() {
        val pipeline = pipeline()
        val learned = mutableListOf<Triple<String, String, String>>()
        pipeline.onBigramLearned = { locale, prefix, nextWord -> learned.add(Triple(locale, prefix, nextWord)) }
        val text = type(pipeline, "ich", startAt = 100)
        press(pipeline, KeyId.Control(ControlKey.SPACE), text, 200)
        assertEquals(1, learned.size)
        assertEquals("ich", learned.first().third)
    }
}
