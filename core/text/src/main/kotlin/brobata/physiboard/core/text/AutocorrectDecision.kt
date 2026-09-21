package brobata.physiboard.core.text

/**
 * The relative-margin confidence between the top candidate and the runner-up. spec: autocorrect-
 * suggestions.md SS9.
 */
object Confidence {
    /** spec: SS9, "The threshold is 0.02; there is no preference for it." */
    const val THRESHOLD: Double = 0.02

    /**
     * `(top - runnerUp) / top`, clamped to 0..1. 1.0 when there is no runner-up or it is not a
     * current-word candidate (nothing to be unsure against); 0.0 when [topScore] is not positive.
     */
    fun compute(topScore: Double, runnerUpScore: Double?, runnerUpIsCurrentWordCandidate: Boolean = true): Double {
        if (topScore <= 0.0) return 0.0
        if (runnerUpScore == null || !runnerUpIsCurrentWordCandidate) return 1.0
        return ((topScore - runnerUpScore) / topScore).coerceIn(0.0, 1.0)
    }
}

/**
 * The nine-step "safe shape" test, run in order with the first applicable decision winning. spec:
 * autocorrect-suggestions.md SS9. [lengthChangeAllowance] is the per-language allowance (English
 * 2, every other language 0 per SS9 step 8 and the settings table).
 *
 * // SPEC GAP: step 9's fallback ("accept only if orthographic and the candidate has the
 * // lookup word's length") can only be reached once steps 6-8 have already excluded every case
 * // where the candidate's length equals the word's, so as written it is unreachable for the
 * // `word`/`candidate` pair this function receives. It is still implemented literally (rather
 * // than "fixed") in case a caller ever passes a pair where that assumption does not hold, for
 * // instance comparing an apostrophe-recomposed candidate against the pre-split typed word.
 */
object SafeShape {

    fun evaluate(
        word: String,
        candidate: String,
        distance: Int,
        isCurrentWordCandidate: Boolean,
        isOrthographicVariant: Boolean,
        isCaseVariant: Boolean,
        maxAutoReplaceDistance: Int,
        lengthChangeAllowance: Int,
    ): Boolean {
        if (!isCurrentWordCandidate) return false
        if (distance == 0) return isOrthographicVariant || isCaseVariant
        if (distance > maxAutoReplaceDistance) return false
        if (isAllLowercaseLetters(word) && isAcronymLike(candidate)) return false
        if (startsLowercase(word) && startsUppercase(candidate) && !isCaseVariant) return false

        val lengthDiff = candidate.length - word.length
        if (lengthDiff == 0) {
            return isOrthographicVariant || isCaseVariant || firstLetterUnchanged(word, candidate)
        }
        if (lengthDiff == 1 && extraLetterDoublesNeighbour(word, candidate)) return true
        if (lengthDiff != 0 && lengthDiff in -lengthChangeAllowance..lengthChangeAllowance) {
            return !isPureAffixChange(word, candidate) && firstLetterUnchanged(word, candidate)
        }
        return isOrthographicVariant && candidate.length == word.length
    }

    private fun isAllLowercaseLetters(word: String): Boolean = word.none { it.isUpperCase() }

    private fun isAcronymLike(candidate: String): Boolean {
        val letters = candidate.filter { it.isLetter() }
        return letters.length >= 2 && letters.all { it.isUpperCase() }
    }

    private fun startsLowercase(s: String): Boolean = s.firstOrNull { it.isLetter() }?.isLowerCase() == true
    private fun startsUppercase(s: String): Boolean = s.firstOrNull { it.isLetter() }?.isUpperCase() == true

    internal fun firstLetterUnchanged(word: String, candidate: String): Boolean {
        val w = word.firstOrNull { it.isLetter() }
        val c = candidate.firstOrNull { it.isLetter() }
        return w != null && c != null && w.lowercaseChar() == c.lowercaseChar()
    }

    /** Whether [candidate], one character longer than [word], is [word] with one letter doubled. spec: SS9 step 7, SS "Length ratio" gate. */
    internal fun extraLetterDoublesNeighbour(word: String, candidate: String): Boolean {
        if (candidate.length != word.length + 1) return false
        var i = 0
        while (i < word.length && i < candidate.length && word[i] == candidate[i]) i++
        if (i >= candidate.length) return false
        val inserted = candidate[i]
        return inserted == candidate.getOrNull(i - 1) || inserted == candidate.getOrNull(i + 1)
    }

    private fun isPureAffixChange(word: String, candidate: String): Boolean {
        val (shorter, longer) = if (word.length <= candidate.length) word to candidate else candidate to word
        return longer.startsWith(shorter) || longer.endsWith(shorter)
    }
}

/**
 * The gate behind autocorrect-suggestions.md SS10 ("a correctly spelled word is never
 * overwritten"): the only replacements a known word can still receive are a case repair or an
 * accent (orthographic) repair, and only when no entry already covers the typed form. spec: SS9,
 * "Known word" row.
 */
object KnownWordGate {
    fun passes(isKnown: Boolean, isCaseVariant: Boolean, isOrthographicVariant: Boolean, exactPrimaryCaseExists: Boolean, exactKnownExists: Boolean): Boolean =
        !isKnown ||
            (isCaseVariant && !exactPrimaryCaseExists) ||
            (isOrthographicVariant && !exactKnownExists)
}

/** Why the automatic correction gate refused to commit, for the debug capture. spec: SS9. */
enum class AutocorrectRefusalReason {
    NO_SUGGESTION, REJECTED_BY_USER, KNOWN_WORD, DISTANCE_TOO_HIGH, NOT_CURRENT_WORD,
    NOT_EDIT_DISTANCE, ACRONYM_CANDIDATE, UNSAFE_SHAPE, TOO_CLOSE_TO_CALL, WORD_TOO_SHORT,
    CANDIDATE_TOO_LONG, CONSTRAINTS_NOT_MET,
}

/** What the automatic correction decision produced. spec: SS9. */
sealed class AutocorrectOutcome {
    /** Commit [recased] in place of the typed word. */
    data class Commit(val recased: String) : AutocorrectOutcome()

    /** The recased candidate equals the typed word: nothing to do. spec: SS9, "same replacement". */
    object SameReplacement : AutocorrectOutcome()

    data class Refuse(val reason: AutocorrectRefusalReason) : AutocorrectOutcome()
}

/**
 * Every fact the automatic correction decision needs about the top candidate. spec: SS9, "Facts
 * gathered about the top candidate (after apostrophe recomposition)".
 */
data class AutocorrectCandidateFacts(
    val word: String,
    val candidate: String,
    val distance: Int,
    val isCurrentWordCandidate: Boolean,
    val isOrthographicVariant: Boolean,
    val isCaseVariant: Boolean,
    val isKnown: Boolean,
    val exactKnownExists: Boolean,
    val exactPrimaryCaseExists: Boolean,
    val isRejected: Boolean,
    val topScore: Double,
    val runnerUpScore: Double?,
    val runnerUpIsCurrentWordCandidate: Boolean = true,
)

/**
 * The automatic correction decision itself: every gate in autocorrect-suggestions.md SS9,
 * combined. This is the module's central invariant (SS10): a known word can only ever come back
 * as [AutocorrectOutcome.Commit] through the known-word gate's own case/orthographic exceptions,
 * never a plain fuzzy replacement.
 */
object AutocorrectDecision {

    fun evaluate(facts: AutocorrectCandidateFacts, maxAutoReplaceDistance: Int, lengthChangeAllowance: Int): AutocorrectOutcome {
        with(facts) {
            if (!KnownWordGate.passes(isKnown, isCaseVariant, isOrthographicVariant, exactPrimaryCaseExists, exactKnownExists)) {
                return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.KNOWN_WORD)
            }
            if (isRejected) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.REJECTED_BY_USER)

            val confidence = Confidence.compute(topScore, runnerUpScore, runnerUpIsCurrentWordCandidate)
            if (confidence < Confidence.THRESHOLD) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.TOO_CLOSE_TO_CALL)

            if (!isCurrentWordCandidate) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.NOT_CURRENT_WORD)
            if (distance > maxAutoReplaceDistance) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.DISTANCE_TOO_HIGH)
            val safe = SafeShape.evaluate(
                word, candidate, distance, isCurrentWordCandidate, isOrthographicVariant, isCaseVariant,
                maxAutoReplaceDistance, lengthChangeAllowance,
            )
            if (!safe) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.UNSAFE_SHAPE)

            val minimumLength = if (isOrthographicVariant) 2 else 3
            if (word.length < minimumLength) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.WORD_TOO_SHORT)

            val maxCandidateLength = kotlin.math.floor(word.length * 1.25).toInt()
            val isDoubledLetterCandidate = SafeShape.extraLetterDoublesNeighbour(word, candidate)
            if (candidate.length > maxCandidateLength && !isDoubledLetterCandidate) {
                return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.CANDIDATE_TOO_LONG)
            }

            val recased = CasingRules.forTypedWord(word, candidate)
            return if (recased == word) AutocorrectOutcome.SameReplacement else AutocorrectOutcome.Commit(recased)
        }
    }
}
