package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictation.md SS16, T10, T11, T17, T18. */
class DictationModeDecisionAndTimingTest {

    private fun settings(api: Int, enabled: Boolean = true, pauseMs: Long = 2500L) =
        DictationSettings(pauseMs = pauseMs, segmentedSessionEnabled = enabled, androidApiLevel = api)

    @Test
    fun `T10 API level gates segmented mode`() {
        assertEquals(DictationMode.SEGMENTED, DictationModeDecision.decide(settings(api = 33), segmentedRefusalLatch = false))
        assertEquals(DictationMode.SEGMENTED, DictationModeDecision.decide(settings(api = 34), segmentedRefusalLatch = false))
        assertEquals(DictationMode.RESTART_LOOP, DictationModeDecision.decide(settings(api = 32), segmentedRefusalLatch = false))
        assertEquals(DictationMode.RESTART_LOOP, DictationModeDecision.decide(settings(api = 29), segmentedRefusalLatch = false))
    }

    @Test
    fun `T11 setting, latch and pause all gate segmented mode`() {
        assertEquals(DictationMode.RESTART_LOOP, DictationModeDecision.decide(settings(api = 33, enabled = false), segmentedRefusalLatch = false))
        assertEquals(DictationMode.RESTART_LOOP, DictationModeDecision.decide(settings(api = 33), segmentedRefusalLatch = true))
        assertEquals(DictationMode.RESTART_LOOP, DictationModeDecision.decide(settings(api = 33, pauseMs = 0L), segmentedRefusalLatch = false))
        assertEquals(DictationMode.SEGMENTED, DictationModeDecision.decide(settings(api = 33, pauseMs = 500L), segmentedRefusalLatch = false))
    }

    @Test
    fun `T17 silence timer is pause minus 1000, floored at 400`() {
        assertEquals(1500L, DictationTiming.silenceTimerMs(2500L))
        assertEquals(400L, DictationTiming.silenceTimerMs(1200L))
        assertEquals(400L, DictationTiming.silenceTimerMs(500L))
    }

    @Test
    fun `T18 watchdog is pause plus 5000`() {
        assertEquals(7500L, DictationTiming.watchdogMs(2500L))
    }
}
