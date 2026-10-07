package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictation.md SS8.1 (the cue table and its double gate, T51), SS4.2 (the request extras), SS11.2 step 1 (the assistant order). */
class DictationCuesTest {

    @Test
    fun `T51 - cues on with the system haptic toggle off play nothing`() {
        assertFalse(DictationCues.shouldPlay(hapticsEnabled = true, systemHapticsEnabled = false))
        assertFalse(DictationCues.shouldPlay(hapticsEnabled = false, systemHapticsEnabled = true))
        assertTrue(DictationCues.shouldPlay(hapticsEnabled = true, systemHapticsEnabled = true))
    }

    @Test
    fun `strong is two 150 ms pulses at 255 with a 90 ms gap, and a 300 ms stop`() {
        assertEquals(CuePattern(listOf(0, 150, 90, 150), listOf(0, 255, 0, 255)), DictationCues.startCue(CueStrength.STRONG))
        assertEquals(CuePattern(listOf(0, 300), listOf(0, 255)), DictationCues.stopCue(CueStrength.STRONG))
    }

    @Test
    fun `standard is two 60 ms pulses at 255 with a 70 ms gap, and a 160 ms stop`() {
        assertEquals(CuePattern(listOf(0, 60, 70, 60), listOf(0, 255, 0, 255)), DictationCues.startCue(CueStrength.STANDARD))
        assertEquals(CuePattern(listOf(0, 160), listOf(0, 255)), DictationCues.stopCue(CueStrength.STANDARD))
    }

    @Test
    fun `light is the only level below full amplitude - 35 ms pulses at 180, 60 ms gap, 90 ms stop`() {
        assertEquals(CuePattern(listOf(0, 35, 60, 35), listOf(0, 180, 0, 180)), DictationCues.startCue(CueStrength.LIGHT))
        assertEquals(CuePattern(listOf(0, 90), listOf(0, 180)), DictationCues.stopCue(CueStrength.LIGHT))
    }

    @Test
    fun `the request plan follows the settings - segmented and formatting need Android 13, private mode forces offline`() {
        val on33 = DictationSettings(androidApiLevel = 33, maskOffensive = true)
        val planned = RecognizerRequestPlanner.plan(on33, segmentedRefusalLatch = false)
        assertEquals(RecognizerRequest(segmented = true, preferOffline = true, enableFormatting = true, maskOffensive = true, completeSilenceMs = 16_000L, minimumLengthMs = 16_000L), planned)
        assertFalse(RecognizerRequestPlanner.plan(on33.copy(maskOffensive = false), false).maskOffensive)
        assertFalse(RecognizerRequestPlanner.plan(on33.copy(autoPunctuation = false), false).enableFormatting)
        assertFalse(RecognizerRequestPlanner.plan(on33.copy(androidApiLevel = 32), false).enableFormatting)
        assertFalse(RecognizerRequestPlanner.plan(on33.copy(androidApiLevel = 32), false).segmented)
        assertFalse(RecognizerRequestPlanner.plan(on33, segmentedRefusalLatch = true).segmented)
        assertFalse(RecognizerRequestPlanner.plan(on33.copy(preferOffline = false), false).preferOffline)
        assertEquals(true, RecognizerRequestPlanner.plan(on33.copy(preferOffline = false, privateMode = true), false).preferOffline)
        assertEquals(6_000L, RecognizerRequestPlanner.plan(on33.copy(stopAfterSilenceMs = 5_000L), false).completeSilenceMs)
        assertEquals(61_000L, RecognizerRequestPlanner.plan(on33.copy(stopAfterSilenceMs = 0L), false).completeSilenceMs)
        assertEquals(6_000L, RecognizerRequestPlanner.plan(on33.copy(stopAfterSilenceMs = 5_000L), false).minimumLengthMs)
    }

    @Test
    fun `auto tries voice command, hands-free, then assist, and a chosen action goes first without repeating`() {
        assertEquals(listOf(AssistantRequest.VOICE_COMMAND, AssistantRequest.HANDS_FREE, AssistantRequest.ASSIST), AssistantLaunch.order(null))
        assertEquals(listOf(AssistantRequest.ASSIST, AssistantRequest.VOICE_COMMAND, AssistantRequest.HANDS_FREE), AssistantLaunch.order(AssistantRequest.ASSIST))
        assertEquals(listOf(AssistantRequest.HANDS_FREE, AssistantRequest.VOICE_COMMAND, AssistantRequest.ASSIST), AssistantLaunch.order(AssistantRequest.HANDS_FREE))
        assertEquals(3, AssistantLaunch.order(AssistantRequest.VOICE_COMMAND).size)
    }
}
