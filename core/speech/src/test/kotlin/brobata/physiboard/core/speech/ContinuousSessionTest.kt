package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google's continuous session as the Titan logged it on 2026-10-07 17:21 (dictation.md D24): the
 * engine delivers each utterance's final as an ordinary result and keeps listening, capitalises
 * the first word of every result, drops the odd delivery, and can deliver one late. The
 * maintainer's Messages screenshot of the same evening (a sentence re-inserted after the send,
 * capitals mid-sentence, the microphone still on) is reproduced here through [DictationHarness].
 */
class ContinuousSessionTest {

    private fun started(settings: DictationSettings = DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 5_000L), textBefore: String = ""): DictationHarness {
        val h = DictationHarness(settings)
        h.send(DictationEvent.Trigger("app", textBefore), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        return h
    }

    @Test
    fun `D24 - three finals inside one continuous session land once each, with no restart and no latch`() {
        val h = started()
        h.drainEffects()
        h.send(DictationEvent.PartialResult("I'm pretty sure I have a tasting"), now = 2_000L)
        h.send(DictationEvent.FinalResult("I'm pretty sure I have a tasting at like 11:30 or noon."), now = 13_000L)
        assertEquals("I'm pretty sure I have a tasting at like 11:30 or noon. ", h.field.text)
        assertTrue(h.drainEffects().none { it is DictationEffect.StartListening }, "no request into a live session")
        assertNotNull(h.session?.continuationProbeDeadlineMs, "a final alone proves nothing; the probe waits for a sign of life")

        h.send(DictationEvent.BeginningOfSpeech, now = 13_100L) // the engine is still listening
        assertNull(h.session?.continuationProbeDeadlineMs)
        assertEquals(true, h.session?.engineContinues)
        h.send(DictationEvent.PartialResult("I'd love to"), now = 14_000L)
        h.send(DictationEvent.FinalResult("I'd love to confirm it with you."), now = 19_000L)
        assertNotNull(h.session?.continuationProbeDeadlineMs, "every final arms the probe; a proven engine is only asked again, never latched")
        h.send(DictationEvent.PartialResult("However the catering"), now = 20_000L)
        h.send(DictationEvent.FinalResult("However, the catering software we use can't be accessed from my house."), now = 31_000L)
        h.runClockTo(31_000L)
        assertEquals(
            "I'm pretty sure I have a tasting at like 11:30 or noon. I'd love to confirm it with you. However, the catering software we use can't be accessed from my house. ",
            h.field.text,
        )
        assertEquals(false, h.segmentedRefusalLatch)
        assertTrue(h.effects.none { it is DictationEffect.StartListening })
        assertNotNull(h.session)
    }

    @Test
    fun `a final with no sign of life for 1500 ms means a one-shot engine - then the plain re-listen and the latch`() {
        val h = started()
        h.send(DictationEvent.PartialResult("hello"), now = 1_000L)
        h.send(DictationEvent.FinalResult("hello."), now = 2_000L)
        h.drainEffects()
        assertTrue(h.runClockTo(3_499L).all { it.effects.isEmpty() })
        val idle = h.runClockTo(3_500L).single()
        val restart = idle.effects.filterIsInstance<DictationEffect.StartListening>().single()
        assertEquals(false, restart.request.segmented)
        assertEquals(true, h.segmentedRefusalLatch)
        assertEquals("Hello. ", h.field.text)
    }

    @Test
    fun `an audio level report answers the probe too`() {
        val h = started()
        h.send(DictationEvent.FinalResult("hello."), now = 2_000L)
        assertNotNull(h.session?.continuationProbeDeadlineMs)
        h.send(DictationEvent.EngineActivity, now = 2_100L)
        assertNull(h.session?.continuationProbeDeadlineMs)
        assertTrue(h.runClockTo(10_000L).all { it.effects.none { e -> e is DictationEffect.StartListening } })
    }

    @Test
    fun `B - capitals at every segment start are undone mid-sentence and kept at a sentence start`() {
        val h = started()
        h.send(DictationEvent.PartialResult("However the catering"), now = 1_000L)
        assertEquals("However the catering", h.field.text)
        h.send(DictationEvent.FinalResult("However, the catering software we use"), now = 2_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 2_100L)
        h.send(DictationEvent.PartialResult("Can't be"), now = 3_000L)
        assertEquals("However, the catering software we use can't be", h.field.text, "the partial's capital goes too")
        h.send(DictationEvent.FinalResult("Can't be accessed"), now = 4_000L)
        h.send(DictationEvent.FinalResult("From my house."), now = 6_000L)
        h.send(DictationEvent.FinalResult("I'd love to confirm it with you."), now = 8_000L)
        h.send(DictationEvent.FinalResult("Literally, didn't even step foot in the kitchen."), now = 10_000L)
        assertEquals(
            "However, the catering software we use can't be accessed from my house. I'd love to confirm it with you. Literally, didn't even step foot in the kitchen. ",
            h.field.text,
        )
    }

    @Test
    fun `B - a result with spaces around it never doubles a space`() {
        val h = started(textBefore = "At noon. ")
        h.send(DictationEvent.FinalResult(" I'd love to confirm it with you. "), now = 2_000L)
        assertEquals("I'd love to confirm it with you. ", h.field.text)
        h.send(DictationEvent.FinalResult("  Thanks.  "), now = 4_000L)
        assertEquals("I'd love to confirm it with you. Thanks. ", h.field.text)
    }

    @Test
    fun `lost words - a final that is not the composing partial's own commits the partial first`() {
        // 17:21:37 in the log: one utterance's final was never delivered; the next final arrived
        // with that utterance's partial still on screen. Replacing the partial lost it.
        val h = started()
        h.send(DictationEvent.PartialResult("Can't be accessed from my house"), now = 2_000L)
        val final = h.send(DictationEvent.FinalResult("To go grab my daughter."), now = 3_000L)
        assertEquals("Can't be accessed from my house to go grab my daughter. ", h.field.text, "the partial opens the field (a capital), the second utterance follows mid-sentence (none)")
        assertEquals(2, final.textOps.count { it is DictationTextOp.FinishComposing })
        // ... while a final that IS the partial's own replaces it as before.
        val h2 = started()
        h2.send(DictationEvent.PartialResult("the coffee is hot"), now = 2_000L)
        h2.send(DictationEvent.FinalResult("That coffee is hot."), now = 3_000L)
        assertEquals("That coffee is hot. ", h2.field.text)
    }

    @Test
    fun `A - the app emptying the field ends the session and a late final writes nothing`() {
        val h = started()
        h.send(DictationEvent.PartialResult("however the catering software we use"), now = 2_000L)
        h.send(DictationEvent.FinalResult("However, the catering software we use can't be accessed from my house."), now = 5_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 5_100L)
        assertEquals("However, the catering software we use can't be accessed from my house. ", h.field.text)
        // The user taps send; the app clears its box.
        val cleared = h.send(DictationEvent.FieldClearedByApp, now = 6_000L)
        assertNull(cleared.session)
        assertEquals(listOf(DictationEffect.CancelListening, DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus, DictationEffect.ReleaseImeVisible), cleared.effects)
        assertTrue(cleared.textOps.isEmpty(), "nothing was composing; nothing to clear")
        val late = h.send(DictationEvent.FinalResult("However, the catering software we use can't be accessed from my house."), now = 6_500L)
        assertTrue(late.textOps.isEmpty() && late.effects.isEmpty())
    }

    @Test
    fun `A - a late exact echo of the words in the field writes nothing, and ordinary repeats of recent words are not echoes`() {
        val h = started()
        h.send(DictationEvent.PartialResult("however the catering software we use can't be accessed from my house"), now = 2_000L)
        h.send(DictationEvent.FinalResult("However, the catering software we use can't be accessed from my house."), now = 5_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 5_100L)
        val before = h.field.text
        h.send(DictationEvent.FinalResult("However, the catering software we use can't be accessed from my house."), now = 5_500L)
        assertEquals(before, h.field.text, "the exact echo of the whole transcript writes nothing")
        // Speech that repeats recent words is new speech, never an echo (the reviewer's recipe).
        val r = started()
        r.send(DictationEvent.FinalResult("Add two cups of flour."), now = 2_000L)
        r.send(DictationEvent.BeginningOfSpeech, now = 2_100L)
        r.send(DictationEvent.PartialResult("add two cups"), now = 3_000L)
        assertEquals("Add two cups of flour. add two cups".replace(". add", ". Add"), r.field.text)
        r.send(DictationEvent.FinalResult("Add two cups of sugar."), now = 4_000L)
        assertEquals("Add two cups of flour. Add two cups of sugar. ", r.field.text)
        r.send(DictationEvent.FinalResult("Yes yes."), now = 6_000L)
        r.send(DictationEvent.FinalResult("I love you."), now = 8_000L)
        r.send(DictationEvent.FinalResult("I love you so much."), now = 10_000L)
        assertEquals("Add two cups of flour. Add two cups of sugar. Yes yes. I love you. I love you so much. ", r.field.text)
    }

    @Test
    fun `lost words - a final that reformats or corrects the partial replaces it, never doubles it`() {
        val numbers = started()
        numbers.send(DictationEvent.PartialResult("twenty five dollars"), now = 2_000L)
        numbers.send(DictationEvent.FinalResult("$25."), now = 3_000L)
        assertEquals("$25. ", numbers.field.text)
        val homophone = started()
        homophone.send(DictationEvent.PartialResult("I scream for"), now = 2_000L)
        homophone.send(DictationEvent.FinalResult("Ice cream for you."), now = 3_000L)
        assertEquals("Ice cream for you. ", homophone.field.text)
        val short = started()
        short.send(DictationEvent.PartialResult("see you"), now = 2_000L)
        short.send(DictationEvent.FinalResult("Go now."), now = 3_000L)
        assertEquals("Go now. ", short.field.text, "a two-word partial is always replaced")
    }

    @Test
    fun `the probe is armed on every final - an engine that went quiet after proving itself is asked again without the latch`() {
        val h = started()
        h.send(DictationEvent.FinalResult("one."), now = 2_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 2_100L)
        h.send(DictationEvent.FinalResult("two."), now = 5_000L)
        assertNotNull(h.session?.continuationProbeDeadlineMs)
        h.drainEffects()
        val again = h.runClockTo(6_500L).single()
        val restart = again.effects.filterIsInstance<DictationEffect.StartListening>().single()
        assertEquals(true, restart.request.segmented)
        assertEquals(false, h.segmentedRefusalLatch)
        assertTrue(again.effects.none { it is DictationEffect.LogMessage })
    }

    @Test
    fun `German keeps its capitals - the engine's segment capital is left alone`() {
        val h = DictationHarness(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 5_000L), DictationTextSettings(undoEngineSegmentCapitals = false))
        h.send(DictationEvent.Trigger("app", "ich habe das "), now = 0L)
        h.send(DictationEvent.FinalResult("Haus gesehen."), now = 2_000L)
        assertEquals("Haus gesehen. ", h.field.text)
        assertEquals(false, DictationTextSettings.undoEngineCapitalsFor("de-DE"))
        assertEquals(false, DictationTextSettings.undoEngineCapitalsFor("lb"))
        assertEquals(true, DictationTextSettings.undoEngineCapitalsFor("en-US"))
        assertEquals(true, DictationTextSettings.undoEngineCapitalsFor(null))
    }

    @Test
    fun `C - Enter ends the session completely, and nothing the engine says afterwards lands`() {
        val h = started()
        h.send(DictationEvent.PartialResult("see you at eleven"), now = 2_000L)
        val enter = h.send(DictationEvent.KeyDown, now = 3_000L)
        assertNull(enter.session)
        assertEquals(listOf(DictationEffect.CancelListening, DictationEffect.PlayStopCue, DictationEffect.ReleaseAudioFocus, DictationEffect.ReleaseImeVisible), enter.effects)
        assertEquals("See you at eleven ", h.field.text)
        val late = h.send(DictationEvent.FinalResult("See you at eleven."), now = 3_200L)
        assertTrue(late.textOps.isEmpty() && late.effects.isEmpty())
        assertEquals("See you at eleven ", h.field.text)
    }

    @Test
    fun `A2 - the send empties the box while the FIRST utterance is still composing, then Fn, then the late final - nothing is re-inserted`() {
        // 19:58 in the trace: partials only, no finished segment; the app's send emptied the box;
        // the user pressed Fn; the final for the sent words was typed into the empty box.
        val h = started()
        h.send(DictationEvent.PartialResult("I'm pretty sure I have a tasting at eleven"), now = 2_000L)
        assertEquals("I'm pretty sure I have a tasting at eleven", h.field.text)
        h.field.apply(listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing)) // the app's send
        val cleared = h.send(DictationEvent.FieldClearedByApp, now = 4_300L)
        assertNull(cleared.session)
        assertEquals("", h.field.text)
        val press = h.send(DictationEvent.FinalResult("I'm pretty sure I have a tasting at eleven."), now = 5_700L)
        assertTrue(press.textOps.isEmpty() && press.effects.isEmpty())
        assertEquals("", h.field.text)

        // The same with the stop already requested when the box empties.
        val s = started()
        s.send(DictationEvent.PartialResult("I'm pretty sure I have a tasting at eleven"), now = 2_000L)
        s.send(DictationEvent.Trigger("app", null), now = 4_000L)
        assertEquals(DictationPhase.STOPPING, s.session?.phase)
        s.field.apply(listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing))
        val ended = s.send(DictationEvent.FieldClearedByApp, now = 4_300L)
        assertNull(ended.session)
        assertTrue(DictationEffect.CancelListening in ended.effects && DictationEffect.ReleaseImeVisible in ended.effects)
        s.send(DictationEvent.FinalResult("I'm pretty sure I have a tasting at eleven."), now = 4_400L)
        assertEquals("", s.field.text)

        // And when the field is not empty but no longer holds our partial, the utterance is dead
        // even while stopping: the final writes nothing, the session still ends.
        val c = started()
        c.send(DictationEvent.PartialResult("I'm pretty sure I have a tasting at eleven"), now = 2_000L)
        c.send(DictationEvent.Trigger("app", null), now = 4_000L)
        c.send(DictationEvent.UserEditedComposingText, now = 4_300L)
        val dead = c.send(DictationEvent.FinalResult("I'm pretty sure I have a tasting at eleven."), now = 4_400L)
        assertNull(dead.session)
        assertTrue(dead.textOps.isEmpty())
    }

    @Test
    fun `A2 - the same order in a terminal-mode field commits the words once, at the stop`() {
        // A terminal holds no composing region: nothing is written until the utterance ends,
        // and the terminal emptying its box is its own doing, never a signal (the keyboard does
        // not raise FieldClearedByApp there). The user never saw the words, so the stop's
        // final is their one appearance. A terminal is a raw-mode field: no capitalisation.
        val h = DictationHarness(DictationSettings(androidApiLevel = 36, stopAfterSilenceMs = 5_000L), DictationTextSettings(capitalizationAllowed = false))
        h.send(DictationEvent.Trigger("app", ""), now = 0L)
        h.send(DictationEvent.ReadyForSpeech, now = 10L)
        h.send(DictationEvent.FirstAudio, now = 40L)
        var direct = DirectCommitState()
        val written = mutableListOf<DictationTextOp>()
        fun apply(ops: List<DictationTextOp>) {
            val t = DirectCommit.translate(ops, direct)
            direct = t.state
            written += t.ops
        }
        apply(h.send(DictationEvent.PartialResult("ls minus la"), now = 2_000L).textOps)
        assertTrue(written.isEmpty(), "a terminal gets no running preview")
        apply(h.send(DictationEvent.Trigger("app", null), now = 4_000L).textOps)
        apply(h.send(DictationEvent.FinalResult("ls -la"), now = 4_200L).textOps)
        assertEquals(listOf<DictationTextOp>(DictationTextOp.CommitText("ls -la ")), written)
        assertNull(h.session)
        apply(h.send(DictationEvent.FinalResult("ls -la"), now = 4_400L).textOps)
        assertEquals(1, written.size, "a late final after the end writes nothing")
    }

    @Test
    fun `a 5 s limit survives a breath and ends a session left in silence`() {
        val h = started()
        h.send(DictationEvent.FinalResult("one."), now = 2_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 2_100L) // the engine is alive: no re-listen probe
        h.drainEffects()
        assertTrue(h.runClockTo(6_000L).all { it.effects.isEmpty() }, "a 4 s pause is not the end")
        h.send(DictationEvent.PartialResult("two"), now = 6_000L)
        h.send(DictationEvent.FinalResult("two."), now = 7_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 7_050L)
        h.drainEffects()
        // Five seconds from the last new words (the partial at 6 s), not from their final.
        assertTrue(h.runClockTo(10_999L).all { it.effects.isEmpty() })
        val stop = h.runClockTo(11_000L).single()
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
    }

    @Test
    fun `the voice detector firing on background noise does not keep the session open`() {
        // Titan, 2026-10-07: the engine reported beginning and end of speech every half second
        // with nobody talking, and the silence limit, reset by each one, never ran out.
        val h = started()
        h.send(DictationEvent.FinalResult("one."), now = 2_000L)
        h.drainEffects()
        var t = 2_100L
        while (t < 6_900L) {
            h.send(DictationEvent.BeginningOfSpeech, now = t)
            h.send(DictationEvent.EndOfSpeech, now = t + 300L)
            t += 500L
        }
        h.drainEffects()
        val stop = h.runClockTo(7_000L).single()
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
    }

    @Test
    fun `an unchanged partial repeated by the engine is not new speech`() {
        val h = started()
        h.send(DictationEvent.PartialResult("hello there"), now = 1_000L)
        h.send(DictationEvent.PartialResult("hello there"), now = 3_000L)
        h.send(DictationEvent.PartialResult("hello there"), now = 5_500L)
        h.drainEffects()
        val stop = h.runClockTo(6_000L).single()
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
    }

    @Test
    fun `the tidied final of words already shown does not push the silence stop back`() {
        // Titan, 2026-10-07: last partial at 31.0 s, the same words' final at 34.1 s, and the
        // stop came 5 s after the final, 7.7 s after the user went quiet.
        val h = started()
        h.send(DictationEvent.PartialResult("I'm heading to the store"), now = 1_000L)
        h.send(DictationEvent.SegmentResult("I'm heading to the store."), now = 3_600L)
        h.send(DictationEvent.BeginningOfSpeech, now = 3_700L)
        h.drainEffects()
        val stop = h.runClockTo(6_000L).single { it.effects.isNotEmpty() }
        assertEquals(listOf(DictationEffect.StopListening), stop.effects)
    }

    @Test
    fun `the 2_5 s default stops two and a half seconds after the last word`() {
        val h = started(DictationSettings(androidApiLevel = 36))
        h.send(DictationEvent.PartialResult("see you at noon"), now = 1_000L)
        h.send(DictationEvent.BeginningOfSpeech, now = 1_100L)
        h.drainEffects()
        assertTrue(h.runClockTo(3_499L).all { it.effects.isEmpty() })
        assertEquals(listOf(DictationEffect.StopListening), h.runClockTo(3_500L).single().effects)
    }
}
