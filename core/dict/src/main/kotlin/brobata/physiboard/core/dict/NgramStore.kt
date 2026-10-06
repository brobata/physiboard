package brobata.physiboard.core.dict

/**
 * One row of the bigram table `user_ngrams.db`'s `bigrams` holds. spec: autocorrect-suggestions.md
 * SS4 ("locale, prefix, next word, count, last used"). [prefix] is the previous word's normalized
 * key ([NgramPrefix.of]), or [NgramStore.SENTENCE_START] for the "sentence start -> this word"
 * row. [nextWord] keeps its display form as typed; only the lookup key is normalized.
 */
data class Bigram(
    val locale: String,
    val prefix: String,
    val nextWord: String,
    val count: Int,
    val lastUsedMillis: Long,
)

/**
 * Normalizes a previous word into the bigram table's prefix key. spec: SS4, "the prefix key is
 * the previous word normalized (lowercase, ligatures folded, accents stripped, non-alphanumerics
 * removed)". This is [DictNormalization.normalizedKey]'s own recipe with one difference: a digit
 * survives the final filter here (SS4 says "non-alphanumerics", not "non-letters"), since a
 * dictionary key never needs a digit but a previous word such as "gpt4" might.
 */
object NgramPrefix {
    fun of(previousWord: String): String {
        val straightened = DictNormalization.straightenApostrophes(previousWord)
        val lowered = straightened.lowercase()
        val folded = DictNormalization.foldLigatures(lowered)
        val unaccented = DictNormalization.stripAccents(folded)
        return buildString(unaccented.length) {
            for (ch in unaccented) if (ch.isLetterOrDigit()) append(ch)
        }
    }
}

/**
 * The bigram store behind autocorrect-suggestions.md SS4's next-word predictions: an immutable,
 * pure model of `user_ngrams.db`'s `bigrams` table plus the "just-learned pair is visible
 * immediately through an in-memory overlay" rule, which this type satisfies for free by being the
 * one structure both a persisted load and a live learn write into (`:ime` decides when to persist
 * [rows] back; this module has no I/O). Every mutator returns a new store.
 *
 * `locale` and `prefix` together key a bucket; [prefix] is [SENTENCE_START] for the sentence-start
 * row SS4 also learns. Rows are keyed by (locale, prefix, next word's own key) so learning the
 * same pair again increments its count rather than duplicating the row (SS4 does not say this in
 * so many words, but "predictions are ordered by count descending" only makes sense if repeats
 * accumulate on one row rather than each becoming its own count-1 row).
 */
class NgramStore private constructor(private val rows: Map<RowKey, Bigram>) {

    private data class RowKey(val locale: String, val prefix: String, val nextWordKey: String)

    /**
     * Learns [nextWord] following [prefix] for [locale]: a new pair starts at count 1; an
     * existing one increments its count and refreshes [nowMillis] as its last-used time. spec:
     * SS4, "Every completed word is learned as 'previous word -> this word'".
     */
    fun learn(locale: String, prefix: String, nextWord: String, nowMillis: Long): NgramStore {
        if (nextWord.isBlank()) return this
        val key = RowKey(locale, prefix, DictNormalization.normalizedKey(nextWord))
        val existing = rows[key]
        val updated = if (existing != null) {
            existing.copy(count = existing.count + 1, lastUsedMillis = nowMillis, nextWord = nextWord)
        } else {
            Bigram(locale, prefix, nextWord, count = 1, lastUsedMillis = nowMillis)
        }
        return NgramStore(rows + (key to updated))
    }

    /**
     * Takes back one learn of [nextWord] after [prefix]: the count drops by one, and a pair learned
     * only once goes. For a pair learned by mistake (the word was rewritten after it was learned,
     * autocorrect-suggestions.md SS10's mix-up fix), so the rest of its history is kept. A pair
     * not present is a no-op.
     */
    fun unlearn(locale: String, prefix: String, nextWord: String): NgramStore {
        val key = RowKey(locale, prefix, DictNormalization.normalizedKey(nextWord))
        val existing = rows[key] ?: return this
        return if (existing.count <= 1) NgramStore(rows - key) else NgramStore(rows + (key to existing.copy(count = existing.count - 1)))
    }

    /**
     * Forgets [nextWord] as a prediction under [prefix] for [locale]: the strip's long-press hide
     * on a next-word suggestion (spec SS5, "for a next-word suggestion it also forgets the
     * bigram"). A pair not present is a no-op.
     */
    fun forget(locale: String, prefix: String, nextWord: String): NgramStore {
        val key = RowKey(locale, prefix, DictNormalization.normalizedKey(nextWord))
        if (key !in rows) return this
        return NgramStore(rows - key)
    }

    /**
     * Forgets [word] as a next word under every prefix and locale at once: deleting a personal
     * word (spec SS4, "deleting a user word forgets it as a next word under every prefix"; SS5,
     * "forgets it as a next word everywhere").
     */
    fun forgetEverywhere(word: String): NgramStore {
        val key = DictNormalization.normalizedKey(word)
        return NgramStore(rows.filterKeys { it.nextWordKey != key })
    }

    /**
     * Up to [limit] predictions for [prefix] in [locale], ordered by count descending then
     * recency (last-used time) descending. spec: SS4, "Predictions are ordered by count
     * descending then recency."
     */
    fun predict(locale: String, prefix: String, limit: Int): List<Bigram> {
        if (limit <= 0) return emptyList()
        return rows.values
            .asSequence()
            .filter { it.locale == locale && it.prefix == prefix }
            .sortedWith(compareByDescending<Bigram> { it.count }.thenByDescending { it.lastUsedMillis })
            .take(limit)
            .toList()
    }

    /** Every row, for a caller (`:ime`) to persist back to `user_ngrams.db`. */
    fun allRows(): List<Bigram> = rows.values.toList()

    /**
     * Folds a persisted load into this store, keeping whichever side of a shared row is more
     * advanced (higher count, or the same count with a later last-used time), so a row this
     * session's in-memory overlay already grew past its persisted snapshot is never regressed by
     * a background load landing late. spec: SS4, "a just-learned pair is visible immediately
     * through an in-memory overlay": the overlay must survive the load that follows it, not just
     * precede it.
     */
    fun mergedWith(loaded: List<Bigram>): NgramStore {
        val merged = rows.toMutableMap()
        for (row in loaded) {
            val key = RowKey(row.locale, row.prefix, DictNormalization.normalizedKey(row.nextWord))
            val existing = merged[key]
            val winner = when {
                existing == null -> row
                row.count > existing.count -> row
                row.count == existing.count && row.lastUsedMillis > existing.lastUsedMillis -> row
                else -> existing
            }
            merged[key] = winner
        }
        return NgramStore(merged)
    }

    companion object {
        /** spec: SS4, "at sentence start, as 'sentence start -> this word'". Not a normal word key, so it can never collide with one. */
        const val SENTENCE_START: String = "\u0000sentence-start"

        fun empty(): NgramStore = NgramStore(emptyMap())

        /** Rebuilds a store from persisted rows (a `user_ngrams.db` load), keyed exactly as [learn] would have left them. */
        fun of(rows: List<Bigram>): NgramStore =
            NgramStore(rows.associateBy { RowKey(it.locale, it.prefix, DictNormalization.normalizedKey(it.nextWord)) })
    }
}
