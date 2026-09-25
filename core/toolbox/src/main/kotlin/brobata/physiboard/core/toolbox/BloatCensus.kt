package brobata.physiboard.core.toolbox

/** Where one catalogue entry stands right now. spec: broker-privileged-toolbox.md SS12.5. */
enum class BloatState { ACTIVE, DISABLED, UNINSTALLED, ABSENT }

/**
 * One census read: every catalogue entry's state, plus the vendor packages the phone has that
 * this catalogue does not know about (SS12.5, "N vendor apps not in this list").
 */
data class BloatCensusResult(
    val states: Map<String, BloatState>,
    val unrecognizedVendorPackages: List<String>,
)

/**
 * Splits the one broker line's output (`echo __E__; pm list packages -e; echo __D__; pm list
 * packages -d; echo __U__; pm list packages -u`) into enabled, disabled and "everything including
 * uninstalled", then classifies every catalogue entry against them.
 *
 * Reading through the shell rather than the package manager API is deliberate (SS12.5, "a
 * disabled or uninstalled-for-user package is awkward to classify locally without hidden flags").
 *
 * spec: broker-privileged-toolbox.md SS12.5; T9.
 */
object BloatCensus {
    const val COMMAND = "echo __E__; pm list packages -e; echo __D__; pm list packages -d; echo __U__; pm list packages -u"

    private const val ENABLED_MARKER = "__E__"
    private const val DISABLED_MARKER = "__D__"
    private const val EVERYTHING_MARKER = "__U__"

    fun parse(raw: String, catalog: List<BloatEntry> = BloatCatalog.entries): BloatCensusResult {
        val sections = split(raw)
        val states = catalog.associate { entry ->
            val state = when {
                entry.packageName in sections.disabled -> BloatState.DISABLED
                entry.packageName in sections.enabled -> BloatState.ACTIVE
                entry.packageName in sections.everything -> BloatState.UNINSTALLED
                else -> BloatState.ABSENT
            }
            entry.packageName to state
        }
        val catalogued = catalog.map { it.packageName }.toSet()
        val unrecognized = sections.everything
            .filter { it !in catalogued && BloatCatalog.isVendorNamespace(it) && !BloatCatalog.isCatalogued(it) }
            .sorted()
        return BloatCensusResult(states, unrecognized)
    }

    private data class Sections(val enabled: Set<String>, val disabled: Set<String>, val everything: Set<String>)

    private fun split(raw: String): Sections {
        val lines = raw.lines()
        val enabled = mutableSetOf<String>()
        val disabled = mutableSetOf<String>()
        val everything = mutableSetOf<String>()
        var target: MutableSet<String>? = null
        for (line in lines) {
            val trimmed = line.trim()
            when (trimmed) {
                ENABLED_MARKER -> { target = enabled; continue }
                DISABLED_MARKER -> { target = disabled; continue }
                EVERYTHING_MARKER -> { target = everything; continue }
            }
            if (trimmed.isEmpty()) continue
            target?.add(trimmed.removePrefix("package:"))
        }
        return Sections(enabled, disabled, everything)
    }
}
