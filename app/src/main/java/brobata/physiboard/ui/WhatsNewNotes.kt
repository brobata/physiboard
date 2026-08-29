package brobata.physiboard.ui

/**
 * Parses the release's own section of the change record, which the build copies into
 * `assets/common/whats_new.md`, into title/body pairs for the what's-new card.
 *
 * A bullet is `- **Title** body` or `- **Title** — body`, wrapped over any number of lines.
 * Paragraphs above the first bullet (the release preamble) become a single untitled entry.
 * Inline emphasis and code marks are stripped; the card renders plain text.
 */
object WhatsNewNotes {

    data class Note(val title: String, val body: String)

    fun parse(markdown: String): List<Note> {
        val bullets = mutableListOf<String>()
        val preamble = StringBuilder()
        var inBullets = false
        markdown.lines().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("- ") -> {
                    inBullets = true
                    bullets.add(line.removePrefix("- "))
                }
                line.isEmpty() -> if (!inBullets && preamble.isNotEmpty()) preamble.append('\n')
                inBullets -> bullets[bullets.lastIndex] = bullets.last() + " " + line
                else -> {
                    if (preamble.isNotEmpty() && !preamble.endsWith('\n')) preamble.append(' ')
                    preamble.append(line)
                }
            }
        }
        val notes = mutableListOf<Note>()
        val intro = preamble.toString().trim()
        if (intro.isNotEmpty()) notes.add(Note("", stripInline(intro)))
        bullets.mapTo(notes) { splitBullet(it) }
        return notes
    }

    private fun splitBullet(bullet: String): Note {
        val bold = BOLD_LEAD.find(bullet)
        if (bold != null) {
            val title = bold.groupValues[1].trim().trimEnd('.', ':')
            val body = bullet.substring(bold.range.last + 1).trimStart(' ', '—', '-', ':')
            return Note(stripInline(title), stripInline(body.trim()))
        }
        val clean = stripInline(bullet)
        val split = clean.indexOf(" — ")
        return if (split > 0) Note(clean.substring(0, split), clean.substring(split + 3))
        else Note(clean, "")
    }

    private fun stripInline(text: String): String =
        text.replace("**", "")
            .replace("`", "")
            .replace(EMPHASIS) { it.groupValues[1] }

    private val BOLD_LEAD = Regex("""^\*\*(.+?)\*\*""")
    private val EMPHASIS = Regex("""(?<![\w*])\*([^*\n]+?)\*(?![\w*])""")
}
