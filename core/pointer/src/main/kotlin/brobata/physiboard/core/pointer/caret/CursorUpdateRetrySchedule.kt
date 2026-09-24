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
