package brobata.physiboard.core.toolbox

/**
 * The three tiers Remove bloat groups its catalogue into, in display order.
 * spec: broker-privileged-toolbox.md SS12.2.
 */
enum class BloatTier(val header: String, val description: String) {
    FACTORY("Factory tools", "Test and calibration apps left over from the production line. Nothing uses them."),
    VENDOR("Vendor features", "Real features that duplicate something Android already does, or that you may simply not want."),
    HARDWARE("Drives hardware", "Listed so you can see them, but removing these makes real hardware stop working."),
}

/**
 * The four preset categories a catalogue entry can belong to (the "Presets" column of SS12.2's
 * table); [BloatCatalog.presets] turns each into the card SS12.3 describes. An entry with no tag
 * is in no preset ("none" in the table).
 */
enum class BloatPresetTag { FACTORY_TOOLS, VENDOR_EXTRAS, BACKGROUND_KILLERS, PRIVACY }

/** One catalogue row. spec: SS12.2. */
data class BloatEntry(
    val packageName: String,
    val label: String,
    val tier: BloatTier,
    val summary: String,
    val tags: Set<BloatPresetTag> = emptySet(),
)

/** One preset card. spec: SS12.3. [tag] is the entries it draws from; membership is [BloatCatalog.entriesForPreset]. */
data class BloatPresetInfo(
    val label: String,
    val description: String,
    val tag: BloatPresetTag,
    val badge: String? = null,
)

/**
 * The Remove bloat catalogue: which of the phone's own packages the app knows how to remove,
 * which it refuses to touch no matter what, and the preset cards built from both.
 *
 * The catalogue is the tripwire the rest of Remove bloat is built around (SS23, "keep; the
 * denylist is the tripwire"): [isRemovable] is the one function every mutation must pass, and it
 * is deliberately AND, not OR, so a future entry that is accidentally both catalogued and
 * protected removes nothing rather than removing something protected.
 *
 * SPEC GAP: SS12.2 states the label, tier and preset tags for all 28 rows but gives a verbatim
 * one-line summary for only two examples (Phone Manager, IR Remote). The other 26 summaries below
 * are written to the tier's own stated purpose and are not quoted from anywhere; the maintainer
 * should replace them with the real 2.x wording after a clean-room review of the shipped strings,
 * not from the old source tree.
 *
 * spec: broker-privileged-toolbox.md SS12.2, SS12.3, SS12.4, SS12.5; T1 to T8.
 */
object BloatCatalog {

    val entries: List<BloatEntry> = listOf(
        // Factory tools: alphabetical by label. spec: SS12.2 rows 1-11.
        BloatEntry("com.bhpme.AgingTest", "Aging Test", BloatTier.FACTORY, "Factory burn-in test. Nothing on a consumer phone runs it.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.agui.app.apninfocollector", "APN Info Collector", BloatTier.FACTORY, "Collects carrier APN details for the production line.", setOf(BloatPresetTag.PRIVACY, BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.agui.batterystatsdumper", "Battery Stats Dumper", BloatTier.FACTORY, "Dumps battery telemetry for the assembly line, not for you.", setOf(BloatPresetTag.PRIVACY, BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.agui.calibration", "Calibration", BloatTier.FACTORY, "Sensor and display calibration tool from the production line.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.devices116", "Devices116", BloatTier.FACTORY, "An unlabelled factory-test package.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.agui.factorytest", "Factory Test", BloatTier.FACTORY, "The production line's own self-test suite.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.example.feedback", "Feedback", BloatTier.FACTORY, "A leftover sample feedback-form app from the build template.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.swatch.gps", "GPS Test", BloatTier.FACTORY, "Raw GPS fix test used on the production line.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.agui.app.imei", "IMEI Tool", BloatTier.FACTORY, "Reads and writes IMEI values during manufacturing.", setOf(BloatPresetTag.FACTORY_TOOLS)),
        BloatEntry("com.debug.loggerui", "MediaTek Logger", BloatTier.FACTORY, "MediaTek's own modem and system logger; runs constantly in the background.", setOf(BloatPresetTag.PRIVACY, BloatPresetTag.FACTORY_TOOLS, BloatPresetTag.BACKGROUND_KILLERS)),
        BloatEntry("com.agui.app.memtester", "Memory Tester", BloatTier.FACTORY, "RAM stress test used on the production line.", setOf(BloatPresetTag.FACTORY_TOOLS)),

        // Vendor features: alphabetical by label. spec: SS12.2 rows 12-27.
        BloatEntry("com.agui.appblock", "App Block", BloatTier.VENDOR, "Vendor app-blocking manager that duplicates Android's own restrictions.", setOf(BloatPresetTag.BACKGROUND_KILLERS)),
        BloatEntry("com.agui.frozen", "App Freezer", BloatTier.VENDOR, "Freezes background apps with its own scheduler, duplicating Doze and App Standby.", setOf(BloatPresetTag.BACKGROUND_KILLERS)),
        BloatEntry("com.agui.applock", "App Lock", BloatTier.VENDOR, "PIN-locks individual apps.", emptySet()),
        BloatEntry("com.agold.autopoweronoff", "Auto Power On/Off", BloatTier.VENDOR, "Schedules automatic power on/off, waking the phone on its own timer.", setOf(BloatPresetTag.BACKGROUND_KILLERS)),
        BloatEntry("com.agui.bedtimesetting", "Bedtime", BloatTier.VENDOR, "Vendor bedtime and do-not-disturb scheduler.", setOf(BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry("com.agui.callrecord", "Call Recorder", BloatTier.VENDOR, "Records phone calls and stores them locally.", setOf(BloatPresetTag.PRIVACY, BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry("com.agold.cyclocomputer", "Cyclocomputer", BloatTier.VENDOR, "Bicycle speed and cadence computer using the phone's sensors.", setOf(BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry("com.agui.game", "Game Mode", BloatTier.VENDOR, "Vendor game-performance mode and overlay.", setOf(BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry("com.agui.aguigrabageclear", "Garbage Clear", BloatTier.VENDOR, "Vendor RAM and cache cleaner that duplicates Android's own memory management.", setOf(BloatPresetTag.BACKGROUND_KILLERS)),
        BloatEntry("com.agui.nfc", "NFC Tools", BloatTier.VENDOR, "Vendor NFC utilities beyond the stock NFC service.", setOf(BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry("com.agui.providers.pedometer", "Pedometer", BloatTier.VENDOR, "Step counter reading the phone's own accelerometer.", setOf(BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry(
            "com.agui.systemmanager",
            "Phone Manager",
            BloatTier.VENDOR,
            "Vendor battery and memory manager. Android already does all of this in the system server — Doze, App Standby, Adaptive Battery — and this duplicates it with its own opinions about killing background apps.",
            setOf(BloatPresetTag.BACKGROUND_KILLERS),
        ),
        BloatEntry("com.agui.privatespace", "Private Space", BloatTier.VENDOR, "A second, hidden app space with its own PIN.", emptySet()),
        BloatEntry("com.agui.rotationcontrol", "Rotation Control", BloatTier.VENDOR, "Per-app auto-rotate override.", setOf(BloatPresetTag.VENDOR_EXTRAS)),
        BloatEntry("com.agui.studentmodel", "Student Mode", BloatTier.VENDOR, "Restricts the phone to a fixed app allow-list.", emptySet()),
        BloatEntry("com.agui.toolbox", "Toolbox", BloatTier.VENDOR, "Vendor grab-bag of flashlight, compass and unit-converter utilities.", setOf(BloatPresetTag.VENDOR_EXTRAS)),

        // Drives hardware. spec: SS12.2 row 28.
        BloatEntry("com.tiqiaa.icontrol", "IR Remote", BloatTier.HARDWARE, "Drives the infrared blaster. Removing it makes that hardware unusable.", emptySet()),
    )

    /**
     * spec: SS12.3, in the spec's own order; card visibility and membership come from
     * [entriesForPreset]. SPEC GAP: SS12.3 item 3 says "9 packages" but names only 8, and those 8
     * are exactly the entries tagged [BloatPresetTag.VENDOR_EXTRAS] in SS12.2's own table (cross-
     * checked against the explicit exclusion list in the same sentence); the named 8 are used and
     * the "9" is treated as the spec's own typo rather than an invented ninth package.
     */
    val presets: List<BloatPresetInfo> = listOf(
        BloatPresetInfo(
            label = "Android Auto stabilizer",
            description = "Disables the six background managers most likely to be the reason Android Auto drops mid-drive. This is the most likely cause, not a proven one.",
            tag = BloatPresetTag.BACKGROUND_KILLERS,
            badge = "In testing",
        ),
        BloatPresetInfo(
            label = "Factory and lab tools",
            description = "Every test and calibration app left over from the production line.",
            tag = BloatPresetTag.FACTORY_TOOLS,
        ),
        BloatPresetInfo(
            label = "Vendor apps and games",
            description = "Real vendor features you may not want. App Lock, Private Space and Student Mode are left out on purpose; IR Remote is never touched.",
            tag = BloatPresetTag.VENDOR_EXTRAS,
        ),
        BloatPresetInfo(
            label = "Sends data onward",
            description = "Apps whose whole job is collecting and reporting phone data.",
            tag = BloatPresetTag.PRIVACY,
        ),
    )

    /** spec: SS12.4, the 12 names refused by name regardless of pattern. */
    private val protectedNames: Set<String> = setOf(
        "com.agui.shortcutsettings",
        "com.agui.settings",
        "com.agui.update",
        "com.agui.spacebarkey",
        "com.agui.esim.service",
        "com.agui.keyboard",
        "com.agui.overlay.kika",
        "com.agold.networkmanager.service",
        "com.agold.networkmanager.ui",
        "com.agui.systemui.fixed_status_bar_icon_size",
        "com.agui.internal.fixed_status_bar_icon_size",
        "com.iqqijni.bbkeyboard",
    )

    /** spec: SS12.4, case-insensitive substrings. */
    private val protectedKeywords: List<String> = listOf("overlay", "telephony", "dialer", "sms", "systemui", "launcher")

    /** spec: SS12.4, case-insensitive prefixes. */
    private val protectedPrefixes: List<String> = listOf("com.android.", "com.google.android.")

    /** spec: SS12.5, the vendor namespaces the census also lists unrecognised packages under. */
    private val vendorNamespacePrefixes: List<String> = listOf(
        "com.agui.", "com.agold.", "com.bhpme.", "com.swatch.", "com.devices", "com.debug.", "com.iqqijni.", "com.tiqiaa.",
    )

    /** spec: SS12.4 ("By name") and ("By pattern, case-insensitive"); T1, T2. */
    fun isProtected(packageName: String): Boolean {
        if (packageName in protectedNames) return true
        val lower = packageName.lowercase()
        if (protectedPrefixes.any { lower.startsWith(it) }) return true
        return protectedKeywords.any { lower.contains(it) }
    }

    /** spec: SS12.5 ("neither catalogued nor protected"); T8. */
    fun isCatalogued(packageName: String): Boolean = isInCatalog(packageName) || isProtected(packageName)

    private fun isInCatalog(packageName: String): Boolean = entries.any { it.packageName == packageName }

    /**
     * spec: SS12.6 ("the last gate before a name reaches the shell and not only in the UI"); T1
     * to T4. AND, not OR: a name must be catalogued AND not protected, so an entry that is
     * accidentally both never removes anything.
     */
    fun isRemovable(packageName: String): Boolean = isInCatalog(packageName) && !isProtected(packageName)

    /** spec: SS12.5 ("every package in the everything set that starts with one of the vendor namespaces"); T7. */
    fun isVendorNamespace(packageName: String): Boolean = vendorNamespacePrefixes.any { packageName.startsWith(it) }

    /** spec: SS12.3, the packages a preset's "Disable all N" button walks; T6. */
    fun entriesForPreset(tag: BloatPresetTag): List<BloatEntry> = entries.filter { tag in it.tags }
}

/**
 * spec: SS9 ("Package names are validated the same way"), the same shape as the side-key
 * validator but for a package name; T16.
 */
object PackageNameFormat {
    private val PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")
    fun isValid(value: String): Boolean = PATTERN.matches(value)
}

/** spec: SS12.1, SS12.6 ("This catalog is for the Titan 2 Elite only", "Not a package name", "This package is protected"). */
object BloatMutationMessages {
    const val WRONG_DEVICE = "This catalog is for the Titan 2 Elite only"
    const val NOT_A_PACKAGE_NAME = "Not a package name"
    const val PROTECTED = "This package is protected"
}

/** The result of the last gate before a package name reaches the shell. spec: SS12.1, SS12.6; T15, T16. */
sealed class BloatMutationGate {
    object Allowed : BloatMutationGate()
    data class Refused(val reason: String) : BloatMutationGate()
}

/**
 * spec: SS12.1 ("every mutation is refused"), SS12.6 ("the last gate ... not only in the UI");
 * T15, T16. Order: device profile, then format, then catalog membership and protection, matching
 * "before any journal write" (T15).
 */
object BloatMutationChecker {
    fun check(packageName: String, isTitanElite: Boolean): BloatMutationGate {
        if (!isTitanElite) return BloatMutationGate.Refused(BloatMutationMessages.WRONG_DEVICE)
        if (!PackageNameFormat.isValid(packageName)) return BloatMutationGate.Refused(BloatMutationMessages.NOT_A_PACKAGE_NAME)
        // SPEC GAP: the spec names only three refusal reasons and never a fourth for "well-formed
        // but not in the catalog at all"; the UI only ever offers a mutation for a catalogued row,
        // so a defensive caller with an uncatalogued name is folded into "protected" rather than
        // invented a new string the spec never gives.
        if (!BloatCatalog.isRemovable(packageName)) return BloatMutationGate.Refused(BloatMutationMessages.PROTECTED)
        return BloatMutationGate.Allowed
    }
}
