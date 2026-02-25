package it.palsoftware.pastiera.data.emoji

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Shared emoji search index used by both the settings picker dialog and the inline IME picker.
 *
 * Search terms are loaded from local CLDR-derived assets in assets/common/emoji_search/<locale>.tsv.
 */
object EmojiSearchRepository {
    private const val SEARCH_ASSET_DIR = "common/emoji_search"
    private const val EN_LOCALE = "en"

    data class EmojiSearchResult(
        val entry: EmojiRepository.EmojiEntry,
        val categoryId: String,
        val score: Int
    )

    class EmojiSearchIndex internal constructor(
        internal val items: List<IndexedEmoji>
    )

    internal data class IndexedEmoji(
        val entry: EmojiRepository.EmojiEntry,
        val categoryId: String,
        val categoryOrder: Int,
        val terms: List<SearchTerm>
    )

    internal data class SearchTerm(
        val normalizedText: String,
        val kind: TermKind,
        val preferredLocale: Boolean
    )

    internal data class MetadataRecord(
        val name: String?,
        val keywords: List<String>
    )

    internal enum class TermKind {
        NAME,
        KEYWORD
    }

    private data class RankedResult(
        val result: EmojiSearchResult,
        val categoryOrder: Int
    )

    private val cacheMutex = Mutex()
    private val indexCache = LinkedHashMap<String, EmojiSearchIndex>()
    private const val MAX_CACHED_INDEXES = 3

    suspend fun getSearchIndex(context: Context): EmojiSearchIndex {
        val localeChain = getPreferredLocaleChain(context)
        val cacheKey = localeChain.joinToString("|")

        cacheMutex.withLock {
            indexCache[cacheKey]?.let { existing ->
                return existing
            }
        }

        val built = withContext(Dispatchers.IO) {
            buildIndex(context, localeChain)
        }

        cacheMutex.withLock {
            indexCache[cacheKey]?.let { existing ->
                return existing
            }
            indexCache[cacheKey] = built
            while (indexCache.size > MAX_CACHED_INDEXES) {
                val firstKey = indexCache.entries.firstOrNull()?.key ?: break
                indexCache.remove(firstKey)
            }
            return built
        }
    }

    fun search(
        index: EmojiSearchIndex,
        query: String,
        limit: Int = 200
    ): List<EmojiSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val normalizedQuery = normalizeSearchText(trimmed)
        if (normalizedQuery.isEmpty()) return emptyList()
        val allowContains = normalizedQuery.length >= 2

        return index.items.mapNotNull { item ->
            val score = scoreItem(item, trimmed, normalizedQuery, allowContains)
            if (score <= 0) return@mapNotNull null
            RankedResult(
                result = EmojiSearchResult(
                    entry = item.entry,
                    categoryId = item.categoryId,
                    score = score
                ),
                categoryOrder = item.categoryOrder
            )
        }
            .sortedWith(
                compareByDescending<RankedResult> { it.result.score }
                    .thenBy { it.categoryOrder }
                    .thenBy { it.result.entry.base }
            )
            .map { it.result }
            .take(limit)
    }

    fun clearCache() {
        indexCache.clear()
    }

    private fun scoreItem(
        item: IndexedEmoji,
        rawQuery: String,
        normalizedQuery: String,
        allowContains: Boolean
    ): Int {
        if (item.entry.base == rawQuery) return 2000
        if (item.entry.variants.any { it == rawQuery }) return 1900

        var best = 0
        for (term in item.terms) {
            val value = term.normalizedText
            if (value.isEmpty()) continue
            val localeBonus = if (term.preferredLocale) 25 else 0

            val candidateScore = when {
                value == normalizedQuery -> when (term.kind) {
                    TermKind.NAME -> 1700 + localeBonus
                    TermKind.KEYWORD -> 1600 + localeBonus
                }

                value.startsWith(normalizedQuery) -> when (term.kind) {
                    TermKind.NAME -> 1400 + localeBonus
                    TermKind.KEYWORD -> 1300 + localeBonus
                }

                allowContains && value.contains(normalizedQuery) -> when (term.kind) {
                    TermKind.NAME -> 1000 + localeBonus
                    TermKind.KEYWORD -> 900 + localeBonus
                }

                else -> 0
            }
            if (candidateScore > best) best = candidateScore
        }

        if (best == 0) return 0
        // Stable preference for canonical category order.
        return best - item.categoryOrder
    }

    private suspend fun buildIndex(context: Context, localeChain: List<String>): EmojiSearchIndex {
        val categories = EmojiRepository.getEmojiCategories(context)
        val metadataByEmoji = loadMetadataMap(context, localeChain)

        val items = ArrayList<IndexedEmoji>()
        categories.forEachIndexed { categoryOrder, category ->
            category.emojis.forEach { entry ->
                val terms = LinkedHashMap<String, SearchTerm>()

                addTermsForEmoji(
                    terms = terms,
                    metadataByEmoji = metadataByEmoji,
                    emoji = entry.base
                )
                entry.variants.forEach { variant ->
                    addTermsForEmoji(
                        terms = terms,
                        metadataByEmoji = metadataByEmoji,
                        emoji = variant
                    )
                }

                // If no metadata is available, still index the literal emoji string as a fallback.
                if (terms.isEmpty()) {
                    val literal = normalizeSearchText(entry.base)
                    if (literal.isNotEmpty()) {
                        terms[literal] = SearchTerm(literal, TermKind.KEYWORD, false)
                    }
                }

                items.add(
                    IndexedEmoji(
                        entry = entry,
                        categoryId = category.id,
                        categoryOrder = categoryOrder,
                        terms = terms.values.toList()
                    )
                )
            }
        }

        return EmojiSearchIndex(items = items)
    }

    private fun addTermsForEmoji(
        terms: MutableMap<String, SearchTerm>,
        metadataByEmoji: Map<String, List<Pair<MetadataRecord, Boolean>>>,
        emoji: String
    ) {
        val records = metadataByEmoji[emoji].orEmpty()
        records.forEach { (record, preferredLocale) ->
            record.name?.let { name ->
                val normalized = normalizeSearchText(name)
                if (normalized.isNotEmpty() && normalized !in terms) {
                    terms[normalized] = SearchTerm(normalized, TermKind.NAME, preferredLocale)
                }
            }
            record.keywords.forEach { keyword ->
                val normalized = normalizeSearchText(keyword)
                if (normalized.isNotEmpty() && normalized !in terms) {
                    terms[normalized] = SearchTerm(normalized, TermKind.KEYWORD, preferredLocale)
                }
            }
        }
    }

    private fun loadMetadataMap(
        context: Context,
        localeChain: List<String>
    ): Map<String, List<Pair<MetadataRecord, Boolean>>> {
        val out = LinkedHashMap<String, MutableList<Pair<MetadataRecord, Boolean>>>()
        localeChain.forEachIndexed { index, localeTag ->
            val isPreferred = index == 0
            val records = loadMetadataAsset(context, localeTag) ?: return@forEachIndexed
            records.forEach { (emoji, record) ->
                out.getOrPut(emoji) { mutableListOf() }.add(record to isPreferred)
            }
        }
        return out
    }

    private fun loadMetadataAsset(
        context: Context,
        localeTag: String
    ): Map<String, MetadataRecord>? {
        val candidates = localeAssetCandidates(localeTag)
        for (candidate in candidates) {
            val assetPath = "$SEARCH_ASSET_DIR/$candidate.tsv"
            val parsed = runCatching {
                context.assets.open(assetPath).use { input ->
                    BufferedReader(InputStreamReader(input)).lineSequence()
                        .mapNotNull { line ->
                            if (line.isBlank()) return@mapNotNull null
                            val parts = line.split('\t')
                            if (parts.isEmpty()) return@mapNotNull null
                            val emoji = parts.getOrNull(0)?.trim().orEmpty()
                            if (emoji.isEmpty()) return@mapNotNull null
                            val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                            val keywords = parts.getOrNull(2)
                                ?.split('|')
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                .orEmpty()
                            emoji to MetadataRecord(name = name, keywords = keywords)
                        }
                        .toMap()
                }
            }.getOrNull()

            if (!parsed.isNullOrEmpty()) {
                return parsed
            }
        }
        return null
    }

    private fun localeAssetCandidates(localeTag: String): List<String> {
        val normalized = localeTag.replace('-', '_').lowercase(Locale.ROOT)
        val languageOnly = normalized.substringBefore('_')
        return buildList {
            if (normalized.isNotBlank()) add(normalized)
            if (languageOnly.isNotBlank() && languageOnly != normalized) add(languageOnly)
        }.distinct()
    }

    @VisibleForTesting
    internal fun getPreferredLocaleChain(context: Context): List<String> {
        val locales = context.resources.configuration.locales
        val out = ArrayList<String>()
        for (i in 0 until locales.size()) {
            val locale = locales[i] ?: continue
            val tag = locale.toLanguageTag()
            if (tag.isNotBlank()) out.add(tag)
            val language = locale.language
            if (!language.isNullOrBlank()) out.add(language)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && out.isEmpty()) {
            val fallback = context.resources.configuration.locale
            fallback?.toLanguageTag()?.takeIf { it.isNotBlank() }?.let(out::add)
            fallback?.language?.takeIf { it.isNotBlank() }?.let(out::add)
        }
        out.add(EN_LOCALE)
        return out.distinct()
    }

    @VisibleForTesting
    internal fun normalizeSearchText(text: String): String {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty()) return ""

        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = buildString(decomposed.length) {
            var previousSpace = false
            decomposed.forEach { ch ->
                val type = Character.getType(ch)
                if (type == Character.NON_SPACING_MARK.toInt()) return@forEach
                if (Character.isLetterOrDigit(ch)) {
                    append(ch)
                    previousSpace = false
                } else if (Character.isWhitespace(ch) || ch == '-' || ch == '_') {
                    if (!previousSpace && isNotEmpty()) {
                        append(' ')
                        previousSpace = true
                    }
                }
            }
            if (isNotEmpty() && last() == ' ') {
                deleteCharAt(lastIndex)
            }
        }
        return stripped
    }
}
