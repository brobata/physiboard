package brobata.physiboard.toolbox

import android.content.Context
import android.util.Log
import brobata.physiboard.inputmethod.EmbeddedAdbShell

/** What a package is currently doing, read from the phone rather than assumed. */
enum class PackageState {
    /** Installed and running normally. */
    ACTIVE,
    /** Present but disabled: gone from the drawer, not executing. */
    DISABLED,
    /** Uninstalled for this user. Still on /system, so it can be restored. */
    UNINSTALLED,
    /** Not on this device at all. */
    ABSENT
}

/**
 * Applies catalog-approved package changes through the paired ADB broker.
 *
 * Two rules hold everything else up:
 *
 *  1. **Only catalog packages reach a command line.** The broker is a shell, so an
 *     unvalidated package name is a command-injection primitive — the same class of bug
 *     closed in VendorSideKeyManager. Every mutation re-checks [BloatCatalog.isRemovable]
 *     immediately before running, not merely at the point the UI offered the choice.
 *  2. **The journal is written before the command, not after.** A change that lands but is
 *     never recorded is a change the user cannot undo.
 *
 * BLOCKING — every call does network IO through the broker. Never call from the main thread.
 */
object PackageRemover {
    private const val TAG = "PackageRemover"

    /** Package names are `[a-z][a-z0-9_]*(\.[a-z0-9_]+)*` — nothing here can be shell syntax. */
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")

    sealed interface Result {
        data object Success : Result
        data object NotPaired : Result
        data class Refused(val reason: String) : Result
        data class Failed(val reason: String) : Result
    }


    /**
     * States for every catalog package, in one broker round trip.
     *
     * Read through the shell rather than PackageManager on purpose: a disabled or
     * uninstalled-for-user package is awkward to classify locally without hidden flags or
     * broad package-visibility permissions, and the broker is required for this screen anyway.
     */
    /**
     * Catalog state plus anything vendor-shaped the catalog has never heard of.
     *
     * The second half only means something because every Titan 2 Elite ships identically: an
     * unrecognised vendor package is evidence the firmware moved, not noise.
     */
    data class Census(
        val states: Map<String, PackageState>,
        val unrecognised: List<String>
    )

    /**
     * Cheap pre-flight before an action is attempted: is a key stored at all.
     *
     * Deliberately NOT a readiness claim for the UI — a stored key does not mean the phone
     * accepts it. Screens use rememberVerifiedBrokerStatus, which actually connects.
     */
    internal fun isReady(context: Context): Boolean = EmbeddedAdbShell.isPaired(context)

    fun census(context: Context): Census? {
        val states = readStates(context) ?: return null
        val unrecognised = lastEverything
            .filter { BloatCatalog.isVendorNamespace(it) && !BloatCatalog.isCatalogued(it) }
            .sorted()
        return Census(states = states, unrecognised = unrecognised)
    }

    /** Every package the last census saw, kept so [census] needs no second round trip. */
    private var lastEverything: Set<String> = emptySet()

    fun readStates(context: Context): Map<String, PackageState>? {
        if (!isReady(context)) return null
        val ok = EmbeddedAdbShell.runShell(
            context,
            "echo __E__; pm list packages -e; echo __D__; pm list packages -d; echo __U__; pm list packages -u"
        )
        if (!ok) return null
        val out = EmbeddedAdbShell.lastResult.orEmpty()

        fun section(from: String, to: String?): Set<String> {
            val start = out.indexOf(from).takeIf { it >= 0 }?.plus(from.length) ?: return emptySet()
            val end = to?.let { out.indexOf(it, start) }?.takeIf { it >= 0 } ?: out.length
            return out.substring(start, end)
                .lineSequence()
                .mapNotNull { it.trim().removePrefix("package:").takeIf { p -> p.isNotEmpty() } }
                .toSet()
        }

        val enabled = section("__E__", "__D__")
        val disabled = section("__D__", "__U__")
        val everything = section("__U__", null)
        lastEverything = everything

        return BloatCatalog.entries().associate { entry ->
            val pkg = entry.packageName
            entry.packageName to when {
                pkg in disabled -> PackageState.DISABLED
                pkg in enabled -> PackageState.ACTIVE
                pkg in everything -> PackageState.UNINSTALLED
                else -> PackageState.ABSENT
            }
        }
    }

    /** Hide the package and stop it running. Reversible with [restore]; nothing is deleted. */
    fun disable(context: Context, packageName: String, currentState: PackageState): Result =
        mutate(context, packageName, currentState, RemovalJournal.Action.DISABLED) {
            "pm disable-user --user 0 $it"
        }

    /**
     * Uninstall for this user. The APK stays on /system, so [restore] can bring it back —
     * but a factory reset or a firmware update is the only other way back, which is why the
     * UI warns before offering this.
     */
    fun uninstall(context: Context, packageName: String, currentState: PackageState): Result =
        mutate(context, packageName, currentState, RemovalJournal.Action.UNINSTALLED) {
            "pm uninstall --user 0 $it"
        }

    /**
     * Put a package back the way the journal says it was. Both commands are issued because a
     * package can be both uninstalled-for-user and disabled, and reinstalling does not by
     * itself re-enable.
     */
    fun restore(context: Context, packageName: String): Result {
        if (!PACKAGE_NAME.matches(packageName)) {
            return Result.Refused("Not a package name")
        }
        if (!isReady(context)) return Result.NotPaired

        val reinstalled = runCatching {
            EmbeddedAdbShell.runShell(context, "cmd package install-existing --user 0 $packageName")
        }.getOrDefault(false)
        val enabled = runCatching {
            EmbeddedAdbShell.runShell(context, "pm enable --user 0 $packageName")
        }.getOrDefault(false)

        return if (reinstalled || enabled) {
            RemovalJournal.forget(context, packageName)
            Result.Success
        } else {
            Result.Failed(EmbeddedAdbShell.lastError ?: "Restore failed")
        }
    }

    /** Undo everything the journal knows about. Reports how many came back. */
    fun restoreAll(context: Context): Pair<Int, Int> {
        val records = RemovalJournal.all(context)
        var restored = 0
        records.forEach { record ->
            if (restore(context, record.packageName) is Result.Success) restored++
        }
        return restored to records.size
    }

    private fun mutate(
        context: Context,
        packageName: String,
        currentState: PackageState,
        action: RemovalJournal.Action,
        command: (String) -> String
    ): Result {
        if (!BloatCatalog.isSupportedDevice()) {
            return Result.Refused("This catalog is for the Titan 2 Elite only")
        }
        if (!PACKAGE_NAME.matches(packageName)) {
            Log.w(TAG, "Refusing a package name that is not one")
            return Result.Refused("Not a package name")
        }
        // Re-checked here, not just where the UI built its list: this is the last gate before
        // the name becomes part of a shell command.
        if (!BloatCatalog.isRemovable(packageName)) {
            Log.w(TAG, "Refusing $packageName — not removable")
            return Result.Refused("This package is protected")
        }
        if (!isReady(context)) return Result.NotPaired

        // Before the command: an unrecorded change is an change that cannot be undone.
        RemovalJournal.record(
            context,
            RemovalJournal.Record(
                packageName = packageName,
                previousState = currentState,
                action = action,
                timestamp = System.currentTimeMillis()
            )
        )

        val ok = runCatching { EmbeddedAdbShell.runShell(context, command(packageName)) }
            .getOrDefault(false)
        if (!ok) {
            // It never happened, so the journal must not claim it did.
            RemovalJournal.forget(context, packageName)
            return Result.Failed(EmbeddedAdbShell.lastError ?: "Command failed")
        }
        return Result.Success
    }
}
