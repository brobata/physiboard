package it.palsoftware.pastiera.toolbox

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * What we changed and what it was before, written BEFORE each command runs.
 *
 * Restore cannot depend on the catalog: a later version may retire an entry, and the packages
 * a user disabled two releases ago must still come back. The journal is therefore the sole
 * source of truth for undo, and it records the prior state rather than assuming "active".
 */
object RemovalJournal {
    private const val TAG = "RemovalJournal"
    private const val PREFS = "physiboard_toolbox"
    private const val KEY_ENTRIES = "removal_journal"

    data class Record(
        val packageName: String,
        val previousState: PackageState,
        val action: Action,
        val timestamp: Long
    )

    enum class Action { DISABLED, UNINSTALLED }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Record> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val pkg = o.optString("pkg").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Record(
                    packageName = pkg,
                    previousState = runCatching {
                        PackageState.valueOf(o.optString("prev"))
                    }.getOrDefault(PackageState.ACTIVE),
                    action = runCatching {
                        Action.valueOf(o.optString("action"))
                    }.getOrDefault(Action.DISABLED),
                    timestamp = o.optLong("at")
                )
            }
        }.onFailure { Log.w(TAG, "Journal unreadable — starting empty", it) }.getOrDefault(emptyList())
    }

    /** One record per package: re-acting on the same package updates rather than stacking. */
    fun record(context: Context, record: Record) {
        val merged = all(context).filterNot { it.packageName == record.packageName } + record
        write(context, merged)
    }

    fun forget(context: Context, packageName: String) {
        write(context, all(context).filterNot { it.packageName == packageName })
    }

    fun clear(context: Context) = write(context, emptyList())

    private fun write(context: Context, records: List<Record>) {
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject()
                    .put("pkg", r.packageName)
                    .put("prev", r.previousState.name)
                    .put("action", r.action.name)
                    .put("at", r.timestamp)
            )
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }
}
