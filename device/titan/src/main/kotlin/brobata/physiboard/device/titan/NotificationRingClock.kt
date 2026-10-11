package brobata.physiboard.device.titan

/**
 * The time line of the ring's clock: the phone's 12/24-hour choice, no AM/PM (the stock
 * always-on look), minutes always two digits.
 *
 * spec: device-backlight-ring.md SS5.11.1.
 */
object RingClockFormat {
    /** [hourOfDay] 0..23 and [minute] 0..59 (out-of-range values are clamped). 12-hour: "7:05", "12:00"; 24-hour: "07:05", "19:05". */
    fun time(hourOfDay: Int, minute: Int, is24Hour: Boolean): String {
        val hour = hourOfDay.coerceIn(0, 23)
        val mm = minute.coerceIn(0, 59).toString().padStart(2, '0')
        if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
        val twelve = hour % 12
        return "${if (twelve == 0) 12 else twelve}:$mm"
    }
}

/** How far the clock block sits from its home position, in dp. */
data class BurnInOffset(val dxDp: Int, val dyDp: Int)

/**
 * Burn-in protection for the clock: the whole block (time, date, icons) moves to the next
 * position of a fixed 16-step loop once a minute, never more than [MAX_SHIFT_DP] from home on
 * either axis and never more than 2 dp per axis in one step, so the move is not noticed but no
 * pixel of the time stays lit in the same place for more than a minute. The ring does not move:
 * it has to stay on the lens.
 *
 * spec: device-backlight-ring.md SS5.11.3.
 */
object RingClockBurnIn {
    const val MAX_SHIFT_DP = 4
    const val MAX_STEP_DP = 2

    /** A slow loop around home: out to the right, round through every quadrant, back to the start. */
    val PATTERN: List<BurnInOffset> = listOf(
        BurnInOffset(0, 0), BurnInOffset(2, 1), BurnInOffset(4, 2), BurnInOffset(3, 4),
        BurnInOffset(1, 3), BurnInOffset(-1, 4), BurnInOffset(-3, 3), BurnInOffset(-4, 1),
        BurnInOffset(-2, 0), BurnInOffset(-4, -1), BurnInOffset(-3, -3), BurnInOffset(-1, -4),
        BurnInOffset(1, -3), BurnInOffset(3, -4), BurnInOffset(4, -2), BurnInOffset(2, -1),
    )

    /** The offset for the minute [epochMinute] (wall-clock milliseconds / 60000), so the position follows the clock, not how long the screen has been up. */
    fun offsetFor(epochMinute: Long): BurnInOffset = PATTERN[Math.floorMod(epochMinute, PATTERN.size.toLong()).toInt()]
}

/**
 * Which apps the clock's icon row shows: the distinct packages of the notifications the ring is
 * currently holding (never anything else), oldest first, at most [MAX_ICONS] (the most recent).
 *
 * spec: device-backlight-ring.md SS5.11.1.
 */
object RingClockIcons {
    const val MAX_ICONS = 6

    fun packages(state: RingSessionState): List<String> = state.sources.map { it.packageName }.distinct().takeLast(MAX_ICONS)
}

/** What a screen-off, a plug-in or a retry should do about the charging clock. spec: SS5.11.2. */
enum class NightstandAttempt { START, RETRY_LATER, NONE }

/** The facts the charging clock's start decision reads, all taken at the moment of the attempt. */
data class NightstandFacts(
    /** [Nightstand.isEnabled] over the three switches. */
    val featureOn: Boolean,
    val pluggedIn: Boolean,
    val screenInteractive: Boolean,
    val keyguardLocked: Boolean,
    /** The user turned the screen off on one of the ring's screens; see [NightstandMemory]. */
    val putAway: Boolean,
    /** A ring or clock is already on screen, or its launch is still on the way. */
    val ringOnScreen: Boolean,
)

/**
 * "Stay on while charging": the decisions. Nothing here touches a window, a receiver or a timer.
 *
 * spec: device-backlight-ring.md SS5.11.2 (start, retries, hold, letting go), SS5.11.4 (brightness, animation).
 */
object Nightstand {
    /**
     * The window brightness of the charging clock while no ring is breathing: as low as a window
     * may ask for. Android clamps any override to the panel's own minimum, so this lands on the
     * dimmest level the Titan's panel offers; 0 itself is avoided because some drivers read it as
     * "backlight off".
     */
    const val BRIGHTNESS = 0.01f

    /**
     * Gaps between the checks after a screen-off that found the phone not yet locked (Android's
     * "lock after screen timeout" delay). Cumulative: 6 s, 16 s, 31 s, 61 s, 121 s, 301 s, which
     * covers every delay Android offers up to 5 minutes. Each check is one inexact alarm; the
     * chain ends at the first check that starts the clock or finds a reason not to.
     */
    val RETRY_DELAYS_MS: List<Long> = listOf(6_000L, 10_000L, 15_000L, 30_000L, 60_000L, 180_000L)

    /** A launch counts as on its way for this long (the announcement's own timeout, SS5.6), on the time-since-boot clock. */
    const val LAUNCH_PENDING_MS = 15_000L

    /** The charging clock rides on the ring and needs the clock: all three switches. */
    fun isEnabled(ringEnabled: Boolean, clockOn: Boolean, stayOnCharging: Boolean): Boolean = ringEnabled && clockOn && stayOnCharging

    fun attempt(facts: NightstandFacts): NightstandAttempt = when {
        !facts.featureOn || !facts.pluggedIn || facts.putAway || facts.ringOnScreen || facts.screenInteractive -> NightstandAttempt.NONE
        // Never hold the screen over a phone that is not locked: that would stretch Android's
        // grace period into a whole night.
        !facts.keyguardLocked -> NightstandAttempt.RETRY_LATER
        else -> NightstandAttempt.START
    }

    /** The wait before check number [checkIndex] + 1 (0 is the one at screen-off), or null when the chain is over. */
    fun retryDelayMs(checkIndex: Int): Long? = RETRY_DELAYS_MS.getOrNull(checkIndex)

    /** Whether the ring screen holds the screen on because of the charger (as opposed to its own timer). */
    fun holdsScreen(featureOn: Boolean, pluggedIn: Boolean, keyguardLocked: Boolean, putAway: Boolean, demo: Boolean): Boolean =
        !demo && featureOn && pluggedIn && keyguardLocked && !putAway

    fun keepsScreenOn(chargingHold: Boolean, ringTimerRunning: Boolean): Boolean = chargingHold || ringTimerRunning

    /**
     * The ring's own brightness while its timer runs; [BRIGHTNESS] otherwise on a screen the
     * charger has held ([heldByCharger] stays true after unplugging, so letting go never
     * brightens the clock on its way out). An ordinary ring keeps its own level throughout.
     */
    fun windowBrightness(heldByCharger: Boolean, ringTimerRunning: Boolean, ringBrightness: Float): Float =
        if (heldByCharger && !ringTimerRunning) BRIGHTNESS else ringBrightness

    /**
     * The glow breathes (a redraw every frame) only while the ring's timer runs. On a screen the
     * charger has held ([heldByCharger] stays true after unplugging) the glow is otherwise still
     * and the screen is redrawn once a minute; an ordinary ring keeps 3.3's breathing.
     */
    fun breathes(hasSources: Boolean, heldByCharger: Boolean, ringTimerRunning: Boolean): Boolean =
        hasSources && (ringTimerRunning || !heldByCharger)
}

/**
 * What the charging clock remembers between events. [putAway]: the user pressed power while a
 * ring screen was holding the screen on, so it stays dark until the user next wakes the phone,
 * unlocks it or plugs it in. [launchPendingSinceMs]: a launch was sent and its screen has not appeared yet,
 * so the screen-on it causes is not the user's.
 *
 * spec: device-backlight-ring.md SS5.11.2.
 */
data class NightstandMemory(val putAway: Boolean = false, val launchPendingSinceMs: Long? = null) {
    /** [nowMs] and the launch time are both time since boot, never wall-clock time, which the user or the network can move. */
    fun launchPending(nowMs: Long): Boolean = launchPendingSinceMs != null && nowMs - launchPendingSinceMs in 0 until Nightstand.LAUNCH_PENDING_MS

    fun onLaunched(nowMs: Long): NightstandMemory = copy(launchPendingSinceMs = nowMs)

    fun onRingCreated(): NightstandMemory = copy(launchPendingSinceMs = null)

    /**
     * Screen off while a ring screen holds keep-screen-on can only be the power key (the window
     * holds off the timeout). A ring screen that has let go was ended by the phone's own timeout,
     * which puts nothing away.
     */
    fun onScreenOff(screenHeld: Boolean): NightstandMemory = if (screenHeld) copy(putAway = true) else this

    /** A screen-on the ring did not cause is the user waking the phone. */
    fun onScreenOn(ringOnScreen: Boolean, nowMs: Long): NightstandMemory =
        if (ringOnScreen || launchPending(nowMs)) this else copy(putAway = false)

    fun onUnlocked(): NightstandMemory = copy(putAway = false)

    fun onPowerConnected(): NightstandMemory = copy(putAway = false)
}
