package brobata.physiboard.core.strip

/**
 * The dip's own memory. spec: status-bar.md SS12.2. [startedAtMs] is the instant the current or
 * last dip began (it doubles as the cool-down's reference, SS12.2 step 1, "fewer than 1500 ms
 * have passed since the last dip started"); [reshown] records that the hold's re-show has been
 * issued, so a late timer can never issue it twice. Whether a dip is "in flight" or "holding" is
 * a question of the clock, answered by [StripDip], never a flag that could go stale.
 */
data class DipState(val startedAtMs: Long? = null, val reshown: Boolean = false)

/** What `:ime` must do to the real candidates view for one dip step. spec: status-bar.md SS12.2 steps 2 and 3. */
enum class DipEffect { HIDE_STRIP, SHOW_STRIP }

/** The answer to a refused show request. [started] is true only when a dip actually began (T8 to T14). */
data class DipDecision(val state: DipState, val started: Boolean, val effects: List<DipEffect>)

/**
 * The per-app dip ("Text box under the bar"): for a listed app whose show request was refused,
 * hide the strip, hold for [HOLD_MS] during which every show request is refused, re-show, and
 * keep the dip "in flight" for another [SETTLE_MS] during which window-hidden handling is skipped
 * so "the field, the modifiers and the suggestion context survive the blink". spec:
 * status-bar.md SS12 (D5: a 21 ms re-show was folded into the hide; 200 ms works), SS13, SS14.
 */
object StripDip {
    /** spec SS14: "Dip: hold before re-show 200 ms". */
    const val HOLD_MS: Long = 200

    /** spec SS14: "Dip: settle after re-show 300 ms". */
    const val SETTLE_MS: Long = 300

    /** spec SS12.2 step 4: the dip is over 300 ms after the re-show, 500 ms after it began. */
    const val IN_FLIGHT_MS: Long = HOLD_MS + SETTLE_MS

    /** spec SS14: "Dip: cool-down between dips 1500 ms", because "requests arrive in pairs". */
    const val COOLDOWN_MS: Long = 1500

    /** spec SS12.2: the list "seeded on first read with Teams". */
    val SEEDED_APPS: Set<String> = setOf("com.microsoft.teams")

    /**
     * spec SS12.2 steps 1 and 2, for one refused show request at [nowMs]. Nothing happens (T11 to
     * T13, SS17's "Configuration change triggers a refused show") when a dip is in flight, the
     * app is not listed or has no package, the strip is not actually rendered on screen, fewer
     * than [COOLDOWN_MS] have passed since the last dip started, or the request is a
     * configuration change. Otherwise the strip hides now and the hold begins.
     */
    fun onShowRefused(
        state: DipState,
        nowMs: Long,
        packageName: String?,
        listedApps: Set<String>,
        stripRendered: Boolean,
        configurationChange: Boolean,
    ): DipDecision {
        val listed = packageName != null && packageName in listedApps
        val coolingDown = state.startedAtMs?.let { nowMs - it < COOLDOWN_MS } ?: false
        if (configurationChange || !listed || !stripRendered || isInFlight(state, nowMs) || coolingDown) {
            return DipDecision(state, started = false, effects = emptyList())
        }
        return DipDecision(DipState(startedAtMs = nowMs, reshown = false), started = true, effects = listOf(DipEffect.HIDE_STRIP))
    }

    /**
     * spec SS12.2 step 3: "200 ms later the hold ends and the strip is shown again". Called when
     * `:ime`'s timer fires; answers nothing if the hold has not elapsed yet or the re-show was
     * already issued, so a timer that fires early, late or twice is harmless.
     */
    fun onHoldElapsed(state: DipState, nowMs: Long): Pair<DipState, List<DipEffect>> {
        val startedAt = state.startedAtMs ?: return state to emptyList()
        if (state.reshown || nowMs - startedAt < HOLD_MS) return state to emptyList()
        return state.copy(reshown = true) to listOf(DipEffect.SHOW_STRIP)
    }

    /** spec SS12.2 and T9: "true from the refusal until t=200 exclusive, false at t=200". */
    fun isHolding(state: DipState, nowMs: Long): Boolean = state.startedAtMs?.let { nowMs - it < HOLD_MS } ?: false

    /** spec SS12.2 and T10: "true from refusal, still true at t=200, false at t=500". */
    fun isInFlight(state: DipState, nowMs: Long): Boolean = state.startedAtMs?.let { nowMs - it < IN_FLIGHT_MS } ?: false

    /** spec SS12.2: "While the hold is on... every other request to show the candidates view is refused, including PhysiBoard's own re-show". */
    fun refusesShowRequest(state: DipState, nowMs: Long): Boolean = isHolding(state, nowMs)

    /** spec SS12.2 and SS13: "While the dip is in flight... the window-hidden handling is skipped entirely". */
    fun skipsWindowHidden(state: DipState, nowMs: Long): Boolean = isInFlight(state, nowMs)

    /** When `:ime` should arm its re-show timer for, or null when no re-show is pending. */
    fun reshowDueAtMs(state: DipState): Long? = state.startedAtMs?.takeUnless { state.reshown }?.plus(HOLD_MS)
}
