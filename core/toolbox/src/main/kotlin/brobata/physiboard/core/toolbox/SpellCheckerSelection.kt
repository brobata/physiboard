package brobata.physiboard.core.toolbox

/**
 * What the phone reported for its spell checker: the three `Settings.Secure` rows Android's text
 * services read. A null field is a row the system has no value for (`settings get` prints the
 * string `null`), which is a real state and is restored as "unset".
 */
data class SpellCheckerReading(val selected: String?, val enabled: String?, val subtype: String?)

/** Who owns the spell checker that is selected now, as far as the decision needs to know. */
enum class SpellCheckerOwner {
    /** No spell checker is selected. */
    NONE,

    /** This build of PhysiBoard. */
    OURS,

    /** The selected package is no longer installed: a leftover, not a choice. */
    MISSING,

    /** A spell checker that came with the phone. Android picks one of these by itself, so it is the factory default, not a choice. */
    PREINSTALLED,

    /** A spell checker someone installed: another keyboard, another PhysiBoard build. That is a choice, and it is respected. */
    USER_INSTALLED,
}

/** What the setup pass does about the spell checker this time. */
sealed interface SpellCheckerPlan {
    /** Nothing to write. [markDone] records that the one-time decision has been made, so it is never revisited. */
    data class Leave(val reason: String, val markDone: Boolean) : SpellCheckerPlan

    /** Write [line]; on success record [previous] as what "Reset device settings to stock" puts back. */
    data class Select(val line: String, val previous: SpellCheckerReading) : SpellCheckerPlan
}

/**
 * PhysiBoard as the phone's spell checker, chosen once after pairing (the `auto_select_spell_checker`
 * setting). An app cannot select a spell checker for itself; the paired broker can write the
 * secure settings that do. The rules:
 *
 * - It runs once per install. After the first decision (selected, already ours, or someone else's
 *   choice respected) it never looks again, so a spell checker the user picks later is never
 *   taken back.
 * - A spell checker someone installed is their choice and is left alone; that includes the other
 *   PhysiBoard build, so the sideload build never takes over from the release one.
 * - Nothing selected, a leftover from an uninstalled app, or the phone's own preinstalled one is
 *   replaced, and spell checking is switched on.
 * - What was there before is recorded so the reset can put it back.
 *
 * Pure: the device layer runs [READ_LINE] and the package checks and executes the plan.
 * spec: broker-privileged-toolbox.md SS7 step 5, SS10 step 6.
 */
object SpellCheckerSelection {
    /** The service every PhysiBoard build declares; the package in front of it is the running build's. */
    const val SERVICE_CLASS = "brobata.physiboard.ime.PhysiBoardSpellCheckerService"

    const val SELECTED_KEY = "selected_spell_checker"
    const val ENABLED_KEY = "spell_checker_enabled"
    const val SUBTYPE_KEY = "selected_spell_checker_subtype"

    /** Subtype 0 is Android's "follow the system languages", the right start for a new checker. */
    private const val SUBTYPE_SYSTEM_LANGUAGES = "0"

    const val REASON_DISABLED = "skipped_feature_disabled"
    const val REASON_DONE = "already_decided"
    const val REASON_ALREADY_OURS = "already_selected"
    const val REASON_OTHER_CHOSEN = "another_spell_checker_chosen"
    const val REASON_UNREADABLE = "unreadable"

    /** Reads all three rows in one broker round trip, one value per line in this order. */
    const val READ_LINE = "settings get secure $SELECTED_KEY; settings get secure $ENABLED_KEY; settings get secure $SUBTYPE_KEY"

    private val COMPONENT = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")
    private val PACKAGE = Regex("[A-Za-z0-9_.]+")
    private val INTEGER = Regex("-?[0-9]{1,11}")

    fun component(packageName: String): String = "$packageName/$SERVICE_CLASS"

    /** The package half of a `package/class` component, or null when the value is not one. */
    fun packageOf(component: String?): String? {
        val value = component?.trim().orEmpty()
        if (!COMPONENT.matches(value)) return null
        return value.substringBefore('/')
    }

    /** Whether [packageName] may be put into a `pm list packages` line. */
    fun isSafePackage(packageName: String): Boolean = packageName.length <= 256 && PACKAGE.matches(packageName)

    /** `pm list packages [-s] <filter>` matches substrings, so only an exact `package:<name>` line counts. */
    fun listsPackage(output: String, packageName: String): Boolean = output.lines().any { it.trim() == "package:$packageName" }

    fun preinstalledLine(packageName: String): String = "pm list packages -s $packageName"

    fun installedLine(packageName: String): String = "pm list packages $packageName"

    /** [READ_LINE]'s output, or null when it did not print three lines. */
    fun parse(output: String): SpellCheckerReading? {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 3) return null
        fun value(line: String): String? = line.takeUnless { it == "null" }
        return SpellCheckerReading(value(lines[0]), value(lines[1]), value(lines[2]))
    }

    /** Who owns [selected], given what the package checks found; [preinstalled] and [installed] are only asked for another package. */
    fun ownerOf(selected: String?, ourPackage: String, preinstalled: (String) -> Boolean, installed: (String) -> Boolean): SpellCheckerOwner {
        if (selected.isNullOrBlank()) return SpellCheckerOwner.NONE
        val pkg = packageOf(selected) ?: return SpellCheckerOwner.MISSING
        return when {
            pkg == ourPackage -> SpellCheckerOwner.OURS
            preinstalled(pkg) -> SpellCheckerOwner.PREINSTALLED
            installed(pkg) -> SpellCheckerOwner.USER_INSTALLED
            else -> SpellCheckerOwner.MISSING
        }
    }

    /**
     * The decision. [alreadyDecided] is the stored "done" marker; [reading] null means the rows could
     * not be read, which is retried next time rather than marked done.
     */
    fun decide(
        autoSelect: Boolean,
        alreadyDecided: Boolean,
        reading: SpellCheckerReading?,
        owner: SpellCheckerOwner,
        ourComponent: String,
    ): SpellCheckerPlan {
        if (!autoSelect) return SpellCheckerPlan.Leave(REASON_DISABLED, markDone = false)
        if (alreadyDecided) return SpellCheckerPlan.Leave(REASON_DONE, markDone = false)
        if (reading == null) return SpellCheckerPlan.Leave(REASON_UNREADABLE, markDone = false)
        return when (owner) {
            SpellCheckerOwner.USER_INSTALLED -> SpellCheckerPlan.Leave(REASON_OTHER_CHOSEN, markDone = true)
            SpellCheckerOwner.OURS ->
                if (reading.enabled?.trim() == "1") {
                    SpellCheckerPlan.Leave(REASON_ALREADY_OURS, markDone = true)
                } else {
                    SpellCheckerPlan.Select("settings put secure $ENABLED_KEY 1", reading)
                }
            SpellCheckerOwner.NONE, SpellCheckerOwner.MISSING, SpellCheckerOwner.PREINSTALLED ->
                SpellCheckerPlan.Select(selectLine(ourComponent), reading)
        }
    }

    /**
     * Whether the app should offer "Turn on spell checking" itself (no pairing to do it): the
     * same rules as [decide] for a first run, from what the app can see without a shell.
     */
    fun wouldSelect(autoSelect: Boolean, enabled: Boolean, owner: SpellCheckerOwner): Boolean = when {
        !autoSelect -> false
        owner == SpellCheckerOwner.USER_INSTALLED -> false
        owner == SpellCheckerOwner.OURS -> !enabled
        else -> true
    }

    fun selectLine(ourComponent: String): String =
        "settings put secure $SELECTED_KEY $ourComponent; settings put secure $SUBTYPE_KEY $SUBTYPE_SYSTEM_LANGUAGES; settings put secure $ENABLED_KEY 1"

    /**
     * The reset's line: each row goes back to what was recorded, a row that was unset is deleted,
     * and a recorded value that is not a plain component or number (only the system can write
     * these rows, but they come back into a shell line) is deleted rather than written.
     * Null when the selection is no longer ours: the user has picked something since, and the
     * reset leaves their choice alone.
     */
    fun revertLine(previous: SpellCheckerReading, current: SpellCheckerReading, ourComponent: String): String? {
        if (current.selected?.trim() != ourComponent) return null
        fun row(key: String, value: String?, valid: Regex): String {
            val v = value?.trim()
            return if (v != null && valid.matches(v)) "settings put secure $key $v" else "settings delete secure $key"
        }
        return listOf(
            row(SELECTED_KEY, previous.selected, COMPONENT),
            row(SUBTYPE_KEY, previous.subtype, INTEGER),
            row(ENABLED_KEY, previous.enabled, INTEGER),
        ).joinToString("; ")
    }
}
