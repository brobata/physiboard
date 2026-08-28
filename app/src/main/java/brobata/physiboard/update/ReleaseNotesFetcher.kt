package brobata.physiboard.update

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import brobata.physiboard.BuildConfig

/**
 * PhysiBoard's own releases, read from the GitHub API.
 *
 * This used to fetch `pastiera.eu/releases/<version>/<lang>.json` — upstream's site. For a fork
 * that was wrong twice over: the versions there never match ours, and the notes describe a
 * different app. GitHub has no per-language notes, so the language argument now only chooses the
 * offline fallback text.
 */
private val RELEASE_NOTES_API_URL = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/tags"
private val RELEASES_PAGE_URL = "https://github.com/${BuildConfig.GITHUB_REPO}/releases"

private val releaseNotesClient = OkHttpClient()
private val releaseNotesHandler = Handler(Looper.getMainLooper())

data class ReleaseNotesSummary(
    val version: String,
    val title: String,
    val highlights: List<String>,
    val improvements: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList(),
    val docsUrl: String = RELEASES_PAGE_URL
) {
    companion object {
        fun fallback(version: String, languageTag: String = "en"): ReleaseNotesSummary {
            // Shown when the notes cannot be fetched. It deliberately makes no claims about what
            // changed: the previous version hardcoded upstream Pastiera's feature list, which was
            // wrong for this app and went stale the moment it was written.
            val language = normalizeReleaseNotesLanguage(languageTag)
            return ReleaseNotesSummary(
                version = version,
                title = "PhysiBoard $version",
                highlights = when (language) {
                    "de" -> listOf("Die vollständigen Notizen zu dieser Version stehen auf der GitHub-Releases-Seite.")
                    "it" -> listOf("Le note complete di questa versione sono sulla pagina delle release su GitHub.")
                    else -> listOf("The full notes for this release are on the GitHub releases page.")
                },
                docsUrl = RELEASES_PAGE_URL
            )
        }
    }
}

fun fetchReleaseNotesForVersion(
    version: String,
    languageTag: String,
    callback: (ReleaseNotesSummary?) -> Unit
) {
    val normalizedVersion = normalizeReleaseVersion(version)
    if (normalizedVersion.isBlank()) {
        postReleaseNotes(callback, null)
        return
    }

    val preferredLanguage = normalizeReleaseNotesLanguage(languageTag)
    fetchReleaseNotesFromDocs(
        normalizedVersion = normalizedVersion,
        language = preferredLanguage,
        allowEnglishFallback = preferredLanguage != "en",
        callback = callback
    )
}

private fun fetchReleaseNotesFromDocs(
    normalizedVersion: String,
    language: String,
    allowEnglishFallback: Boolean,
    callback: (ReleaseNotesSummary?) -> Unit
) {
    // Releases are tagged vX.Y.Z. GitHub serves one set of notes per tag, so `language` and
    // `allowEnglishFallback` no longer affect the request — they are kept for the caller's shape.
    val request = Request.Builder()
        .url("$RELEASE_NOTES_API_URL/v$normalizedVersion")
        .header("Accept", "application/vnd.github+json")
        .build()

    releaseNotesClient.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            postReleaseNotes(callback, null)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                // A tag with no release yet is a 404, which is normal rather than an error.
                if (!res.isSuccessful) {
                    postReleaseNotes(callback, null)
                    return
                }
                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postReleaseNotes(callback, null)
                    return
                }
                postReleaseNotes(callback, parseReleaseNotesJson(body, normalizedVersion))
            }
        }
    })
}

private fun parseReleaseNotesJson(body: String, expectedVersion: String): ReleaseNotesSummary? {
    return runCatching {
        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        if (tag.isNotBlank() && normalizeReleaseVersion(tag) != expectedVersion) return@runCatching null

        // GitHub release bodies are markdown. Bullet lines carry the substance; headings, blank
        // lines and prose are dropped rather than shown as pseudo-highlights.
        val highlights = json.optString("body")
            .lineSequence()
            .map(String::trim)
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            // Notes are written as "- **Lead in.** then prose", so the bold markers sit mid-line and
            // stripping only matched ends would leave asterisks on screen.
            .map { it.removeRange(0, 2).replace("**", "").trim() }
            .filter(String::isNotBlank)
            .take(8)
            .toList()
        if (highlights.isEmpty()) return@runCatching null

        ReleaseNotesSummary(
            version = expectedVersion,
            title = json.optString("name").takeIf(String::isNotBlank) ?: "PhysiBoard $expectedVersion",
            highlights = highlights,
            docsUrl = json.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                ?: RELEASES_PAGE_URL
        )
    }.getOrNull()
}

private fun normalizeReleaseNotesLanguage(languageTag: String): String {
    val language = languageTag
        .substringBefore('-')
        .substringBefore('_')
        .lowercase()
        .filter { it in 'a'..'z' }
    return language.ifBlank { "en" }
}

private fun postReleaseNotes(
    callback: (ReleaseNotesSummary?) -> Unit,
    summary: ReleaseNotesSummary?
) {
    releaseNotesHandler.post {
        callback(summary)
    }
}
