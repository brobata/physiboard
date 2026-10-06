package brobata.physiboard.ime

import brobata.physiboard.core.dict.Bigram
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
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
import kotlin.test.assertTrue

/**
 * app-shell.md SS31: with learning off (private mode, or a field that asks for no personalized
 * learning) the keyboard learns no next-word pair, in memory or on disk, and still offers the
 * pairs it already knew.
 */
class PrivateLearningTest {

    private val english = DictionaryIndex.build(LanguageCode.of("en")!!, listOf(WordFrequency("brisket", 50), WordFrequency("smoked", 40), WordFrequency("the", 150)))
    private val learned = mutableListOf<String>()

    private val pipeline = KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty(), resources = TextInputResources(dictionaries = listOf(english))).also {
        it.onStartInput(FieldContext(FieldKind.NORMAL), appProfile = AppProfile.default("com.example.chat"))
        it.onBigramLearned = { _, prefix, word -> learned += "$prefix>$word" }
        it.onBigramUnlearned = { _, prefix, word -> learned += "-$prefix>$word" }
    }

    private var text = ""
    private var now = 1_000L

    private fun press(key: KeyId) {
        val snapshot = EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = now)
        val result = pipeline.onKeyStroke(KeyStroke(key, KeyEdge.DOWN, 0, now), snapshot)
        for (op in result.ops) if (op is EditorOp.CommitText) text += op.text
        pipeline.onKeyStroke(KeyStroke(key, KeyEdge.UP, 0, now + 1), snapshot.copy(textBeforeCursor = text, nowMs = now + 1))
        now += 1_000
    }

    private fun type(s: String) {
        for (c in s) {
            when (c) {
                ' ' -> press(KeyId.Control(ControlKey.SPACE))
                '.' -> press(KeyId.Punctuation(PunctuationKey.PERIOD))
                else -> press(KeyId.Letter(c.uppercaseChar()))
            }
        }
    }

    @Test
    fun `with learning on, finished words are learned as pairs`() {
        type("smoked brisket ")
        assertTrue(learned.isNotEmpty())
    }

    @Test
    fun `with learning off, no pair is learned, on a soft or a hard boundary`() {
        pipeline.learningAllowed = false
        type("smoked brisket. the brisket ")
        assertEquals(emptyList(), learned)
    }

    @Test
    fun `with learning off, a pair known before is still offered after its first word`() {
        pipeline.onNgramStoreLoaded(listOf(Bigram("en", "smoked", "brisket", count = 3, lastUsedMillis = 1)))
        pipeline.learningAllowed = false
        type("the smoked ")
        assertEquals("brisket", pipeline.nextWordSuggestions().first(), "the context follows the typing even though nothing is learned")
        assertEquals(emptyList(), learned)
    }

    @Test
    fun `a word typed in private mode is not the first half of a pair learned after it ends`() {
        pipeline.learningAllowed = false
        type("smoked brisket ")
        pipeline.learningAllowed = true
        type("the ")
        assertEquals(1, learned.size, "learned: $learned")
        assertTrue(learned.none { "brisket" in it || "smoked" in it }, "learned: $learned")
    }
}
