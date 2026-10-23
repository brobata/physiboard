package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** dictation.md SS6.2, D28: what a partial-results bundle means, read the way Chromium's SpeechRecognitionImpl reads Google's. */
class RecognizerPartialTest {

    @Test
    fun `a partial flagged final is the utterance finalized - a segment, never composing text`() {
        assertEquals(DictationEvent.SegmentResult("Sounds good."), RecognizerPartial.toEvent("Sounds good.", null, flaggedFinal = true))
        // The unstable tail belongs to a provisional hypothesis; a finalized one has none worth keeping.
        assertEquals(DictationEvent.SegmentResult("Sounds good."), RecognizerPartial.toEvent("Sounds good.", " stale", flaggedFinal = true))
    }

    @Test
    fun `a provisional partial shows the stable text with its unstable tail, concatenated as the engine formats them`() {
        assertEquals(DictationEvent.PartialResult("I'll be there at"), RecognizerPartial.toEvent("I'll be there", " at", flaggedFinal = false))
        assertEquals(DictationEvent.PartialResult("I'll be there"), RecognizerPartial.toEvent("I'll be there", null, flaggedFinal = false))
        assertEquals(DictationEvent.PartialResult(" at"), RecognizerPartial.toEvent("", " at", flaggedFinal = false), "the first words of an utterance may all be unstable")
    }

    @Test
    fun `a bundle with no words is nothing - metadata-only bundles and empty finals are ignored`() {
        assertNull(RecognizerPartial.toEvent(null, null, flaggedFinal = false))
        assertNull(RecognizerPartial.toEvent(null, " at", flaggedFinal = false), "no stable list at all is no result")
        assertNull(RecognizerPartial.toEvent("", "", flaggedFinal = false))
        assertNull(RecognizerPartial.toEvent("  ", null, flaggedFinal = true))
    }
}
