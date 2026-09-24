package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The search field every hub carries (settings-catalog.md SS8, "drives the search field at the
 * top of Settings and of both hubs"). [onSettingsRoot] switches the result description between
 * "In &lt;screen&gt;" (SS8, from Settings) and the bare screen title (SS8, "on a hub the
 * description is the screen title").
 */
@Composable
fun SettingsSearchField(onSettingsRoot: Boolean, onNavigate: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { SearchCatalog.search(query) }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search settings…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).defaultMinSizeHeight(),
    )

    if (query.isNotBlank()) {
        if (results.isEmpty()) {
            Text(
                "No settings match “$query”",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            RowList {
                items(results, key = { it.title }) { entry ->
                    val description = if (onSettingsRoot && entry.screenTitle != entry.title) "In ${entry.screenTitle}" else entry.screenTitle
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(description) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSizeHeight()
                            .clickable {
                                query = ""
                                onNavigate(entry.route)
                            },
                    )
                }
            }
        }
    }
}

private fun Modifier.defaultMinSizeHeight(): Modifier = this.defaultMinSize(minHeight = MinTouchTarget)
