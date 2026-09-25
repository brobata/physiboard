package brobata.physiboard.device.privileged.toolbox

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import brobata.physiboard.core.toolbox.JournalRecord
import brobata.physiboard.core.toolbox.PendingRevert
import brobata.physiboard.core.toolbox.PendingRevertCodec
import brobata.physiboard.core.toolbox.RemovalJournalCodec

/**
 * The toolbox's own preferences file, `physiboard_toolbox` (broker-privileged-toolbox.md SS12.6,
 * SS13, SS20): the removal journal and the density's pending revert. Kept beside, not inside, the
 * main typed settings store for the same reason [brobata.physiboard.device.privileged.
 * PreferencesDiagnosticsStore] is: neither row is part of the typed schema, and neither is in the
 * app's backup file (SS12.6, "The journal is not in the app's backup file").
 */
interface ToolboxStateStore {
    fun journal(): List<JournalRecord>
    fun updateJournal(transform: (List<JournalRecord>) -> List<JournalRecord>): List<JournalRecord>
    fun pendingRevert(): PendingRevert?
    fun setPendingRevert(record: PendingRevert?)
}

class PreferencesToolboxStateStore(private val prefs: SharedPreferences) : ToolboxStateStore {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    @Synchronized
    override fun journal(): List<JournalRecord> = RemovalJournalCodec.decode(prefs.getString(JOURNAL_KEY, null))

    @Synchronized
    override fun updateJournal(transform: (List<JournalRecord>) -> List<JournalRecord>): List<JournalRecord> {
        val next = transform(journal())
        prefs.edit(commit = true) { putString(JOURNAL_KEY, RemovalJournalCodec.encode(next)) }
        return next
    }

    override fun pendingRevert(): PendingRevert? = PendingRevertCodec.decode(prefs.getString(PENDING_REVERT_KEY, null))

    override fun setPendingRevert(record: PendingRevert?) {
        prefs.edit(commit = true) {
            if (record == null) remove(PENDING_REVERT_KEY) else putString(PENDING_REVERT_KEY, PendingRevertCodec.encode(record))
        }
    }

    companion object {
        const val FILE_NAME = "physiboard_toolbox"
        private const val JOURNAL_KEY = "removal_journal"
        private const val PENDING_REVERT_KEY = "pending_revert"
    }
}

/** The JVM tests' store. */
class InMemoryToolboxStateStore : ToolboxStateStore {
    @Volatile
    private var journal: List<JournalRecord> = emptyList()

    @Volatile
    private var pending: PendingRevert? = null

    override fun journal(): List<JournalRecord> = journal

    @Synchronized
    override fun updateJournal(transform: (List<JournalRecord>) -> List<JournalRecord>): List<JournalRecord> {
        journal = transform(journal)
        return journal
    }

    override fun pendingRevert(): PendingRevert? = pending
    override fun setPendingRevert(record: PendingRevert?) {
        pending = record
    }
}
