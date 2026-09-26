package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictNormalization
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.ScoredCandidate
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** Where a candidate came from, driving both its prefix bonus and the section 3.7 ordering tiers. spec: autocorrect-suggestions.md SS3.4, SS3.7. */
enum class CandidateSource { COMPLETION, FUZZY, ACCENT_STRIPPED_FUZZY }

/** One ranked suggestion. spec: autocorrect-suggestions.md SS3. */
data class RankedSuggestion(
    val word: String,
    val distance: Int,
    val source: CandidateSource,
    val isUserWord: Boolean,
    val score: Double,
)

/** The subset of `use_keyboard_proximity` / `use_edit_type_ranking` / `accent_matching_enabled` that ranking itself consults. */
data class RankingOptions(
    val useKeyboardProximity: Boolean = false,
    val accentMatchingEnabled: Boolean = true,
)

/**
 * A compact QWERTY key-adjacency grid, used only for the keyboard-proximity filter. spec:
 * autocorrect-suggestions.md SS3.5 ("a 3-row grid ... Distance is Euclidean over (row, column)").
 * AZERTY and QWERTZ grids are dropped for 3.0 (spec SS18: "Titan 2 Elite ships QWERTY; other
 * layouts are software remaps"), so this is the only grid this module carries.
 */
object QwertyGrid {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val positions: Map<Char, Pair<Int, Int>> = buildMap {
        for ((row, letters) in rows.withIndex()) {
            for ((col, letter) in letters.withIndex()) put(letter, row to col)
        }
    }

    /** Euclidean key distance, or null when either character is off the grid (no constraint). */
    fun distance(a: Char, b: Char): Double? {
        val pa = positions[a.lowercaseChar()] ?: return null
        val pb = positions[b.lowercaseChar()] ?: return null
        val dRow = (pa.first - pb.first).toDouble()
        val dCol = (pa.second - pb.second).toDouble()
        return kotlin.math.sqrt(dRow * dRow + dCol * dCol)
    }
}

/**
 * Candidate retrieval and scoring for the current word. spec: autocorrect-suggestions.md SS3.
 *
 * // SPEC GAP: SS3.4's "accent-stripped fuzzy matches" describes a second fuzzy lookup on the
 * // word with its accents stripped, gated on `accent_matching_enabled`. `:core:dict`'s own key
 * // normalization ([brobata.physiboard.core.dict.DictNormalization.normalizedKey]) already
 * // strips accents unconditionally before every lookup, so a query's accents are already gone
 * // by the time [DictionaryIndex.neighbours] runs the first (and only) fuzzy pass; a second pass
 * // on an already-unaccented key would search the identical key space. This ranking therefore
 * // does not run a distinct second pass. Instead, [RankingOptions.accentMatchingEnabled] gates
 * // the one place accent tolerance actually shows up given that architecture: since every fuzzy
 * // and completion lookup already runs on an accent-stripped key, a candidate that differs from
 * // the typed word only by accents, case, or ligature folding (an "orthographic variant", spec
 * // SS9's own term for the same relationship) is only surfaced when the setting is on; turning
 * // it off restores accent sensitivity by filtering those candidates back out. SS3.4's
 * // single-letter elisions and single-letter variant sources (N = 1) are likewise not
 * // implemented: they are a handful of
 * // narrow-width special cases with no test in scope's mandate, and are left as a follow-up
 * // rather than guessed at.
 */
object SuggestionRanking {

    /**
     * Ranks candidates for [typedWord] using [dictionaries] (primary first) and [userWords],
     * returning at most [limit] results ordered per SS3.7. [proximityLayout], when supplied, is
     * consulted only when [options].useKeyboardProximity is on.
     */
    fun suggest(
        typedWord: String,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        options: RankingOptions,
        limit: Int = 3,
    ): List<RankedSuggestion> {
        val normalized = DictNormalization.normalizedKey(typedWord)
        if (normalized.isEmpty()) return emptyList()
        val n = normalized.length

        val results = LinkedHashMap<String, RankedSuggestion>()

        for (dict in dictionaries) {
            val completions = mutableListOf<WordFrequency>()
            dict.prefixLookup(normalized, limit = 200, into = completions)
            for (entry in completions) {
                if (entry.word.length <= typedWord.length) continue
                if (!passesCompletionFilters(typedWord, entry, n, userWords)) continue
                addOrKeepBetter(results, score(typedWord, entry.word, 0, entry.frequency, CandidateSource.COMPLETION, userWords, n, options))
            }

            if (n >= 2) {
                val fuzzyLimit = if (n <= 3) 2 * limit else 4 * limit
                val fuzzy = mutableListOf<ScoredCandidate>()
                dict.neighbours(normalized, maxDistance = 2, limit = fuzzyLimit, into = fuzzy)
                for (candidate in fuzzy) {
                    if (!passesGeneralFilters(typedWord, candidate.word, candidate.distance, n, options)) continue
                    addOrKeepBetter(
                        results,
                        score(typedWord, candidate.word, candidate.distance, candidate.frequency, CandidateSource.FUZZY, userWords, n, options),
                    )
                }
            }
        }

        // spec dictionaries-languages.md SS7: "Both lists are merged into every loaded
        // dictionary... a personal word can never be filtered out of suggestions." A personal or
        // default word need not appear in any loaded [DictionaryIndex] (it is added from the strip
        // or shipped as `common/dictionaries/user_defaults.json`, independent of the trie a
        // dictionary install builds), so it needs its own completion/fuzzy pass here, mirroring
        // [StarterWords.of]'s own fold of `userWords.personalWords()` into its candidate list.
        val userWordEntries = userWords.personalWords().map { WordFrequency(it.word, it.frequency) } + userWords.defaultWords()
        for (entry in userWordEntries) {
            val key = DictNormalization.normalizedKey(entry.word)
            if (key.isEmpty()) continue
            if (entry.word.length > typedWord.length && key.startsWith(normalized) && passesCompletionFilters(typedWord, entry, n, userWords)) {
                addOrKeepBetter(results, score(typedWord, entry.word, 0, entry.frequency, CandidateSource.COMPLETION, userWords, n, options))
            }
            if (n >= 2) {
                val distance = boundedEditDistance(normalized, key, maxDistance = 2) ?: continue
                if (!passesGeneralFilters(typedWord, entry.word, distance, n, options)) continue
                addOrKeepBetter(results, score(typedWord, entry.word, distance, entry.frequency, CandidateSource.FUZZY, userWords, n, options))
            }
        }

        return results.values
            .filterNot { it.word.equals(typedWord, ignoreCase = false) }
            .sortedWith(
                compareByDescending<RankedSuggestion> { it.isUserWord }
                    .thenBy { it.distance }
                    .thenByDescending { it.score }
                    .thenBy { it.word.length },
            )
            .distinctBy { it.word.lowercase() }
            .take(limit)
    }

    /**
     * Plain Levenshtein distance between two normalized keys, capped at [maxDistance] (null once
     * exceeded): the small personal/default-word lists have no trie of their own to walk the way
     * [DictionaryIndex.neighbours] does, so this is a direct DP over the handful of candidates
     * instead.
     */
    private fun boundedEditDistance(a: String, b: String, maxDistance: Int): Int? {
        if (abs(a.length - b.length) > maxDistance) return null
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                rowMin = minOf(rowMin, curr[j])
            }
            if (rowMin > maxDistance) return null
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length].takeIf { it <= maxDistance }
    }

    private fun addOrKeepBetter(into: MutableMap<String, RankedSuggestion>, candidate: RankedSuggestion) {
        val key = candidate.word.lowercase()
        val existing = into[key]
        if (existing == null || candidate.score > existing.score) into[key] = candidate
    }

    /** spec: SS3.5, "Rare completion" and "Proper-noun completion" filters, applied only to completion-source candidates. */
    private fun passesCompletionFilters(typedWord: String, entry: WordFrequency, n: Int, userWords: UserWordStore): Boolean {
        val isUser = userWords.isKnown(entry.word)
        val rareThreshold = when {
            n <= 2 -> 150
            n == 3 -> 100
            n == 4 -> 80
            else -> 60
        }
        if (!isUser && entry.frequency < rareThreshold) return false
        val typedStartsLower = typedWord.firstOrNull { it.isLetter() }?.isLowerCase() == true
        val candidateStartsUpper = entry.word.firstOrNull()?.isUpperCase() == true
        if (typedStartsLower && candidateStartsUpper && !isUser) return false
        return true
    }

    private fun passesGeneralFilters(typedWord: String, candidate: String, distance: Int, n: Int, options: RankingOptions): Boolean {
        if (candidate == typedWord) return false
        if (n <= 2 && candidate.length == 1 && !candidate.equals(typedWord, ignoreCase = true)) return false
        if (n <= 2 && distance > 1) return false
        if (!options.accentMatchingEnabled && isOrthographicVariant(typedWord, candidate)) return false
        if (options.useKeyboardProximity && distance > 0 && candidate.length == typedWord.length && isDistantSubstitution(typedWord, candidate)) {
            return false
        }
        return true
    }

    /**
     * Whether [candidate] differs from [typedWord] only by accent, case, or ligature folding
     * (spec SS9's "orthographic variant"): same dictionary key, different spelling. spec: SS3.4's
     * `accent_matching_enabled` gate, realised here since this module's fuzzy lookup already runs
     * on an accent-stripped key (see the SPEC GAP note above [SuggestionRanking]).
     */
    private fun isOrthographicVariant(typedWord: String, candidate: String): Boolean =
        candidate != typedWord && DictNormalization.normalizedKey(typedWord) == DictNormalization.normalizedKey(candidate)

    /** spec: SS3.5, "Distant substitution": same length, not a transposition, some differing pair more than 2.5 key-widths apart. */
    /**
     * spec: autocorrect-suggestions.md SS3.6 "Edit type": a dropped letter (+0.5) outranks a
     * substitution (+0.4 when every changed key is adjacent or the two are transposed, else
     * +0.2), which outranks an extra letter (+0.3 when the typed word doubles a letter the
     * candidate does not, +0.1 when it doubles any letter, else 0). The spec gates this on
     * `use_edit_type_ranking`, a setting 3.0 dropped (settings-catalog Keep/Drop); it is applied
     * unconditionally here because on a physical keyboard a missed key is the common typo, and
     * without it the same-length bonus alone turned "postr" into "posts" rather than "poster"
     * (Titan, 2026-09-25). Decision to confirm with the maintainer; noted in the plan.
     */
    internal fun editTypeTerm(typedWord: String, candidateWord: String): Double = when (candidateWord.length - typedWord.length) {
        1 -> 0.5
        0 -> if (isTransposition(typedWord, candidateWord) || changedKeysAllAdjacent(typedWord, candidateWord)) 0.4 else 0.2
        -1 -> {
            val doubled = doubledPairCount(typedWord)
            when {
                doubled > doubledPairCount(candidateWord) -> 0.3
                doubled > 0 -> 0.1
                else -> 0.0
            }
        }
        else -> 0.0
    }

    private fun changedKeysAllAdjacent(word: String, candidate: String): Boolean {
        if (word.length != candidate.length) return false
        var changed = 0
        for (i in word.indices) {
            if (word[i] == candidate[i]) continue
            changed++
            val d = QwertyGrid.distance(word[i], candidate[i]) ?: return false
            if (d > ADJACENT_KEY_DISTANCE) return false
        }
        return changed > 0
    }

    /** Adjacent equal letters, counted per position, so "helllo" holds two and "hello" one: the candidate broke a double. */
    private fun doubledPairCount(word: String): Int = (1 until word.length).count { word[it] == word[it - 1] }

    private const val ADJACENT_KEY_DISTANCE = 1.15

    private fun isDistantSubstitution(word: String, candidate: String): Boolean {
        if (isTransposition(word, candidate)) return false
        for (i in word.indices) {
            if (word[i] == candidate[i]) continue
            val d = QwertyGrid.distance(word[i], candidate[i]) ?: continue
            if (d > 2.5) return true
        }
        return false
    }

    private fun isTransposition(word: String, candidate: String): Boolean {
        if (word.length != candidate.length) return false
        val diffs = word.indices.filter { word[it] != candidate[it] }
        return diffs.size == 2 && word[diffs[0]] == candidate[diffs[1]] && word[diffs[1]] == candidate[diffs[0]]
    }

    /**
     * spec: SS3.2, raw 0..255 to effective frequency `(raw/255)^0.75 * 1600`, floored at 1. A
     * negative raw value (a corrupt or malformed dictionary/user-word entry) is clamped to 0
     * before the `pow` call rather than left to propagate NaN, matching this module's other
     * malformed-input guards.
     */
    fun effectiveFrequency(raw: Int): Double = maxOf(1.0, (raw.coerceAtLeast(0) / 255.0).pow(0.75) * 1600.0)

    private fun score(
        typedWord: String,
        candidateWord: String,
        distance: Int,
        rawFrequency: Int,
        source: CandidateSource,
        userWords: UserWordStore,
        n: Int,
        options: RankingOptions,
    ): RankedSuggestion {
        val isUser = userWords.isKnown(candidateWord)
        val effective = if (isUser) effectiveFrequency(maxOf(1, userWords.frequencyOf(candidateWord))) else effectiveFrequency(rawFrequency)

        var total = 0.0
        total += 1.0 / (1 + distance)
        total += effective / 1600.0

        val isCompletion = source == CandidateSource.COMPLETION
        if (isCompletion) total += if (n <= 2) 1.8 else 4.0

        val lengthDiff = abs(candidateWord.length - typedWord.length)
        total += when (lengthDiff) {
            0 -> 0.35
            1 -> 0.2
            2 -> 0.05
            else -> -0.15 * minOf(lengthDiff, 4)
        }

        if (distance > 0) total += editTypeTerm(typedWord, candidateWord)

        if (candidateWord.any { it.isDigit() }) total -= if (n <= 2) 3.0 else 1.5
        val hasSymbol = candidateWord.any { !it.isLetterOrDigit() && !WordChars.isApostrophe(it) }
        if (hasSymbol) total -= if (n <= 2) 1.2 else 0.6

        if (isUser) total *= 5.0

        return RankedSuggestion(candidateWord, distance, source, isUser, total)
    }
}

/**
 * The apostrophe-split branch: when [typedWord] has exactly one apostrophe, not at either end,
 * with a short/allowed prefix and a real root, suggestions are computed for the root alone and
 * recomposed with the user's prefix. spec: autocorrect-suggestions.md SS3.3.
 */
object ApostropheSplit {
    private val ALLOWED_LONG_PREFIXES = setOf("dall", "dell", "nell", "sull", "coll", "quell", "quest")

    data class Split(val prefix: String, val root: String)

    fun split(typedWord: String): Split? {
        val straightened = buildString(typedWord.length) { for (c in typedWord) append(WordChars.straighten(c)) }
        val apostropheCount = straightened.count { it == '\'' }
        if (apostropheCount != 1) return null
        val index = straightened.indexOf('\'')
        if (index <= 0 || index >= straightened.length - 1) return null
        val prefix = straightened.substring(0, index)
        val root = straightened.substring(index + 1)
        if (!prefix.all { it.isLetter() }) return null
        if (!(prefix.length <= 3 || prefix.lowercase() in ALLOWED_LONG_PREFIXES)) return null
        if (root.length < 3 || !root.all { it.isLetter() }) return null
        return Split(prefix, root)
    }

    /**
     * Recomposes a candidate found for [split]'s root back into a full word with the user's
     * prefix: strips a matching prefix from the candidate first, drops a candidate that still
     * carries a different apostrophe, otherwise prepends the prefix as typed. spec: SS3.3 step 3.
     */
    fun recompose(split: Split, candidate: String): String? {
        if (candidate.startsWith(split.prefix, ignoreCase = true)) {
            val stripped = candidate.substring(split.prefix.length).trimStart('\'')
            return if ('\'' in stripped) null else split.prefix + "'" + stripped
        }
        if ('\'' in candidate) return null
        return split.prefix + "'" + candidate
    }
}
