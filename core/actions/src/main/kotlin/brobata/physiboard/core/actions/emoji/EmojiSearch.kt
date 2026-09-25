package brobata.physiboard.core.actions.emoji

import java.text.Normalizer

/** The nine shipped search-term locales. spec: expansion-clipboard-pickers-launcher.md SS4.2. */
object EmojiSearchLocales {
    val SHIPPED: List<String> = listOf("de", "en", "es", "fr", "hy", "it", "pl", "ru", "uk")
    const val FALLBACK: String = "en"

    /**
     * spec SS4.2: "for each locale, its full tag then its bare language (`de-CH` gives `de-CH` then
     * `de`), then `en`, duplicates removed. The first entry is the preferred locale" (T29).
     */
    fun chain(systemLocaleTags: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (tag in systemLocaleTags) {
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) continue
            out.add(trimmed)
            out.add(trimmed.substringBefore('-').substringBefore('_'))
        }
        out.add(FALLBACK)
        return out.toList()
    }

    /** spec SS4.2: "the file tried is the tag lowercased with `-` replaced by `_`, then the bare language". */
    fun fileCandidates(chainEntry: String): List<String> {
        val full = chainEntry.lowercase().replace('-', '_')
        val bare = full.substringBefore('_')
        return listOf("$full.tsv", "$bare.tsv").distinct()
    }
}

/** One line of a `<locale>.tsv` file: `emoji<TAB>name<TAB>keyword|keyword|...`. spec SS4.2. */
data class EmojiTermLine(val emoji: String, val name: String?, val keywords: List<String>)

/** One locale's parsed search terms. */
data class EmojiTermFile(val locale: String, val lines: List<EmojiTermLine>)

/** spec SS4.2: how a query and every term are normalised before comparison (T26). */
object EmojiTermNormalizer {
    /**
     * "lowercase, Unicode decomposition with combining marks removed, only letters and digits
     * kept, runs of whitespace, `-` and `_` collapsed to one space, trimmed".
     */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (c in decomposed) {
            val type = Character.getType(c)
            val isMark = type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()
            when {
                isMark -> Unit
                c.isLetterOrDigit() -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.append(c)
                }
                c.isWhitespace() || c == '-' || c == '_' -> pendingSpace = true
                else -> Unit
            }
        }
        return sb.toString()
    }

    /** spec SS4.2: "blank lines are skipped, a missing name is allowed". */
    fun parseTermFile(locale: String, text: String): EmojiTermFile {
        val lines = text.lineSequence().mapNotNull { raw ->
            if (raw.isBlank()) return@mapNotNull null
            val cols = raw.split('\t')
            val emoji = cols.getOrNull(0)?.trim().orEmpty()
            if (emoji.isEmpty()) return@mapNotNull null
            val name = cols.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            val keywords = cols.getOrNull(2)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            EmojiTermLine(emoji, name, keywords)
        }.toList()
        return EmojiTermFile(locale, lines)
    }
}

/** One normalised term of the index, remembering what it was and where it came from. spec SS4.2. */
data class EmojiTerm(val text: String, val isName: Boolean, val preferredLocale: Boolean)

/** One indexed emoji: the base, its variants (which share the entry), its category order and its terms. */
data class IndexedEmoji(val entry: EmojiEntry, val categoryOrder: Int, val terms: List<EmojiTerm>)

/** A scored search hit. spec SS4.2. */
data class EmojiSearchHit(val entry: EmojiEntry, val score: Int)

/**
 * The search index and its scoring. spec SS4.2. The index is built from the available categories
 * and the loaded term files; the cache of up to three indexes keyed by locale chain is the
 * caller's (it owns the loading), so this object holds no state.
 */
class EmojiSearchIndex private constructor(private val emojis: List<IndexedEmoji>) {

    /**
     * spec SS4.2's scoring table. The best term wins; the category's position is subtracted as a
     * tie-breaker; results sorted by score descending, then category order, then the emoji string,
     * cut to [MAX_RESULTS] (T27, T28). An empty normalised query gives no results.
     */
    fun search(rawQuery: String, limit: Int = MAX_RESULTS): List<EmojiSearchHit> {
        val raw = rawQuery.trim()
        if (raw.isEmpty()) return emptyList()
        // The raw query may be an emoji itself (no letters, so it normalises to nothing); the
        // equality rows of the table still apply to it, only the term rows need the normalised form.
        val query = EmojiTermNormalizer.normalize(raw)
        val hits = ArrayList<Pair<IndexedEmoji, Int>>()
        for (indexed in emojis) {
            val score = scoreOf(indexed, raw, query) ?: continue
            hits.add(indexed to (score - indexed.categoryOrder))
        }
        return hits.sortedWith(
            compareByDescending<Pair<IndexedEmoji, Int>> { it.second }
                .thenBy { it.first.categoryOrder }
                .thenBy { it.first.entry.base },
        ).take(limit).map { EmojiSearchHit(it.first.entry, it.second) }
    }

    private fun scoreOf(indexed: IndexedEmoji, raw: String, query: String): Int? {
        if (raw == indexed.entry.base) return SCORE_BASE_EQUALS
        if (raw in indexed.entry.variants) return SCORE_VARIANT_EQUALS
        if (query.isEmpty()) return null
        var best: Int? = null
        for (term in indexed.terms) {
            val bonus = if (term.preferredLocale) PREFERRED_LOCALE_BONUS else 0
            val score = when {
                term.text == query -> if (term.isName) SCORE_NAME_EQUALS else SCORE_KEYWORD_EQUALS
                term.text.startsWith(query) -> if (term.isName) SCORE_NAME_PREFIX else SCORE_KEYWORD_PREFIX
                query.length >= 2 && term.text.contains(query) -> if (term.isName) SCORE_NAME_CONTAINS else SCORE_KEYWORD_CONTAINS
                else -> continue
            } + bonus
            if (best == null || score > best) best = score
        }
        return best
    }

    val size: Int get() = emojis.size

    companion object {
        const val MAX_RESULTS: Int = 200
        const val SCORE_BASE_EQUALS: Int = 2000
        const val SCORE_VARIANT_EQUALS: Int = 1900
        const val SCORE_NAME_EQUALS: Int = 1700
        const val SCORE_KEYWORD_EQUALS: Int = 1600
        const val SCORE_NAME_PREFIX: Int = 1400
        const val SCORE_KEYWORD_PREFIX: Int = 1300
        const val SCORE_NAME_CONTAINS: Int = 1000
        const val SCORE_KEYWORD_CONTAINS: Int = 900
        const val PREFERRED_LOCALE_BONUS: Int = 25

        /** spec SS4.2: "Up to 3 indexes (keyed by the chain) are cached; the oldest is evicted." */
        const val CACHE_SIZE: Int = 3

        /**
         * spec SS4.2 "Index": for every available emoji (base and variants share one entry), the
         * names and keywords from every loaded locale, normalised and deduplicated, each remembering
         * whether it is a name or a keyword and whether it came from the preferred locale; an emoji
         * with no metadata in any loaded file is indexed by its own literal string. [termFiles] are
         * in chain order, so the first is the preferred locale.
         */
        fun build(categories: List<EmojiCategory>, termFiles: List<EmojiTermFile>): EmojiSearchIndex {
            val preferred = termFiles.firstOrNull()?.locale
            val byEmoji = HashMap<String, MutableList<EmojiTerm>>()
            for (file in termFiles) {
                val isPreferred = file.locale == preferred
                for (line in file.lines) {
                    val terms = byEmoji.getOrPut(line.emoji) { ArrayList() }
                    line.name?.let { name -> terms.addTerm(EmojiTermNormalizer.normalize(name), isName = true, isPreferred) }
                    for (k in line.keywords) terms.addTerm(EmojiTermNormalizer.normalize(k), isName = false, isPreferred)
                }
            }
            val indexed = ArrayList<IndexedEmoji>()
            categories.forEachIndexed { order, category ->
                if (category.id == EmojiCategories.RECENTS_ID) return@forEachIndexed
                for (entry in category.entries) {
                    val terms = ArrayList<EmojiTerm>()
                    byEmoji[entry.base]?.let(terms::addAll)
                    for (v in entry.variants) byEmoji[v]?.let(terms::addAll)
                    val deduped = terms.distinct()
                    indexed.add(IndexedEmoji(entry, order, deduped.ifEmpty { listOf(EmojiTerm(entry.base, isName = true, preferredLocale = false)) }))
                }
            }
            return EmojiSearchIndex(indexed)
        }

        private fun MutableList<EmojiTerm>.addTerm(text: String, isName: Boolean, preferred: Boolean) {
            if (text.isEmpty()) return
            val term = EmojiTerm(text, isName, preferred)
            if (term !in this) add(term)
        }
    }
}
