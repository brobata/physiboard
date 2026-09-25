package brobata.physiboard.core.shell

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The release-notes fetcher's parsed result: a title, up to 8 highlight lines, and where the full
 * notes live. spec: app-shell.md SS14. Documented because the version-matching rule is a data
 * contract worth keeping even though nothing in 2.x's reachable UI calls it (SS21).
 */
data class ReleaseNotes(val title: String, val highlights: List<String>, val docsUrl: String)

/**
 * Parses a single GitHub release lookup (`GET .../releases/tags/v<version>`) into [ReleaseNotes].
 * spec: SS14.
 */
object ReleaseNotesFeed {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val MAX_HIGHLIGHTS = 8

    /**
     * A success is accepted only when the response's `tag_name`, normalized, equals
     * [requestedVersion]; a 404 or any parse failure yields null (T22 shows the mismatch case).
     */
    fun parse(body: String, requestedVersion: String): ReleaseNotes? {
        val obj = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val tagName = (obj["tag_name"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (VersionComparison.normalize(tagName) != VersionComparison.normalize(requestedVersion)) return null

        val bodyText = (obj["body"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val highlights = bodyText.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.removePrefix("- ").removePrefix("* ").replace("**", "").trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_HIGHLIGHTS)
        if (highlights.isEmpty()) return null

        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull
        val title = if (!name.isNullOrBlank()) name else "PhysiBoard $requestedVersion"
        val htmlUrl = (obj["html_url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val docsUrl = if (htmlUrl.startsWith("https://github.com/")) htmlUrl else "https://github.com/brobata/physiboard/releases"
        return ReleaseNotes(title, highlights, docsUrl)
    }

    /** spec: SS14, the offline fallback sentence. The language tag only ever picks one of these three. */
    fun offlineFallback(languageTag: String): String = when (languageTag.substringBefore('-').lowercase()) {
        "de" -> "Die vollständigen Hinweise zu dieser Version findest du auf der GitHub-Releases-Seite."
        "it" -> "Le note complete di questa versione sono nella pagina delle release di GitHub."
        else -> "The full notes for this release are on the GitHub releases page."
    }
}
