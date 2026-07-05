package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class DeviceControlCommandSource : CommandSource {
    override val id = CommandSourceId.DeviceControl

    override fun getCommands(context: Context): List<CommandTarget> {
        return buildList {
            add(deviceAction("device.home", "Home screen", "System", ACTION_HOME_SCREEN))
            add(deviceAction("device.media.play_pause", "Play / pause", "Media", ACTION_MEDIA_PLAY_PAUSE))
            add(deviceAction("device.media.previous", "Previous track", "Media", ACTION_MEDIA_PREVIOUS))
            add(deviceAction("device.media.next", "Next track", "Media", ACTION_MEDIA_NEXT))
            add(deviceAction("device.volume.up", "Volume up", "Audio", ACTION_VOLUME_UP))
            add(deviceAction("device.volume.down", "Volume down", "Audio", ACTION_VOLUME_DOWN))
            add(deviceAction("device.volume.mute", "Mute volume", "Audio", ACTION_VOLUME_MUTE))
            add(deviceAction("device.brightness.up", "Brightness up", "Display", ACTION_BRIGHTNESS_UP))
            add(deviceAction("device.brightness.down", "Brightness down", "Display", ACTION_BRIGHTNESS_DOWN))
            add(settingsCommand("settings.android.main", "Settings", Settings.ACTION_SETTINGS))
            add(settingsCommand("settings.android.apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS))
            add(settingsCommand("settings.android.default_apps", "Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            add(settingsCommand("settings.android.input_method", "Keyboard settings", Settings.ACTION_INPUT_METHOD_SETTINGS))
            add(settingsCommand("settings.android.accessibility", "Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS))
            add(settingsCommand("settings.android.language_input", "Language & input", Settings.ACTION_LOCALE_SETTINGS))
            add(settingsCommand("settings.android.bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS))
            add(settingsCommand("settings.android.wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(settingsCommand("settings.android.internet_panel", "Internet", Settings.Panel.ACTION_INTERNET_CONNECTIVITY, opensPanel = true))
            }
            add(settingsCommand("settings.android.display", "Display / brightness", Settings.ACTION_DISPLAY_SETTINGS))
            add(settingsCommand("settings.android.sound", "Sound & vibration", Settings.ACTION_SOUND_SETTINGS))
            add(settingsCommand("settings.android.nfc", "NFC", Settings.ACTION_NFC_SETTINGS))
            add(settingsCommand("settings.android.battery", "Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS))
            add(settingsCommand("settings.android.notifications", "Notifications", ACTION_NOTIFICATION_SETTINGS))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    settingsCommand(
                        id = "settings.android.pastiera_notifications",
                        label = "Pastiera notifications",
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        data = null,
                        extras = mapOf(Settings.EXTRA_APP_PACKAGE to context.packageName)
                    )
                )
            }
        }.filter { context.canResolve(it.launch) }
    }

    private fun deviceAction(
        id: String,
        label: String,
        group: String,
        actionId: String
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.DeviceControl,
            label = label,
            subtitle = group,
            icon = CommandIcon.DeviceControl,
            launch = CommandLaunchSpec.InternalAction(actionId),
            capabilities = setOf(CommandCapability.AdjustsDeviceState),
            defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode, CommandSurface.QuickLauncher),
            searchTokens = listOf(label, group, "device", "control")
        )
    }

    private fun settingsCommand(
        id: String,
        label: String,
        action: String,
        data: String? = null,
        opensPanel: Boolean = false,
        extras: Map<String, String> = emptyMap()
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.DeviceControl,
            label = label,
            subtitle = if (opensPanel) "System panel" else "Settings",
            icon = CommandIcon.Settings,
            launch = SettingsIntentSpec(action, data, extras),
            capabilities = buildSet {
                add(CommandCapability.SendsIntent)
                if (opensPanel) add(CommandCapability.OpensSystemPanel)
            },
            defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode, CommandSurface.QuickLauncher),
            searchTokens = listOf(label, "settings", "system", "device", "control")
        )
    }

    private fun SettingsIntentSpec(
        action: String,
        data: String?,
        extras: Map<String, String>
    ): CommandLaunchSpec.IntentUri {
        return CommandLaunchSpec.IntentUri(
            action = action,
            data = data,
            flags = extras.map { "${it.key}=${it.value}" }
        )
    }

    private fun Context.canResolve(launch: CommandLaunchSpec): Boolean {
        if (launch is CommandLaunchSpec.InternalAction) return true
        val spec = launch as? CommandLaunchSpec.IntentUri ?: return false
        val intent = Intent(spec.action, spec.data?.let(Uri::parse))
        return intent.resolveActivity(packageManager) != null
    }

    companion object {
        const val ACTION_HOME_SCREEN = "device.home"
        const val ACTION_MEDIA_PLAY_PAUSE = "device.media.play_pause"
        const val ACTION_MEDIA_PREVIOUS = "device.media.previous"
        const val ACTION_MEDIA_NEXT = "device.media.next"
        const val ACTION_VOLUME_UP = "device.volume.up"
        const val ACTION_VOLUME_DOWN = "device.volume.down"
        const val ACTION_VOLUME_MUTE = "device.volume.mute"
        const val ACTION_BRIGHTNESS_UP = "device.brightness.up"
        const val ACTION_BRIGHTNESS_DOWN = "device.brightness.down"
        private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
    }
}
