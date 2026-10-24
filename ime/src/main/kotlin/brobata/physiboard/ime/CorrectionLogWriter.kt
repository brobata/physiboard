package brobata.physiboard.ime

import android.content.Context
import android.util.Log
import brobata.physiboard.core.shell.CorrectionLog
import brobata.physiboard.core.shell.CorrectionLogEntry
import brobata.physiboard.core.text.BoundaryDebugInfo
import brobata.physiboard.core.text.LastReplacement
import java.io.File
import java.util.concurrent.Executors

/**
 * Writes the private on-phone correction log (autocorrect-suggestions.md SS9, "The correction
 * log"): every correction the boundary engine committed, and the undo that took one back. The
 * rules (format, bound, undo matching) are `:core:shell`'s [CorrectionLog]; this class supplies
 * the file and the background thread, the same split [UserWordFileLoader] uses. Writes run one
 * at a time, in order, so an undo can never overtake the commit it undoes.
 *
 * The caller decides what may be logged (app-shell.md SS31.1: nothing while learning is off, and
 * never from a password field); this class only records what it is handed.
 */
internal class CorrectionLogWriter(context: Context) {
    private val file = File(context.filesDir, CorrectionLog.FILE_NAME)
    private val writes = Executors.newSingleThreadExecutor { task -> Thread(task, "physiboard-correction-log").apply { isDaemon = true } }

    /**
     * Logs the commit [debug] describes: the word just finished when it changed, and the word
     * before it when the mix-up fix rewrote that too (two entries, one per word). A text
     * replacement is the user's own rule, not a correction, and is not logged.
     */
    fun record(debug: BoundaryDebugInfo, nowMs: Long) {
        if (debug.type != "commit" || debug.source == "TEXT_REPLACEMENT") return
        val entries = ArrayList<CorrectionLogEntry>(2)
        val previousBefore = debug.previousWordBefore
        val previousAfter = debug.previousWordAfter
        if (previousBefore != null && previousAfter != null && previousBefore != previousAfter) {
            entries.add(CorrectionLogEntry(nowMs, previousBefore, previousAfter, "WORD_MIXUP", "", undone = false))
        }
        if (debug.before != debug.after && debug.after.isNotEmpty()) {
            val source = debug.source.orEmpty().removeSuffix("+WORD_MIXUP").ifEmpty { "WORD_MIXUP" }
            entries.add(CorrectionLogEntry(nowMs, debug.before, debug.after, source, debug.detail, undone = false))
        }
        if (entries.isEmpty()) return
        writes.execute {
            for (entry in entries) {
                if (!CorrectionLog.append(file, entry)) Log.e(TAG, "correction log write failed")
            }
        }
    }

    /** Backspace put [undone]'s original back: the entries that logged it are marked undone. */
    fun markUndone(undone: LastReplacement) {
        writes.execute { CorrectionLog.markUndone(file, undone.original, undone.replacement) }
    }

    private companion object {
        const val TAG = "PhysiBoard.CorrectionLog"
    }
}
