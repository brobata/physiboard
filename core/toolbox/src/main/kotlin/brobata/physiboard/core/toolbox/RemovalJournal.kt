package brobata.physiboard.core.toolbox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** What a package's state was before Remove bloat touched it. spec: broker-privileged-toolbox.md SS12.6. */
enum class JournalPriorState { ACTIVE, DISABLED, UNINSTALLED, ABSENT }

/** What Remove bloat did to it. spec: SS12.6. */
enum class JournalAction { DISABLED, UNINSTALLED }

/** One journal record: a package changed by PhysiBoard, and what it was before. spec: SS12.6. */
data class JournalRecord(val packageName: String, val prev: JournalPriorState, val action: JournalAction, val atMs: Long)

/**
 * The removal journal's JSON shape and the "one record per package" rule: acting on the same
 * package again replaces its record instead of stacking, a record that fails to write is removed
 * again (SS12.6, "it never happened, so the journal must not claim it did"), and an unreadable
 * journal reads as empty rather than throwing.
 *
 * spec: broker-privileged-toolbox.md SS12.6; T10 to T14.
 */
object RemovalJournalCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** spec: SS12.6 ("An unreadable journal reads as empty; a record with an unknown `prev` reads as ACTIVE and an unknown `action` as DISABLED"); T13, T14. */
    fun decode(text: String?): List<JournalRecord> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val pkg = obj["pkg"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val prevRaw = obj["prev"]?.jsonPrimitive?.contentOrNull
            val actionRaw = obj["action"]?.jsonPrimitive?.contentOrNull
            val at = obj["at"]?.jsonPrimitive?.longOrNull ?: 0L
            JournalRecord(
                packageName = pkg,
                prev = JournalPriorState.entries.firstOrNull { it.name == prevRaw } ?: JournalPriorState.ACTIVE,
                action = JournalAction.entries.firstOrNull { it.name == actionRaw } ?: JournalAction.DISABLED,
                atMs = at,
            )
        }
    }

    fun encode(records: List<JournalRecord>): String {
        val array = JsonArray(
            records.map { record ->
                JsonObject(
                    mapOf(
                        "pkg" to JsonPrimitive(record.packageName),
                        "prev" to JsonPrimitive(record.prev.name),
                        "action" to JsonPrimitive(record.action.name),
                        "at" to JsonPrimitive(record.atMs),
                    ),
                )
            },
        )
        return json.encodeToString(JsonArray.serializer(), array)
    }

    /** spec: SS12.6 ("One record per package: acting again on the same package replaces its record rather than stacking"); T12. */
    fun upsert(records: List<JournalRecord>, record: JournalRecord): List<JournalRecord> =
        records.filterNot { it.packageName == record.packageName } + record

    /** spec: SS12.6 ("If the command fails the record is removed again"), and "Restore" forgetting the record on success. */
    fun remove(records: List<JournalRecord>, packageName: String): List<JournalRecord> =
        records.filterNot { it.packageName == packageName }
}
