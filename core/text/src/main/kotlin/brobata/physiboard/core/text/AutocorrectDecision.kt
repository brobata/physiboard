package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictNormalization

/**
 * The relative-margin confidence between the top candidate and the runner-up. spec: autocorrect-
 * suggestions.md SS9.
 */
object Confidence {
    /** spec: SS9, "The threshold is 0.02; there is no preference for it." */
    const val THRESHOLD: Double = 0.02

    /**
     * spec: SS9, "Edit reach" (2026-10-23): a candidate two edits away needs a clear margin, not
     * the 0.02 that serves one edit. 0.10 is the value SS12's measured history records for the old
     * list ("threshold 0.10: fcr 0.014, one real word overruled"), the one threshold that was
     * ever measured above 0.02.
     */
    const val THRESHOLD_DISTANCE_2: Double = 0.10

    /** The margin a candidate at [distance] needs. */
    fun thresholdFor(distance: Int): Double = if (distance >= 2) THRESHOLD_DISTANCE_2 else THRESHOLD

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
 * 1, every other language 0 per SS9 step 8 and the settings table; a doubled letter is allowed
 * past it by step 7).
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
        if (lengthDiff in 1..2 && extraLettersDoubleNeighbours(word, candidate)) return true
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

    /**
     * Whether [candidate], one or two characters longer than [word], is [word] with letters
     * doubled (`wil` -> `will`, `acomodate` -> `accommodate`): every extra letter equals the letter
     * beside it in the candidate. spec: SS9 step 7, SS "Length ratio" gate. A missing half of a
     * double is the one slip allowed past the length allowance, since it is the one a thumb makes
     * twice in a word without the word changing.
     */
    internal fun extraLettersDoubleNeighbours(word: String, candidate: String): Boolean {
        if (candidate.length - word.length !in 1..2) return false
        var i = 0
        var j = 0
        while (j < candidate.length) {
            if (i < word.length && word[i] == candidate[j]) {
                i++
                j++
                continue
            }
            val extra = candidate[j]
            if (extra != candidate.getOrNull(j - 1) && extra != candidate.getOrNull(j + 1)) return false
            j++
        }
        return i == word.length
    }

    private fun isPureAffixChange(word: String, candidate: String): Boolean {
        val (shorter, longer) = if (word.length <= candidate.length) word to candidate else candidate to word
        return longer.startsWith(shorter) || longer.endsWith(shorter)
    }
}

/**
 * How far a correction may reach, by the word's length and the kind of slip. spec: SS9, "Edit
 * reach" (2026-10-23, after "the fix is well beyond the word I am going for"). Two edits are a
 * pair of thumb slips in a long word, or nothing: a short word loses too much of itself to two
 * edits, and two edits that are not thumb slips (a far key, a changed first letter, a vowel for a
 * vowel) are a different word, not a slip. Both paths apply the same numbers: the context path
 * through [ContextCorrection.Tuning]'s defaults, the path without a table through
 * [AutocorrectDecision]. The sentence harness (SS12.1) chose them; the plan has the sweep.
 */
object EditReach {
    /** Letters a word needs before a candidate two edits away is considered at all. */
    const val DISTANCE_2_MIN_LETTERS: Int = 7

    /**
     * The most a two-edit slip may cost ([ChannelCost]): 1.0 admits any two thumb slips (adjacent
     * key 0.35, transposition 0.4, doubled or half-dropped letter 0.3, extra key beside one
     * pressed 0.45, dropped letter 0.5; a dropped letter and a stray key beside one pressed is
     * 0.95) and refuses a far key (1.6), a changed first letter (+0.8), a vowel for a vowel (0.9)
     * or an extra letter beside nothing (0.9) paired with anything. The harness could not tell
     * 0.9 from 1.0 once the sentence's agreement is required. (Two plain dropped letters also
     * price at 1.0 but change the length by two, which the allowance stops first; two missing
     * halves of doubles, 0.6, pass through step 7.)
     */
    const val DISTANCE_2_MAX_COST: Double = 1.0

    /** The largest distance a word of [letters] letters reaches, under the setting's own cap. */
    fun maxDistance(letters: Int, settingCap: Int): Int = minOf(if (letters < DISTANCE_2_MIN_LETTERS) 1 else 2, settingCap)

    /**
     * Whether the slip from [candidate] to [typed] is cheap enough to be two thumb slips. The
     * geometry knows the Titan's QWERTY letters only ([TitanKeyGeometry]); a word with a letter
     * off that grid (Cyrillic, Greek) is not judged by it and passes, leaving the length and
     * margin gates to do the work. On QWERTZ or AZERTY the moved letters are priced where QWERTY
     * has them, the known limit docs/plans/autocorrect-context.md records.
     */
    fun isThumbSlipPair(typed: String, candidate: String): Boolean {
        if (!onGrid(typed) || !onGrid(candidate)) return true
        return ChannelCost.cost(typed, candidate) <= DISTANCE_2_MAX_COST
    }

    private fun onGrid(word: String): Boolean = DictNormalization.stripAccents(word.lowercase()).all { !it.isLetter() || it in 'a'..'z' }
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
            if (confidence < Confidence.thresholdFor(distance)) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.TOO_CLOSE_TO_CALL)

            if (!isCurrentWordCandidate) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.NOT_CURRENT_WORD)
            if (distance > maxAutoReplaceDistance) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.DISTANCE_TOO_HIGH)
            // spec: SS9, "Edit reach": two edits only in a word long enough, and only as a pair of thumb slips.
            val letters = word.count { it.isLetter() }
            if (distance > EditReach.maxDistance(letters, maxAutoReplaceDistance) || (distance >= 2 && !EditReach.isThumbSlipPair(word, candidate))) {
                return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.DISTANCE_TOO_HIGH)
            }
            val safe = SafeShape.evaluate(
                word, candidate, distance, isCurrentWordCandidate, isOrthographicVariant, isCaseVariant,
                maxAutoReplaceDistance, lengthChangeAllowance,
            )
            if (!safe) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.UNSAFE_SHAPE)

            val minimumLength = if (isOrthographicVariant) 2 else 3
            if (word.length < minimumLength) return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.WORD_TOO_SHORT)

            val maxCandidateLength = kotlin.math.floor(word.length * 1.25).toInt()
            val isDoubledLetterCandidate = SafeShape.extraLettersDoubleNeighbours(word, candidate)
            if (candidate.length > maxCandidateLength && !isDoubledLetterCandidate) {
                return AutocorrectOutcome.Refuse(AutocorrectRefusalReason.CANDIDATE_TOO_LONG)
            }

            val recased = CasingRules.forTypedWord(word, candidate)
            return if (recased == word) AutocorrectOutcome.SameReplacement else AutocorrectOutcome.Commit(recased)
        }
    }
}
