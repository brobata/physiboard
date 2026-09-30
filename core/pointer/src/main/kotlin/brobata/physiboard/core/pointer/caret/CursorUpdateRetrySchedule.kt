package brobata.physiboard.core.pointer.caret

/** How many cursor-update requests have gone out for the current editor, and whether one landed. spec: trackpad-caret-nav.md SS4.7. */
data class CursorUpdateRequestState(val attemptsMade: Int = 0, val accepted: Boolean = false)

/**
 * Decides whether one more cursor-update request should go out to the editor.
 *
 * spec: trackpad-caret-nav.md SS4.7: "retries are staged at 80, 250, 600 and 1200 ms after the
 * start. A retry is skipped once a request has been accepted or after 8 attempts in total for that
 * editor." [SCHEDULE_OFFSETS_MS] is the initial request (at editor start, offset 0) plus the four
 * staged retries; once that schedule is exhausted, [onRefresh] models "on every strip refresh...
 * while the setting is on and no request has been accepted yet, a retry is attempted", still under
 * the same cap.
 */
/**
 * Whether the editor should be asked for cursor-anchor reports at all. spec: trackpad-caret-nav.md
 * SS4.7: "a request for cursor updates (immediate plus monitor) is issued if `caret_modifier_badge`
 * is on or the emoji-picker search needs it; with neither, a request with no flags is issued to
 * turn monitoring off. Both features share this one switch, so neither can turn it off under the
 * other."
 */
object CursorUpdateRequestPolicy {
    /**
     * [stripNeedsCaret] is the third consumer: the strip asks where the caret is to find out
     * whether the app has drawn its text box underneath it
     * (`brobata.physiboard.core.strip.StripOverlap`). Without it that fix would have worked only
     * for someone who happened to have the caret badge switched on.
     */
    fun wantsReports(
        caretBadgeEnabled: Boolean,
        emojiSearchNeedsCaret: Boolean,
        stripNeedsCaret: Boolean = false,
    ): Boolean = caretBadgeEnabled || emojiSearchNeedsCaret || stripNeedsCaret
}

object CursorUpdateRetrySchedule {
    val SCHEDULE_OFFSETS_MS: List<Long> = listOf(0L, 80L, 250L, 600L, 1200L)
    const val MAX_ATTEMPTS = 8

    /** The [index]th entry of [SCHEDULE_OFFSETS_MS] (0 = the immediate request at editor start) firing. */
    fun onScheduledAttempt(state: CursorUpdateRequestState): Pair<CursorUpdateRequestState, Boolean> = attempt(state)

    /** spec SS4.7: a strip refresh while the schedule is exhausted keeps retrying under the same cap. */
    fun onRefresh(state: CursorUpdateRequestState): Pair<CursorUpdateRequestState, Boolean> = attempt(state)

    /** The editor accepted a request: no further retries are needed. */
    fun onRequestAccepted(state: CursorUpdateRequestState): CursorUpdateRequestState = state.copy(accepted = true)

    private fun attempt(state: CursorUpdateRequestState): Pair<CursorUpdateRequestState, Boolean> {
        if (state.accepted || state.attemptsMade >= MAX_ATTEMPTS) return state to false
        return state.copy(attemptsMade = state.attemptsMade + 1) to true
    }
}
