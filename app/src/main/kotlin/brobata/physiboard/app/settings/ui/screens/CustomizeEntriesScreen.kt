package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.SourceVisibility
import brobata.physiboard.core.actions.launcher.CommandCustomizations
import brobata.physiboard.core.actions.launcher.QuickLauncherRules
import brobata.physiboard.ime.actions.AndroidCommandCatalog

/**
 * "Customize entries" (expansion-clipboard-pickers-launcher.md SS7.8): a "Search entries" field,
 * "All" / "Favorites" chips, and per entry a star, hide/show, an alias pencil, up/down arrows for
 * favourites, and a colour chooser with "Dynamic" plus the eight swatches. Every choice is saved
 * at once into `quick_launcher_command_customizations` through [CommandCustomizations] (SS7.5).
 */
@Composable
fun CustomizeEntriesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val context = LocalContext.current
    val launcher = controller.current.value.launcher
    val customizations = CommandCustomizations.parse(launcher.commandCustomizationsJson)
    val catalog = remember { AndroidCommandCatalog(context) }
    val commands = remember { catalog.build().forQuickLauncher(SourceVisibility.parse(launcher.commandSurfaceSourcesJson)) }
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var aliasFor by remember { mutableStateOf<Command?>(null) }
    var colorFor by remember { mutableStateOf<Command?>(null) }

    fun save(transform: (CommandCustomizations) -> CommandCustomizations) = controller.update { s ->
        s.copy(launcher = s.launcher.copy(commandCustomizationsJson = CommandCustomizations.encode(transform(CommandCustomizations.parse(s.launcher.commandCustomizationsJson)))))
    }

    val shown = commands
        .filter { !favoritesOnly || customizations[it.id].favorite }
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || (it.subtitle?.contains(query, ignoreCase = true) ?: false) }
        .sortedWith(compareBy<Command> { customizations[it.id].favoriteOrder }.thenBy { it.label.lowercase() })

    SettingsScreenScaffold(title = "Customize entries", onBack = onBack) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search entries") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("All") })
            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favorites") })
        }
        if (commands.isEmpty()) Text("No commands available for the selected sources.", modifier = Modifier.padding(16.dp))
        else if (shown.isEmpty()) Text("No entries match \"$query\"", modifier = Modifier.padding(16.dp))
        RowList {
            items(shown.size) { index ->
                val command = shown[index]
                val c = customizations[command.id]
                Row(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (c.alias.isNotEmpty()) "${c.alias} | ${command.label}" else command.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(command.subtitle ?: command.source.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { save { it.setFavorite(command.id, !c.favorite) } }) {
                        Icon(if (c.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = if (c.favorite) "Unfavorite" else "Favorite")
                    }
                    IconButton(onClick = { save { it.setHidden(command.id, !c.hidden) } }) {
                        Icon(if (c.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (c.hidden) "Show" else "Hide")
                    }
                    IconButton(onClick = { aliasFor = command }) { Icon(Icons.Filled.Edit, contentDescription = "Search alias") }
                    if (c.favorite) {
                        IconButton(onClick = { save { it.moveFavorite(command.id, up = true) } }) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                        IconButton(onClick = { save { it.moveFavorite(command.id, up = false) } }) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                    }
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(c.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline).clickable { colorFor = command },
                    )
                }
            }
        }
    }

    aliasFor?.let { command ->
        var alias by remember(command) { mutableStateOf(customizations[command.id].alias) }
        AlertDialog(
            onDismissRequest = { aliasFor = null },
            title = { Text(command.label) },
            text = { OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Search alias") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { save { it.setAlias(command.id, alias) }; aliasFor = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { save { it.setAlias(command.id, "") }; aliasFor = null }) { Text("Clear") } },
        )
    }
    colorFor?.let { command ->
        AlertDialog(
            onDismissRequest = { colorFor = null },
            title = { Text("Entry color") },
            text = {
                Column {
                    Text("Dynamic", modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).clickable { save { it.setColor(command.id, null) }; colorFor = null }.padding(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickLauncherRules.ENTRY_COLORS.forEach { argb ->
                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(argb)).clickable { save { it.setColor(command.id, argb) }; colorFor = null })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { colorFor = null }) { Text("Close") } },
        )
    }
}
