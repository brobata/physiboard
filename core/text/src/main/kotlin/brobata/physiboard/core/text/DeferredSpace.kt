package brobata.physiboard.core.text

/**
 * "Before next text" bookkeeping (`space_after_punctuation`): after a character in that list is
 * committed, a space is owed but withheld until more text arrives, so a message sent right after
 * `?` or `!` does not end in a trailing space. spec: text-input.md SS6.6.
 *
 * The debt itself carries no data beyond "is one owed"; every rule about *when* it resolves lives
 * in [DeferredSpace.onNextCommit].
 */
data class DeferredSpaceDebt(val owed: Boolean = false) {
    companion object {
        fun none(): DeferredSpaceDebt = DeferredSpaceDebt(false)
    }
}

/** What to do about a pending space debt when new text is about to be committed. */
sealed class DeferredSpaceOutcome {
    /** No debt was pending; commit [text] exactly as given. */
    data class Unaffected(val debt: DeferredSpaceDebt) : DeferredSpaceOutcome()

    /** The debt is paid: insert a space before [text] as its own commit, marked as an auto-space. */
    data class InsertSpaceBefore(val text: String) : DeferredSpaceOutcome()

    /** The debt is cancelled without inserting anything: [text] starts with whitespace. */
    object Cancelled : DeferredSpaceOutcome()

    /** The debt survives unpaid: [text] starts with a character in the no-space-before set. */
    data class Kept(val debt: DeferredSpaceDebt) : DeferredSpaceOutcome()
}

/**
 * Pure transitions over a [DeferredSpaceDebt]. spec: text-input.md SS6.6.
 */
object DeferredSpace {

    /** A character in `space_after_punctuation` was just committed: a space is now owed. */
    fun onPunctuationInList(): DeferredSpaceDebt = DeferredSpaceDebt(owed = true)

    /**
     * New text is about to be committed while [debt] may or may not be pending. [text] is the
     * text the keyboard is about to commit (a single character in the ordinary case). Enter,
     * Backspace, leaving the field, or any cursor move other than the keyboard's own one-step
     * forward step should call [cancelled] instead of this function.
     */
    fun onNextCommit(debt: DeferredSpaceDebt, text: String): DeferredSpaceOutcome {
        if (!debt.owed || text.isEmpty()) return DeferredSpaceOutcome.Unaffected(debt)
        val first = text.first()
        return when {
            first.isWhitespace() -> DeferredSpaceOutcome.Cancelled
            first in SpacingSettings.NO_SPACE_BEFORE -> DeferredSpaceOutcome.Kept(debt)
            else -> DeferredSpaceOutcome.InsertSpaceBefore(text)
        }
    }

    /** Enter, Backspace, leaving the field, or an unrelated cursor move: the debt is dropped unpaid. spec: SS6.6. */
    fun cancelled(): DeferredSpaceDebt = DeferredSpaceDebt.none()
}
