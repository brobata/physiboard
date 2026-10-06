package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The context-aware boundary (autocorrect-suggestions.md §16 W2, W5) and the mix-up fix (§10's
 * exception) on the shipped English dictionary and word-pair table, one sentence at a time, the
 * way [TextInputPipeline] hands them to [BoundaryEngine]. The sentence harness measures the same
 * engine in bulk ([brobata.physiboard.core.text.eval.SentenceEvalTest]); these pin the behaviours
 * the maintainer named.
 */
class ContextCorrectionTest {

    private companion object {
        /** Gradle passes the app's asset directory (core/text's build file); an IDE run walks up to it. */
        fun assets(): File {
            System.getProperty("physiboard.assets.dictionaries")?.let { return File(it) }
            var dir: File? = File(".").absoluteFile
            while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
            return File(checkNotNull(dir), "app/src/main/assets/dictionaries")
        }

        val dictionary: DictionaryIndex by lazy { assertNotNull(DictionaryIndex.fromPbdBytes(File(assets(), "en.pbd").readBytes())) }
        val model: ContextModel by lazy { assertNotNull(ContextModel.read(File(assets(), "en.bigrams").readBytes())) }
        val defaults: UserWordStore by lazy {
            UserWordStore.of(UserWordFileCodec.decodeDefaultWords(File(assets().parentFile, "common/dictionaries/user_defaults.json").readText()))
        }
    }

    /** The Titan's shipped baseline (§13): automatic correction on, distance 2, proximity on. */
    private val shipped = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2, useKeyboardProximity = true)
    private val withMixups = shipped.copy(fixWordMixups = true)

    /** The boundary at the end of [text] (Space), with the keyboard's own tracked word and window. */
    private fun boundary(
        text: String,
        settings: AutocorrectSettings = withMixups,
        memory: AutocorrectMemory = AutocorrectMemory(),
        userWords: UserWordStore = defaults,
        contextModel: ContextModel? = model,
    ): BoundaryEvaluation {
        val tracked = CurrentWordTracker.empty().syncedFrom(text).word
        return BoundaryEngine.evaluate(
            trackedWord = tracked,
            textBeforeCursor = text.takeLast(tracked.length + BoundaryEngine.CONTEXT_WINDOW),
            boundaryChar = ' ',
            ruleSets = emptyList(),
            dictionaries = listOf(dictionary),
            userWords = userWords,
            settings = settings,
            rankingOptions = RankingOptions(useKeyboardProximity = true),
            lengthChangeAllowance = 2,
            memory = memory,
            contextModel = contextModel,
        )
    }

    private fun applied(text: String, evaluation: BoundaryEvaluation): String {
        val outcome = evaluation.outcome as? BoundaryOutcome.Replaced ?: return text
        assertTrue(text.endsWith(outcome.original), "asked to delete '${outcome.original}' from '$text'")
        return text.dropLast(outcome.original.length) + outcome.replacement
    }

    @Test
    fun `T-wagged it's tail becomes wagged its tail when the word after it arrives`() {
        val text = "The dog wagged it's tail"
        val evaluation = boundary(text)
        val outcome = assertIs<BoundaryOutcome.Replaced>(evaluation.outcome)
        assertEquals("it's tail", outcome.original)
        assertEquals("its tail", outcome.replacement)
        assertEquals("The dog wagged its tail", applied(text, evaluation))
        assertEquals("WORD_MIXUP", evaluation.debug.source)
        // The debug record is what `:ime` learns the completed word from: it must be "tail", not the span.
        assertEquals("tail", evaluation.debug.before)
        assertEquals("tail", evaluation.debug.after)
        assertEquals("previous it's -> its", evaluation.debug.reason)
    }

    @Test
    fun `T-the mix-up fix is off unless fix_word_mixups is on`() {
        assertEquals(false, AutocorrectSettings().fixWordMixups)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("The dog wagged it's tail", settings = shipped).outcome)
    }

    @Test
    fun `T-the twin keeps the case it was typed in`() {
        assertEquals("You're welcome", applied("Your welcome", boundary("Your welcome")))
        assertEquals("It is bigger than mine", applied("It is bigger then mine", boundary("It is bigger then mine")))
    }

    @Test
    fun `T-a lowercase ill becomes I'll, spelled as the dictionary spells it`() {
        assertEquals("I think I'll go", applied("I think ill go", boundary("I think ill go")))
    }

    @Test
    fun `T-a word stuck to a symbol is not a word the mix-up fix may touch`() {
        assertEquals(BoundaryOutcome.CommitPlain, boundary("ping @your welcome").outcome)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("see site.com/its tail").outcome)
    }

    @Test
    fun `T-the word after the mix-up goes back exactly as typed, curly apostrophe and all`() {
        assertEquals("There isn\u2019t", applied("Their isn\u2019t", boundary("Their isn\u2019t")))
    }

    @Test
    fun `T-the right twin is left alone`() {
        for (text in listOf("The dog wagged its tail", "You're welcome", "There is a cat", "It is bigger than mine", "Was there money")) {
            assertEquals(BoundaryOutcome.CommitPlain, boundary(text).outcome, text)
        }
    }

    @Test
    fun `T-the word is fixed first, then judged with the fixed word on its right`() {
        // "taill" is a slip for "tail"; only once it is "tail" does "it's" read wrong.
        assertEquals("The dog wagged its tail", applied("The dog wagged it's taill", boundary("The dog wagged it's taill")))
    }

    @Test
    fun `T-no mix-up fix across punctuation, a second space, or a word the field did not report`() {
        assertEquals(BoundaryOutcome.CommitPlain, boundary("The dog wagged it's, tail").outcome)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("The dog wagged it's  tail").outcome)
        // A field that reports only the word itself (or nothing, which never reaches the engine: DriftCheck).
        assertEquals(BoundaryOutcome.CommitPlain, boundary("tail").outcome)
    }

    @Test
    fun `T-Backspace right after a mix-up fix puts back what was typed and it is not redone`() {
        val text = "The dog wagged it's tail"
        val evaluation = boundary(text)
        val fixed = applied(text, evaluation) + " "
        val undo = assertIs<Backspace.Decision.Undo>(
            Backspace.decide(
                hasSelection = false, shiftHeld = false, altActive = false, settings = BackspaceSettings(), charsBeforeCursor = fixed.length,
                autocorrectMemory = evaluation.memory, undoTextBeforeCursor = fixed.takeLast("its tail".length + 2),
            ),
        )
        val ops = undo.result.ops
        val delete = assertIs<EditorOp.DeleteSurrounding>(ops[0])
        val restored = fixed.dropLast(delete.before) + assertIs<EditorOp.CommitText>(ops[1]).text
        assertEquals(text, restored)
        // Space again, straight away: the rejection covers both words, so nothing is redone.
        assertEquals(BoundaryOutcome.CommitPlain, boundary(restored, memory = undo.result.memory).outcome)
    }

    @Test
    fun `T-a correctly spelled word is never replaced by a typo correction, whatever the context`() {
        // "form" after "came" reads like "from", but it is a word: §10.
        assertEquals(BoundaryOutcome.CommitPlain, boundary("I came form", settings = shipped).outcome)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("He was quiet", settings = shipped).outcome)
    }

    @Test
    fun `T-a slip is corrected by key geometry and the word before it`() {
        assertEquals("I want to sleep", applied("I want to slep", boundary("I want to slep", settings = shipped)))
        assertEquals("This is the", applied("This is teh", boundary("This is teh", settings = shipped)))
        // The one the old engine got wrong on purpose (§16): "definately" is not "defiantly".
        assertEquals("I definitely", applied("I definately", boundary("I definately", settings = shipped)))
    }

    @Test
    fun `T-short words, names and words the user rejected stay as typed`() {
        assertEquals(BoundaryOutcome.CommitPlain, boundary("I am ot", settings = shipped).outcome)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("I met Ziri", settings = shipped).outcome)
        val rejected = AutocorrectUndo.attempt(AutocorrectMemory().afterReplacement("teh", "the"), "the")!!.memory
        assertEquals(BoundaryOutcome.CommitPlain, boundary("This is teh", settings = shipped, memory = rejected).outcome)
    }

    @Test
    fun `T-a personal word is never corrected`() {
        val userWords = defaults.withPersonalWordAdded("frobnik", nowMillis = 0L)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("I like frobnik", settings = shipped, userWords = userWords).outcome)
    }

    @Test
    fun `T-a possessive, a contraction or a missing apostrophe`() {
        assertEquals(BoundaryOutcome.CommitPlain, boundary("The players' bus", settings = shipped).outcome)
        assertEquals(BoundaryOutcome.CommitPlain, boundary("You must've", settings = shipped).outcome)
        assertEquals("I don't", applied("I dont", boundary("I dont", settings = shipped)))
    }

    @Test
    fun `T-without a table the engine decides exactly as it did before`() {
        // The legacy path keeps its own quirks; this only proves the table is what switches paths.
        val withTable = boundary("This is teh", settings = shipped)
        val without = boundary("This is teh", settings = shipped, contextModel = null)
        assertIs<BoundaryOutcome.Replaced>(withTable.outcome)
        assertEquals("CONTEXT", withTable.debug.source)
        assertTrue(without.debug.source != "CONTEXT")
    }
}

class ChannelCostTest {
    @Test
    fun `T-a neighbouring key costs less than a far one, and a first-letter change costs more`() {
        assertTrue(ChannelCost.cost("tge", "the") < ChannelCost.cost("tme", "the"))
        assertTrue(ChannelCost.cost("fing", "ding") > ChannelCost.cost("dong", "ding"))
        assertEquals(0.0, ChannelCost.cost("the", "the"))
    }

    @Test
    fun `T-swapping the first two letters is a transposition, not a first-letter change`() {
        assertEquals(ChannelCost.DEFAULT.transposition, ChannelCost.cost("hte", "the"), 1e-9)
    }

    @Test
    fun `T-a doubled or dropped letter, and a left-out apostrophe, are cheap`() {
        assertEquals(ChannelCost.DEFAULT.doubledLetter, ChannelCost.cost("thhe", "the"), 1e-9)
        assertEquals(ChannelCost.DEFAULT.droppedDouble, ChannelCost.cost("wil", "will"), 1e-9)
        assertEquals(ChannelCost.DEFAULT.apostropheAdded, ChannelCost.cost("dont", "don't"), 1e-9)
    }
}

class SentenceContextTest {
    @Test
    fun `T-the word before, across spaces and commas, as the table counted it`() {
        val word = assertIs<Preceding.Word>(SentenceContext.before("Well, its", 6, windowTruncated = false))
        assertEquals("Well", word.text)
        assertEquals(", ", word.gap)
        assertEquals("well", word.key)
    }

    @Test
    fun `T-the start of the text and a sentence end are the sentence start`() {
        assertEquals(Preceding.SentenceStart, SentenceContext.before("", 0, windowTruncated = false))
        assertEquals(Preceding.SentenceStart, SentenceContext.before("It rained. ", 11, windowTruncated = false))
        assertEquals(Preceding.SentenceStart, SentenceContext.before("Note: ", 6, windowTruncated = false))
    }

    @Test
    fun `T-a cut-off window, a digit or a symbol is no context at all`() {
        assertEquals(Preceding.Unknown, SentenceContext.before("ning ", 5, windowTruncated = true))
        assertEquals(Preceding.Unknown, SentenceContext.before("", 0, windowTruncated = true))
        assertEquals(Preceding.Unknown, SentenceContext.before("at 5pm ", 7, windowTruncated = false))
        assertEquals(Preceding.Unknown, SentenceContext.before("me @ ", 5, windowTruncated = false))
    }
}

class RegionalSpellingTest {
    @Test
    fun `T-British and American spellings are both words, a doubled slip is not`() {
        assertTrue(RegionalSpelling.isVariant("realised", "realized"))
        assertTrue(RegionalSpelling.isVariant("neighbours", "neighbors"))
        assertTrue(RegionalSpelling.isVariant("travelled", "traveled"))
        assertTrue(!RegionalSpelling.isVariant("calll", "call"))
    }
}
