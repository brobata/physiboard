package brobata.physiboard.device.titan

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.11; test cases T45 to T58. */
class NotificationRingClockTest {

    private fun source(key: String, pkg: String, at: Long = 0) = RingSource(key, pkg, 0xFF00FF00.toInt(), at)

    private val ready = NightstandFacts(
        featureOn = true, pluggedIn = true, screenInteractive = false, keyguardLocked = true, putAway = false, ringOnScreen = false,
    )

    // ---- 12/24-hour time (T45) ----

    @Test
    fun `T45 - 12-hour time has no leading zero and no AM or PM, midnight and noon read 12`() {
        assertEquals("7:05", RingClockFormat.time(7, 5, is24Hour = false))
        assertEquals("7:05", RingClockFormat.time(19, 5, is24Hour = false))
        assertEquals("12:00", RingClockFormat.time(0, 0, is24Hour = false))
        assertEquals("12:30", RingClockFormat.time(12, 30, is24Hour = false))
        assertEquals("11:59", RingClockFormat.time(23, 59, is24Hour = false))
    }

    @Test
    fun `T45 - 24-hour time pads the hour`() {
        assertEquals("07:05", RingClockFormat.time(7, 5, is24Hour = true))
        assertEquals("19:05", RingClockFormat.time(19, 5, is24Hour = true))
        assertEquals("00:00", RingClockFormat.time(0, 0, is24Hour = true))
        assertEquals("23:59", RingClockFormat.time(23, 59, is24Hour = true))
    }

    @Test
    fun `out-of-range hours and minutes are clamped rather than printed`() {
        assertEquals("23:59", RingClockFormat.time(30, 99, is24Hour = true))
        assertEquals("12:00", RingClockFormat.time(-1, -1, is24Hour = false))
    }

    // ---- Burn-in (T46, T47) ----

    @Test
    fun `T46 - every position stays within 4 dp of home and every step moves at most 2 dp per axis`() {
        val pattern = RingClockBurnIn.PATTERN
        assertEquals(16, pattern.size)
        for (offset in pattern) {
            assertTrue(abs(offset.dxDp) <= RingClockBurnIn.MAX_SHIFT_DP && abs(offset.dyDp) <= RingClockBurnIn.MAX_SHIFT_DP, "$offset")
        }
        for (i in pattern.indices) {
            val a = pattern[i]
            val b = pattern[(i + 1) % pattern.size]
            assertTrue(a != b, "step $i does not move")
            assertTrue(abs(a.dxDp - b.dxDp) <= RingClockBurnIn.MAX_STEP_DP && abs(a.dyDp - b.dyDp) <= RingClockBurnIn.MAX_STEP_DP, "step $i jumps: $a to $b")
        }
        assertEquals(pattern.size, pattern.toSet().size, "no position repeats within one loop")
        assertTrue(pattern.any { it.dxDp > 0 } && pattern.any { it.dxDp < 0 } && pattern.any { it.dyDp > 0 } && pattern.any { it.dyDp < 0 })
    }

    @Test
    fun `T47 - the offset follows the wall-clock minute and loops every 16 minutes, negative minutes included`() {
        assertEquals(BurnInOffset(0, 0), RingClockBurnIn.offsetFor(0))
        assertEquals(BurnInOffset(2, 1), RingClockBurnIn.offsetFor(1))
        assertEquals(RingClockBurnIn.offsetFor(5), RingClockBurnIn.offsetFor(21))
        assertEquals(RingClockBurnIn.PATTERN.last(), RingClockBurnIn.offsetFor(-1))
        val minute = 29_000_000L
        assertTrue(RingClockBurnIn.offsetFor(minute) != RingClockBurnIn.offsetFor(minute + 1))
    }

    // ---- Icons (T48) ----

    @Test
    fun `T48 - the icon row is the held notifications' distinct packages and nothing else`() {
        assertEquals(emptyList(), RingClockIcons.packages(RingSessionState()))
        val state = RingSessionState(listOf(source("a1", "pkg.a"), source("b1", "pkg.b"), source("a2", "pkg.a")))
        assertEquals(listOf("pkg.a", "pkg.b"), RingClockIcons.packages(state))
        val removed = NotificationRingLifecycle.onNotificationRemoved(state, "b1")
        assertEquals(listOf("pkg.a"), RingClockIcons.packages(removed))
    }

    @Test
    fun `T48 - at most six icons, the most recent`() {
        val state = RingSessionState((1..8).map { source("k$it", "pkg.$it", it.toLong()) })
        assertEquals((3..8).map { "pkg.$it" }, RingClockIcons.packages(state))
    }

    // ---- When to show (T49 to T52) ----

    @Test
    fun `T49 - the charging clock needs the ring, the clock and the charging switch`() {
        assertTrue(Nightstand.isEnabled(ringEnabled = true, clockOn = true, stayOnCharging = true))
        assertFalse(Nightstand.isEnabled(ringEnabled = true, clockOn = false, stayOnCharging = true))
        assertFalse(Nightstand.isEnabled(ringEnabled = false, clockOn = true, stayOnCharging = true))
        assertFalse(Nightstand.isEnabled(ringEnabled = true, clockOn = true, stayOnCharging = false))
    }

    @Test
    fun `T50 - plugged in, dark, locked, not put away and nothing on screen starts the clock`() {
        assertEquals(NightstandAttempt.START, Nightstand.attempt(ready))
    }

    @Test
    fun `T51 - any missing condition means no clock`() {
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(featureOn = false)))
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(pluggedIn = false)))
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(screenInteractive = true)))
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(putAway = true)))
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(ringOnScreen = true)))
        // Not locked but nothing else missing: wait for the lock, never show over an open phone.
        assertEquals(NightstandAttempt.RETRY_LATER, Nightstand.attempt(ready.copy(keyguardLocked = false)))
        // Not locked AND unplugged: nothing to wait for.
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(keyguardLocked = false, pluggedIn = false)))
    }

    @Test
    fun `T52 - the retry chain covers lock delays up to five minutes and then ends`() {
        val cumulative = Nightstand.RETRY_DELAYS_MS.runningReduce { a, b -> a + b }
        assertEquals(listOf(6_000L, 16_000L, 31_000L, 61_000L, 121_000L, 301_000L), cumulative)
        assertEquals(6_000L, Nightstand.retryDelayMs(0))
        assertNull(Nightstand.retryDelayMs(Nightstand.RETRY_DELAYS_MS.size))
        // Android rounds alarms under five seconds up anyway.
        assertTrue(Nightstand.RETRY_DELAYS_MS.all { it >= 5_000L })
    }

    // ---- Put away (T53, T54) ----

    @Test
    fun `T53 - power pressed on the clock keeps it dark until the user wakes the phone`() {
        var memory = NightstandMemory()
        memory = memory.onScreenOff(screenHeld = true)
        assertTrue(memory.putAway)
        assertEquals(NightstandAttempt.NONE, Nightstand.attempt(ready.copy(putAway = memory.putAway)))
        // The user wakes it: a screen-on the ring did not cause.
        memory = memory.onScreenOn(ringOnScreen = false, nowMs = 100_000)
        assertFalse(memory.putAway)
        // And turns it off again: this screen-off had no ring screen up, so the clock comes back.
        memory = memory.onScreenOff(screenHeld = false)
        assertEquals(NightstandAttempt.START, Nightstand.attempt(ready.copy(putAway = memory.putAway)))
    }

    @Test
    fun `T53 - a ring screen that had let go and timed out puts nothing away`() {
        assertFalse(NightstandMemory().onScreenOff(screenHeld = false).putAway)
    }

    @Test
    fun `T54 - the ring's own screen-on does not clear put-away, unlocking and plugging in do`() {
        val away = NightstandMemory().onScreenOff(screenHeld = true)
        val launched = away.onLaunched(nowMs = 1_000)
        assertTrue(launched.onScreenOn(ringOnScreen = false, nowMs = 2_000).putAway, "a launch on its way turned the screen on")
        assertTrue(away.onScreenOn(ringOnScreen = true, nowMs = 2_000).putAway, "the ring is up")
        assertFalse(launched.onScreenOn(ringOnScreen = false, nowMs = 1_000 + Nightstand.LAUNCH_PENDING_MS).putAway, "a launch that never came is forgotten")
        assertFalse(away.onUnlocked().putAway)
        assertFalse(away.onPowerConnected().putAway)
        assertFalse(launched.onRingCreated().launchPending(1_500))
    }

    // ---- Hold and letting go (T55 to T58) ----

    @Test
    fun `T55 - the charger holds the screen only when on, plugged, locked, not put away and not a demo`() {
        assertTrue(Nightstand.holdsScreen(featureOn = true, pluggedIn = true, keyguardLocked = true, putAway = false, demo = false))
        assertFalse(Nightstand.holdsScreen(featureOn = true, pluggedIn = false, keyguardLocked = true, putAway = false, demo = false))
        assertFalse(Nightstand.holdsScreen(featureOn = true, pluggedIn = true, keyguardLocked = false, putAway = false, demo = false))
        assertFalse(Nightstand.holdsScreen(featureOn = true, pluggedIn = true, keyguardLocked = true, putAway = true, demo = false))
        assertFalse(Nightstand.holdsScreen(featureOn = true, pluggedIn = true, keyguardLocked = true, putAway = false, demo = true))
        assertFalse(Nightstand.holdsScreen(featureOn = false, pluggedIn = true, keyguardLocked = true, putAway = false, demo = false))
    }

    @Test
    fun `T56 - while held, expiry releases nothing and the last notification leaving keeps the clock up`() {
        val expiry = NotificationRingLifecycle.onEndTrigger(RingEndTrigger.TIMER_EXPIRY, isDemoMode = false, chargingHold = true)
        assertEquals(RingEndDecision(finishes = false, releasesKeepScreenOn = false), expiry)
        val lastGone = NotificationRingLifecycle.onEndTrigger(RingEndTrigger.LAST_SOURCE_REMOVED, isDemoMode = false, chargingHold = true)
        assertEquals(RingEndDecision(finishes = false, releasesKeepScreenOn = false), lastGone)
        for (trigger in listOf(RingEndTrigger.TOUCH, RingEndTrigger.KEY_DOWN, RingEndTrigger.SCREEN_OFF, RingEndTrigger.UNLOCK)) {
            assertEquals(RingEndDecision(finishes = true, releasesKeepScreenOn = true), NotificationRingLifecycle.onEndTrigger(trigger, isDemoMode = false, chargingHold = true), "$trigger")
        }
        // Without the hold, the 3.3 behaviour is unchanged.
        assertEquals(RingEndDecision(finishes = true, releasesKeepScreenOn = true), NotificationRingLifecycle.onEndTrigger(RingEndTrigger.LAST_SOURCE_REMOVED, isDemoMode = false))
    }

    @Test
    fun `T57 - unplugged, the screen is kept on only while the ring's own timer runs`() {
        assertTrue(Nightstand.keepsScreenOn(chargingHold = true, ringTimerRunning = false))
        assertTrue(Nightstand.keepsScreenOn(chargingHold = false, ringTimerRunning = true))
        assertFalse(Nightstand.keepsScreenOn(chargingHold = false, ringTimerRunning = false))
    }

    @Test
    fun `T58 - brightness and breathing - the charging clock is at the floor and still, a fresh ring uses its own level and breathes`() {
        assertEquals(Nightstand.BRIGHTNESS, Nightstand.windowBrightness(heldByCharger = true, ringTimerRunning = false, ringBrightness = 0.2f))
        assertEquals(0.2f, Nightstand.windowBrightness(heldByCharger = true, ringTimerRunning = true, ringBrightness = 0.2f))
        assertEquals(0.6f, Nightstand.windowBrightness(heldByCharger = false, ringTimerRunning = false, ringBrightness = 0.6f))
        assertTrue(Nightstand.BRIGHTNESS > 0f && Nightstand.BRIGHTNESS < NotificationRingBrightness.DIM)

        assertFalse(Nightstand.breathes(hasSources = false, chargingHold = true, ringTimerRunning = false), "the bare clock never animates")
        assertFalse(Nightstand.breathes(hasSources = true, chargingHold = true, ringTimerRunning = false), "an old notification on the charger: still glow")
        assertTrue(Nightstand.breathes(hasSources = true, chargingHold = true, ringTimerRunning = true))
        assertTrue(Nightstand.breathes(hasSources = true, chargingHold = false, ringTimerRunning = false), "an ordinary ring keeps 3.3's breathing")
    }

    @Test
    fun `a notification landing on the empty charging clock joins it instead of being skipped`() {
        val decision = NotificationRingLifecycle.onNotificationPosted(
            RingSessionState(), source("a", "pkg.a"), screenInteractive = true, pocketCovered = false, ringOnScreen = true,
        )
        assertEquals(listOf("pkg.a"), assertIs<RingArrivalDecision.UpdateExisting>(decision).sources.map { it.packageName })
    }
}
