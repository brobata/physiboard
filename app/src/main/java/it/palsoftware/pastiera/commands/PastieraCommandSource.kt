package it.palsoftware.pastiera.commands

import android.content.Context

class PastieraCommandSource : CommandSource {
    override val id = CommandSourceId.Pastiera

    override fun getCommands(context: Context): List<CommandTarget> {
        return listOf(
            CommandTarget(
                id = COMMAND_QUICK_LAUNCHER,
                source = id,
                kind = CommandKind.PastieraAction,
                label = "Pastiera QuickLauncher",
                subtitle = "Open Pastiera search",
                icon = CommandIcon.Search,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_QUICK_LAUNCHER),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Pastiera", "QuickLauncher", "Search")
            ),
            CommandTarget(
                id = COMMAND_MAIN_ACTIVITY,
                source = id,
                kind = CommandKind.PastieraAction,
                label = "Pastiera",
                subtitle = "Open app settings",
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_MAIN_ACTIVITY),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Pastiera", "Settings")
            )
        )
    }

    companion object {
        const val COMMAND_QUICK_LAUNCHER = "pastiera.quick_launcher"
        const val COMMAND_MAIN_ACTIVITY = "pastiera.main"
        const val ACTION_OPEN_QUICK_LAUNCHER = "open_quick_launcher"
        const val ACTION_OPEN_MAIN_ACTIVITY = "open_main_activity"
    }
}
