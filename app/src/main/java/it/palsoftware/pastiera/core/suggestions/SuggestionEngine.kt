package it.palsoftware.pastiera.core.suggestions

import kotlin.math.min
import java.text.Normalizer
import java.util.Locale
import android.util.Log

data class SuggestionResult(
    val candidate: String,
    val distance: Int,
    val score: Double,
    val source: SuggestionSource
)

class SuggestionEngine(
    private val repository: DictionaryRepository,
    private val locale: Locale = Locale.ITALIAN,
    private val debugLogging: Boolean = false
) {

    // Keep only Unicode letters (supports Latin, Cyrillic, Greek, Arabic, Chinese, etc.)
    // Removes: punctuation, numbers, spaces, emoji, symbols
    private val normalizeRegex = "[^\\p{L}]".toRegex()
    private val accentCache: MutableMap<String, String> = mutableMapOf()
    private val tag = "SuggestionEngine"
    private val wordNormalizeCache: MutableMap<String, String> = mutableMapOf()
    // Limits to avoid processing thousands of entries on short prefixes
    private val maxCandidatesByPrefixLength = mapOf(
        1 to 600,
        2 to 300
    )

    fun suggest(
        currentWord: String,
        limit: Int = 3,
        includeAccentMatching: Boolean = true
    ): List<SuggestionResult> {
        if (currentWord.isBlank()) return emptyList()
        if (!repository.isReady) return emptyList()
        val normalizedWord = normalize(currentWord)
        // Require at least 1 character to start suggesting.
        if (normalizedWord.length < 1) return emptyList()

        // Always pull the dedicated prefix bucket; it is already ordered by frequency.
        val candidates: List<DictionaryEntry> = repository.lookupByPrefix(normalizedWord)
        val maxCandidates = maxCandidatesByPrefixLength[normalizedWord.length]
        val limitedCandidates = if (maxCandidates != null && candidates.size > maxCandidates) {
            candidates.take(maxCandidates)
        } else {
            candidates
        }

        if (debugLogging) {
            Log.d(tag, "suggest '$currentWord' normalized='$normalizedWord' candidates=${limitedCandidates.size}")
        }

        if (debugLogging) {
            Log.d(tag, "suggest '$currentWord' normalized='$normalizedWord' candidates=${limitedCandidates.size}")
        }

        val top = ArrayList<SuggestionResult>(limit)
        val comparator = compareBy<SuggestionResult> { it.distance }
            .thenByDescending { it.score }
            .thenBy { it.candidate.length }
        for (entry in limitedCandidates) {
            val normalizedCandidate = normalizeCached(entry.word)
            val distance = if (normalizedCandidate.startsWith(normalizedWord)) {
                0 // treat prefix match as perfect to surface completions early
            } else {
                boundedLevenshtein(normalizedWord, normalizedCandidate, 2)
            }
            if (distance < 0) continue

            val accentDistance = if (includeAccentMatching) {
                val normalizedNoAccent = stripAccents(normalizedCandidate)
                if (normalizedNoAccent.startsWith(normalizedWord)) {
                    0
                } else {
                    boundedLevenshtein(normalizedWord, normalizedNoAccent, 2)
                }
            } else distance

            val effectiveDistance = min(distance, accentDistance)
            val distanceScore = 1.0 / (1 + effectiveDistance)
            val frequencyScore = entry.frequency / 10_000.0
            val sourceBoost = if (entry.source == SuggestionSource.USER) 2.0 else 1.0
            val score = (distanceScore + frequencyScore) * sourceBoost
            val suggestion = SuggestionResult(
                candidate = entry.word,
                distance = effectiveDistance,
                score = score,
                source = entry.source
            )

            if (top.size < limit) {
                top.add(suggestion)
                top.sortWith(comparator)
            } else if (comparator.compare(suggestion, top.last()) < 0) {
                top.add(suggestion)
                top.sortWith(comparator)
                while (top.size > limit) top.removeAt(top.lastIndex)
            }
        }

        return top
    }

    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return -1
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            var minRow = dp[0]
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
                minRow = min(minRow, dp[j])
            }
            if (minRow > maxDistance) return -1
        }
        return if (dp[b.length] <= maxDistance) dp[b.length] else -1
    }

    private fun normalize(word: String): String {
        return stripAccents(word.lowercase(locale))
            .replace(normalizeRegex, "")
    }

    private fun normalizeCached(word: String): String {
        return wordNormalizeCache.getOrPut(word) { normalize(word) }
    }

    private fun stripAccents(input: String): String {
        return accentCache.getOrPut(input) {
            Normalizer.normalize(input, Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
        }
    }
}
