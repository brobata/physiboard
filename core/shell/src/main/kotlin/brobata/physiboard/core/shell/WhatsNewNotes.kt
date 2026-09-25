package brobata.physiboard.core.shell

/** One note on the what's-new page: a possibly-blank title over a possibly-blank body. spec: app-shell.md SS5.2. */
data class WhatsNewNote(val title: String, val body: String)

/**
 * Parses the generated `common/whats_new.md` asset into the cards the what's-new page shows.
 * spec: app-shell.md SS5.2. Pure text in, pure notes out: the asset itself is produced by
 * [ChangeRecordSection] at build time.
 */
object WhatsNewNotes {

    /** spec: SS5.2. Blank input yields no notes (T17). */
    fun parse(markdown: String): List<WhatsNewNote> {
        val lines = markdown.lines()
        val notes = mutableListOf<WhatsNewNote>()
        val preamble = mutableListOf<String>()
        var sawBullet = false
        val bulletBuffer = StringBuilder()

        fun flushBullet() {
            if (bulletBuffer.isEmpty()) return
            notes += splitBullet(bulletBuffer.toString())
            bulletBuffer.clear()
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("- ")) {
                flushBullet()
                sawBullet = true
                bulletBuffer.append(line.removePrefix("- ").trim())
            } else if (line.isEmpty()) {
                if (!sawBullet) preamble += "" // a blank line inside the preamble becomes a newline
            } else if (!sawBullet) {
                preamble += line
            } else {
                // a non-empty line following a bullet that does not start a new one: a wrapped bullet
                bulletBuffer.append(' ').append(line)
            }
        }
        flushBullet()

        val preambleText = joinPreamble(preamble)
        val result = mutableListOf<WhatsNewNote>()
        if (preambleText.isNotEmpty()) result += WhatsNewNote(title = "", body = preambleText)
        result += notes
        return result
    }

    private fun joinPreamble(lines: List<String>): String {
        // consecutive non-blank lines join with a space; a blank line becomes a newline
        val sb = StringBuilder()
        var previousWasBlank = false
        for ((index, line) in lines.withIndex()) {
            if (line.isEmpty()) {
                if (index != 0) sb.append('\n')
                previousWasBlank = true
            } else {
                if (index != 0 && !previousWasBlank) sb.append(' ')
                sb.append(line)
                previousWasBlank = false
            }
        }
        return sb.toString().trim()
    }

    private fun splitBullet(rawBullet: String): WhatsNewNote {
        // The bold-lead check runs on the raw text: stripping "**" first would erase the very
        // marker this branch looks for.
        val boldMatch = Regex("^\\*\\*(.+?)\\*\\*").find(rawBullet)
        if (boldMatch != null) {
            val title = stripInlineMarkup(boldMatch.groupValues[1]).trimEnd('.', ':').trim()
            val rest = rawBullet.substring(boldMatch.range.last + 1).trimStart(' ', '—', '-', ':')
            val body = stripInlineMarkup(rest).trim()
            return WhatsNewNote(title, body)
        }
        val bullet = stripInlineMarkup(rawBullet)
        val dashIndex = bullet.indexOf(" — ")
        if (dashIndex >= 0) {
            val title = bullet.substring(0, dashIndex).trim()
            val body = bullet.substring(dashIndex + 3).trim()
            return WhatsNewNote(title, body)
        }
        return WhatsNewNote(title = bullet.trim(), body = "")
    }

    /**
     * spec: SS5.2. `**` and backticks are removed everywhere; `*text*` becomes `text` only when
     * the asterisks are not adjacent to word characters or other asterisks, so `2*3` survives.
     */
    private fun stripInlineMarkup(text: String): String {
        var result = text.replace("**", "").replace("`", "")
        result = Regex("(?<![\\w*])\\*([^*]+)\\*(?![\\w*])").replace(result) { it.groupValues[1] }
        return result
    }
}

/**
 * Builds the `common/whats_new.md` asset from `PHYSIBOARD_CHANGES.md` at build time. spec:
 * app-shell.md SS5.1. Pure text transform so the "find the right section" rule is a JVM test
 * (T18, T19) instead of something only a release proves.
 */
object ChangeRecordSection {

    private val versionHeading = Regex("^##\\s+(\\d+\\.\\d+(?:\\.\\d+)?)")

    /**
     * Finds the section whose heading is `## <versionName> ` (a trailing space, so "2.0.7" does
     * not match "2.0.71"); failing that, the first heading matching `## <digits>.<digits>` so an
     * "Unreleased" section never reaches users. Keeps the section's lines up to, not including,
     * the first trimmed line starting with `<!-- /card -->`; a section with no numeric heading at
     * all yields an empty string.
     */
    fun extractCard(changeRecord: String, versionName: String): String {
        val lines = changeRecord.lines()
        val headingIndex = lines.indexOfFirst { it.startsWith("## $versionName ") }
        val startIndex = if (headingIndex >= 0) headingIndex else lines.indexOfFirst { versionHeading.containsMatchIn(it) }
        if (startIndex < 0) return ""

        val body = mutableListOf<String>()
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            if (line.startsWith("## ")) break
            if (line.trim().startsWith("<!-- /card -->")) break
            body += line
        }
        return body.joinToString("\n").trim().let { if (it.isEmpty()) "" else it + "\n" }
    }
}

/** spec: app-shell.md SS3, step 2: whether the what's-new note is due for the launcher icon. */
object WhatsNewDue {
    /**
     * Due when [tutorialCompleted] is true, [currentVersionName] is non-blank, and
     * [lastSeenWhatsNewVersion] differs from it (including being absent, modeled as null). T25.
     */
    fun isDue(tutorialCompleted: Boolean, lastSeenWhatsNewVersion: String?, currentVersionName: String): Boolean {
        if (!tutorialCompleted) return false
        if (currentVersionName.isBlank()) return false
        return lastSeenWhatsNewVersion != currentVersionName
    }
}
