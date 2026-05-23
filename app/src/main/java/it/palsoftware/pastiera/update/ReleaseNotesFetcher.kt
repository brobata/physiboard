package it.palsoftware.pastiera.update

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

private const val RELEASE_NOTES_BASE_URL = "https://pastiera.eu/releases"

private val releaseNotesClient = OkHttpClient()
private val releaseNotesHandler = Handler(Looper.getMainLooper())

data class ReleaseNotesSummary(
    val version: String,
    val title: String,
    val highlights: List<String>
) {
    companion object {
        fun fallback(version: String, languageTag: String = "en"): ReleaseNotesSummary {
            val language = normalizeReleaseNotesLanguage(languageTag)
            return ReleaseNotesSummary(
                version = version,
                title = when (language) {
                    "de" -> "Pastiera $version"
                    "it" -> "Pastiera $version"
                    else -> "Pastiera $version"
                },
                highlights = when (language) {
                    "de" -> listOf(
                        "Ein überarbeitetes Tutorial zeigt nach dem Upgrade die wichtigsten neuen Einstellungen.",
                        "Der QuickLauncher lässt sich jetzt auch aus Textfeldern mit SYM + Leertaste öffnen.",
                        "Die Statusleiste, Variationen und Shortcuts sind flexibler konfigurierbar.",
                        "App-spezifische Messenger-Presets unterstützen optional SYM + Enter zum Senden.",
                        "Viele kleinere Fixes verbessern Layouts, Symbole, Vorschläge und Release-Stabilität."
                    )
                    "it" -> listOf(
                        "Il tutorial aggiornato presenta dopo l’upgrade le impostazioni nuove più importanti.",
                        "QuickLauncher ora si può aprire anche dai campi di testo con SYM + Spazio.",
                        "Barra di stato, variazioni e scorciatoie sono più flessibili da configurare.",
                        "I preset messenger per app supportano una scorciatoia opzionale SYM + Invio per inviare.",
                        "Molti fix minori migliorano layout, simboli, suggerimenti e stabilità del rilascio."
                    )
                    else -> listOf(
                    "New QuickLauncher for fast keyboard-driven app launching.",
                    "More flexible status bar, variation, and shortcut controls.",
                    "Improved hardware support for Titan 2 Elite, MP01, Q25, and related profiles.",
                    "App-specific Enter behavior for messenger-style text entry.",
                    "Many smaller fixes around layouts, symbols, suggestions, and release stability."
                )
                }
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
    val request = Request.Builder()
        .url("$RELEASE_NOTES_BASE_URL/$normalizedVersion/$language.json")
        .header("Accept", "application/json")
        .build()

    releaseNotesClient.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (allowEnglishFallback) {
                fetchReleaseNotesFromDocs(normalizedVersion, "en", false, callback)
            } else {
                postReleaseNotes(callback, null)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                if (!res.isSuccessful) {
                    if (allowEnglishFallback) {
                        fetchReleaseNotesFromDocs(normalizedVersion, "en", false, callback)
                    } else {
                        postReleaseNotes(callback, null)
                    }
                    return
                }

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postReleaseNotes(callback, null)
                    return
                }

                val notes = parseReleaseNotesJson(body, normalizedVersion)
                postReleaseNotes(callback, notes)
            }
        }
    })
}

private fun parseReleaseNotesJson(body: String, expectedVersion: String): ReleaseNotesSummary? {
    return runCatching {
        val json = JSONObject(body)
        val version = json.optString("version", expectedVersion).takeIf(String::isNotBlank) ?: expectedVersion
        if (normalizeReleaseVersion(version) != expectedVersion) return@runCatching null

        val highlightsJson = json.optJSONArray("highlights") ?: return@runCatching null
        val highlights = buildList {
            for (index in 0 until highlightsJson.length()) {
                val highlight = highlightsJson.optString(index).trim()
                if (highlight.isNotBlank()) add(highlight)
                if (size >= 8) break
            }
        }
        if (highlights.isEmpty()) return@runCatching null

        ReleaseNotesSummary(
            version = version,
            title = json.optString("title").takeIf(String::isNotBlank) ?: "Pastiera $version",
            highlights = highlights
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
