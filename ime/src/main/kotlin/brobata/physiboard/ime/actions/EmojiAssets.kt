package brobata.physiboard.ime.actions

import android.content.res.AssetManager
import android.graphics.Paint
import android.os.Handler
import android.util.Log
import brobata.physiboard.core.actions.emoji.EmojiAvailability
import brobata.physiboard.core.actions.emoji.EmojiCategories
import brobata.physiboard.core.actions.emoji.EmojiCategory
import brobata.physiboard.core.actions.emoji.EmojiSearchIndex
import brobata.physiboard.core.actions.emoji.EmojiSearchLocales
import brobata.physiboard.core.actions.emoji.EmojiTabIcon
import brobata.physiboard.core.actions.emoji.EmojiTermFile
import brobata.physiboard.core.actions.emoji.EmojiTermNormalizer
import brobata.physiboard.core.actions.picker.SymbolSearchIndex
import brobata.physiboard.core.actions.picker.UnicodeSymbols
import java.util.concurrent.Executors

/** What one load produced: the available categories in display order and the search index for the locale chain. */
internal data class EmojiData(val categories: List<EmojiCategory>, val index: EmojiSearchIndex, val chain: List<String>)

/**
 * Loads the picker's assets off the main thread. spec: expansion-clipboard-pickers-launcher.md
 * SS4.1 (the nine category files, `minApi.txt`, availability by API level or font glyph, cached
 * for the life of the process) and SS4.2 (the locale chain, the `.tsv` files, up to three indexes
 * cached by chain). The parsing and scoring are `:core:actions`'; only the asset reads and the
 * `Paint.hasGlyph` probe are Android's.
 *
 * SPEC GAP: only `en.tsv` ships in 3.0 (generated from Unicode 17.0's own short names by
 * tools/emoji/build_emoji_assets.py); the eight other locales load the moment their files are
 * added, since the chain logic already tries them.
 */
internal class EmojiAssets(private val assets: AssetManager, private val mainHandler: Handler, private val runningApi: Int) {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "physiboard-emoji") }
    private var categories: List<EmojiCategory>? = null
    private val indexes = LinkedHashMap<List<String>, EmojiSearchIndex>()

    /**
     * Ends the loader thread. Called when the keyboard service is destroyed: without it every
     * rebind of the service (switching input method away and back) left another parked
     * "physiboard-emoji" thread behind (2026-09-25 review).
     */
    fun shutdown() {
        runCatching { executor.shutdownNow() }
    }

    fun loadAsync(localeTags: List<String>, onLoaded: (EmojiData?) -> Unit) {
        // A shut-down executor (service destroyed) refuses work; nothing is left to call back.
        if (executor.isShutdown) return
        executor.execute {
            val data = runCatching { load(localeTags) }.onFailure { Log.e(TAG, "emoji load failed", it) }.getOrNull()
            mainHandler.post { onLoaded(data) }
        }
    }

    private fun load(localeTags: List<String>): EmojiData? {
        val cats = categories ?: loadCategories().also { categories = it }
        if (cats.isEmpty()) return null
        val chain = EmojiSearchLocales.chain(localeTags)
        val index = synchronized(indexes) { indexes[chain] } ?: buildIndex(cats, chain).also { built ->
            synchronized(indexes) {
                indexes[chain] = built
                while (indexes.size > EmojiSearchIndex.CACHE_SIZE) indexes.remove(indexes.keys.first())
            }
        }
        return EmojiData(cats, index, chain)
    }

    private fun loadCategories(): List<EmojiCategory> {
        val names = runCatching { assets.list(EMOJI_DIR)?.toList() }.getOrNull().orEmpty()
        val minApi = read("$EMOJI_DIR/${EmojiCategories.MIN_API_FILE}")?.let(EmojiCategories::parseMinApi).orEmpty()
        val shipped = EmojiCategories.SHIPPED.mapNotNull { s ->
            read("$EMOJI_DIR/${s.fileName}")?.let { EmojiCategory(s.id, s.label, s.icon, EmojiCategories.parseCategoryFile(it)) }
        }
        val extra = names.sorted().mapNotNull { name ->
            val label = EmojiCategories.extraCategoryLabel(name) ?: return@mapNotNull null
            read("$EMOJI_DIR/$name")?.let { EmojiCategory(label, label, EmojiTabIcon.FILE, EmojiCategories.parseCategoryFile(it)) }
        }
        val paint = Paint()
        return EmojiAvailability.filterCategories(shipped + extra, minApi, runningApi) { paint.hasGlyph(it) }
    }

    /** spec SS4.2: for each chain entry the full tag file then the bare language; the first that parses to at least one line is used. */
    private fun buildIndex(cats: List<EmojiCategory>, chain: List<String>): EmojiSearchIndex {
        val files = ArrayList<EmojiTermFile>()
        val used = HashSet<String>()
        for (entry in chain) {
            val file = EmojiSearchLocales.fileCandidates(entry).firstNotNullOfOrNull { candidate ->
                if (candidate in used) return@firstNotNullOfOrNull null
                read("$SEARCH_DIR/$candidate")?.let { EmojiTermNormalizer.parseTermFile(candidate.removeSuffix(".tsv"), it) }?.takeIf { it.lines.isNotEmpty() }?.also { used.add(candidate) }
            } ?: continue
            files.add(file)
        }
        return EmojiSearchIndex.build(cats, files)
    }

    // -----------------------------------------------------------------------------------------
    // Kaomoji and Unicode symbols. spec SS4.8, SS4.9.
    // -----------------------------------------------------------------------------------------

    @Volatile private var symbols: UnicodeSymbols.Catalog? = null
    @Volatile private var symbolIndex: SymbolSearchIndex? = null

    /**
     * spec SS4.8: the symbol catalogue, built on the loader thread the first time Symbols mode
     * opens (a name lookup and a glyph probe per candidate, some thousands of each) and kept for
     * the life of the process. Null to [onLoaded] when it could not be built.
     */
    fun loadSymbolsAsync(onLoaded: (UnicodeSymbols.Catalog?) -> Unit) {
        symbols?.let { onLoaded(it); return }
        runCatching {
            executor.execute {
                val catalog = runCatching { symbolCatalog() }.onFailure { Log.e(TAG, "symbol catalogue failed", it) }.getOrNull()
                mainHandler.post { onLoaded(catalog) }
            }
        }.onFailure { Log.e(TAG, "symbol catalogue not scheduled", it) }
    }

    private fun symbolCatalog(): UnicodeSymbols.Catalog = symbols ?: run {
        val paint = Paint()
        UnicodeSymbols.build(glyphs = { paint.hasGlyph(it) }).also { symbols = it }
    }

    /**
     * One search, on the loader thread, so neither the key path nor the main thread scores a
     * few thousand names (spec SS4.5, SS4.8). [onResult] runs on the main thread; the caller
     * drops answers to a query it has since replaced.
     */
    fun <T> searchAsync(search: () -> T, onResult: (T?) -> Unit) {
        runCatching {
            executor.execute {
                val result = runCatching(search).onFailure { Log.e(TAG, "search failed", it) }.getOrNull()
                mainHandler.post { onResult(result) }
            }
        }.onFailure { Log.e(TAG, "search not scheduled", it) }
    }

    /** spec SS4.8: the symbol search index, built on first use, on the loader thread (only call from [searchAsync]). */
    fun symbolIndex(): SymbolSearchIndex = symbolIndex ?: SymbolSearchIndex(symbolCatalog()).also { symbolIndex = it }

    private fun read(path: String): String? = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private companion object {
        const val TAG = "PhysiBoardEmoji"
        const val EMOJI_DIR = "emoji"
        const val SEARCH_DIR = "emoji_search"
    }
}
