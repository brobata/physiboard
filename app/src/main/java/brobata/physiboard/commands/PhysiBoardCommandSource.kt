package brobata.physiboard.commands

import android.content.Context
import androidx.core.content.ContextCompat
import brobata.physiboard.R

class PhysiBoardCommandSource : CommandSource {
    override val id = CommandSourceId.PhysiBoard

    override fun getCommands(context: Context): List<CommandTarget> {
        return listOf(
            CommandTarget(
                id = COMMAND_QUICK_LAUNCHER,
                source = id,
                kind = CommandKind.PhysiBoardAction,
                label = "PhysiBoard QuickLauncher",
                subtitle = "Open PhysiBoard search",
                icon = CommandIcon.Search,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_QUICK_LAUNCHER),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("PhysiBoard", "QuickLauncher", "Search")
            ),
            CommandTarget(
                id = COMMAND_MAIN_ACTIVITY,
                source = id,
                kind = CommandKind.PhysiBoardAction,
                label = "PhysiBoard",
                subtitle = "Open app settings",
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_MAIN_ACTIVITY),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("PhysiBoard", "Settings")
            ),
            CommandTarget(
                id = COMMAND_VOICE_ASSISTANT,
                source = id,
                kind = CommandKind.PhysiBoardAction,
                label = "Voice assistant",
                subtitle = "Open it already listening",
                icon = CommandIcon.Search,
                launch = CommandLaunchSpec.InternalAction(ACTION_START_VOICE_ASSISTANT),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf("Assistant", "Voice", "Gemini", "Ask", "Siri")
            ),
            CommandTarget(
                id = COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE,
                source = id,
                kind = CommandKind.PhysiBoardAction,
                label = "Toggle Keyboard Mode",
                subtitle = "Switch Virtual / Hardware",
                icon = CommandIcon.DrawableIcon(ContextCompat.getDrawable(context, R.drawable.expansion_panels_24)),
                launch = CommandLaunchSpec.InternalAction(ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE),
                capabilities = setOf(CommandCapability.AdjustsDeviceState),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf("Keyboard", "Software", "Virtual", "Hardware", "Toggle")
            )
        )
    }

    companion object {
        const val COMMAND_QUICK_LAUNCHER = "pastiera.quick_launcher"
        const val COMMAND_MAIN_ACTIVITY = "pastiera.main"
        const val COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE = "pastiera.toggle_software_keyboard_mode"
        const val COMMAND_VOICE_ASSISTANT = "pastiera.voice_assistant"
        const val ACTION_OPEN_QUICK_LAUNCHER = "open_quick_launcher"
        const val ACTION_OPEN_MAIN_ACTIVITY = "open_main_activity"
        const val ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE = "toggle_software_keyboard_mode"
        const val ACTION_START_VOICE_ASSISTANT = "start_voice_assistant"
    }
}
