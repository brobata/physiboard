package brobata.physiboard.core.text

/** Who armed the currently active Shift one-shot, when one is armed. spec: text-input.md SS9.3. */
enum class ShiftArmSource { AUTO_CAP, USER }

/** What this module wants done to the Shift one-shot (or Caps Lock) after evaluating auto-cap. */
sealed class CapDecision {
    /** Arm a Shift one-shot, tagged as auto-cap's so a later context change can clear only this one. */
    object ArmOneShot : CapDecision()

    /** Turn Caps Lock on. Only ever produced once, at field start, for `CAP_CHARACTERS`. spec: SS3, SS9.2. */
    object EnableCapsLock : CapDecision()

    /** Clear an armed one-shot, but only because auto-cap itself armed it. spec: SS9.2, SS9.3. */
    object ClearOneShot : CapDecision()

    /** Nothing to do: no one-shot is armed, or one is armed by the user and auto-cap leaves it alone. */
    object Leave : CapDecision()
}

/**
 * Auto-capitalization's own bookkeeping: which of the two things that can arm a one-shot armed the
 * one currently in effect, and the suppression context recorded when the user overrides auto-cap
 * by tapping Shift. Everything else about Shift (the one-shot state machine itself) belongs to
 * `:core:keys`; this module only ever asks "should a one-shot be armed" and separately remembers
 * *why* the answer was yes, since `:core:keys` has no notion of auto-cap at all. spec: text-
 * input.md SS9.3.
 */
data class AutoCapState(
    val armSource: ShiftArmSource? = null,
    private val suppressedContext: String? = null,
) {
    /** A new field started: the suppression context is per field session. spec: SS9.3 ("cleared when a new field starts, not on a restart"). */
    fun forNewField(): AutoCapState = AutoCapState(armSource = null, suppressedContext = null)

    /**
     * The user tapped Shift while auto-cap had a one-shot armed, turning it off. [context] is the
     * 200-characters-before, 1-character-after key from SS9.3 / SS2; supplying it here (rather
     * than a callback the module invokes later) is what keeps this a pure state transition.
     */
    fun onUserDisarmed(context: String): AutoCapState = copy(armSource = null, suppressedContext = context)

    /**
     * A key that unconditionally consumes any pending Shift one-shot (Enter, spec text-input.md
     * SS7: "a Shift one-shot is consumed before the newline is processed"). This only clears this
     * module's own bookkeeping of who armed it; consuming the one-shot itself is `:core:keys`'.
     */
    fun consumedUnconditionally(): AutoCapState = copy(armSource = null)

    internal fun isSuppressedAt(context: String?): Boolean = context != null && context == suppressedContext

    internal fun withArmSource(source: ShiftArmSource?): AutoCapState = copy(armSource = source)

    companion object {
        fun initial(): AutoCapState = AutoCapState()
    }
}

/**
 * The auto-capitalization decision: whether to arm a Shift one-shot (or Caps Lock), evaluated
 * fresh at every trigger point text-input.md SS9.1 lists (field start, every selection/cursor
 * update, after Space/Enter/double-space-period/a deferred space, after an Alt or variation
 * character). spec: text-input.md SS9.
 *
 * A dictation-specific note this API is shaped to make impossible: text-input.md's provenance
 * records a bug where a decision about the text ahead of an utterance was re-read *after* the
 * keyboard had already written into the field, corrupting the decision. [evaluate] takes
 * [textBeforeCursor] as a plain parameter, captured by the caller once, before any insertion; it
 * has no callback or live field handle it could use to peek again later. A caller that needs "cap
 * decision for the text as it stood before this utterance" gets exactly that by reading the field
 * once and calling this function once, and has no API surface to accidentally do otherwise.
 */
object AutoCapitalization {

    /**
     * [textBeforeCursor] is the text immediately before the cursor with any selection already
     * excluded (spec: SS2, "selection is treated as removed"), or null when the field read failed
     * (spec: SS2, "no auto-cap; the Shift one-shot ... is cleared"). [suppressionContext], when
     * non-null, is the same 200-before-1-after key the caller used with [AutoCapState.onUserDisarmed];
     * a null value (an unreadable field) can never match a recorded suppression, matching SS2's
     * "the auto-cap suppression key ... when the app returns null there is no key and suppression
     * cannot be recorded" for the read side too.
     *
     * [knownSentenceEndPending] is the fallback rebuild-from-scratch.md's "the editor is not a
     * reliable narrator" work built for the boundary/autocorrect engine ([DriftCheck]) but never
     * extended to this decision: whether the caller independently knows, from its own very recent
     * output rather than from [textBeforeCursor], that the text now ends in a sentence-ending mark
     * followed only by whitespace (a period/question mark/exclamation mark it just committed
     * itself, with nothing but the boundary whitespace after it). A caller that cannot make that
     * claim passes `false`, which reduces to exactly today's read-only decision. One exists because
     * "capitalize after sentence end" is the one arm condition in this function that depends on a
     * character a *previous* keystroke committed still being visible in *this* keystroke's read;
     * every other condition here (field start, CAP_WORDS, the newline case) depends only on
     * whitespace or emptiness this same keystroke's own commit already guarantees, or on state the
     * caller reads once and passes straight through. A field whose `InputConnection` answers a read
     * with text that lags one commit behind (`EditorReadTrust.POSSIBLY_STALE`'s own documented
     * failure mode: "an asynchronous Compose or Flutter field applying edits on its own schedule")
     * would otherwise silently lose the mark and never arm, exactly like an ordinary "no" answer,
     * with no way for this function to tell the two apart from [textBeforeCursor] alone.
     */
    fun evaluate(
        state: AutoCapState,
        field: FieldContext,
        settings: AutoCapSettings,
        textBeforeCursor: String?,
        suppressionContext: String? = null,
        knownSentenceEndPending: Boolean = false,
    ): Pair<AutoCapState, CapDecision> {
        if (field.capFlags.capCharacters) {
            // CAP_CHARACTERS wins outright and disables per-letter auto-cap entirely (SS9.2).
            return state to CapDecision.Leave
        }

        if (field.capFlags.capWords) {
            return if (textBeforeCursor != null && armsForCapWords(textBeforeCursor)) {
                state.withArmSource(ShiftArmSource.AUTO_CAP) to CapDecision.ArmOneShot
            } else {
                clearIfOwnedByAutoCap(state)
            }
        }

        if (!field.autoCapAllowed(settings.capitalizeRestrictedFields)) {
            return clearIfOwnedByAutoCap(state)
        }

        if (field.capFlags.capSentences && !settings.capitalizeAtTextStart && !settings.capitalizeAfterSentenceEnd) {
            // "CAP_SENTENCES uses the rules below but only if at least one user setting is on."
            return clearIfOwnedByAutoCap(state)
        }

        if (state.isSuppressedAt(suppressionContext)) {
            return state to CapDecision.Leave
        }

        if (textBeforeCursor == null) {
            return if (settings.capitalizeAfterSentenceEnd && knownSentenceEndPending) {
                state.withArmSource(ShiftArmSource.AUTO_CAP) to CapDecision.ArmOneShot
            } else {
                clearIfOwnedByAutoCap(state)
            }
        }

        val armsAtTextStart = settings.capitalizeAtTextStart && (textBeforeCursor.isEmpty() || textBeforeCursor.endsWith("\n"))
        val armsAfterSentence = settings.capitalizeAfterSentenceEnd &&
            (WordChars.endsSentenceFollowedByWhitespace(textBeforeCursor) || knownSentenceEndPending)

        return if (armsAtTextStart || armsAfterSentence) {
            state.withArmSource(ShiftArmSource.AUTO_CAP) to CapDecision.ArmOneShot
        } else {
            clearIfOwnedByAutoCap(state)
        }
    }

    /** spec: SS3, `CAP_CHARACTERS` at field start turns Caps Lock on; evaluated once, not per letter. */
    fun evaluateFieldStartCapsLock(field: FieldContext): Boolean = field.capFlags.capCharacters

    /** spec: SS9.2, CAP_WORDS: "arm at every word start" - cursor at field start, or right after whitespace or boundary punctuation. */
    private fun armsForCapWords(textBeforeCursor: String): Boolean =
        textBeforeCursor.isEmpty() ||
            textBeforeCursor.last().isWhitespace() ||
            textBeforeCursor.last() in WordChars.BOUNDARY_PUNCTUATION

    private fun clearIfOwnedByAutoCap(state: AutoCapState): Pair<AutoCapState, CapDecision> =
        if (state.armSource == ShiftArmSource.AUTO_CAP) {
            state.withArmSource(null) to CapDecision.ClearOneShot
        } else {
            state to CapDecision.Leave
        }
}
