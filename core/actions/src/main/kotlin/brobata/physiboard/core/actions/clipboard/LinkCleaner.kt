package brobata.physiboard.core.actions.clipboard

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Strips tracking from links in clipboard text. spec: expansion-clipboard-pickers-launcher.md
 * SS3.7 ("Clean links").
 *
 * Two things are done to every `http`/`https` link found in the text, and nothing else is touched:
 *
 * 1. A link that is only a redirect wrapper around another link ([REDIRECT_WRAPPERS], such as
 *    `https://www.google.com/url?q=...`) is replaced by the link it wraps, when that link decodes
 *    to a valid `http`/`https` address. Anything else (a missing or malformed target, a target
 *    that is not a web address) leaves the wrapper exactly as it was.
 * 2. Query parameters on the tracking list ([GLOBAL_PARAMETERS], [GLOBAL_PARAMETER_PREFIXES] and
 *    the per-site [SITE_PARAMETERS]) are removed.
 *
 * What stays is byte for byte what was there: the remaining parameters keep their order and their
 * own encoding (nothing is decoded and re-encoded), the fragment is kept as written, and a link
 * with nothing to remove comes back as the very same string. The `?` goes only when every
 * parameter did. Text longer than [MAX_TEXT_LENGTH] is returned untouched, so a huge copy never
 * costs the main thread a scan.
 */
object LinkCleaner {

    /** Texts longer than this are not scanned at all. */
    const val MAX_TEXT_LENGTH: Int = 64 * 1024

    /** How many wrappers deep a link is followed (a wrapper around a wrapper is real: Outlook around Google). */
    private const val MAX_UNWRAP_DEPTH = 5

    // Declared before the tables below, which read them while the object initialises.

    /** A Google host: google.com, www.google.co.uk, google.de... (the country domains, with or without www). */
    private val GOOGLE_HOST = Regex("""^(?:www\.)?google\.(?:[a-z]{2,3}|co\.[a-z]{2}|com\.[a-z]{2})$""")

    /** A link starts at `http://` or `https://` and runs to whitespace or a character no link contains bare. */
    private val LINK_IN_TEXT = Regex("""(?i)\bhttps?://[^\s<>"]+""")

    /**
     * Parameters that only ever identify a click, a campaign or a sharer, on any site. Compared
     * case-insensitively against the decoded parameter name.
     */
    val GLOBAL_PARAMETERS: Set<String> = setOf(
        // Google Ads and Analytics
        "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "gad_source", "_gl", "srsltid",
        // Meta (Facebook, Instagram)
        "fbclid", "igshid", "igsh", "mibextid", "fb_action_ids", "fb_action_types", "fb_ref", "fb_source",
        // Microsoft Advertising
        "msclkid",
        // Mailchimp
        "mc_cid", "mc_eid",
        // Yandex, Twitter/X ads, TikTok ads, LinkedIn ads, Pinterest
        "yclid", "_openstat", "twclid", "ttclid", "li_fat_id", "epik",
        // HubSpot
        "_hsenc", "_hsmi", "__hstc", "__hssc", "__hsfp", "hsctatracking",
        // Marketo, Oracle Eloqua/Olytics, Vero, Wicked Reports, Rakuten
        "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "wickedid", "rb_clickid",
        // Adobe
        "s_kwcid", "ef_id",
    )

    /** Every parameter whose decoded name starts with one of these goes (`utm_source`, `utm_medium`, ...). */
    val GLOBAL_PARAMETER_PREFIXES: List<String> = listOf("utm_")

    /**
     * Parameters that are tracking on one site but could mean something real on another (`t` is
     * a share token on X and a start time on YouTube, `si` is a sharer id on YouTube and Spotify).
     * Keyed by the site's registrable domain, which matches the domain itself and any subdomain.
     */
    val SITE_PARAMETERS: List<SiteRule> = listOf(
        SiteRule(listOf("youtube.com", "youtu.be"), setOf("si", "feature", "pp")),
        SiteRule(listOf("spotify.com", "spotify.link"), setOf("si")),
        SiteRule(listOf("twitter.com", "x.com"), setOf("s", "t", "ref_src", "ref_url")),
        SiteRule(listOf("instagram.com"), setOf("igsh", "igshid")),
        SiteRule(listOf("threads.net", "threads.com"), setOf("xmt")),
        SiteRule(listOf("tiktok.com"), setOf("is_from_webapp", "sender_device", "sender_web_id", "_r", "_t", "share_app_id", "share_link_id", "u_code")),
        SiteRule(listOf("reddit.com"), setOf("share_id", "rdt")),
        SiteRule(listOf("linkedin.com"), setOf("trk", "trackingid", "lipi", "trkemail", "midtoken", "midsig")),
        SiteRule(listOf("facebook.com"), setOf("__tn__", "sfnsn")),
        SiteRule(hostPattern = Regex("""^(?:[a-z0-9-]+\.)*amazon\.(?:[a-z]{2,3}|co\.[a-z]{2}|com\.[a-z]{2})$"""), parameters = setOf("ref_", "content-id"), prefixes = listOf("pd_rd_", "pf_rd_")),
        SiteRule(
            hostPattern = GOOGLE_HOST,
            pathPrefix = "/search",
            parameters = setOf("ved", "ei", "sca_esv", "sca_upv", "sxsrf", "gs_lcrp", "gs_lp", "sclient", "sourceid", "iflsig", "oq", "aqs", "rlz", "uact", "bih", "biw", "dpr"),
        ),
    )

    /**
     * Well-known redirect wrappers: the link the user meant sits in one parameter of the wrapper.
     * Each is matched on host and path; the first named parameter present that decodes to a valid
     * web address is the target.
     */
    val REDIRECT_WRAPPERS: List<Wrapper> = listOf(
        Wrapper(hostPattern = GOOGLE_HOST, path = "/url", parameters = listOf("q", "url")),
        Wrapper(hosts = listOf("l.facebook.com", "lm.facebook.com", "m.facebook.com", "www.facebook.com"), path = "/l.php", parameters = listOf("u")),
        Wrapper(hosts = listOf("l.messenger.com"), path = "/l.php", parameters = listOf("u")),
        Wrapper(hosts = listOf("l.instagram.com"), path = "/", parameters = listOf("u")),
        Wrapper(hosts = listOf("l.threads.net", "l.threads.com"), path = "/", parameters = listOf("u")),
        Wrapper(hosts = listOf("www.youtube.com", "youtube.com", "m.youtube.com"), path = "/redirect", parameters = listOf("q")),
        Wrapper(hosts = listOf("out.reddit.com"), path = null, parameters = listOf("url")),
        Wrapper(hosts = listOf("slack-redir.net"), path = "/link", parameters = listOf("url")),
        Wrapper(hosts = listOf("steamcommunity.com"), path = "/linkfilter/", parameters = listOf("u", "url")),
        Wrapper(hostSuffix = ".safelinks.protection.outlook.com", path = "/", parameters = listOf("url")),
        Wrapper(hosts = listOf("vk.com", "m.vk.com"), path = "/away.php", parameters = listOf("to")),
        Wrapper(hosts = listOf("duckduckgo.com"), path = "/l/", parameters = listOf("uddg")),
        Wrapper(hosts = listOf("www.linkedin.com", "linkedin.com"), path = "/redir/redirect", parameters = listOf("url")),
        Wrapper(hosts = listOf("t.umblr.com"), path = "/redirect", parameters = listOf("z")),
    )

    /**
     * Cleans every link in [text]. Returns [text] itself (the same instance) when nothing was
     * changed, so a caller can tell with `===`.
     */
    fun cleanText(text: String): String {
        if (text.length > MAX_TEXT_LENGTH || !text.contains("://")) return text
        var changed = false
        val out = StringBuilder(text.length)
        var last = 0
        for (match in LINK_IN_TEXT.findAll(text)) {
            val (link, trailing) = splitTrailingPunctuation(match.value)
            val cleaned = cleanUrl(link)
            if (cleaned === link) continue
            changed = true
            out.append(text, last, match.range.first).append(cleaned).append(trailing)
            last = match.range.last + 1
        }
        if (!changed) return text
        out.append(text, last, text.length)
        return out.toString()
    }

    /**
     * Cleans one link. Anything that is not a recognised `http`/`https` URL comes back unchanged,
     * and so does a URL with nothing to remove (as the same instance).
     */
    fun cleanUrl(url: String): String {
        var current = ParsedUrl.parse(url) ?: return url
        var depth = 0
        while (depth < MAX_UNWRAP_DEPTH) {
            val target = unwrap(current) ?: break
            current = target
            depth++
        }
        val stripped = stripTracking(current)
        val result = stripped ?: current
        if (depth == 0 && stripped == null) return url
        return result.toString()
    }

    /** One per-site parameter rule. A host matches [domains] (the domain or any subdomain), or [hostPattern]. */
    data class SiteRule(
        val domains: List<String> = emptyList(),
        val parameters: Set<String> = emptySet(),
        val prefixes: List<String> = emptyList(),
        val hostPattern: Regex? = null,
        val pathPrefix: String? = null,
    ) {
        fun matches(host: String, path: String): Boolean {
            val hostOk = hostPattern?.matches(host) ?: domains.any { host == it || host.endsWith(".$it") }
            return hostOk && (pathPrefix == null || path.startsWith(pathPrefix))
        }

        fun isTracking(name: String): Boolean = name in parameters || prefixes.any { name.startsWith(it) }
    }

    /** One redirect wrapper. [path] null means any path. */
    data class Wrapper(
        val hosts: List<String> = emptyList(),
        val path: String?,
        val parameters: List<String>,
        val hostPattern: Regex? = null,
        val hostSuffix: String? = null,
    ) {
        fun matches(host: String, urlPath: String): Boolean {
            val hostOk = when {
                hostPattern != null -> hostPattern.matches(host)
                hostSuffix != null -> host.endsWith(hostSuffix)
                else -> host in hosts
            }
            if (!hostOk) return false
            if (path == null) return true
            val actual = urlPath.ifEmpty { "/" }
            return actual == path || (path.endsWith("/") && actual == path.dropLast(1))
        }
    }

    private fun unwrap(url: ParsedUrl): ParsedUrl? {
        val query = url.query ?: return null
        if (';' in query) return null
        val wrapper = REDIRECT_WRAPPERS.firstOrNull { it.matches(url.host, url.path) } ?: return null
        val pieces = query.split('&')
        for (name in wrapper.parameters) {
            val piece = pieces.firstOrNull { decodeComponent(it.substringBefore('='))?.lowercase() == name && it.contains('=') } ?: continue
            val raw = piece.substringAfter('=')
            // A target written into the wrapper without encoding has already been cut: its own
            // query at the wrapper's next `&`, its fragment at the wrapper's `#`. Which parts were
            // its own cannot be known, so such a wrapper is left alone rather than shortened.
            if ("://" in raw && ('?' in raw || url.fragment != null)) continue
            val decoded = decodeComponent(raw, plusIsSpace = true) ?: continue
            val target = ParsedUrl.parse(decoded) ?: continue
            if (decoded.any { it.isWhitespace() || it.isISOControl() }) continue
            return target
        }
        return null
    }

    /** The URL without its tracking parameters, or null when it has none. */
    private fun stripTracking(url: ParsedUrl): ParsedUrl? {
        val query = url.query ?: return null
        // An empty query has nothing to remove; one using `;` between parameters cannot be split safely.
        if (query.isEmpty() || ';' in query) return null
        val siteRules = SITE_PARAMETERS.filter { it.matches(url.host, url.path) }
        val pieces = query.split('&')
        val kept = pieces.filter { piece ->
            if (piece.isEmpty()) return@filter true
            val name = (decodeComponent(piece.substringBefore('=')) ?: piece.substringBefore('=')).lowercase()
            !(name in GLOBAL_PARAMETERS || GLOBAL_PARAMETER_PREFIXES.any { name.startsWith(it) } || siteRules.any { it.isTracking(name) })
        }
        if (kept.size == pieces.size) return null
        val remaining = kept.filter { it.isNotEmpty() }
        return url.copy(query = if (remaining.isEmpty()) null else remaining.joinToString("&"))
    }

    /**
     * A link ends where trailing sentence punctuation starts: "see https://x.com/a?utm_source=b."
     * keeps its full stop outside the link. A closing bracket is the link's own only when the link
     * opened one (Wikipedia's `Foo_(bar)`).
     */
    internal fun splitTrailingPunctuation(token: String): Pair<String, String> {
        var end = token.length
        while (end > 0) {
            val c = token[end - 1]
            val drop = when (c) {
                '.', ',', ';', ':', '!', '?', '\'', '"', '…' -> true
                ')' -> token.substring(0, end).count { it == '(' } < token.substring(0, end).count { it == ')' }
                ']' -> token.substring(0, end).count { it == '[' } < token.substring(0, end).count { it == ']' }
                '}' -> token.substring(0, end).count { it == '{' } < token.substring(0, end).count { it == '}' }
                else -> false
            }
            if (!drop) break
            end--
        }
        return token.substring(0, end) to token.substring(end)
    }

    /**
     * Percent-decodes one URL component as UTF-8; null when it is malformed (a bad escape or
     * bytes that are not UTF-8), so a caller never acts on a half-decoded value.
     */
    internal fun decodeComponent(value: String, plusIsSpace: Boolean = false): String? {
        if (!value.contains('%') && !(plusIsSpace && value.contains('+'))) return value
        val bytes = ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' -> {
                    if (i + 2 >= value.length) return null
                    val hi = Character.digit(value[i + 1], 16)
                    val lo = Character.digit(value[i + 2], 16)
                    if (hi < 0 || lo < 0) return null
                    bytes.write(hi * 16 + lo)
                    i += 3
                    continue
                }
                c == '+' && plusIsSpace -> bytes.write(' '.code)
                else -> bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            }
            i++
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /** An `http`/`https` URL cut into the parts this cleaner touches; every part is the original text. */
    internal data class ParsedUrl(
        val schemeAndAuthority: String,
        val host: String,
        val path: String,
        val query: String?,
        val fragment: String?,
    ) {
        override fun toString(): String = buildString {
            append(schemeAndAuthority).append(path)
            if (query != null) append('?').append(query)
            if (fragment != null) append('#').append(fragment)
        }

        companion object {
            fun parse(url: String): ParsedUrl? {
                val schemeEnd = url.indexOf("://")
                if (schemeEnd <= 0) return null
                val scheme = url.substring(0, schemeEnd).lowercase()
                if (scheme != "http" && scheme != "https") return null
                if (url.any { it.isWhitespace() || it.isISOControl() }) return null
                val authorityStart = schemeEnd + 3
                val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) url.length else it }
                val authority = url.substring(authorityStart, authorityEnd)
                val host = hostOf(authority) ?: return null
                val fragmentStart = url.indexOf('#', authorityEnd)
                val beforeFragment = if (fragmentStart < 0) url.length else fragmentStart
                val queryStart = url.indexOf('?', authorityEnd).takeIf { it in 0 until beforeFragment }
                val pathEnd = queryStart ?: beforeFragment
                return ParsedUrl(
                    schemeAndAuthority = url.substring(0, authorityEnd),
                    host = host,
                    path = url.substring(authorityEnd, pathEnd),
                    query = queryStart?.let { url.substring(it + 1, beforeFragment) },
                    fragment = if (fragmentStart < 0) null else url.substring(fragmentStart + 1),
                )
            }

            private fun hostOf(authority: String): String? {
                val hostAndPort = authority.substringAfterLast('@')
                val host = if (hostAndPort.startsWith("[")) {
                    hostAndPort.substringBefore(']', missingDelimiterValue = "").takeIf { it.isNotEmpty() }?.plus("]") ?: return null
                } else {
                    hostAndPort.substringBefore(':')
                }
                val normalized = host.lowercase().trimEnd('.')
                return normalized.takeIf { it.isNotEmpty() && it.none { c -> c == '%' } }
            }
        }
    }

}
