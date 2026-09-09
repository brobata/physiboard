package brobata.physiboard.core.suggestions.eval

import brobata.physiboard.core.suggestions.DictionaryEntry
import brobata.physiboard.core.suggestions.DictionaryRepository
import brobata.physiboard.core.suggestions.SuggestionSource
import brobata.physiboard.core.suggestions.SymSpell
import brobata.physiboard.core.suggestions.WordNormalization
import java.util.Locale
import kotlin.math.pow

/**
 * A dictionary for the evaluation harness, backed by the REAL [SymSpell].
 *
 * [brobata.physiboard.core.suggestions.FakeDictionaryRepository] exists for unit tests and is
 * the wrong tool here: its `symSpellLookup` brute-force scans every entry with plain
 * Levenshtein, so it has no transpositions and none of SymSpell's delete-neighbourhood
 * behaviour or its `distance, frequency, length` ordering. Measuring correction quality
 * against a different retriever than the one that ships would report numbers about software
 * nobody runs.
 *
 * The vocabulary is small and hand-picked rather than the real 50k `en_base.dict`, which is a
 * deliberate limit: this measures the DECISION - retrieval, ranking, the shape gates and the
 * commit rule - with dictionary coverage held constant. It says nothing about whether a word
 * is in the shipped dictionary or whether its Leipzig frequency is right. Those are W7.
 */
class EvalDictionaryRepository(
    vocabulary: List<Pair<String, Int>>,
    private val locale: Locale = Locale.ENGLISH
) : DictionaryRepository {

    override var isReady: Boolean = true
    override var isLoadStarted: Boolean = true

    private val entries: List<DictionaryEntry> =
        vocabulary.map { (word, frequency) -> DictionaryEntry(word, frequency, SuggestionSource.MAIN) }

    private val byNormalized: Map<String, List<DictionaryEntry>> =
        entries.groupBy { normalize(it.word) }

    private val symSpell = SymSpell(maxEditDistance = 2, prefixLength = 7).apply {
        entries.forEach { addWord(normalize(it.word), effectiveFrequency(it)) }
    }

    override suspend fun loadIfNeeded() = Unit

    override suspend fun refreshUserEntries() = Unit

    override fun addUserEntryQuick(word: String) = Unit

    override fun removeUserEntry(word: String) = Unit

    override fun markUsed(word: String) = Unit

    /** The shipped curve, copied deliberately: `(raw/255)^0.75 * 1600`, floored at 1. */
    override fun effectiveFrequency(entry: DictionaryEntry): Int =
        ((entry.frequency.coerceIn(0, 255) / 255.0).pow(0.75) * 1600.0).toInt().coerceAtLeast(1)

    override fun getExactWordFrequency(word: String): Int =
        entries.filter { it.word == word }.maxOfOrNull { it.frequency } ?: 0

    override fun lookupByPrefixMerged(prefix: String, maxSize: Int): List<DictionaryEntry> {
        val normalizedPrefix = normalize(prefix)
        return entries
            .filter { normalize(it.word).startsWith(normalizedPrefix) }
            .sortedByDescending { effectiveFrequency(it) }
            .take(maxSize)
    }

    override fun symSpellLookup(term: String, maxSuggestions: Int): List<SymSpell.SuggestItem> =
        symSpell.lookup(normalize(term), maxSuggestions)

    override fun bestEntryForNormalized(normalized: String): DictionaryEntry? =
        byNormalized[normalized]?.maxByOrNull { effectiveFrequency(it) }

    override fun topByNormalized(normalized: String, limit: Int): List<DictionaryEntry> =
        byNormalized[normalized].orEmpty().sortedByDescending { effectiveFrequency(it) }.take(limit)

    override fun topCommonEntries(limit: Int): List<DictionaryEntry> =
        entries.sortedByDescending { effectiveFrequency(it) }.take(limit)

    override fun isKnownWord(word: String): Boolean = byNormalized.containsKey(normalize(word))

    override fun ensureLoadScheduled(background: () -> Unit) = Unit

    private fun normalize(word: String): String =
        WordNormalization.normalizeForDictionary(word, locale)
}
