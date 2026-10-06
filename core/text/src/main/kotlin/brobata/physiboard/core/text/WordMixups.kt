package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel

/**
 * The mix-up fix of the previous word: a real word typed for its twin (`its` for `it's`, `then`
 * for `than`), which §10's rule otherwise forbids ever touching because both spellings are
 * correct words. spec: autocorrect-suggestions.md §10 (the one narrow exception, behind
 * `fix_word_mixups`, off by default).
 *
 * Only the hand-curated sets in [sets] are ever acted on. Every set in [measured] is replayed by
 * the sentence harness (§12, corpus d); one is kept only with at least 10 measured flips, at least
 * 60% of them fixed, and not one correct word changed on the held-out clean text.
 * docs/plans/autocorrect-context.md has the numbers for each, kept and dropped, so nobody re-adds
 * a set without a new measurement.
 *
 * The judgement waits for the word after: when word N's boundary arrives, word N-1 is scored
 * from both sides, `P(N-1 | N-2) * P(N | N-1)`, for itself and each twin. A twin replaces it only
 * when it is at least [Tuning.minLogRatio] likelier overall (a strong ratio, not a lean), no
 * worse than [Tuning.maxSideLoss] on either side alone, and the table has actually seen it next
 * to one of its neighbours at least [Tuning.minEvidence] times (an unseen pair proves nothing).
 */
object WordMixups {

    /**
     * Every set the harness measures (docs/plans/autocorrect-context.md has the numbers for each).
     * Lowercase, straight apostrophes; each word is in at most one set.
     */
    val measured: List<Set<String>> = listOf(
        setOf("its", "it's"),
        setOf("your", "you're"),
        setOf("their", "there", "they're"),
        setOf("then", "than"),
        setOf("to", "too"),
        setOf("lose", "loose"),
        setOf("whose", "who's"),
        setOf("were", "we're", "where"),
        setOf("affect", "effect"),
        setOf("accept", "except"),
        setOf("of", "off"),
        setOf("weather", "whether"),
        setOf("quite", "quiet"),
        setOf("advice", "advise"),
        setOf("passed", "past"),
        setOf("here", "hear"),
        setOf("know", "no"),
        setOf("new", "knew"),
        setOf("principal", "principle"),
        setOf("desert", "dessert"),
        setOf("peace", "piece"),
        setOf("brake", "break"),
        setOf("lead", "led"),
        setOf("cant", "can't"),
        setOf("wont", "won't"),
        setOf("lets", "let's"),
        setOf("well", "we'll"),
        setOf("ill", "i'll"),
    )

    /** One member of each set that earned its place (the plan's table); declared before [sets], which reads it. */
    private val KEPT: Set<String> = setOf(
        "its", "your", "their", "then", "to", "lose", "whose", "were",
        "cant", "wont", "lets", "ill", "know", "new", "quite", "weather",
    )

    /** The sets the fix acts on: the measured ones that earned it. */
    val sets: List<Set<String>> = measured.filter { set -> KEPT.any { it in set } }

    data class Tuning(
        /** `ln` of how many times likelier the twin must be, both sides counted. */
        val minLogRatio: Double = 5.0,
        /** How many times the table must have seen the twin beside one of the two neighbours. */
        val minEvidence: Int = 5,
        /**
         * How much worse (in `ln`) the twin may fit on either side alone. Both sides must
         * broadly agree: "Was there money" fits "their money" on the right but "was there" on the
         * left, and a question like that is exactly where a one-sided reading goes wrong.
         */
        val maxSideLoss: Double = 2.0,
    )

    val DEFAULT_TUNING: Tuning = Tuning()

    private val setByWord: Map<String, Set<String>> = buildMap {
        for (set in measured) for (word in set) {
            require(put(word, set) == null) { "'$word' is in two confusion sets" }
        }
    }

    private val activeWords: Set<String> = sets.flatten().toSet()

    /** The measured set [word] (lowercase, straight apostrophes) belongs to, or null. */
    fun setOf(word: String): Set<String>? = setByWord[word]

    /**
     * The twin that should replace [previousKey] (word N-1, lowercase, straight apostrophes), or
     * null to leave it. [beforeId] is the context of N-1 itself ([ContextModel.SENTENCE_START], a
     * word id, or [ContextModel.NO_CONTEXT]); [nextKey] is word N as it now stands. [sets] lets the
     * harness measure a set that does not ship.
     */
    fun judge(
        previousKey: String,
        beforeId: Int,
        nextKey: String,
        model: ContextModel,
        tuning: Tuning = DEFAULT_TUNING,
        sets: Set<String> = activeWords,
    ): String? {
        if (previousKey !in sets) return null
        val twins = setByWord[previousKey] ?: return null
        val nextId = model.idOf(nextKey)
        if (nextId < 0) return null
        val previousId = model.idOf(previousKey)
        val typedLeft = model.logProbability(beforeId, previousId)
        val typedRight = model.logProbability(previousId, nextId)
        val asTyped = typedLeft + typedRight
        var best: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (twin in twins) {
            if (twin == previousKey) continue
            val twinId = model.idOf(twin)
            if (twinId < 0) continue
            val evidence = model.pairCount(beforeId, twinId) + model.pairCount(twinId, nextId)
            if (evidence < tuning.minEvidence) continue
            val left = model.logProbability(beforeId, twinId)
            val right = model.logProbability(twinId, nextId)
            if (left - typedLeft < -tuning.maxSideLoss || right - typedRight < -tuning.maxSideLoss) continue
            val s = left + right
            if (s > bestScore) {
                bestScore = s
                best = twin
            }
        }
        return best?.takeIf { bestScore - asTyped >= tuning.minLogRatio }
    }
}
