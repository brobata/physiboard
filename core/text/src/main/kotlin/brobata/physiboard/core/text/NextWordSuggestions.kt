package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.NgramStore
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.dict.WordSource

/**
 * The starter word list: what fills the strip when there are no learned bigrams (yet) for the
 * current context. spec: autocorrect-suggestions.md SS4, "Starter words": "the top 48 entries by
 * effective frequency across the primary dictionary (personal words first, then frequency, then
 * length), excluding one-letter words, default user words and words containing anything but
 * letters and apostrophes."
 *
 * // SPEC GAP: "the primary dictionary's starters get the 0.35 merge bonus over additional
 * // dictionaries" describes merging starters across more than one active dictionary, the same
 * // 0.35 bonus autocorrect-suggestions.md SS2 uses for current-word suggestions; no multi-
 * // dictionary merge exists anywhere in 3.0 yet (SS2's own merge is unbuilt too), so this reads
 * // the primary dictionary only. A caller with more than one loaded dictionary should merge
 * // their starter lists itself once that merge exists, exactly as SS2 will need to.
 */
object StarterWords {
    private const val LIMIT = 48

    /** [primary] is the current subtype's primary dictionary, or null while it is still loading (spec SS2 point 3: an unready dictionary is simply absent). */
    fun of(primary: DictionaryIndex?, userWords: UserWordStore, limit: Int = LIMIT): List<String> {
        val personal = userWords.personalWords()
            .filter { isEligibleSpelling(it.word) }
            .sortedWith(compareByDescending<PersonalWord> { it.frequency }.thenBy { it.word.length })
            .map { it.word }
        val dictionaryWords = if (primary == null) {
            emptyList()
        } else {
            val entries = mutableListOf<WordFrequency>()
            primary.topByFrequency(limit, entries)
            entries
                .filter { isEligibleSpelling(it.word) && userWords.sourceOf(it.word) != WordSource.DEFAULT_USER }
                .sortedWith(compareByDescending<WordFrequency> { it.frequency }.thenBy { it.word.length })
                .map { it.word }
        }
        return (personal + dictionaryWords).distinctBy { it.lowercase() }.take(limit)
    }

    /** spec: SS4, excludes "one-letter words ... and words containing anything but letters and apostrophes." */
    private fun isEligibleSpelling(word: String): Boolean =
        word.length > 1 && word.all { it.isLetter() || WordChars.isApostrophe(it) }
}

/**
 * Next-word predictions for the strip after a soft boundary or when the cursor sits on empty
 * space. spec: autocorrect-suggestions.md SS4: learned bigrams first (by count, then recency,
 * already [NgramStore.predict]'s own order), then starter words filling any remaining slots,
 * skipping a starter that duplicates a prediction already shown.
 */
object NextWordSuggestions {
    fun of(
        ngramStore: NgramStore,
        locale: String,
        prefixKey: String,
        primary: DictionaryIndex?,
        userWords: UserWordStore,
        limit: Int = 3,
    ): List<String> {
        val predicted = ngramStore.predict(locale, prefixKey, limit).map { it.nextWord }
        if (predicted.size >= limit) return predicted.take(limit)
        val starters = StarterWords.of(primary, userWords)
        val filler = starters.filterNot { starter -> predicted.any { it.equals(starter, ignoreCase = true) } }
        return (predicted + filler).take(limit)
    }
}
