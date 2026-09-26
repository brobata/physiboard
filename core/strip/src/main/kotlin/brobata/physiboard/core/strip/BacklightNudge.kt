package brobata.physiboard.core.strip

/** What the smart-backlight-paused nudge draws, right-anchored inside the right buttons. spec: status-bar.md SS10. */
enum class BacklightNudgeVisibility { HIDDEN, PILL, DOT }

/**
 * The smart-backlight-paused nudge: spec status-bar.md SS10, kept for 3.0 ("Titan-specific nudge
 * for the smart backlight; the numbers are settled", SS19). Shown when the feature is on but has
 * never actually applied, it starts as an amber pill and shrinks itself to a 9 dp dot, either on
 * the first strip refresh that lands a decent moment after it appeared or, failing that, on its
 * own clock (T38, SS14). `:ime` owns the one real timer and the "has this been shown long enough"
 * bookkeeping (the same `shownAtMs`/`collapsedToDot` idiom [StripDip] uses for the per-app dip);
 * this object only answers the pure questions.
 */
object BacklightNudge {
    /** spec SS14: "Backlight pill auto-collapse... 4000 ms". */
    const val AUTO_COLLAPSE_MS: Long = 4000

    /** spec SS14: "...minimum show before keypress collapse 350 ms". */
    const val MIN_SHOW_BEFORE_REFRESH_COLLAPSE_MS: Long = 350

    /** spec SS10: the pill's own copy, a lightning glyph and a long dash sit around this in `:ime`. */
    const val PILL_TEXT: String = "backlight paused, tap to fix"

    /** spec SS10: "Both disappear as soon as the backlight is applied"; dismissing "✕" also hides it until the next time it becomes eligible. */
    fun visibility(enabled: Boolean, applied: Boolean, dismissedByUser: Boolean, collapsedToDot: Boolean): BacklightNudgeVisibility {
        if (!enabled || applied || dismissedByUser) return BacklightNudgeVisibility.HIDDEN
        return if (collapsedToDot) BacklightNudgeVisibility.DOT else BacklightNudgeVisibility.PILL
    }

    /** spec SS10, T38: "the first refresh that arrives at least 350 ms after it appeared" collapses the pill. */
    fun collapsesOnRefresh(shownAtMs: Long, nowMs: Long): Boolean = nowMs - shownAtMs >= MIN_SHOW_BEFORE_REFRESH_COLLAPSE_MS

    /** spec SS10, SS14, T38: the pill's own 4000 ms clock, independent of any refresh. */
    fun collapsesAfterTimeout(shownAtMs: Long, nowMs: Long): Boolean = nowMs - shownAtMs >= AUTO_COLLAPSE_MS
}

/**
 * `:ime`'s own memory for one nudge "episode": when it first became eligible, whether the user
 * dismissed it with "✕", and whether it has already shrunk to the dot. Mirrors [DipState]'s own
 * shape (a clock reference plus small flags, answered by pure functions, never by a live timer
 * inside the data itself).
 */
data class BacklightNudgeMemory(val shownAtMs: Long? = null, val dismissedByUser: Boolean = false, val collapsedToDot: Boolean = false)

/**
 * Drives [BacklightNudgeMemory] from the two settings and the real clock. spec SS10: leaving
 * `smart_backlight_enabled` on while `smart_backlight_applied` stays false is what makes the nudge
 * eligible at all; becoming ineligible (either flag flips) forgets the whole episode, so the next
 * time it becomes eligible again it starts as a fresh pill.
 */
object BacklightNudgeEpisode {
    /** spec SS10: one strip refresh's worth of bookkeeping; [BacklightNudge.collapsesOnRefresh] is folded in here so `:ime` needs to call only this. */
    fun onRefresh(memory: BacklightNudgeMemory, enabled: Boolean, applied: Boolean, nowMs: Long): Pair<BacklightNudgeMemory, BacklightNudgeVisibility> {
        if (!enabled || applied) return BacklightNudgeMemory() to BacklightNudgeVisibility.HIDDEN
        val shownAtMs = memory.shownAtMs ?: nowMs
        val collapsed = memory.collapsedToDot || BacklightNudge.collapsesOnRefresh(shownAtMs, nowMs)
        val next = memory.copy(shownAtMs = shownAtMs, collapsedToDot = collapsed)
        return next to BacklightNudge.visibility(enabled = true, applied = false, dismissedByUser = next.dismissedByUser, collapsedToDot = next.collapsedToDot)
    }

    /** spec SS10, SS14: the pill's own 4000 ms clock firing; a no-op once the episode already collapsed or ended. */
    fun onTimeoutElapsed(memory: BacklightNudgeMemory): BacklightNudgeMemory =
        if (memory.shownAtMs == null) memory else memory.copy(collapsedToDot = true)

    /** spec SS10: tapping the pill's "✕". */
    fun onDismissed(memory: BacklightNudgeMemory): BacklightNudgeMemory = memory.copy(dismissedByUser = true)
}
