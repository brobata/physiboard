package brobata.physiboard.clipboard

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors

/**
 * Data Access Object for clipboard history.
 *
 * The in-memory [cache] is the read model and is updated synchronously on the calling
 * thread (guarded by its own lock); every SQLite touch, including the initial load, runs
 * on [dbExecutor] so the clipboard listener never blocks the IME main thread.
 */
class ClipboardDao private constructor(private val db: ClipboardDatabase) {

    interface Listener {
        fun onClipInserted(position: Int)
        fun onClipsRemoved(position: Int, count: Int)
        fun onClipMoved(oldPosition: Int, newPosition: Int)
        /** Called off the main thread once the stored history has been read in. */
        fun onHistoryLoaded() {}
    }

    var listener: Listener? = null

    // Track when we last cleaned old clips
    private var lastClearOldClips = 0L

    private val cache = mutableListOf<ClipboardHistoryEntry>()

    init {
        dbExecutor.execute { loadFromDb() }
    }

    private fun loadFromDb() {
        val loaded = mutableListOf<ClipboardHistoryEntry>()
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_ID, COLUMN_TIMESTAMP, COLUMN_PINNED, COLUMN_TEXT),
            null,
            null,
            null,
            null,
            "$COLUMN_PINNED DESC, $COLUMN_TIMESTAMP DESC"
        ).use {
            while (it.moveToNext()) {
                loaded.add(ClipboardHistoryEntry(
                    it.getLong(0),
                    it.getLong(1),
                    it.getInt(2) != 0,
                    it.getString(3)
                ))
            }
        }
        // A clip copied before the load finished is already in the cache and queued for
        // insert behind us, so the row from the previous session would be a duplicate.
        val superseded = mutableListOf<Long>()
        synchronized(cache) {
            for (entry in loaded) {
                if (cache.any { it.text == entry.text }) superseded.add(entry.id) else cache.add(entry)
            }
            cache.sort()
        }
        superseded.forEach { db.writableDatabase.delete(TABLE, "$COLUMN_ID = $it", null) }
        listener?.onHistoryLoaded()
    }

    /**
     * Add a new clipboard entry or update existing one with same text.
     * @param retentionMinutes Optional retention time in minutes. If provided, will clear old clips using this value.
     */
    fun addClip(timestamp: Long, pinned: Boolean, text: String, retentionMinutes: Long? = null) {
        if (retentionMinutes != null) {
            clearOldClips(now = true, retentionMinutes = retentionMinutes)
        } else {
            clearOldClips() // Use default if not provided (for backwards compatibility)
        }

        val existing = synchronized(cache) { cache.firstOrNull { it.text == text } }
        if (existing != null) {
            updateTimestamp(existing, timestamp)
            return
        }

        insertNewEntry(timestamp, pinned, text)
    }

    private fun insertNewEntry(timestamp: Long, pinned: Boolean, text: String) {
        val entry = ClipboardHistoryEntry(PENDING_ID, timestamp, pinned, text)
        val position = synchronized(cache) {
            cache.add(entry)
            cache.sort()
            cache.indexOfFirst { it === entry }
        }
        listener?.onClipInserted(position)

        dbExecutor.execute {
            val cv = ContentValues(3).apply {
                put(COLUMN_TIMESTAMP, timestamp)
                put(COLUMN_PINNED, pinned)
                put(COLUMN_TEXT, text)
            }
            entry.id = db.writableDatabase.insert(TABLE, null, cv)
        }
    }

    private fun updateTimestamp(entry: ClipboardHistoryEntry, timestamp: Long) {
        val (oldPos, newPos) = synchronized(cache) {
            val oldPos = cache.indexOfFirst { it === entry }
            entry.timeStamp = timestamp
            cache.sort()
            oldPos to cache.indexOfFirst { it === entry }
        }
        listener?.onClipMoved(oldPos, newPos)

        dbExecutor.execute {
            val cv = ContentValues(1).apply {
                put(COLUMN_TIMESTAMP, timestamp)
            }
            db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
        }
    }

    fun isPinned(index: Int) = synchronized(cache) { cache.getOrNull(index)?.isPinned ?: false }

    fun getAt(index: Int) = synchronized(cache) { cache.getOrNull(index) }

    fun get(id: Long) = synchronized(cache) { cache.firstOrNull { it.id == id } }

    fun count() = synchronized(cache) { cache.size }

    fun sort() = synchronized(cache) { cache.sort() }

    fun togglePinned(id: Long) {
        val entry: ClipboardHistoryEntry
        val oldPos: Int
        val newPos: Int
        synchronized(cache) {
            entry = cache.firstOrNull { it.id == id } ?: return
            entry.isPinned = !entry.isPinned
            entry.timeStamp = System.currentTimeMillis()
            oldPos = cache.indexOfFirst { it === entry }
            cache.sort()
            newPos = cache.indexOfFirst { it === entry }
        }
        listener?.onClipMoved(oldPos, newPos)

        val pinned = entry.isPinned
        val timestamp = entry.timeStamp
        dbExecutor.execute {
            val cv = ContentValues(2).apply {
                put(COLUMN_PINNED, pinned)
                put(COLUMN_TIMESTAMP, timestamp)
            }
            db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
        }
    }

    fun deleteClipAt(index: Int) {
        val entry = synchronized(cache) {
            cache.getOrNull(index)?.also { cache.removeAt(index) }
        } ?: return
        dbExecutor.execute {
            db.writableDatabase.delete(TABLE, "$COLUMN_ID = ${entry.id}", null)
        }
    }

    /**
     * Remove old clipboard entries based on retention time setting.
     * @param now If true, force clear immediately; otherwise respect debounce
     * @param retentionMinutes Retention time in minutes. If <= 0, no cleanup is performed.
     *                         Values > 0 will remove entries older than this time (except pinned ones).
     */
    fun clearOldClips(now: Boolean = false, retentionMinutes: Long = 120) {
        if (!now && lastClearOldClips > SystemClock.elapsedRealtime() - 5 * 1000) {
            return // Debounce: only clear every 5 seconds
        }

        lastClearOldClips = SystemClock.elapsedRealtime()

        // If retentionMinutes <= 0, disable automatic cleanup (infinite retention)
        if (retentionMinutes <= 0) return

        val minTime = System.currentTimeMillis() - retentionMinutes * 60 * 1000L

        val firstRemoved: Int
        val removedCount: Int
        synchronized(cache) {
            firstRemoved = cache.indexOfFirst { it.timeStamp < minTime && !it.isPinned }
            if (firstRemoved < 0) return
            removedCount = cache.count { it.timeStamp < minTime && !it.isPinned }
            cache.removeAll { it.timeStamp < minTime && !it.isPinned }
        }
        listener?.onClipsRemoved(firstRemoved, removedCount)

        dbExecutor.execute {
            db.writableDatabase.delete(TABLE, "$COLUMN_TIMESTAMP < $minTime AND $COLUMN_PINNED = 0", null)
        }
    }

    fun clearNonPinned() {
        val firstRemoved: Int
        val removedCount: Int
        synchronized(cache) {
            firstRemoved = cache.indexOfFirst { !it.isPinned }
            if (firstRemoved < 0) return // Nothing to remove
            removedCount = cache.count { !it.isPinned }
            cache.removeAll { !it.isPinned }
        }
        listener?.onClipsRemoved(firstRemoved, removedCount)

        dbExecutor.execute {
            db.writableDatabase.delete(TABLE, "$COLUMN_PINNED = 0", null)
        }
    }

    fun clear() {
        val count = synchronized(cache) {
            cache.size.also { cache.clear() }
        }
        if (count == 0) return
        listener?.onClipsRemoved(0, count)
        dbExecutor.execute {
            db.writableDatabase.delete(TABLE, null, null)
        }
    }

    companion object {
        private const val TAG = "ClipboardDao"
        const val PENDING_ID = -1L

        private val dbExecutor = Executors.newSingleThreadExecutor()

        const val TABLE = "CLIPBOARD"
        private const val COLUMN_ID = "ID"
        private const val COLUMN_TIMESTAMP = "TIMESTAMP"
        private const val COLUMN_PINNED = "PINNED"
        private const val COLUMN_TEXT = "TEXT"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PINNED TINYINT NOT NULL,
                $COLUMN_TEXT TEXT
            )
        """

        @Volatile
        private var instance: ClipboardDao? = null

        /**
         * Get the singleton instance, or create it if needed.
         * Returns null if instance can't be created (e.g. device locked).
         */
        fun getInstance(context: Context): ClipboardDao? {
            return instance ?: synchronized(this) {
                instance ?: try {
                    ClipboardDao(ClipboardDatabase.getInstance(context)).also {
                        instance = it
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create ClipboardDao", e)
                    null
                }
            }
        }
    }
}
