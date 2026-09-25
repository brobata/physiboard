package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.CommandIds
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.actions.commands.InternalActions
import brobata.physiboard.core.actions.commands.LaunchSpec
import brobata.physiboard.core.actions.commands.str
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The 29 assignable keys and the decimal Android keycode strings the `launcher_shortcuts` JSON
 * is keyed by. spec: expansion-clipboard-pickers-launcher.md SS6.1 ("Assignable keys: the 26
 * letters, Backspace (`KEYCODE_DEL`), Space, Enter"; keys outside these can appear in the
 * preference but are never matched). The numbers are the stored contract of the 2.x document
 * (settings-catalog.md SS2.7, `"62"` for Space), not a platform import.
 */
object AssignableKeys {
    private const val KEYCODE_A = 29
    const val KEYCODE_SPACE: Int = 62
    const val KEYCODE_ENTER: Int = 66
    const val KEYCODE_BACKSPACE: Int = 67

    /** spec SS6.5: `Q W E R T Y U I O P` / `A S D F G H J K L ⌫` / `Z X C V ␣ B N M ⏎`. */
    val GRID_ROWS: List<List<KeyId>> = listOf(
        "QWERTYUIOP".map { KeyId.Letter(it) },
        "ASDFGHJKL".map { KeyId.Letter(it) } + KeyId.Control(ControlKey.BACKSPACE),
        "ZXCV".map { KeyId.Letter(it) } + KeyId.Control(ControlKey.SPACE) + "BNM".map { KeyId.Letter(it) } + KeyId.Control(ControlKey.ENTER),
    )

    val ALL: List<KeyId> = GRID_ROWS.flatten()

    fun keycodeOf(key: KeyId): Int? = when (key) {
        is KeyId.Letter -> KEYCODE_A + (key.qwertyLetter - 'A')
        KeyId.Control(ControlKey.SPACE) -> KEYCODE_SPACE
        KeyId.Control(ControlKey.ENTER) -> KEYCODE_ENTER
        KeyId.Control(ControlKey.BACKSPACE) -> KEYCODE_BACKSPACE
        else -> null
    }

    fun keyOf(keycode: Int): KeyId? = when (keycode) {
        in KEYCODE_A..KEYCODE_A + 25 -> KeyId.Letter('A' + (keycode - KEYCODE_A))
        KEYCODE_SPACE -> KeyId.Control(ControlKey.SPACE)
        KEYCODE_ENTER -> KeyId.Control(ControlKey.ENTER)
        KEYCODE_BACKSPACE -> KeyId.Control(ControlKey.BACKSPACE)
        else -> null
    }

    fun isAssignable(key: KeyId): Boolean = keycodeOf(key) != null

    /** spec SS6.4: the chip's name, `Q`..`M`, `⌫`, `␣`, `⏎`, or "Key 62" for anything else. */
    fun label(keycode: Int): String = when (val key = keyOf(keycode)) {
        is KeyId.Letter -> key.qwertyLetter.toString()
        KeyId.Control(ControlKey.BACKSPACE) -> "⌫"
        KeyId.Control(ControlKey.SPACE) -> "␣"
        KeyId.Control(ControlKey.ENTER) -> "⏎"
        else -> "Key $keycode"
    }
}

/** One stored assignment. spec SS6.1's field table; [type] is `app`, `command`, `quick_launcher` or `shortcut` (reserved, never executed). */
data class ShortcutEntry(
    val type: String,
    val commandId: String? = null,
    val packageName: String? = null,
    val appName: String? = null,
    val source: CommandSource? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val launch: LaunchSpec? = null,
) {
    /** spec SS6.1: a quick launcher entry "by type, by command id `pastiera.quick_launcher`, or by launch spec `open_quick_launcher`". */
    val isQuickLauncher: Boolean
        get() = type == TYPE_QUICK_LAUNCHER || commandId == CommandIds.QUICK_LAUNCHER ||
            (launch as? LaunchSpec.InternalAction)?.actionId == InternalActions.OPEN_QUICK_LAUNCHER

    /** spec SS6.5: "the app's icon filling the key for an installed app"; which package a key shows. */
    val displayPackage: String? get() = packageName ?: commandId?.let(CommandIds::packageOfAppCommand)

    companion object {
        const val TYPE_APP = "app"
        const val TYPE_COMMAND = "command"
        const val TYPE_QUICK_LAUNCHER = "quick_launcher"
        const val TYPE_SHORTCUT = "shortcut"

        /** spec SS6.4: "Choosing a command writes the entry (type `command`, all fields from the command)". */
        fun of(command: Command): ShortcutEntry = ShortcutEntry(
            type = TYPE_COMMAND,
            commandId = command.id,
            packageName = (command.launch as? LaunchSpec.AppPackage)?.packageName ?: command.iconPackage,
            appName = if (command.source == CommandSource.APPS) command.label else null,
            source = command.source,
            title = command.label,
            subtitle = command.subtitle,
            launch = command.launch,
        )

        /** The shipped default for Space (settings-catalog.md SS2.7's baseline row). */
        val QUICK_LAUNCHER: ShortcutEntry = ShortcutEntry(
            type = TYPE_COMMAND, commandId = CommandIds.QUICK_LAUNCHER, appName = "PhysiBoard QuickLauncher", source = CommandSource.PHYSIBOARD,
            title = "PhysiBoard QuickLauncher", subtitle = "Open PhysiBoard search", launch = LaunchSpec.InternalAction(InternalActions.OPEN_QUICK_LAUNCHER),
        )
    }
}

/** The whole `launcher_shortcuts` document, keyed by keycode. spec SS6.1. */
data class LauncherShortcuts(val entries: Map<Int, ShortcutEntry> = emptyMap()) {
    operator fun get(keycode: Int): ShortcutEntry? = entries[keycode]
    fun forKey(key: KeyId): ShortcutEntry? = AssignableKeys.keycodeOf(key)?.let { entries[it] }
    val quickLauncherKeycode: Int? get() = entries.entries.firstOrNull { it.value.isQuickLauncher }?.key

    /** spec SS6.1: "Only one key can hold the quick launcher: saving a quick launcher assignment first removes every other entry that is one" (T36). */
    fun assign(keycode: Int, entry: ShortcutEntry): LauncherShortcuts {
        val cleared = if (entry.isQuickLauncher) entries.filterValues { !it.isQuickLauncher } else entries
        return LauncherShortcuts(cleared + (keycode to entry))
    }

    fun remove(keycode: Int): LauncherShortcuts = LauncherShortcuts(entries - keycode)

    /** spec SS6.5: swap two keys' entries "both directions; dropping on nothing does nothing" (T39). Kept for tap-to-swap; drag is dropped (SS13). */
    fun swap(a: Int, b: Int): LauncherShortcuts {
        val ea = entries[a]
        val eb = entries[b]
        if (ea == null && eb == null) return this
        val next = entries.toMutableMap()
        if (eb != null) next[a] = eb else next.remove(a)
        if (ea != null) next[b] = ea else next.remove(b)
        return LauncherShortcuts(next)
    }

    /** spec SS6.5: "Opening the screen removes entries whose app is no longer installed (by package, for both legacy and command entries)". */
    fun pruneUninstalled(installed: (String) -> Boolean): LauncherShortcuts =
        LauncherShortcuts(entries.filterValues { entry -> entry.displayPackage?.let(installed) ?: true })

    /** What the first read decided. spec SS6.1, "Default assignment" (T37, T38). */
    data class DefaultOutcome(val shortcuts: LauncherShortcuts, val defaultAssigned: Boolean, val blockedBySpace: Boolean)

    /**
     * spec SS6.1: "the first time the shortcuts are read... if `quick_launcher_default_assigned` is
     * not yet true and no entry is a quick launcher, Space gets the quick launcher and the flag is
     * set, unless Space already holds something else, in which case nothing is written and the
     * settings screen shows the hint". Removing it later never re-adds it (SS11).
     */
    fun applyDefault(defaultAlreadyAssigned: Boolean): DefaultOutcome {
        if (defaultAlreadyAssigned || quickLauncherKeycode != null) return DefaultOutcome(this, defaultAlreadyAssigned || quickLauncherKeycode != null, blockedBySpace = false)
        if (entries.containsKey(AssignableKeys.KEYCODE_SPACE)) return DefaultOutcome(this, defaultAssigned = false, blockedBySpace = true)
        return DefaultOutcome(assign(AssignableKeys.KEYCODE_SPACE, ShortcutEntry.QUICK_LAUNCHER), defaultAssigned = true, blockedBySpace = false)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        const val BLOCKED_DEFAULT_HINT: String =
            "New: SYM can open the quick launcher. PhysiBoard would normally assign it to Space, but Space already has a shortcut. " +
                "Open the assignments page and choose “Open quick launcher” on any key to try it."

        /**
         * spec SS6.1 "Loading": an entry without `launch` derives one from its legacy `type`
         * (`app` from `packageName`; `quick_launcher` to `open_quick_launcher`); unknown keys and
         * malformed entries are skipped; a corrupt preference yields no shortcuts and is not
         * rewritten (the caller caches against the raw string).
         */
        fun parse(text: String?): LauncherShortcuts {
            if (text.isNullOrBlank()) return LauncherShortcuts()
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return LauncherShortcuts()
            val out = LinkedHashMap<Int, ShortcutEntry>()
            for ((key, value) in obj) {
                val keycode = key.trim().toIntOrNull() ?: continue
                val entryObj = value as? JsonObject ?: continue
                val entry = parseEntry(entryObj) ?: continue
                out[keycode] = entry
            }
            return LauncherShortcuts(out)
        }

        private fun parseEntry(obj: JsonObject): ShortcutEntry? {
            val type = obj.str("type") ?: return null
            val packageName = obj.str("packageName")
            val explicit = LaunchSpec.fromJson(obj["launch"] as? JsonObject)
            val launch = explicit ?: when (type) {
                ShortcutEntry.TYPE_APP -> packageName?.let { LaunchSpec.AppPackage(it) }
                ShortcutEntry.TYPE_QUICK_LAUNCHER -> LaunchSpec.InternalAction(InternalActions.OPEN_QUICK_LAUNCHER)
                else -> null
            }
            return ShortcutEntry(
                type = type,
                commandId = obj.str("commandId"),
                packageName = packageName,
                appName = obj.str("appName"),
                source = CommandSource.fromStorage(obj.str("source")) ?: CommandSource.fromKind(obj.str("kind")),
                title = obj.str("title"),
                subtitle = obj.str("subtitle"),
                launch = launch,
            )
        }

        fun encode(shortcuts: LauncherShortcuts): String {
            val obj = JsonObject(
                shortcuts.entries.entries.sortedBy { it.key }.associate { (keycode, entry) ->
                    keycode.toString() to JsonObject(
                        buildMap {
                            put("type", JsonPrimitive(entry.type))
                            entry.packageName?.let { put("packageName", JsonPrimitive(it)) }
                            entry.appName?.let { put("appName", JsonPrimitive(it)) }
                            entry.commandId?.let { put("commandId", JsonPrimitive(it)) }
                            entry.source?.let { put("source", JsonPrimitive(it.storageValue)); put("kind", JsonPrimitive(it.kind)) }
                            entry.title?.let { put("title", JsonPrimitive(it)) }
                            entry.subtitle?.let { put("subtitle", JsonPrimitive(it)) }
                            entry.launch?.let { put("launch", LaunchSpec.toJson(it)) }
                        },
                    )
                },
            )
            return json.encodeToString(JsonObject.serializer(), obj)
        }
    }
}

/**
 * What an assigned key runs. spec SS6.3: the id is resolved against the live catalogue (an
 * uninstalled app no longer resolves, a renamed label is refreshed); else the stored launch spec;
 * else nothing. A quick launcher entry always goes through the quick launcher opener; an `app`
 * entry whose command fails falls back to a plain package launch.
 */
sealed class ShortcutRun {
    data object OpenQuickLauncher : ShortcutRun()
    data class RunCommand(val command: Command) : ShortcutRun()
    data class RunLaunchSpec(val launch: LaunchSpec, val source: CommandSource?, val fallbackPackage: String?) : ShortcutRun()
    data object Nothing : ShortcutRun()

    companion object {
        fun resolve(entry: ShortcutEntry, catalog: (String) -> Command?): ShortcutRun {
            if (entry.isQuickLauncher) return OpenQuickLauncher
            val live = entry.commandId?.let(catalog)
            if (live != null) return RunCommand(live)
            val launch = entry.launch ?: return Nothing
            val fallback = if (entry.type == ShortcutEntry.TYPE_APP) entry.packageName else null
            return RunLaunchSpec(launch, entry.source, fallback)
        }
    }
}
