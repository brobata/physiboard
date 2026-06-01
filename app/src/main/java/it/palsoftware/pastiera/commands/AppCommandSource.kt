package it.palsoftware.pastiera.commands

import android.content.Context
import it.palsoftware.pastiera.AppListHelper

class AppCommandSource : CommandSource {
    override val id = CommandSourceId.Apps

    override fun getCommands(context: Context): List<CommandTarget> {
        return AppListHelper.getInstalledApps(context).map { app ->
            CommandTarget(
                id = "app:${app.packageName}",
                source = id,
                kind = CommandKind.App,
                label = app.appName,
                subtitle = app.packageName,
                icon = CommandIcon.DrawableIcon(app.icon),
                launch = CommandLaunchSpec.AppPackage(app.packageName),
                capabilities = setOf(
                    CommandCapability.LaunchesActivity,
                    CommandCapability.RequiresInstalledPackage
                ),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf(app.appName, app.packageName)
            )
        }
    }
}
