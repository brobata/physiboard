package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The context-aware boundary (autocorrect-suggestions.md §16 W2, W5) and the mix-up fix (§10's
 * exception) driven through [TextInputPipeline] keystroke by keystroke, on the shipped English
 * dictionary, word-pair table and default word list: what [ContextCorrectionTest] checks of the
 * engine alone, here with the pipeline's own deletes, boundary commits, undo and the memory it
 * carries from one keystroke to the next.
 */
class ContextPipelineTest {

    private companion object {
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

    /** The Titan's shipped baseline (§13) with the mix-up fix switched on, English's length allowance. */
    private val settings = TextInputSettingsBundle(
        autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2, useKeyboardProximity = true, fixWordMixups = true),
        rankingOptions = RankingOptions(useKeyboardProximity = true),
        lengthChangeAllowance = 2,
    )

    /** A field the pipeline types into, replaying every op it returns; letters are typed exactly as given (no auto-cap). */
    private inner class Session(val field: FieldContext = FieldContext(FieldKind.NORMAL, isMultiLine = true)) {
        var text: String = ""
            private set
        var cursor: Int = 0
            private set
        var state = TextInputState()
        private val resources = TextInputResources(dictionaries = listOf(dictionary), userWords = defaults, contextModel = model)

        private fun apply(ops: List<EditorOp>) {
            for (op in ops) {
                when (op) {
                    is EditorOp.CommitText -> {
                        text = text.substring(0, cursor) + op.text + text.substring(cursor)
                        cursor += op.text.length
                    }
                    is EditorOp.DeleteSurrounding -> {
                        val before = minOf(op.before, cursor)
                        text = text.substring(0, cursor - before) + text.substring(cursor + minOf(op.after, text.length - cursor))
                        cursor -= before
                    }
                    is EditorOp.ReplaceBeforeCursor -> {
                        val count = minOf(op.count, cursor)
                        text = text.substring(0, cursor - count) + op.text + text.substring(cursor)
                        cursor = cursor - count + op.text.length
                    }
                    else -> Unit
                }
            }
        }

        private fun snapshot() = EditorSnapshot(textBeforeCursor = text.substring(maxOf(0, cursor - 240), cursor))

        private fun handle(request: TextInputRequest): List<EditorOp> {
            val result = TextInputPipeline.handle(request, field, settings, resources, state, snapshot())
            apply(result.ops)
            state = result.state
            return result.ops
        }

        private fun key(action: Action) = handle(TextInputRequest.Key(action))

        /** Types [keys] one key at a time: a space is the Space key, `.` and `,` the Alt-layer marks. */
        fun type(keys: String) {
            for (ch in keys) key(Action.Commit(ch.toString()))
        }

        fun enter() = key(Action.Edit(EditEffect.NEWLINE))

        /** Text arriving in one commit (a paste, a Sym-page string): not typed letter by letter. */
        fun paste(text: String) = key(Action.Commit(text))

        fun backspace() {
            if (key(Action.Edit(EditEffect.DELETE_CHAR_BACKWARD)) == listOf(EditorOp.PassThroughKey)) apply(listOf(EditorOp.DeleteSurrounding(1, 0)))
        }

        fun acceptSuggestion(word: String) = handle(TextInputRequest.AcceptSuggestion(word))

        /** The user taps the field at [position]; `:ime` reports it as a move the pipeline did not make. */
        fun tapAt(position: Int) {
            cursor = position
            state = state.afterExternalCursorMove(text.substring(0, cursor))
        }

        /** The field's input restarted with the cursor where it was (a WebView doing so mid-sentence). */
        fun restart() {
            state = state.afterInputRestart(text.substring(0, cursor))
        }
    }

    @Test
    fun `T-typed in sequence, the word before is fixed when the next one's Space arrives`() {
        val session = Session()
        session.type("The dog wagged it's tail ")
        assertEquals("The dog wagged its tail ", session.text)
    }

    @Test
    fun `T-a period boundary replaces both words and keeps the period`() {
        val session = Session()
        session.type("The dog wagged it's tail.")
        assertEquals("The dog wagged its tail.", session.text)
        val corrected = Session()
        corrected.type("The dog wagged it's taill.")
        assertEquals("The dog wagged its tail.", corrected.text)
        val slip = Session()
        slip.type("I want to slep.")
        assertEquals("I want to sleep.", slip.text)
    }

    @Test
    fun `T-a comma or a question mark is a boundary too, and the fix never reaches across one`() {
        val comma = Session()
        comma.type("The dog wagged it's tail, ")
        assertEquals("The dog wagged its tail, ", comma.text)
        val question = Session()
        question.type("Is it bigger then mine?")
        assertEquals("Is it bigger than mine?", question.text)
        // The word before the comma is not judged by the word after it.
        val across = Session()
        across.type("Yes its, tail ")
        assertEquals("Yes its, tail ", across.text)
    }

    @Test
    fun `T-Backspace after a fix at a period puts back the words as typed, the period with them`() {
        val session = Session()
        session.type("The dog wagged it's tail.")
        assertEquals("The dog wagged its tail.", session.text)
        session.backspace()
        // §7.5 step 2: the replacement and the boundary after it go, the original comes back.
        assertEquals("The dog wagged it's tail", session.text)
        session.type(".")
        assertEquals("The dog wagged it's tail.", session.text)
    }

    @Test
    fun `T-Enter is a boundary for the mix-up fix and the context correction alike`() {
        val session = Session()
        session.type("The dog wagged it's tail")
        session.enter()
        assertEquals("The dog wagged its tail\n", session.text)
        val slip = Session()
        slip.type("I want to slep")
        slip.enter()
        assertEquals("I want to sleep\n", slip.text)
    }

    @Test
    fun `T-Backspace undoes a mix-up fix and Space straight after does not redo it`() {
        val session = Session()
        session.type("The dog wagged it's tail ")
        session.backspace()
        assertEquals("The dog wagged it's tail", session.text)
        session.type(" ")
        assertEquals("The dog wagged it's tail ", session.text)
    }

    @Test
    fun `T-repro a, undo then one more letter of the next word, the word put back stays put`() {
        val session = Session()
        session.type("The dog wagged it's tail ")
        session.backspace()
        session.type("s ")
        assertEquals("The dog wagged it's tails ", session.text)
        // And it stays put as the sentence goes on.
        session.type("and slept ")
        assertEquals("The dog wagged it's tails and slept ", session.text)
    }

    @Test
    fun `T-repro b, a word taken from a suggestion is the user's choice`() {
        val session = Session()
        session.type("The dog wagged it")
        session.acceptSuggestion("it's")
        assertEquals("The dog wagged it's ", session.text)
        session.type("tail ")
        assertEquals("The dog wagged it's tail ", session.text)
    }

    @Test
    fun `T-repro c, a Space after a tap into old text does not rewrite the word before`() {
        // Text already in the field (typed earlier with the fix off, or pasted): typed here, the
        // fix would already have changed it at the Space after "tail".
        val session = Session()
        session.paste("The dog wagged it's tail and slept")
        session.tapAt("The dog wagged it's tail".length)
        session.type(" ")
        assertEquals("The dog wagged it's tail  and slept", session.text)
        // The tap's word is not one typed here either: a new word after it does not judge it.
        session.type("big ")
        assertEquals("The dog wagged it's tail big  and slept", session.text)
    }

    @Test
    fun `T-a word next to a tap or an input restart is judged only once a word is typed after it`() {
        val tapped = Session()
        tapped.type("The dog wagged it's ")
        tapped.tapAt(tapped.text.length)
        tapped.type("tail ")
        assertEquals("The dog wagged it's tail ", tapped.text)

        val restarted = Session()
        restarted.type("The dog wagged it's ")
        restarted.restart()
        restarted.type("tail ")
        assertEquals("The dog wagged it's tail ", restarted.text)
        // Typed after the restart, in sequence: judged again.
        restarted.type("then I think ill go ")
        assertEquals("The dog wagged it's tail then I think I'll go ", restarted.text)
    }

    @Test
    fun `T-Backspace behind the word being typed stops the fix vouching for the word before`() {
        val session = Session()
        session.type("The dog wagged it's ")
        session.backspace() // the space after it's
        session.type(" tail ")
        assertEquals("The dog wagged it's tail ", session.text)
    }

    @Test
    fun `T-the undo memory and the rejection are carried from keystroke to keystroke`() {
        val session = Session()
        session.type("I want to slep ")
        assertEquals("I want to sleep ", session.text)
        session.backspace()
        assertEquals("I want to slep", session.text)
        session.type(" ")
        assertEquals("I want to slep ", session.text)
        // The rejection is gone with the next word's first letter, so the next slip is fixed.
        session.type("nwo ")
        assertEquals("I want to slep now ", session.text)
    }
}
