package brobata.physiboard.ime.actions

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import brobata.physiboard.core.actions.clipboard.Clip
import brobata.physiboard.core.actions.clipboard.ClipCapture
import brobata.physiboard.core.actions.clipboard.ClipboardHistory

/**
 * Captures the system clipboard into the [ClipboardHistory] model and mirrors it to SQLite.
 * spec: expansion-clipboard-pickers-launcher.md SS3.1 to SS3.4 and SS3.7 (a copy is stored with
 * its links cleaned), app-shell.md SS31 (nothing is captured while learning is off). Every rule (dedupe, ordering,
 * retention, the 5 s debounce, the sensitive-clip exclusion 3.0 adds) is the model's; this class
 * owns the listener, the database, the 60 s timer and the single background thread the writes
 * go to "so the keyboard's input thread never waits for disk" (SS3.2).
 *
 * The enable flag is read once, from the store's first emission, as SS3.1 says of `clipboard_history_enabled` ("changing
 * it takes effect after the keyboard service restarts"); [retentionMinutes] is live.
 */
internal class ClipboardHistoryController(
    private val context: Context,
    private val mainHandler: Handler,
    private val onChanged: () -> Unit,
    /** app-shell.md SS31: false in private mode or a field that asks for no learning; asked at every copy. */
    private val learningAllowed: () -> Boolean,
    /** SS3.7: `clean_links`, asked at every copy. */
    private val cleanLinks: () -> Boolean,
) {
    /** spec SS3.1: read once; the store's first emission is the "service creation" read, later changes wait for a restart. */
    private var enabled: Boolean = false
    private var enabledSettled = false

    /**
     * spec SS3.1: "Capture only happens when `clipboard_history_enabled` was true at service
     * creation." Nothing is listened to, loaded or captured before this lands, because the
     * store's first emission arrives after construction: the controller used to register the
     * listener and capture the system clipboard in its own `init`, so a disabled history still
     * wrote the current clip to the database at every service start (2026-09-25 review).
     */
    fun applyEnabledOnce(value: Boolean) {
        if (enabledSettled) return
        enabledSettled = true
        enabled = value
        if (!value) return
        clipboardManager?.addPrimaryClipChangedListener(listener)
        loadAsync()
        runCatching { captureCurrent() }.onFailure { Log.e(TAG, "initial capture crashed", it) }
    }

    var retentionMinutes: Long = ClipboardHistory.DEFAULT_RETENTION_MINUTES

    var history: ClipboardHistory = ClipboardHistory()
        private set

    val count: Int get() = history.count

    private val worker = HandlerThread("physiboard-clipboard").also { it.start() }
    private val workerHandler = Handler(worker.looper)
    private var database: ClipboardDatabase? = null
    private val clipboardManager: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { runCatching { captureCurrent() }.onFailure { Log.e(TAG, "capture crashed", it) } }
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanup(forced = true)
            onChanged()
            mainHandler.postDelayed(this, ClipboardHistory.CLEANUP_INTERVAL_MS)
        }
    }


    /** spec SS3.3: the 60 s cleanup timer runs while a field is active and also pushes the count. */
    fun onFieldStarted() {
        if (!enabled) return
        mainHandler.removeCallbacks(cleanupRunnable)
        mainHandler.postDelayed(cleanupRunnable, ClipboardHistory.CLEANUP_INTERVAL_MS)
    }

    fun onFieldFinished() = mainHandler.removeCallbacks(cleanupRunnable)

    fun onServiceDestroyed() {
        mainHandler.removeCallbacks(cleanupRunnable)
        clipboardManager?.removePrimaryClipChangedListener(listener)
        workerHandler.post { runCatching { database?.close() } }
        worker.quitSafely()
    }

    fun togglePinned(id: Long) {
        val next = history.togglePinned(id, System.currentTimeMillis())
        commit(next)
    }

    fun delete(id: Long) = commit(history.delete(id))

    /** spec SS3.5: Clear All also empties the system clipboard. */
    fun clearAll() {
        commit(history.clearAll())
        runCatching { clipboardManager?.clearPrimaryClip() }
    }

    /** spec SS3.3: every listed cleanup is forced; the panel's own refresh calls this too. */
    fun cleanup(forced: Boolean) {
        val result = history.cleanup(System.currentTimeMillis(), retentionMinutes, forced)
        if (result.ran) commit(result.history)
    }

    private fun captureCurrent() {
        val manager = clipboardManager ?: return
        val clip: ClipData = manager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val description = clip.description
        val mimeTypes = (0 until description.mimeTypeCount).map { description.getMimeType(it) }
        val sensitive = description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false
        val text = runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
        if (!ClipCapture.accepts(mimeTypes, text, sensitive, learningAllowed())) return
        commit(history.capture(ClipCapture.text(text!!, cleanLinks()), System.currentTimeMillis()))
        cleanup(forced = true)
    }

    /** Applies [next] to the working set first, then mirrors the difference to the database off the main thread (SS3.2). */
    private fun commit(next: ClipboardHistory) {
        val before = history
        history = next
        if (before.entries != next.entries) {
            val removed = before.entries.map { it.id }.toSet() - next.entries.map { it.id }.toSet()
            val changed = next.entries.filter { clip -> before.entries.none { it == clip } }
            workerHandler.post { runCatching { database()?.apply(changed, removed) }.onFailure { Log.e(TAG, "clipboard write crashed", it) } }
            onChanged()
        }
    }

    /** spec SS3.2: the table is loaded in the background; a clip captured before it lands supersedes its stored twin. */
    private fun loadAsync() {
        workerHandler.post {
            val stored = runCatching { database()?.loadAll() }.getOrNull() ?: return@post
            mainHandler.post {
                val (merged, superseded) = history.mergeLoaded(stored)
                history = merged
                if (superseded.isNotEmpty()) workerHandler.post { runCatching { database()?.apply(emptyList(), superseded.toSet()) } }
                cleanup(forced = true)
                onChanged()
            }
        }
    }

    /** spec SS3.2: "If the database cannot be opened... the history simply reports zero entries and the open is retried on the next use." */
    private fun database(): ClipboardDatabase? {
        database?.let { return it }
        return runCatching { ClipboardDatabase(context).also { it.writableDatabase; database = it } }
            .onFailure { Log.w(TAG, "clipboard database not openable yet", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "PhysiBoardClipboard"
    }
}

/** spec SS3.2: table `CLIPBOARD` with `ID`, `TIMESTAMP`, `PINNED`, `TEXT`, schema version 1. The file is 3.0's own (a different package from 2.x). */
internal class ClipboardDatabase(context: Context) : SQLiteOpenHelper(context, FILE_NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS CLIPBOARD (ID INTEGER PRIMARY KEY, TIMESTAMP INTEGER NOT NULL, PINNED INTEGER NOT NULL DEFAULT 0, TEXT TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadAll(): List<Clip> {
        val out = ArrayList<Clip>()
        readableDatabase.query("CLIPBOARD", arrayOf("ID", "TIMESTAMP", "PINNED", "TEXT"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) out.add(Clip(c.getLong(0), c.getString(3), c.getLong(1), c.getInt(2) != 0))
        }
        return out
    }

    fun apply(upserts: List<Clip>, removedIds: Set<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in removedIds) db.delete("CLIPBOARD", "ID = ?", arrayOf(id.toString()))
            for (clip in upserts) {
                val values = ContentValues().apply {
                    put("ID", clip.id)
                    put("TIMESTAMP", clip.timestampMs)
                    put("PINNED", if (clip.pinned) 1 else 0)
                    put("TEXT", clip.text)
                }
                db.insertWithOnConflict("CLIPBOARD", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val FILE_NAME = "physiboard_clipboard.db"
        const val VERSION = 1
    }
}
