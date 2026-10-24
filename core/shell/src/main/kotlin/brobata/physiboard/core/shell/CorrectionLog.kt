package brobata.physiboard.core.shell

import java.io.File

/**
 * One automatic correction the keyboard committed, and whether the user undid it. spec:
 * autocorrect-suggestions.md SS9, "The correction log"; app-shell.md SS11.
 */
data class CorrectionLogEntry(
    val atMs: Long,
    val typed: String,
    val committed: String,
    /** The engine's source label (`CONTEXT`, `PRIMARY_CASE`, `WORD_MIXUP`, a dictionary source). */
    val source: String,
    /** The numbers behind the decision (`share=0.93 cost=0.45`, `confidence=0.31`), as the engine reported them. */
    val detail: String,
    /** Backspace straight after put the typed word back (the REJECTED_BY_USER path, SS7.5). */
    val undone: Boolean,
)

/**
 * The private, on-phone log of committed corrections: what was typed, what replaced it, why, and
 * whether Backspace took it back. One tab-separated line per entry in a file of the keyboard's
 * own ([FILE_NAME] under the app's files directory), bounded to [CAPACITY] entries (trimmed to
 * [TRIM_TO] when full, oldest first), never sent anywhere, readable only through the Diagnostics
 * export when "incl. autocorrections" is on (app-shell.md SS10.6, `[correction_log]`), and
 * deleted by that screen's "Clear". Nothing is written while learning is off (private mode, a
 * field that asks for no personalized learning, a password field): the keyboard decides that
 * before calling in (app-shell.md SS31.1).
 *
 * Every operation reads the whole file, changes it and writes it back through a temporary file,
 * so a crash mid-write leaves the previous file. The file is at most a few tens of kilobytes and
 * the keyboard calls this off its main thread, one call at a time, so that is the simplest safe
 * shape. A line that does not parse is dropped on the next write rather than failing the log.
 */
object CorrectionLog {
    const val FILE_NAME: String = "correction-log.tsv"
    const val CAPACITY: Int = 500
    const val TRIM_TO: Int = 400

    /** How many of the newest entries [markUndone] looks back through: a mix-up fix writes two, an undo follows at once. */
    private const val UNDO_LOOKBACK = 4

    /** An undo follows its correction within a keystroke or two; an entry older than this is another correction (a text replacement undone is not logged, and must not mark yesterday's). */
    const val UNDO_WINDOW_MS: Long = 60_000

    fun encode(entry: CorrectionLogEntry): String = listOf(
        entry.atMs.toString(), clean(entry.typed), clean(entry.committed), clean(entry.source), clean(entry.detail), if (entry.undone) "1" else "0",
    ).joinToString("\t")

    fun decode(line: String): CorrectionLogEntry? {
        val parts = line.split('\t')
        if (parts.size != 6) return null
        val atMs = parts[0].toLongOrNull() ?: return null
        return CorrectionLogEntry(atMs, parts[1], parts[2], parts[3], parts[4], parts[5] == "1")
    }

    /** Every entry the file holds, oldest first; an unreadable or missing file is an empty log. */
    fun read(file: File): List<CorrectionLogEntry> {
        val text = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
        return text.lineSequence().mapNotNull(::decode).toList()
    }

    /** Appends [entry], trimming the oldest entries once the log holds more than [CAPACITY]; false when the write failed. */
    @Synchronized
    fun append(file: File, entry: CorrectionLogEntry): Boolean {
        val entries = read(file) + entry
        return write(file, if (entries.size > CAPACITY) entries.takeLast(TRIM_TO) else entries)
    }

    /**
     * Marks the entries an undo of [original] -> [replacement] took back: among the newest
     * [UNDO_LOOKBACK] entries not already undone, the newest one for each typed -> committed pair
     * whose typed word is one of [original]'s words and whose committed word is one of
     * [replacement]'s (a mix-up fix is one replacement of two words, logged as two entries, so an
     * undo of it marks two), and no older than [UNDO_WINDOW_MS] before [nowMs]. An earlier entry
     * for the same pair is a different correction and stays. True when at least one entry was
     * marked.
     */
    @Synchronized
    fun markUndone(file: File, original: String, replacement: String, nowMs: Long): Boolean {
        val originals = original.split(' ').filter { it.isNotEmpty() }.toSet()
        val replacements = replacement.split(' ').filter { it.isNotEmpty() }.toSet()
        val entries = read(file).toMutableList()
        val markedPairs = HashSet<Pair<String, String>>()
        var looked = 0
        for (i in entries.indices.reversed()) {
            if (looked++ >= UNDO_LOOKBACK) break
            val entry = entries[i]
            if (nowMs - entry.atMs > UNDO_WINDOW_MS) break
            if (entry.undone || entry.typed !in originals || entry.committed !in replacements) continue
            if (!markedPairs.add(entry.typed to entry.committed)) continue
            entries[i] = entry.copy(undone = true)
        }
        return markedPairs.isNotEmpty() && write(file, entries)
    }

    /**
     * Deletes the log and any temporary file a write left behind: the Diagnostics screen's
     * "Clear" (app-shell.md SS10.4). Synchronized with the writes, so a write in flight finishes
     * before the delete and cannot bring the log back.
     */
    @Synchronized
    fun clear(file: File) {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    /** The `[correction_log]` export rows, oldest first. [timeFormat] renders the wall-clock time the way the rest of the report does. */
    fun exportLines(entries: List<CorrectionLogEntry>, timeFormat: (Long) -> String): List<String> =
        if (entries.isEmpty()) listOf("(no corrections logged)") else entries.map {
            "${timeFormat(it.atMs)} | typed='${it.typed}' committed='${it.committed}' source=${it.source} ${it.detail} undone=${if (it.undone) "yes" else "no"}"
        }

    private fun clean(field: String): String = field.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun write(file: File, entries: List<CorrectionLogEntry>): Boolean = runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(entries.joinToString("") { encode(it) + "\n" })
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        true
    }.getOrDefault(false)
}
