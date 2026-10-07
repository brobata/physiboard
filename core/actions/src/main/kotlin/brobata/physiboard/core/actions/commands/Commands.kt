package brobata.physiboard.core.actions.commands

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The five command sources in rank order. spec: expansion-clipboard-pickers-launcher.md SS8.2.
 * [storageValue] is what saved assignments contain (`pastiera` is kept because saved assignments
 * contain it); [kind] is the stored `kind` field of SS6.1; [quickLauncherDefault] is SS8.6's
 * default visibility.
 */
enum class CommandSource(val storageValue: String, val label: String, val kind: String, val quickLauncherDefault: Boolean, val hue: Int) {
    APPS("apps", "Apps", "App", true, 214),
    PHYSIBOARD("pastiera", "PhysiBoard", "PhysiBoardAction", true, 145),
    APP_ACTIONS("app_actions", "App actions", "AppAction", false, 282),
    DEVICE_CONTROL("device_control", "Device control", "DeviceControl", false, 28),
    NAVIGATION("nav_actions", "Navigation", "NavAction", false, 190),
    ;

    val rank: Int get() = ordinal

    companion object {
        fun fromStorage(value: String?): CommandSource? = entries.firstOrNull { it.storageValue == value }
        fun fromKind(value: String?): CommandSource? = entries.firstOrNull { it.kind == value }
    }
}

/** Where a command is offered. spec SS8.1. */
enum class CommandSurface { ASSIGNED_KEY, QUICK_LAUNCHER, NAV_MODE }

/** The four launch specs. spec SS8.1 and the `launch` object of SS6.1. */
sealed class LaunchSpec {
    /** `{"type":"app_package"}`: the package's launcher intent, new task. */
    data class AppPackage(val packageName: String) : LaunchSpec()

    /** `{"type":"intent_uri"}`: an action, optional data URI, optional target, categories and flag strings (`clear_top`, `key=value` extras). */
    data class IntentUri(
        val action: String,
        val data: String? = null,
        val packageName: String? = null,
        val componentName: String? = null,
        val categories: List<String> = emptyList(),
        val flags: List<String> = emptyList(),
    ) : LaunchSpec()

    /** `{"type":"internal_action"}`: a PhysiBoard action by id. */
    data class InternalAction(val actionId: String) : LaunchSpec()

    /** `{"type":"nav_action"}`: a nav mapping type and value, executed through nav mode. */
    data class NavAction(val mappingType: String, val value: String) : LaunchSpec()

    companion object {
        const val TYPE_APP_PACKAGE = "app_package"
        const val TYPE_INTENT_URI = "intent_uri"
        const val TYPE_INTERNAL_ACTION = "internal_action"
        const val TYPE_NAV_ACTION = "nav_action"
        const val FLAG_CLEAR_TOP = "clear_top"

        fun fromJson(obj: JsonObject?): LaunchSpec? {
            if (obj == null) return null
            return when (obj.str("type")) {
                TYPE_APP_PACKAGE -> obj.str("packageName")?.let(::AppPackage)
                TYPE_INTENT_URI -> obj.str("action")?.let { action ->
                    IntentUri(action, obj.str("data"), obj.str("packageName"), obj.str("componentName"), obj.strList("categories"), obj.strList("flags"))
                }
                TYPE_INTERNAL_ACTION -> obj.str("actionId")?.let(::InternalAction)
                TYPE_NAV_ACTION -> {
                    val type = obj.str("mappingType") ?: return null
                    val value = obj.str("value") ?: return null
                    NavAction(type, value)
                }
                else -> null
            }
        }

        fun toJson(spec: LaunchSpec): JsonObject = when (spec) {
            is AppPackage -> JsonObject(mapOf("type" to JsonPrimitive(TYPE_APP_PACKAGE), "packageName" to JsonPrimitive(spec.packageName)))
            is IntentUri -> JsonObject(
                buildMap {
                    put("type", JsonPrimitive(TYPE_INTENT_URI))
                    put("action", JsonPrimitive(spec.action))
                    spec.data?.let { put("data", JsonPrimitive(it)) }
                    spec.packageName?.let { put("packageName", JsonPrimitive(it)) }
                    spec.componentName?.let { put("componentName", JsonPrimitive(it)) }
                    put("categories", JsonArray(spec.categories.map(::JsonPrimitive)))
                    put("flags", JsonArray(spec.flags.map(::JsonPrimitive)))
                },
            )
            is InternalAction -> JsonObject(mapOf("type" to JsonPrimitive(TYPE_INTERNAL_ACTION), "actionId" to JsonPrimitive(spec.actionId)))
            is NavAction -> JsonObject(mapOf("type" to JsonPrimitive(TYPE_NAV_ACTION), "mappingType" to JsonPrimitive(spec.mappingType), "value" to JsonPrimitive(spec.value)))
        }
    }
}

/** The Material icon a command without an app drawable shows. spec SS8.5, named for the glyph. */
enum class CommandIcon {
    APPS_GRID, EVENT, TASK_CHECK, MICROPHONE, HOME, MAGNIFIER, GEAR, PLAY, SKIP_PREVIOUS, SKIP_NEXT,
    VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE, SUN, KEYBOARD, ACCESSIBILITY, GLOBE, BLUETOOTH, WIFI, VOLUME,
    CONTACTLESS, BATTERY_SAVER, BELL, ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, TAB, FIRST_PAGE, LAST_PAGE,
    VERTICAL_ALIGN_TOP, VERTICAL_ALIGN_BOTTOM, CLOSE, BACKSPACE, COPY, PASTE, CUT, UNDO, SELECT_ALL, COMMAND_KEY,
    NAVIGATION, PRIVATE,
}

/**
 * One command. spec SS8.1: an id, a source, a kind, a label, an optional subtitle, an icon, a
 * launch spec, the surfaces it is offered on, and search tokens. [hasAppIcon] says the surface
 * should draw the package's own icon (SS8.5, "Where a command has an app drawable it is shown");
 * the drawable itself is the Android side's to fetch by [iconPackage].
 */
data class Command(
    val id: String,
    val source: CommandSource,
    val label: String,
    val subtitle: String? = null,
    val launch: LaunchSpec,
    val surfaces: Set<CommandSurface> = ALL_SURFACES,
    val searchTokens: List<String> = emptyList(),
    val iconPackage: String? = null,
) {
    val kind: String get() = source.kind
    val hasAppIcon: Boolean get() = iconPackage != null
    val icon: CommandIcon get() = CommandIcons.iconFor(this)

    companion object {
        val ALL_SURFACES: Set<CommandSurface> = CommandSurface.entries.toSet()
    }
}

/**
 * The internal action ids the PhysiBoard and device-control sources carry in their launch specs,
 * one vocabulary for the Android executor. spec SS8.2. The four PhysiBoard ids are the stored 2.x
 * values (saved assignments contain them); the device ids are 3.0's own, since 2.x never stored
 * a launch spec for a device control.
 */
object InternalActions {
    const val OPEN_QUICK_LAUNCHER = "open_quick_launcher"
    const val OPEN_MAIN_ACTIVITY = "open_main_activity"
    const val START_VOICE_ASSISTANT = "start_voice_assistant"
    const val TOGGLE_SOFTWARE_KEYBOARD_MODE = "toggle_software_keyboard_mode"

    /** 3.0's own: turns private mode on or off (app-shell.md SS31). */
    const val TOGGLE_PRIVATE_MODE = "toggle_private_mode"

    /** 3.0's own: opens the Sym page chooser (layers-sym-alt.md SS5.10). */
    const val OPEN_SYM_PAGE_CHOOSER = "open_sym_page_chooser"

    /** 3.0's own: starts or stops dictation, the same action as the Fn burst (dictation.md SS2.2). */
    const val TOGGLE_DICTATION = "toggle_dictation"
    const val OPEN_HOME = "device_home"
    const val MEDIA_PLAY_PAUSE = "media_play_pause"
    const val MEDIA_PREVIOUS = "media_previous"
    const val MEDIA_NEXT = "media_next"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"
    const val VOLUME_MUTE = "volume_mute"
    const val BRIGHTNESS_UP = "brightness_up"
    const val BRIGHTNESS_DOWN = "brightness_down"
    const val SHADE_NOTIFICATIONS = "shade_notifications"
    const val SHADE_QUICK_SETTINGS = "shade_quick_settings"
}

/** The fixed command ids other modules refer to. spec SS8.2. */
object CommandIds {
    const val QUICK_LAUNCHER = "pastiera.quick_launcher"
    const val MAIN = "pastiera.main"
    const val VOICE_ASSISTANT = "pastiera.voice_assistant"
    const val TOGGLE_SOFTWARE_KEYBOARD = "pastiera.toggle_software_keyboard_mode"

    /** 3.0's own id, so it carries the new name rather than the 2.x prefix. app-shell.md SS31. */
    const val TOGGLE_PRIVATE_MODE = "physiboard.toggle_private_mode"

    /** layers-sym-alt.md SS5.10: the same id `:core:keys` emits for a Sym double tap. */
    const val SYM_PAGE_CHOOSER = brobata.physiboard.core.keys.KeyCommands.OPEN_SYM_PAGE_CHOOSER

    /** dictation.md SS2.2: the same id `:core:keys` emits for the Fn burst. */
    const val DICTATION = brobata.physiboard.core.keys.KeyCommands.TOGGLE_DICTATION
    const val APP_PREFIX = "app:"
    const val DEVICE_HOME = "device.home"
    const val MEDIA_PLAY_PAUSE = "device.media.play_pause"
    const val MEDIA_PREVIOUS = "device.media.previous"
    const val MEDIA_NEXT = "device.media.next"
    const val VOLUME_UP = "device.volume.up"
    const val VOLUME_DOWN = "device.volume.down"
    const val VOLUME_MUTE = "device.volume.mute"
    const val BRIGHTNESS_UP = "device.brightness.up"
    const val BRIGHTNESS_DOWN = "device.brightness.down"
    const val SHADE_NOTIFICATIONS = "device.shade.notifications"
    const val SHADE_QUICK_SETTINGS = "device.shade.quick_settings"

    fun appCommandId(packageName: String): String = APP_PREFIX + packageName
    fun packageOfAppCommand(id: String): String? = if (id.startsWith(APP_PREFIX)) id.removePrefix(APP_PREFIX) else null
}

/** The failure toasts. spec SS8.4. */
object CommandFailure {
    const val PACKAGE_NOT_AVAILABLE = "Package not available"
    const val COULD_NOT_OPEN_APP = "Could not open app"
    const val COMMAND_NOT_AVAILABLE = "Command not available"
    const val COMMAND_BLOCKED = "Command blocked"
    const val COMMAND_FAILED = "Command failed"
    const val COULD_NOT_OPEN_QUICK_LAUNCHER = "Could not open QuickLauncher"
    const val COULD_NOT_OPEN_PHYSIBOARD = "Could not open PhysiBoard"
    const val AUDIO_UNAVAILABLE = "Audio unavailable"
    const val SHIZUKU_REQUIRED = "Shizuku required"
    const val SHADE_UNAVAILABLE = "Shade unavailable"
    const val NAV_MODE_UNAVAILABLE = "Nav mode unavailable"
    const val NO_INPUT_CONTEXT = "No input context"
    const val NAV_ACTION_FAILED = "Nav action failed"
    const val UNKNOWN_ACTION = "Unknown action"
    const val NO_VOICE_ASSISTANT = "No voice assistant is set up on this device."
}

/**
 * The commands PhysiBoard itself defines, source by source. spec SS8.2. Everything that depends
 * on the device (installed apps, which app actions and settings intents resolve, the API level)
 * arrives as a parameter, so the catalogue itself is a pure function.
 */
object BuiltInCommands {

    /** spec SS8.2, "PhysiBoard". The software-keyboard toggle is dropped with the soft keyboard (settings-catalog.md SS2.14). */
    fun physiboard(): List<Command> = listOf(
        Command(CommandIds.QUICK_LAUNCHER, CommandSource.PHYSIBOARD, "PhysiBoard QuickLauncher", "Open PhysiBoard search",
            LaunchSpec.InternalAction(InternalActions.OPEN_QUICK_LAUNCHER), setOf(CommandSurface.ASSIGNED_KEY, CommandSurface.NAV_MODE), listOf("quick launcher", "search")),
        Command(CommandIds.MAIN, CommandSource.PHYSIBOARD, "PhysiBoard", "Open app settings",
            LaunchSpec.InternalAction(InternalActions.OPEN_MAIN_ACTIVITY), setOf(CommandSurface.ASSIGNED_KEY, CommandSurface.NAV_MODE), listOf("settings")),
        Command(CommandIds.VOICE_ASSISTANT, CommandSource.PHYSIBOARD, "Voice assistant", "Open it already listening",
            LaunchSpec.InternalAction(InternalActions.START_VOICE_ASSISTANT), Command.ALL_SURFACES, listOf("assistant", "voice")),
        Command(CommandIds.TOGGLE_PRIVATE_MODE, CommandSource.PHYSIBOARD, "Private mode", "Turn private mode on or off",
            LaunchSpec.InternalAction(InternalActions.TOGGLE_PRIVATE_MODE), Command.ALL_SURFACES, listOf("private", "incognito", "offline", "privacy")),
        Command(CommandIds.SYM_PAGE_CHOOSER, CommandSource.PHYSIBOARD, "Sym page chooser", "Pick a Sym page by its letter",
            LaunchSpec.InternalAction(InternalActions.OPEN_SYM_PAGE_CHOOSER), setOf(CommandSurface.ASSIGNED_KEY, CommandSurface.NAV_MODE),
            listOf("sym", "page", "emoji", "symbols", "gif", "kaomoji", "clipboard", "chooser")),
        Command(CommandIds.DICTATION, CommandSource.PHYSIBOARD, "Dictation", "Start or stop dictation, like holding Fn",
            LaunchSpec.InternalAction(InternalActions.TOGGLE_DICTATION), Command.ALL_SURFACES, listOf("dictation", "dictate", "voice", "speech", "microphone", "mic", "talk")),
    )

    /** One app with a launcher activity: id `app:<package>`, label the app name, subtitle the package, all three surfaces, tokens name and package. */
    fun app(packageName: String, appName: String): Command = Command(
        CommandIds.appCommandId(packageName), CommandSource.APPS, appName, packageName,
        LaunchSpec.AppPackage(packageName), Command.ALL_SURFACES, listOf(appName, packageName), iconPackage = packageName,
    )

    data class AppAction(val id: String, val label: String, val packageName: String, val spec: LaunchSpec.IntentUri)

    /** spec SS8.2, "App actions": the nine intents and their targets; listed only when the app resolves them ([resolves]). */
    val APP_ACTIONS: List<AppAction> = listOf(
        AppAction("niagara.search", "Niagara search", "bitpit.launcher", uri("android.intent.action.VIEW", "niagara://search", "bitpit.launcher")),
        AppAction("niagara.agenda", "Niagara agenda", "bitpit.launcher", uri("android.intent.action.VIEW", "niagara://agenda", "bitpit.launcher")),
        AppAction("tasker.select_task", "Tasker: select task", "net.dinglisch.android.taskerm", action("net.dinglisch.android.tasker.ACTION_TASK_SELECT", "net.dinglisch.android.taskerm")),
        AppAction("tasker.preferences", "Tasker: preferences", "net.dinglisch.android.taskerm", action("net.dinglisch.android.tasker.ACTION_OPEN_PREFS", "net.dinglisch.android.taskerm")),
        AppAction("tasker.create_shortcut", "Tasker: create shortcut", "net.dinglisch.android.taskerm", action("android.intent.action.CREATE_SHORTCUT", "net.dinglisch.android.taskerm")),
        AppAction("homeassistant.navigate", "Home Assistant: navigate", "io.homeassistant.companion.android", uri("android.intent.action.VIEW", "homeassistant://navigate", "io.homeassistant.companion.android")),
        AppAction("homeassistant.assist", "Home Assistant: assist", "io.homeassistant.companion.android", action("android.intent.action.ASSIST", "io.homeassistant.companion.android")),
        AppAction("homeassistant.voice_command", "Home Assistant: voice command", "io.homeassistant.companion.android", action("android.intent.action.VOICE_COMMAND", "io.homeassistant.companion.android")),
    )

    fun appActions(resolves: (LaunchSpec.IntentUri) -> Boolean, appLabel: (String) -> String?): List<Command> =
        APP_ACTIONS.filter { resolves(it.spec) }.map {
            Command(it.id, CommandSource.APP_ACTIONS, it.label, appLabel(it.packageName) ?: it.packageName, it.spec, Command.ALL_SURFACES, listOf(it.label, it.packageName), iconPackage = it.packageName)
        }

    private fun uri(action: String, data: String, pkg: String) = LaunchSpec.IntentUri(action, data, pkg, categories = listOf("android.intent.category.BROWSABLE"))
    private fun action(action: String, pkg: String) = LaunchSpec.IntentUri(action, packageName = pkg)

    data class SettingsCommand(val id: String, val label: String, val action: String, val minApi: Int = 0, val addsPackageExtra: Boolean = false)

    /** spec SS8.2, the `settings.android.*` commands, each "listed only when resolvable". */
    val SETTINGS_COMMANDS: List<SettingsCommand> = listOf(
        SettingsCommand("settings.android.main", "Settings", "android.settings.SETTINGS"),
        SettingsCommand("settings.android.apps", "Apps", "android.settings.APPLICATION_SETTINGS"),
        SettingsCommand("settings.android.default_apps", "Default apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        SettingsCommand("settings.android.input_method", "Keyboard settings", "android.settings.INPUT_METHOD_SETTINGS"),
        SettingsCommand("settings.android.accessibility", "Accessibility", "android.settings.ACCESSIBILITY_SETTINGS"),
        SettingsCommand("settings.android.language_input", "Language & input", "android.settings.LOCALE_SETTINGS"),
        SettingsCommand("settings.android.bluetooth", "Bluetooth", "android.settings.BLUETOOTH_SETTINGS"),
        SettingsCommand("settings.android.wifi", "Wi-Fi", "android.settings.WIFI_SETTINGS"),
        SettingsCommand("settings.android.internet_panel", "Internet", "android.settings.panel.action.INTERNET_CONNECTIVITY", minApi = 29),
        SettingsCommand("settings.android.display", "Display / brightness", "android.settings.DISPLAY_SETTINGS"),
        SettingsCommand("settings.android.sound", "Sound & vibration", "android.settings.SOUND_SETTINGS"),
        SettingsCommand("settings.android.nfc", "NFC", "android.settings.NFC_SETTINGS"),
        SettingsCommand("settings.android.battery", "Battery", "android.settings.BATTERY_SAVER_SETTINGS"),
        SettingsCommand("settings.android.notifications", "Notifications", "android.settings.NOTIFICATION_SETTINGS"),
        SettingsCommand("settings.android.pastiera_notifications", "PhysiBoard notifications", "android.settings.APP_NOTIFICATION_SETTINGS", addsPackageExtra = true),
    )

    /**
     * spec SS8.2, "Device control", subtitle the group. [resolvesAction] answers whether a settings
     * action resolves; [ownPackage] is the package the last settings command adds as its extra.
     */
    fun deviceControl(runningApi: Int, resolvesAction: (String) -> Boolean, ownPackage: String): List<Command> {
        val fixed = listOf(
            device(CommandIds.DEVICE_HOME, "Home screen", "System", InternalActions.OPEN_HOME),
            device(CommandIds.MEDIA_PLAY_PAUSE, "Play / pause", "Media", InternalActions.MEDIA_PLAY_PAUSE),
            device(CommandIds.MEDIA_PREVIOUS, "Previous track", "Media", InternalActions.MEDIA_PREVIOUS),
            device(CommandIds.MEDIA_NEXT, "Next track", "Media", InternalActions.MEDIA_NEXT),
            device(CommandIds.VOLUME_UP, "Volume up", "Audio", InternalActions.VOLUME_UP),
            device(CommandIds.VOLUME_DOWN, "Volume down", "Audio", InternalActions.VOLUME_DOWN),
            device(CommandIds.VOLUME_MUTE, "Mute volume", "Audio", InternalActions.VOLUME_MUTE),
            device(CommandIds.BRIGHTNESS_UP, "Brightness up", "Display", InternalActions.BRIGHTNESS_UP),
            device(CommandIds.BRIGHTNESS_DOWN, "Brightness down", "Display", InternalActions.BRIGHTNESS_DOWN),
            device(CommandIds.SHADE_NOTIFICATIONS, "Open notifications", "System", InternalActions.SHADE_NOTIFICATIONS),
            device(CommandIds.SHADE_QUICK_SETTINGS, "Open quick settings", "System", InternalActions.SHADE_QUICK_SETTINGS),
        )
        val settings = SETTINGS_COMMANDS.filter { runningApi >= it.minApi && resolvesAction(it.action) }.map {
            val flags = if (it.addsPackageExtra) listOf("android.provider.extra.APP_PACKAGE=$ownPackage") else emptyList()
            Command(it.id, CommandSource.DEVICE_CONTROL, it.label, if (it.minApi > 0) "System panel" else "Settings", LaunchSpec.IntentUri(it.action, flags = flags), Command.ALL_SURFACES, listOf(it.label))
        }
        return fixed + settings
    }

    private fun device(id: String, label: String, group: String, actionId: String) =
        Command(id, CommandSource.DEVICE_CONTROL, label, group, LaunchSpec.InternalAction(actionId), Command.ALL_SURFACES, listOf(label, group))

    /** spec SS8.2, "Navigation": nav mode surface only. */
    val NAV_KEYCODES: List<Pair<String, String>> = listOf(
        "DPAD_UP" to "Up", "DPAD_DOWN" to "Down", "DPAD_LEFT" to "Left", "DPAD_RIGHT" to "Right", "TAB" to "Tab",
        "MOVE_HOME" to "Home", "MOVE_END" to "End", "PAGE_UP" to "Page up", "PAGE_DOWN" to "Page down", "ESCAPE" to "Escape",
        "DPAD_CENTER" to "Center", "FORWARD_DEL" to "Forward delete",
    )
    val NAV_ACTIONS: List<String> = listOf(
        "copy", "paste", "cut", "undo", "select_all", "expand_selection_left", "expand_selection_right", "move_word_left", "move_word_right",
        "expand_selection_word_left", "expand_selection_word_right", "page_start", "page_end", "media_play_pause", "media_previous", "media_next",
    )

    fun navigation(): List<Command> =
        NAV_KEYCODES.map { (name, label) ->
            Command("nav.keycode.$name", CommandSource.NAVIGATION, label, "Navigation", LaunchSpec.NavAction("keycode", name), setOf(CommandSurface.NAV_MODE), listOf(label))
        } + NAV_ACTIONS.map { name ->
            val label = name.replace('_', ' ').replaceFirstChar { it.uppercase() }
            Command("nav.action.$name", CommandSource.NAVIGATION, label, "Action", LaunchSpec.NavAction("action", name), setOf(CommandSurface.NAV_MODE), listOf(label))
        }
}

/**
 * The live catalogue: every command from every source, looked up by id. spec SS8.3: "Lookups by
 * id read the live catalog each time"; a caller rebuilds this when the app list changes.
 */
data class CommandCatalog(val commands: List<Command>) {
    private val byId: Map<String, Command> = commands.associateBy { it.id }

    fun find(id: String): Command? = byId[id]

    fun forSurface(surface: CommandSurface): List<Command> = commands.filter { surface in it.surfaces }

    /** spec SS8.6: only the quick launcher surface is filtered by source visibility. */
    fun forQuickLauncher(visibility: SourceVisibility): List<Command> =
        commands.filter { CommandSurface.QUICK_LAUNCHER in it.surfaces && visibility.isEnabled(it.source) }

    companion object {
        val EMPTY = CommandCatalog(emptyList())
    }
}

/** `command_surface_sources`. spec SS8.6 (T52). */
data class SourceVisibility(val quickLauncher: Map<CommandSource, Boolean> = emptyMap()) {
    fun isEnabled(source: CommandSource): Boolean = quickLauncher[source] ?: source.quickLauncherDefault

    fun with(source: CommandSource, enabled: Boolean): SourceVisibility = copy(quickLauncher = quickLauncher + (source to enabled))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** "missing sources take the defaults"; a malformed document is the defaults. */
        fun parse(text: String?): SourceVisibility {
            if (text.isNullOrBlank()) return SourceVisibility()
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return SourceVisibility()
            val map = HashMap<CommandSource, Boolean>()
            for ((key, value) in obj) {
                val source = CommandSource.fromStorage(key) ?: continue
                val enabled = (value as? JsonObject)?.get("quick_launcher")?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: continue
                map[source] = enabled
            }
            return SourceVisibility(map)
        }

        fun encode(visibility: SourceVisibility): String {
            val obj = JsonObject(
                CommandSource.entries.associate { source ->
                    source.storageValue to JsonObject(mapOf("quick_launcher" to JsonPrimitive(visibility.isEnabled(source))))
                },
            )
            return json.encodeToString(JsonObject.serializer(), obj)
        }
    }
}

/** spec SS8.5: the icon rules, "first match wins". */
object CommandIcons {
    fun iconFor(command: Command): CommandIcon {
        val id = command.id
        return when {
            command.source == CommandSource.APPS -> CommandIcon.APPS_GRID
            command.source == CommandSource.APP_ACTIONS -> when {
                id.contains("agenda") -> CommandIcon.EVENT
                id.startsWith("tasker") -> CommandIcon.TASK_CHECK
                id == "homeassistant.assist" || id.contains("voice") -> CommandIcon.MICROPHONE
                id.startsWith("homeassistant") -> CommandIcon.HOME
                else -> CommandIcon.MAGNIFIER
            }
            id == CommandIds.QUICK_LAUNCHER -> CommandIcon.MAGNIFIER
            id == CommandIds.MAIN -> CommandIcon.GEAR
            id == CommandIds.TOGGLE_PRIVATE_MODE -> CommandIcon.PRIVATE
            id == CommandIds.SYM_PAGE_CHOOSER -> CommandIcon.KEYBOARD
            id == CommandIds.MEDIA_PLAY_PAUSE -> CommandIcon.PLAY
            id == CommandIds.MEDIA_PREVIOUS -> CommandIcon.SKIP_PREVIOUS
            id == CommandIds.MEDIA_NEXT -> CommandIcon.SKIP_NEXT
            id == CommandIds.VOLUME_UP -> CommandIcon.VOLUME_UP
            id == CommandIds.VOLUME_DOWN -> CommandIcon.VOLUME_DOWN
            id == CommandIds.VOLUME_MUTE -> CommandIcon.VOLUME_MUTE
            id.startsWith("device.brightness") -> CommandIcon.SUN
            id.contains("default_apps") || id == "settings.android.apps" -> CommandIcon.APPS_GRID
            id.contains("input_method") -> CommandIcon.KEYBOARD
            id.contains("accessibility") -> CommandIcon.ACCESSIBILITY
            id.contains("language") || id.contains("locale") -> CommandIcon.GLOBE
            id.contains("bluetooth") -> CommandIcon.BLUETOOTH
            id.contains("wifi") || id.contains("internet") -> CommandIcon.WIFI
            id.contains("display") -> CommandIcon.SUN
            id.contains("sound") -> CommandIcon.VOLUME
            id.contains("nfc") -> CommandIcon.CONTACTLESS
            id.contains("battery") -> CommandIcon.BATTERY_SAVER
            id.contains("notification") -> CommandIcon.BELL
            id.startsWith("nav.keycode.") -> navKeycodeIcon(id.removePrefix("nav.keycode."))
            id.startsWith("nav.action.") -> navActionIcon(id.removePrefix("nav.action."))
            command.source == CommandSource.DEVICE_CONTROL -> CommandIcon.GEAR
            command.source == CommandSource.PHYSIBOARD || command.source == CommandSource.NAVIGATION -> CommandIcon.COMMAND_KEY
            else -> CommandIcon.MAGNIFIER
        }
    }

    private fun navKeycodeIcon(name: String): CommandIcon = when (name) {
        "DPAD_UP" -> CommandIcon.ARROW_UP
        "DPAD_DOWN" -> CommandIcon.ARROW_DOWN
        "DPAD_LEFT" -> CommandIcon.ARROW_LEFT
        "DPAD_RIGHT" -> CommandIcon.ARROW_RIGHT
        "TAB" -> CommandIcon.TAB
        "MOVE_HOME", "PAGE_UP" -> CommandIcon.FIRST_PAGE
        "MOVE_END", "PAGE_DOWN" -> CommandIcon.LAST_PAGE
        "ESCAPE" -> CommandIcon.CLOSE
        "FORWARD_DEL" -> CommandIcon.BACKSPACE
        "DPAD_CENTER" -> CommandIcon.VERTICAL_ALIGN_BOTTOM
        else -> CommandIcon.NAVIGATION
    }

    private fun navActionIcon(name: String): CommandIcon = when (name) {
        "copy" -> CommandIcon.COPY
        "paste" -> CommandIcon.PASTE
        "cut" -> CommandIcon.CUT
        "undo" -> CommandIcon.UNDO
        "select_all" -> CommandIcon.SELECT_ALL
        "expand_selection_left", "move_word_left", "expand_selection_word_left" -> CommandIcon.ARROW_LEFT
        "expand_selection_right", "move_word_right", "expand_selection_word_right" -> CommandIcon.ARROW_RIGHT
        "page_start" -> CommandIcon.FIRST_PAGE
        "page_end" -> CommandIcon.LAST_PAGE
        "media_play_pause" -> CommandIcon.PLAY
        "media_previous" -> CommandIcon.SKIP_PREVIOUS
        "media_next" -> CommandIcon.SKIP_NEXT
        else -> CommandIcon.NAVIGATION
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.strList(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }.orEmpty()
internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject
