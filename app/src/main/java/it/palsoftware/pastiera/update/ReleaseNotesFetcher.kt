package it.palsoftware.pastiera.update

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

private const val GITHUB_RELEASES_WITH_BODY_URL =
    "https://api.github.com/repos/palsoftware/pastiera/releases?per_page=30"

private val releaseNotesClient = OkHttpClient()
private val releaseNotesHandler = Handler(Looper.getMainLooper())

data class ReleaseNotesSummary(
    val version: String,
    val title: String,
    val highlights: List<String>
) {
    companion object {
        fun fallback(version: String): ReleaseNotesSummary =
            ReleaseNotesSummary(
                version = version,
                title = "Pastiera $version",
                highlights = listOf(
                    "New QuickLauncher for fast keyboard-driven app launching.",
                    "More flexible status bar, variation, and shortcut controls.",
                    "Improved hardware support for Titan 2 Elite, MP01, Q25, and related profiles.",
                    "App-specific Enter behavior for messenger-style text entry.",
                    "Many smaller fixes around layouts, symbols, suggestions, and release stability."
                )
            )
    }
}

fun fetchReleaseNotesForVersion(
    version: String,
    releaseChannel: String,
    callback: (ReleaseNotesSummary?) -> Unit
) {
    val normalizedVersion = normalizeReleaseVersion(version)
    if (normalizedVersion.isBlank()) {
        postReleaseNotes(callback, null)
        return
    }

    val request = Request.Builder()
        .url(GITHUB_RELEASES_WITH_BODY_URL)
        .header("Accept", "application/vnd.github+json")
        .build()

    releaseNotesClient.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            postReleaseNotes(callback, null)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                if (!res.isSuccessful) {
                    postReleaseNotes(callback, null)
                    return
                }

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postReleaseNotes(callback, null)
                    return
                }

                val releases = JSONArray(body)
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue

                    val tagName = release.optString("tag_name").takeIf(String::isNotBlank) ?: continue
                    if (normalizeReleaseVersion(tagName) != normalizedVersion) continue
                    if (!matchesReleaseChannel(tagName, release.optBoolean("prerelease"), releaseChannel)) continue

                    postReleaseNotes(
                        callback,
                        ReleaseNotesSummary(
                            version = tagName,
                            title = release.optString("name").takeIf(String::isNotBlank) ?: "Pastiera $tagName",
                            highlights = parseReleaseHighlights(release.optString("body", ""))
                        )
                    )
                    return
                }

                postReleaseNotes(callback, null)
            }
        }
    })
}

private fun matchesReleaseChannel(tagName: String, prerelease: Boolean, releaseChannel: String): Boolean {
    return when (releaseChannel.lowercase()) {
        "nightly" -> prerelease && tagName.startsWith("nightly/")
        else -> !prerelease
    }
}

private fun parseReleaseHighlights(body: String): List<String> {
    val lines = body.lines()
    val selected = mutableListOf<String>()
    var inPreferredSection = false
    var sawPreferredSection = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) {
            val heading = trimmed.trimStart('#').trim().lowercase()
            inPreferredSection = heading.contains("highlight") ||
                heading.contains("what") ||
                heading.contains("feature")
            sawPreferredSection = sawPreferredSection || inPreferredSection
            if (sawPreferredSection && !inPreferredSection && selected.isNotEmpty()) break
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            if (!sawPreferredSection || inPreferredSection) {
                selected += cleanMarkdownBullet(trimmed.drop(2))
            }
        }

        if (selected.size >= 8) break
    }

    return selected
        .filter { it.isNotBlank() }
        .distinct()
        .take(8)
}

private fun cleanMarkdownBullet(text: String): String {
    return text
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .trim()
}

private fun postReleaseNotes(
    callback: (ReleaseNotesSummary?) -> Unit,
    summary: ReleaseNotesSummary?
) {
    releaseNotesHandler.post {
        callback(summary)
    }
}
