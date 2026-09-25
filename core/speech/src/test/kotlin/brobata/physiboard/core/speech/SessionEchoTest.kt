package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Titan, 2026-09-25: with Google's segmented session every partial and segment repeated the
 * whole transcript so far, and the field grew by all of it at every segment ("it moves the
 * whole screen and extends beyond the input box"). spec: dictation.md SS7.1 to SS7.3.
 */
class SessionEchoTest {
    private val segmented = DictationSettings(pauseMs = 2000L, androidApiLevel = 34)
    private val textSettings = DictationTextSettings()
    private fun handle(state: DictationSession?, event: DictationEvent, now: Long, settings: DictationSettings = segmented) =
        DictationEngine.handle(state, event, now, settings, textSettings, segmentedRefusalLatch = false)

    private fun ready(): DictationSession? {
        val started = handle(null, DictationEvent.Trigger("app", ""), now = 0L)
        return handle(started.session, DictationEvent.ReadyForSpeech, now = 10L).session
    }

    @Test
    fun `the words a segment already committed are not composed again by the next cumulative partial`() {
        var s = handle(ready(), DictationEvent.PartialResult("hello world"), now = 100L).session
        val segment = handle(s, DictationEvent.SegmentResult("hello world"), now = 200L)
        assertEquals(listOf(DictationTextOp.SetComposingText("Hello world "), DictationTextOp.FinishComposing), segment.textOps)
        s = segment.session
        val cumulative = handle(s, DictationEvent.PartialResult("hello world how are you"), now = 300L)
        assertEquals(listOf(DictationTextOp.SetComposingText("how are you")), cumulative.textOps)
        val secondSegment = handle(cumulative.session, DictationEvent.SegmentResult("hello world how are you"), now = 400L)
        assertEquals(listOf(DictationTextOp.SetComposingText("how are you "), DictationTextOp.FinishComposing), secondSegment.textOps)
    }

    @Test
    fun `a partial that only repeats the committed words writes nothing`() {
        var s = handle(ready(), DictationEvent.PartialResult("hello world"), now = 100L).session
        s = handle(s, DictationEvent.SegmentResult("hello world"), now = 200L).session
        val echo = handle(s, DictationEvent.PartialResult("Hello world"), now = 300L)
        assertEquals(emptyList(), echo.textOps)
    }

    /**
     * SS7.2's new-utterance rule is deliberately not wired: dictation.md's keep-or-drop table
     * marks it undecided, and it duplicates text when the recognizer corrects itself. A partial
     * that differs replaces the composing region, which is SS7.1's plain overwrite.
     */
    @Test
    fun `a differing partial overwrites the composing region rather than committing the previous one`() {
        val s = handle(ready(), DictationEvent.PartialResult("see you tomorrow"), now = 100L).session
        val fresh = handle(s, DictationEvent.PartialResult("bring the car"), now = 300L)
        assertEquals(listOf(DictationTextOp.SetComposingText("Bring the car")), fresh.textOps)
    }

    @Test
    fun `strip removes only a leading echo and compares by word`() {
        assertEquals("how are you", SessionEcho.strip("hello world how are you", "hello world"))
        assertEquals("", SessionEcho.strip("Hello World", "hello world"))
        assertEquals("world peace", SessionEcho.strip("world peace", "hello world"))
        assertEquals("hello", SessionEcho.strip("hello", ""))
        assertEquals("hello world", SessionEcho.extend("hello", "world "))
    }

    @Test
    fun `the echo matches words the finisher punctuated`() {
        assertEquals("how are you", SessionEcho.strip("hello world how are you", "Hello world."))
        assertEquals("", SessionEcho.strip("hello world", "Hello, world!"))
    }
}
