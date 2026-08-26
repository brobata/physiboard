package it.palsoftware.pastiera.toolbox

import it.palsoftware.pastiera.inputmethod.DeviceSpecific

/**
 * The curated list of Unihertz packages this device ships that a user may not want, and — far
 * more importantly — the list of packages that must never be touched.
 *
 * Curation is the whole product here. The privileged mechanism (the paired ADB broker) has
 * existed since 1.0.1; what makes removal safe rather than reckless is knowing which of the
 * ~45 vendor packages are factory leftovers, which are features, and which the phone or this
 * very app depends on. Every entry below was inventoried on a real Titan 2 Elite.
 */
object BloatCatalog {

    /**
     * The firmware this catalog was verified against, package by package, on a real device.
     * Every Titan 2 Elite ships identically, so a different build is the one thing that can
     * make this list wrong.
     */
    const val VERIFIED_FIRMWARE = "V02.00.02"

    fun isCurrentFirmware(): Boolean =
        android.os.Build.VERSION.INCREMENTAL == VERIFIED_FIRMWARE

    enum class Tier {
        /** Factory and lab tooling with no purpose on a shipped phone. */
        SAFE,
        /** Real features. Wanted by someone, just probably not by you. */
        OPTIONAL,
        /** Drives actual hardware. Listed so it is visible, defaulted to keep. */
        USEFUL
    }

    /**
     * Groups an entry into a one-tap recommendation.
     *
     * [BACKGROUND_KILLERS] is the set of vendor tools whose whole job is stopping background
     * apps from running — managers, freezers, blockers, cleaners, and a chipset logger that
     * writes constantly. Android Auto is a long-running foreground projection session, which
     * is exactly what that kind of tool interrupts, so this is the most plausible cause of a
     * connection that keeps dropping.
     *
     * Stated plainly because it matters: this is a hypothesis, not a proven fix. What is
     * certain is what these packages do; what is not is whether they are what breaks Android
     * Auto on any given phone. Everything here is reversible, which is what makes it a
     * reasonable thing to try rather than a claim.
     */
    enum class Preset { BACKGROUND_KILLERS }

    data class Entry(
        val packageName: String,
        val label: String,
        val summary: String,
        val tier: Tier,
        val presets: Set<Preset> = emptySet()
    )

    fun entriesIn(preset: Preset): List<Entry> = ENTRIES.filter { preset in it.presets }

    /**
     * Never removable, enforced here rather than by leaving them out of the UI — a denylist
     * that lives only in a screen is one refactor away from not existing.
     *
     * These break either the phone or PhysiBoard itself:
     *  - shortcutsettings owns the orange side key, which PhysiBoard rebinds.
     *  - settings hosts the vendor keyboard/backlight configuration.
     *  - update is the OTA client; removing it ends firmware updates permanently.
     *  - spacebarkey handles a spacebar that is also the fingerprint sensor and this
     *    keyboard's trackpad trigger.
     */
    val DENYLIST: Set<String> = setOf(
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
        "com.agui.internal.fixed_status_bar_icon_size"
    )

    /**
     * Whole classes of package that must never be removed regardless of catalog membership.
     * Overlays are RRO resource packages rather than apps — removing one strips resources the
     * system expects to exist instead of freeing anything.
     */
    private val DENY_PATTERNS: List<Regex> = listOf(
        Regex("overlay", RegexOption.IGNORE_CASE),
        Regex("^com\\.android\\."),
        Regex("^com\\.google\\.android\\."),
        Regex("telephony|dialer|\\bsms\\b|systemui|launcher", RegexOption.IGNORE_CASE)
    )

    private val ENTRIES: List<Entry> = listOf(
        // ---- Factory and lab tooling -------------------------------------------------
        Entry("com.bhpme.AgingTest", "Aging Test",
            "Factory burn-in test. Runs the hardware flat to check it survives assembly.", Tier.SAFE),
        Entry("com.agui.factorytest", "Factory Test",
            "The production-line hardware test menu.", Tier.SAFE),
        Entry("com.agui.calibration", "Calibration",
            "Factory sensor calibration, done once before the phone was boxed.", Tier.SAFE),
        Entry("com.agui.app.memtester", "Memory Tester",
            "Factory RAM test.", Tier.SAFE),
        Entry("com.agui.app.imei", "IMEI Tool",
            "Factory IMEI writing and checking tool.", Tier.SAFE),
        Entry("com.agui.batterystatsdumper", "Battery Stats Dumper",
            "Dumps battery telemetry for vendor debugging.", Tier.SAFE),
        Entry("com.agui.app.apninfocollector", "APN Info Collector",
            "Collects carrier APN details for the vendor.", Tier.SAFE),
        Entry("com.debug.loggerui", "MediaTek Logger",
            "Chipset debug log capture. Writes continuously when enabled.", Tier.SAFE, setOf(Preset.BACKGROUND_KILLERS)),
        Entry("com.devices116", "Devices116",
            "Unlabelled factory package with no launcher entry.", Tier.SAFE),
        Entry("com.swatch.gps", "GPS Test",
            "Factory GPS verification tool.", Tier.SAFE),
        Entry("com.example.feedback", "Feedback",
            "A shipped app still using the com.example placeholder package name.", Tier.SAFE),

        // ---- Vendor features ---------------------------------------------------------
        Entry("com.agui.systemmanager", "Phone Manager",
            "Vendor battery and memory manager. Android already does all of this in the " +
                "system server — Doze, App Standby, Adaptive Battery — and this duplicates it " +
                "with its own opinions about killing background apps.", Tier.OPTIONAL, setOf(Preset.BACKGROUND_KILLERS)),
        Entry("com.agui.aguigrabageclear", "Garbage Clear",
            "\"Junk file\" cleaner of the kind Android has not needed for a decade.", Tier.OPTIONAL, setOf(Preset.BACKGROUND_KILLERS)),
        Entry("com.agui.frozen", "App Freezer",
            "Vendor app freezing, overlapping Android's own app hibernation.", Tier.OPTIONAL, setOf(Preset.BACKGROUND_KILLERS)),
        Entry("com.agui.appblock", "App Block", "Vendor app blocking.", Tier.OPTIONAL, setOf(Preset.BACKGROUND_KILLERS)),
        Entry("com.agui.applock", "App Lock", "Per-app PIN locking.", Tier.OPTIONAL),
        Entry("com.agui.privatespace", "Private Space",
            "Vendor hidden-app space, separate from Android's own Private Space.", Tier.OPTIONAL),
        Entry("com.agui.studentmodel", "Student Mode", "Parental restriction mode.", Tier.OPTIONAL),
        Entry("com.agui.game", "Game Mode", "Game performance mode and overlay.", Tier.OPTIONAL),
        Entry("com.agui.toolbox", "Toolbox", "Vendor utility collection.", Tier.OPTIONAL),
        Entry("com.agui.bedtimesetting", "Bedtime", "Vendor bedtime scheduling.", Tier.OPTIONAL),
        Entry("com.agui.callrecord", "Call Recorder", "Automatic call recording.", Tier.OPTIONAL),
        Entry("com.agui.providers.pedometer", "Pedometer", "Vendor step counter.", Tier.OPTIONAL),
        Entry("com.agui.rotationcontrol", "Rotation Control", "Vendor rotation lock helper.", Tier.OPTIONAL),
        Entry("com.agold.autopoweronoff", "Auto Power On/Off", "Scheduled power on and off.", Tier.OPTIONAL, setOf(Preset.BACKGROUND_KILLERS)),
        Entry("com.agui.nfc", "NFC Tools", "Vendor NFC helper. Does not affect NFC itself.", Tier.OPTIONAL),
        Entry("com.iqqijni.bbkeyboard", "BB Keyboard",
            "A second preinstalled on-screen keyboard.", Tier.OPTIONAL),
        Entry("com.agold.cyclocomputer", "Cyclocomputer",
            "Vendor cycling speedometer.", Tier.OPTIONAL),

        // ---- Drives real hardware ----------------------------------------------------
        Entry("com.tiqiaa.icontrol", "IR Remote",
            "Drives the infrared blaster. Removing it makes that hardware unusable.", Tier.USEFUL)
    )

    /**
     * Namespaces a package on a stock Titan 2 Elite comes from.
     *
     * Every T2E ships the same firmware and therefore the same packages, so this catalog is a
     * known constant rather than a guess — which makes the inverse useful too: a package in one
     * of these namespaces that is NOT catalogued means either a firmware update added something,
     * or this is not the phone we think it is. Both are worth showing rather than ignoring.
     */
    private val VENDOR_PREFIXES = listOf(
        "com.agui.", "com.agold.", "com.bhpme.", "com.swatch.",
        "com.devices", "com.debug.", "com.iqqijni.", "com.tiqiaa."
    )

    fun isVendorNamespace(packageName: String): Boolean =
        VENDOR_PREFIXES.any { packageName.startsWith(it) }

    /**
     * Accounted for one way or another: catalogued, denied by name, or denied by pattern.
     * Only genuinely unknown packages should surface as drift — reporting the ones we
     * deliberately block would bury the signal under things working exactly as intended.
     */
    fun isCatalogued(packageName: String): Boolean =
        ENTRIES.any { it.packageName == packageName } ||
            packageName in DENYLIST ||
            DENY_PATTERNS.any { it.containsMatchIn(packageName) }

    /** Catalog entries, safest first, so the list reads as an ordered recommendation. */
    fun entries(): List<Entry> = ENTRIES.sortedWith(compareBy({ it.tier.ordinal }, { it.label }))

    /**
     * The single gate every mutation must pass. Membership in the catalog is required — a
     * package name from anywhere else must never reach the broker's command line.
     */
    fun isRemovable(packageName: String): Boolean {
        if (packageName in DENYLIST) return false
        if (DENY_PATTERNS.any { it.containsMatchIn(packageName) }) return false
        return ENTRIES.any { it.packageName == packageName }
    }

    /**
     * The catalog describes one phone's firmware. Running it anywhere else would remove
     * packages chosen by name alone, so the whole feature stays inert off-device.
     */
    fun isSupportedDevice(): Boolean = DeviceSpecific.isTitan2EliteDevice()
}
