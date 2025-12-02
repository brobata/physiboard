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

        // SymSpell lookup on normalized input
        val symResultsPrimary = repository.symSpellLookup(normalizedWord, maxSuggestions = limit * 8)
        val symResultsAccent = if (includeAccentMatching) {
            val normalizedAccentless = stripAccents(normalizedWord)
            if (normalizedAccentless != normalizedWord) {
                repository.symSpellLookup(normalizedAccentless, maxSuggestions = limit * 4)
            } else emptyList()
        } else emptyList()

        val allSymResults = (symResultsPrimary + symResultsAccent)
        // Force prefix completions: take frequent words that start with the input (distance 0)
        val completions = repository.lookupByPrefixMerged(normalizedWord, maxSize = 120)
            .filter {
                val norm = normalizeCached(it.word)
                norm.startsWith(normalizedWord) && it.word.length > currentWord.length
            }

        val seen = HashSet<String>(limit * 3)
        val top = ArrayList<SuggestionResult>(limit)
        val inputLen = normalizedWord.length
        val comparator = Comparator<SuggestionResult> { a, b ->
            val d = a.distance.compareTo(b.distance)
            if (d != 0) return@Comparator d
            val scoreCmp = b.score.compareTo(a.score)
            if (scoreCmp != 0) return@Comparator scoreCmp
            a.candidate.length.compareTo(b.candidate.length)
        }

        fun consider(term: String, distance: Int, frequency: Int, isForcedPrefix: Boolean = false) {
            // For very short inputs, avoid suggesting single-char tokens unless exact
            if (inputLen <= 2 && term.length == 1 && term != normalizedWord) return
            if (inputLen <= 2 && distance > 1) return

            val entry = repository.bestEntryForNormalized(term) ?: DictionaryEntry(term, frequency, SuggestionSource.MAIN)
            val isPrefix = entry.word.startsWith(currentWord, ignoreCase = true)
            val distanceScore = 1.0 / (1 + distance)
            val isCompletion = isPrefix && entry.word.length > currentWord.length
            val prefixBonus = when {
                isForcedPrefix -> 1.5
                isCompletion -> 1.2
                isPrefix -> 0.8
                else -> 0.0
            }
            val frequencyScore = (entry.frequency / 2_000.0)
            val sourceBoost = if (entry.source == SuggestionSource.USER) 5.0 else 1.0
            val score = (distanceScore + frequencyScore + prefixBonus) * sourceBoost
            val key = entry.word.lowercase(locale)
            if (!seen.add(key)) return
            val suggestion = SuggestionResult(
                candidate = entry.word,
                distance = distance,
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

        // Consider completions first to surface them even if SymSpell returns other close words
        for (entry in completions) {
            val norm = normalizeCached(entry.word)
            consider(norm, 0, entry.frequency, isForcedPrefix = true)
        }

        for (item in allSymResults) {
            consider(item.term, item.distance, item.frequency)
        }

        return top
    }

    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
        // Optimal String Alignment distance (Damerau-Levenshtein with adjacent transpositions cost=1)
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return -1
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var minRow = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    prev[j] + 1,      // deletion
                    curr[j - 1] + 1,  // insertion
                    prev[j - 1] + cost // substitution
                )

                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    // adjacent transposition
                    value = min(value, prev[j - 2] + 1)
                }

                curr[j] = value
                minRow = min(minRow, value)
            }

            if (minRow > maxDistance) return -1
            // swap arrays
            for (k in 0..b.length) {
                val tmp = prev[k]
                prev[k] = curr[k]
                curr[k] = tmp
            }
        }
        return if (prev[b.length] <= maxDistance) prev[b.length] else -1
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
