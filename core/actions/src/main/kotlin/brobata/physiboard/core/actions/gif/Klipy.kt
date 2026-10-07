package brobata.physiboard.core.actions.gif

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder

/**
 * One GIF as the page uses it: what to show in the grid (the smallest animated rendition) and
 * what to send (a mid-sized GIF). URLs are kept exactly as the provider returned them, which
 * KLIPY's integration rules require. spec: layers-sym-alt.md SS4.5.
 */
data class GifItem(
    val slug: String,
    val title: String,
    val previewUrl: String,
    val previewWidth: Int,
    val previewHeight: Int,
    val sendUrl: String,
    val sendWidth: Int,
    val sendHeight: Int,
)

/** One page of results, in the provider's order. */
data class GifResultPage(val items: List<GifItem>, val page: Int, val hasNext: Boolean)

/**
 * The KLIPY API (https://docs.klipy.com, the Tenor-compatible service that replaced Tenor's API
 * after Google shut it on 2026-06-30). Pure: it builds request URLs and reads response bodies;
 * the caller does the request through the network gate. spec: layers-sym-alt.md SS4.5.
 *
 * Endpoints used: `GET api/v1/{app_key}/gifs/search` and `GET api/v1/{app_key}/gifs/trending`.
 * The share trigger (`POST .../gifs/share/{slug}`) is optional in KLIPY's documentation and only
 * feeds their personalisation, so it is never sent; no `customer_id` is ever sent either.
 */
object Klipy {
    const val BASE_URL = "https://api.klipy.com/api/v1/"
    const val PER_PAGE = 24
    const val CONTENT_FILTER = "medium"

    /** Only the two formats the page can use; a smaller response than all five. */
    const val FORMAT_FILTER = "gif,webp"

    /** The largest search or trending body read (a page of 24 is about 60 KB). */
    const val MAX_LIST_BYTES = 1_000_000

    fun searchUrl(apiKey: String, query: String, page: Int = 1, country: String? = null): String =
        BASE_URL + enc(apiKey) + "/gifs/search?" + params(page, country, "q" to query.trim())

    fun trendingUrl(apiKey: String, page: Int = 1, country: String? = null): String =
        BASE_URL + enc(apiKey) + "/gifs/trending?" + params(page, country)

    private fun params(page: Int, country: String?, vararg extra: Pair<String, String>): String {
        val all = ArrayList<Pair<String, String>>()
        all.add("page" to page.coerceAtLeast(1).toString())
        all.add("per_page" to PER_PAGE.toString())
        all.addAll(extra)
        country?.trim()?.lowercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.let { all.add("locale" to it) }
        all.add("content_filter" to CONTENT_FILTER)
        all.add("format_filter" to FORMAT_FILTER)
        return all.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    /**
     * Reads a search or trending body: `{"result":true,"data":{"data":[...],"current_page":1,
     * "per_page":24,"has_next":true}}`. Returns null when the body is not that shape or `result`
     * is false. An item with no usable rendition is skipped (nothing could be drawn or sent);
     * every other item keeps its place. A body without `current_page` counts as [requestedPage],
     * so "load more" never asks for the same page twice.
     */
    fun parse(body: String, requestedPage: Int = 1): GifResultPage? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        if (root.bool("result") == false) return null
        val data = root["data"] as? JsonObject ?: return null
        val list = data["data"] as? JsonArray ?: return null
        val items = list.mapNotNull { (it as? JsonObject)?.let(::item) }
        return GifResultPage(items, page = data.int("current_page") ?: requestedPage, hasNext = data.bool("has_next") ?: false)
    }

    private fun item(obj: JsonObject): GifItem? {
        val slug = obj.str("slug") ?: obj.str("id") ?: return null
        val file = obj["file"] as? JsonObject ?: return null
        val preview = firstRendition(file, PREVIEW_ORDER) ?: return null
        val send = firstRendition(file, SEND_ORDER) ?: return null
        return GifItem(
            slug = slug,
            title = obj.str("title").orEmpty(),
            previewUrl = preview.url, previewWidth = preview.width, previewHeight = preview.height,
            sendUrl = send.url, sendWidth = send.width, sendHeight = send.height,
        )
    }

    private class Rendition(val url: String, val width: Int, val height: Int)

    /** Smallest first; animated WebP is smaller than GIF and decodes on every supported Android. */
    private val PREVIEW_ORDER = listOf("xs" to "webp", "xs" to "gif", "sm" to "webp", "sm" to "gif", "md" to "webp", "md" to "gif")

    /** A GIF for the receiving app: `md` is about 220 px wide and a few hundred KB; `hd` only as a last resort. */
    private val SEND_ORDER = listOf("md" to "gif", "sm" to "gif", "hd" to "gif", "xs" to "gif")

    private fun firstRendition(file: JsonObject, order: List<Pair<String, String>>): Rendition? {
        for ((size, format) in order) {
            val r = (file[size] as? JsonObject)?.get(format) as? JsonObject ?: continue
            val url = r.str("url")?.takeIf { it.startsWith("https://") } ?: continue
            return Rendition(url, r.int("width") ?: 0, r.int("height") ?: 0)
        }
        return null
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}
