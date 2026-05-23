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
    val highlights: List<String>,
    val improvements: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList(),
    val docsUrl: String = "https://pastiera.eu/"
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
                        "QuickLauncher öffnet Apps direkt per Tastatur und funktioniert auch aus Textfeldern mit SYM + Leertaste.",
                        "Statusleiste und Variationen sind flexibler steuerbar und können besser an deinen Workflow angepasst werden.",
                        "App-spezifische Messenger-Presets unterstützen optional SYM + Enter zum Senden.",
                        "Nav Mode wurde um Wortnavigation, Mediensteuerung und neue Ctrl-Optionen erweitert."
                    )
                    "it" -> listOf(
                        "QuickLauncher apre app direttamente dalla tastiera e funziona anche dai campi di testo con SYM + Spazio.",
                        "Barra di stato e variazioni sono più flessibili e adattabili al tuo flusso.",
                        "I preset messenger per app supportano una scorciatoia opzionale SYM + Invio per inviare.",
                        "Nav Mode aggiunge navigazione per parole, controlli multimediali e nuove opzioni Ctrl."
                    )
                    else -> listOf(
                        "QuickLauncher opens apps directly from the keyboard and also works from text fields with SYM + Space.",
                        "Status bar and variation controls are more flexible and easier to adapt to your workflow.",
                        "App-specific messenger presets support an optional SYM + Enter send shortcut.",
                        "Nav Mode adds word navigation, media controls, and new Ctrl options."
                    )
                },
                improvements = when (language) {
                    "de" -> listOf("Viele kleinere Verbesserungen betreffen Symbole, Vorschläge, Backups, Shortcuts und Release-Stabilität.")
                    "it" -> listOf("Molti miglioramenti minori riguardano simboli, suggerimenti, backup, scorciatoie e stabilità del rilascio.")
                    else -> listOf("Many smaller improvements cover symbols, suggestions, backups, shortcuts, and release stability.")
                },
                bugFixes = when (language) {
                    "de" -> listOf("Keyboard-Layouts und Subtype-Auswahl respektieren die aktive Sprache zuverlässiger.", "Mehrere Hardware-Mappings und Eingabekantenfälle wurden korrigiert.")
                    "it" -> listOf("Layout tastiera e selezione subtype rispettano meglio la lingua attiva.", "Sono stati corretti diversi mapping hardware e casi limite di input.")
                    else -> listOf("Keyboard layouts and subtype selection more reliably respect the active language.", "Several hardware mapping and input edge cases were fixed.")
                },
                docsUrl = when (language) {
                    "de" -> "https://pastiera.eu/de/"
                    "it" -> "https://pastiera.eu/it/"
                    else -> "https://pastiera.eu/"
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

        val highlights = parseStringArray(json, "highlights", 8)
        if (highlights.isEmpty()) return@runCatching null

        ReleaseNotesSummary(
            version = version,
            title = json.optString("title").takeIf(String::isNotBlank) ?: "Pastiera $version",
            highlights = highlights,
            improvements = parseStringArray(json, "improvements", 8),
            bugFixes = parseStringArray(json, "bugFixes", 12),
            docsUrl = json.optString("docsUrl")
                .takeIf { it.startsWith("https://pastiera.eu/") }
                ?: "https://pastiera.eu/"
        )
    }.getOrNull()
}

private fun parseStringArray(json: JSONObject, key: String, limit: Int): List<String> {
    val array = json.optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) add(value)
            if (size >= limit) break
        }
    }
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
