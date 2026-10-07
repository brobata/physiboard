package brobata.physiboard.core.actions.gif

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The GIF page's local lists: favourites (★, kept until the user removes them) and recently sent.
 * Both are kept on the phone only, as the GIF's slug, title and the provider's URLs (never the
 * image itself). spec: layers-sym-alt.md SS4.5.
 */
object GifShelf {
    const val MAX_FAVOURITES = 48
    const val MAX_RECENTS = 24

    /**
     * After [item] was sent: it moves to the front of [recents], once, and the list is cut to
     * [MAX_RECENTS]. While learning is off (private mode or a field that asks for no personalised
     * learning, app-shell.md SS31.1) the same list comes back unchanged.
     */
    fun afterSend(recents: List<GifItem>, item: GifItem, learningAllowed: Boolean): List<GifItem> {
        if (!learningAllowed) return recents
        return (listOf(item) + recents.filter { it.slug != item.slug }).take(MAX_RECENTS)
    }

    /** What a ★ toggle did. */
    sealed class FavouriteChange {
        data class Added(val list: List<GifItem>) : FavouriteChange()
        data class Removed(val list: List<GifItem>) : FavouriteChange()

        /** Adding while learning is off: nothing is remembered (removing always works). */
        data object RefusedPrivate : FavouriteChange()
    }

    fun toggleFavourite(favourites: List<GifItem>, item: GifItem, learningAllowed: Boolean): FavouriteChange {
        if (favourites.any { it.slug == item.slug }) return FavouriteChange.Removed(favourites.filter { it.slug != item.slug })
        if (!learningAllowed) return FavouriteChange.RefusedPrivate
        return FavouriteChange.Added((listOf(item) + favourites).take(MAX_FAVOURITES))
    }

    fun encode(items: List<GifItem>): String = JsonArray(
        items.map { gif ->
            JsonObject(
                mapOf(
                    "slug" to JsonPrimitive(gif.slug), "title" to JsonPrimitive(gif.title),
                    "preview" to JsonPrimitive(gif.previewUrl), "pw" to JsonPrimitive(gif.previewWidth), "ph" to JsonPrimitive(gif.previewHeight),
                    "send" to JsonPrimitive(gif.sendUrl), "sw" to JsonPrimitive(gif.sendWidth), "sh" to JsonPrimitive(gif.sendHeight),
                ),
            )
        },
    ).toString()

    /** Tolerant: a malformed value is an empty list; an entry missing its slug or URLs is skipped. */
    fun decode(stored: String?): List<GifItem> {
        if (stored.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(stored) }.getOrNull() as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull ?: 0
            val slug = s("slug") ?: return@mapNotNull null
            val preview = s("preview")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val send = s("send")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            GifItem(slug, s("title").orEmpty(), preview, i("pw"), i("ph"), send, i("sw"), i("sh"))
        }.distinctBy { it.slug }
    }
}

/**
 * The GIF page's decisions that do not need a window. spec: layers-sym-alt.md SS4.5.
 */
object GifPage {
    /** The quick searches under the search field, in order; each is also the query it runs. */
    val QUICK_SEARCHES: List<String> = listOf("LOL", "Love", "Sad", "Wow", "Yes", "No", "Bye")

    const val NOT_SET_UP = "GIF search isn't set up in this build: it has no KLIPY API key."
    const val FAILED = "Couldn't reach KLIPY. Check the connection and try again."
    const val NO_RESULTS = "No GIFs found"
    const val SEARCH_HINT = "Search KLIPY"
    const val ATTRIBUTION = "Powered by KLIPY"
    const val IDLE = "Type to search KLIPY, or press Enter for what's trending."

    /** The Sym double-tap window (layers-sym-alt.md SS5.10): trending waits this long after the page opens. */
    const val FIRST_LOAD_DELAY_MS = 300L

    /** The page's height, the cell height, and the narrowest cell, in dp. */
    const val HEIGHT_DP = 260
    const val CELL_DP = 88
    const val MIN_CELL_WIDTH_DP = 104

    /** Columns that fit [widthPx] at [density] with cells at least [MIN_CELL_WIDTH_DP] wide; 2 to 5. */
    fun columns(widthPx: Int, density: Double): Int = ((widthPx / density - 16) / MIN_CELL_WIDTH_DP).toInt().coerceIn(2, 5)

    /** How long the typed query rests before it is searched. */
    const val SEARCH_DEBOUNCE_MS = 400L

    /** The largest GIF fetched to send, and the largest preview. */
    const val MAX_SEND_BYTES = 8_000_000
    const val MAX_PREVIEW_BYTES = 2_000_000

    /** What the page should do for the query on screen. */
    sealed class Request {
        /** Nothing can go out; show [message] in place of results. */
        data class Refused(val message: String) : Request()

        /** GET [url] (through the gate) for the list. [trending] when the query was empty. */
        data class Fetch(val url: String, val trending: Boolean) : Request()
    }

    /**
     * [apiKey] blank means the build has no key ([NOT_SET_UP]). [privateMode] is the keyboard's
     * own view of the switch, checked first so the page says why at once; the gate still makes
     * the final decision when the request is made. [blockedReason] is the gate's sentence for
     * private mode.
     */
    fun request(apiKey: String, query: String, page: Int, country: String?, privateMode: Boolean, blockedReason: String): Request = when {
        apiKey.isBlank() -> Request.Refused(NOT_SET_UP)
        privateMode -> Request.Refused(blockedReason)
        query.isBlank() -> Request.Fetch(Klipy.trendingUrl(apiKey, page, country), trending = true)
        else -> Request.Fetch(Klipy.searchUrl(apiKey, query, page, country), trending = false)
    }

    /** What the grid shows for one list response. */
    sealed class Outcome {
        data class Results(val page: GifResultPage) : Outcome()
        data class Message(val text: String) : Outcome()
    }

    /**
     * A response, as the caller's fetch answered: [body] when it came back, [blockedReason] when
     * the gate refused, neither when it failed.
     */
    fun outcome(body: String?, blockedReason: String?, requestedPage: Int = 1): Outcome {
        if (blockedReason != null) return Outcome.Message(blockedReason)
        val parsed = body?.let { Klipy.parse(it, requestedPage) } ?: return Outcome.Message(FAILED)
        return if (parsed.items.isEmpty() && parsed.page <= 1) Outcome.Message(NO_RESULTS) else Outcome.Results(parsed)
    }

    /**
     * spec SS4.5's insertion rule: the field takes the GIF itself when one of its declared content
     * MIME types (`EditorInfo.contentMimeTypes`) matches `image/gif` (`image/gif`, `image/ *`
     * or `* / *`, matched case-insensitively); otherwise the GIF's address is typed as text.
     */
    fun fieldAcceptsGif(contentMimeTypes: List<String>): Boolean = contentMimeTypes.any { declared ->
        val (type, sub) = declared.trim().lowercase().split('/', limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        (type == "image" || type == "*") && (sub == "gif" || sub == "*")
    }
}
