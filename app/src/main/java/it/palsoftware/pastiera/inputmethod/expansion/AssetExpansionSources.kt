package it.palsoftware.pastiera.inputmethod.expansion

import android.content.res.AssetManager
import org.json.JSONObject
import java.util.Locale
import it.palsoftware.pastiera.data.emoji.EmojiAvailability

abstract class AssetExpansionSource(
    private val assets: AssetManager,
    private val assetPath: String
) : ExpansionSource {
    @Volatile private var loadedEntries: Map<String, List<String>>? = null
    @Volatile private var sortedEntryCache: List<Map.Entry<String, List<String>>>? = null

    internal fun isLoadedForTests(): Boolean = loadedEntries != null
    fun prepare() {
        sortedEntries()
    }

    override fun matches(query: ExpansionQuery, limit: Int): List<ExpansionMatch> {
        val typed = query.typedShortcut.lowercase(Locale.ROOT)
        val entries = sortedEntries()
        val start = entries.lowerBound(typed)
        return entries.asSequence()
            .drop(start)
            .takeWhile { (shortcut, _) -> shortcut.startsWith(typed) }
            .flatMap { (shortcut, replacements) ->
                replacements.asSequence().map { replacement ->
                    ExpansionMatch(
                        provider = provider,
                        shortcut = shortcut,
                        replacement = replacement,
                        token = query.token,
                        tokenStart = query.tokenStart,
                        displayText = "$replacement  :$shortcut:"
                    )
                }
            }
            .sortedWith(compareBy<ExpansionMatch> { it.shortcut.length }.thenBy { it.shortcut })
            .take(limit)
            .toList()
    }

    private fun entries(): Map<String, List<String>> = loadedEntries ?: synchronized(this) {
        loadedEntries ?: loadEntries().also { loadedEntries = it }
    }

    private fun sortedEntries(): List<Map.Entry<String, List<String>>> = sortedEntryCache ?: synchronized(this) {
        sortedEntryCache ?: entries().entries.sortedBy { it.key }.also { sortedEntryCache = it }
    }

    private fun List<Map.Entry<String, List<String>>>.lowerBound(prefix: String): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle].key < prefix) low = middle + 1 else high = middle
        }
        return low
    }

    protected open fun rootObject(document: JSONObject): JSONObject = document
    protected open fun isReplacementAvailable(replacement: String): Boolean = true

    private fun loadEntries(): Map<String, List<String>> = assets.open(assetPath).bufferedReader().use { reader ->
        val root = rootObject(JSONObject(reader.readText()))
        buildMap {
            root.keys().forEach { shortcut ->
                val value = root.get(shortcut)
                val replacements = when (value) {
                    is org.json.JSONArray -> List(value.length()) { value.getString(it) }
                    is String -> listOf(value)
                    else -> emptyList()
                }
                val available = replacements.filter(::isReplacementAvailable)
                if (available.isNotEmpty()) put(shortcut.lowercase(Locale.ROOT), available)
            }
        }
    }
}

class EmojiShortcodeSource(
    assets: AssetManager,
    private val availability: EmojiAvailability = EmojiAvailability.fromAssets(assets)
) :
    AssetExpansionSource(assets, "common/emoji_shortcodes.json") {
    override val provider = ExpansionProvider.EMOJI
    override fun rootObject(document: JSONObject): JSONObject = document.getJSONObject("shortcodes")
    override fun isReplacementAvailable(replacement: String): Boolean = availability.isAvailable(replacement)
}

class SymbolShortcodeSource(assets: AssetManager) :
    AssetExpansionSource(assets, "common/symbol_shortcodes.json") {
    override val provider = ExpansionProvider.SYMBOL
}
