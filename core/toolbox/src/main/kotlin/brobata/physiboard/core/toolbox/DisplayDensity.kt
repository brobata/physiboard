package brobata.physiboard.core.toolbox

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** What `wm density` reported. spec: broker-privileged-toolbox.md SS13. */
data class DensityReading(val physicalDpi: Int, val currentDpi: Int, val overridden: Boolean)

/**
 * An armed revert, persisted so a change that outlives the app (crash, or a reboot inside the
 * window) can still be undone. spec: SS13 ("Apply"); T21.
 */
data class PendingRevert(val id: String, val apply: String, val revert: String)

/** spec: SS13 ("Apply", "the revert is always 'reset'"), scoped to the density feature. */
object PendingRevertRecord {
    const val DISPLAY_DENSITY_ID = "display_density"

    fun forDensity(targetDpi: Int): PendingRevert =
        PendingRevert(id = DISPLAY_DENSITY_ID, apply = DensityShellLines.apply(targetDpi), revert = DensityShellLines.RESET)
}

/** The pending-revert record's JSON shape, under the key `pending_revert` in `physiboard_toolbox`. spec: SS20. */
object PendingRevertCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(text: String?): PendingRevert? {
        if (text.isNullOrBlank()) return null
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val apply = obj["apply"]?.jsonPrimitive?.contentOrNull ?: return null
        val revert = obj["revert"]?.jsonPrimitive?.contentOrNull ?: return null
        return PendingRevert(id, apply, revert)
    }

    fun encode(record: PendingRevert): String {
        val obj = JsonObject(mapOf("id" to JsonPrimitive(record.id), "apply" to JsonPrimitive(record.apply), "revert" to JsonPrimitive(record.revert)))
        return json.encodeToString(JsonObject.serializer(), obj)
    }
}

/** The shell lines Screen density sends. spec: SS13. */
object DensityShellLines {
    const val READ = "wm density"
    const val RESET = "wm density reset"
    fun apply(dpi: Int): String = "wm density $dpi"
}

/** What "Apply" should do with a chosen value. spec: SS13 ("Apply"), T20 to T22. */
sealed class DensityApplyPlan {
    data class Plan(val pendingRevert: PendingRevert, val shellLine: String) : DensityApplyPlan()
    data class Refused(val reason: String) : DensityApplyPlan()
}

/**
 * The pure arithmetic behind Screen density: parsing `wm density`'s output, the safe range (60%
 * to 140% of the physical density, integer-truncated), the slider's 5-unit snap, and the plan for
 * an "Apply" tap. Nothing here touches a shell; `:device:privileged` executes [DensityApplyPlan]s
 * and persists the [PendingRevert] this module builds.
 *
 * spec: broker-privileged-toolbox.md SS13; T17 to T25.
 */
object DisplayDensity {
    const val OUTSIDE_SAFE_RANGE = "Outside the safe range"

    private val PHYSICAL = Regex("Physical density:\\s*(\\d+)")
    private val OVERRIDE = Regex("Override density:\\s*(\\d+)")

    /** spec: SS13 ("Read"); T17, T18. Null when the output does not carry a physical density at all. */
    fun parse(rawOutput: String): DensityReading? {
        val physical = PHYSICAL.find(rawOutput)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val override = OVERRIDE.find(rawOutput)?.groupValues?.get(1)?.toIntOrNull()
        return DensityReading(physicalDpi = physical, currentDpi = override ?: physical, overridden = override != null)
    }

    /** spec: SS13 ("Range: 60% to 140% of the physical density, integer-truncated"); T19. */
    fun safeRange(physicalDpi: Int): IntRange = (physicalDpi * 60 / 100)..(physicalDpi * 140 / 100)

    fun isInSafeRange(physicalDpi: Int, candidateDpi: Int): Boolean = candidateDpi in safeRange(physicalDpi)

    /** spec: SS13 ("Slider: snaps to multiples of 5"). */
    fun snapToStep(rawDpi: Int): Int = ((rawDpi + 2) / 5) * 5

    /** spec: SS13 ("Apply"); T20 to T22. */
    fun planApply(physicalDpi: Int, candidateDpi: Int): DensityApplyPlan {
        if (!isInSafeRange(physicalDpi, candidateDpi)) return DensityApplyPlan.Refused(OUTSIDE_SAFE_RANGE)
        return DensityApplyPlan.Plan(PendingRevertRecord.forDensity(candidateDpi), DensityShellLines.apply(candidateDpi))
    }

    /** spec: SS13 ("Undo now"), the countdown reaching zero, and "Back to stock"; T24, T25. Null when there is nothing to send. */
    fun revertLine(pending: PendingRevert?): String? = pending?.revert
}
