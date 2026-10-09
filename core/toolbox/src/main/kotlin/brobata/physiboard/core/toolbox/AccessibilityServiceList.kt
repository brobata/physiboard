package brobata.physiboard.core.toolbox

/**
 * PhysiBoard's accessibility service in Android's list of enabled services, the
 * `enabled_accessibility_services` secure row: a colon-separated list of `package/class`
 * components, one per enabled service, in any app.
 *
 * Turning the service on through the paired broker appends PhysiBoard's entry and sets
 * `accessibility_enabled` to 1; "Reset device settings to stock" removes PhysiBoard's entry and
 * nothing else. Every other app's entry is kept exactly as it was, in its place. Because the row
 * is read back and written into a shell line, an entry that is not a plain component makes the
 * whole write refuse rather than risk mangling (or dropping) another app's service.
 *
 * Pure: the device layer runs [READ_LINE] and the lines this returns. spec:
 * broker-privileged-toolbox.md SS9 (the write), SS10 step 7 (the revert).
 */
object AccessibilityServiceList {
    /** The service every PhysiBoard build declares; the package in front of it is the running build's. */
    const val SERVICE_CLASS = "brobata.physiboard.ime.access.PhysiBoardAccessibilityService"

    const val SERVICES_KEY = "enabled_accessibility_services"
    const val ENABLED_KEY = "accessibility_enabled"

    /** One broker round trip, the list on the first line and the master switch on the second. */
    const val READ_LINE = "settings get secure $SERVICES_KEY; settings get secure $ENABLED_KEY"

    private val COMPONENT = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")

    fun component(packageName: String): String = "$packageName/$SERVICE_CLASS"

    /** What [READ_LINE] printed: the entries (empty when the row is unset) and whether accessibility is on. */
    data class Reading(val entries: List<String>, val masterOn: Boolean)

    /** Null when the output is not the two lines [READ_LINE] prints. */
    fun parse(output: String): Reading? {
        val lines = output.split('\n').map { it.trim() }.let { if (it.lastOrNull()?.isEmpty() == true) it.dropLast(1) else it }
        if (lines.size < 2) return null
        val list = lines[0].takeUnless { it == "null" }.orEmpty()
        return Reading(entries = split(list), masterOn = lines[1].trim() == "1")
    }

    /** The row's value as entries; Android itself ignores empty entries, and so does this. */
    fun split(value: String?): List<String> = value.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Whether [entry] names [packageName]'s service. Android accepts both the full form
     * (`pkg/pkg.Class`) and the short one (`pkg/.Class`), so both are recognised.
     */
    fun isOurs(entry: String, packageName: String): Boolean {
        val slash = entry.indexOf('/')
        if (slash <= 0) return false
        val pkg = entry.substring(0, slash)
        if (pkg != packageName) return false
        val cls = entry.substring(slash + 1)
        val full = if (cls.startsWith(".")) pkg + cls else cls
        return full == SERVICE_CLASS
    }

    /** Whether the service is in the list (on, as far as the list goes). */
    fun isListed(value: String?, packageName: String): Boolean = split(value).any { isOurs(it, packageName) }

    /** The line that turns the service on, or null when the current list cannot be rewritten safely or it is already on. */
    fun enableLine(reading: Reading, packageName: String): String? {
        if (reading.entries.any { !COMPONENT.matches(it) }) return null
        val listed = reading.entries.any { isOurs(it, packageName) }
        if (listed && reading.masterOn) return null
        val entries = if (listed) reading.entries else reading.entries + component(packageName)
        return "settings put secure $SERVICES_KEY '${entries.joinToString(":")}'; settings put secure $ENABLED_KEY 1"
    }

    /**
     * The reset's line: PhysiBoard's entry out, everything else kept. With no service left the row
     * is deleted and accessibility switched off, as on a phone where nothing was ever turned on.
     * Null when PhysiBoard is not listed (nothing to undo) or the list cannot be rewritten safely.
     */
    fun disableLine(reading: Reading, packageName: String): String? {
        if (reading.entries.none { isOurs(it, packageName) }) return null
        val others = reading.entries.filterNot { isOurs(it, packageName) }
        if (others.any { !COMPONENT.matches(it) }) return null
        return if (others.isEmpty()) {
            "settings delete secure $SERVICES_KEY; settings put secure $ENABLED_KEY 0"
        } else {
            "settings put secure $SERVICES_KEY '${others.joinToString(":")}'"
        }
    }
}
