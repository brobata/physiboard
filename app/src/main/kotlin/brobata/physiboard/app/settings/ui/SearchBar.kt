package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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

    SearchPill(
        value = query,
        onValueChange = { query = it },
        placeholder = "Search settings…",
        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
    )

    if (query.isNotBlank()) {
        if (results.isEmpty()) {
            EmptyState(Icons.Filled.SearchOff, "No settings match “$query”. Try a shorter word.")
        } else {
            RowList {
                items(results, key = { it.title }) { entry ->
                    val description = if (onSettingsRoot && entry.screenTitle != entry.title) "In ${entry.screenTitle}" else entry.screenTitle
                    NavigateRow(label = entry.title, description = description, icon = Icons.Filled.Search) {
                        query = ""
                        onNavigate(entry.route)
                    }
                }
            }
        }
    }
}

/** A rounded, filled search field: the one search style for settings, app lists and pickers. */
@Composable
fun SearchPill(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isEmpty()) null else {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(28),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
    )
}
