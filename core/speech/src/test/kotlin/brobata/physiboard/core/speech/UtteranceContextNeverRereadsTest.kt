package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * spec: dictation.md SS7.1's own documented fragility: "from the second partial on the cursor sits
 * after the previous partial's composing text ... the capital survives only because each new
 * partial is checked against the text that now precedes it (the previous partial's own words)."
 * That is the 2.0.7-class bug this task calls "the keyboard reading back text it had written
 * itself and treating it as context."
 *
 * [UtteranceContext] is captured once, when the utterance begins, and every later partial or final
 * of that SAME utterance is judged against that one frozen snapshot, never against text this
 * session has since written. This test proves that by holding the context fixed across three
 * growing partials of one utterance and checking every one of them still capitalises correctly; a
 * reimplementation that re-derived the context from the composing text already on screen (a live
 * read) would lose the capital from the second partial onward, exactly as 2.x did, and this test
 * would fail.
 */
class UtteranceContextNeverRereadsTest {

    @Test
    fun `every partial of one utterance capitalises the same way from the frozen context, not the growing composing text`() {
        val settings = DictationTextSettings()
        val context = UtteranceContext("") // an empty field: "capitalise here" is true

        val first = DictationPartialDisplay.display("hello", context, settings)
        val second = DictationPartialDisplay.display("hello world", context, settings)
        val third = DictationPartialDisplay.display("hello world again", context, settings)

        // If this module re-read "hello", "hello world", ... as the context for the NEXT partial
        // (the live composing text, standing in for the field), none of these but the first would
        // satisfy "capitalise here" (they neither start the document nor follow a sentence end),
        // and the capital would be lost from the second partial on, exactly as SS7.1 documents.
        assertEquals("Hello", first)
        assertEquals("Hello world", second)
        assertEquals("Hello world again", third)
    }

    @Test
    fun `the finished utterance also capitalises from the frozen context, not from its own composing text`() {
        val settings = DictationTextSettings()
        val context = UtteranceContext("")

        // A naive implementation that read "Hello world" (this utterance's own composing text) as
        // the context before deciding the final's capitalisation and spacing would see a
        // non-empty, non-boundary "before" text and both lose the capital and, per SS7.6's own
        // documented quirk, prepend a spurious leading space (its last character, "d", is a letter).
        val finished = UtteranceFinisher.finish("hello world", context, settings)

        assertEquals("Hello world ", finished.plainText)
    }
}
