package brobata.physiboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One row on a hub. */
data class HubRow(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

/**
 * A list of destinations and nothing else.
 *
 * Device, Keyboard and Extras are the same shape, so they are the same composable with
 * different rows. Beyond saving three near-identical files, it enforces the rule the whole
 * navigation now follows: a destination appears on exactly one hub. When every list is built
 * the same way, a duplicate is obvious at the call site instead of hiding in a third screen
 * nobody re-reads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    modifier: Modifier = Modifier,
    title: String,
    intro: String,
    rows: List<HubRow>,
    onBack: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    onSearchResult: ((SettingsSearchTarget) -> Unit)? = null
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // Every directory surface can be searched, not just the first one. The catalog is
            // app-wide, so a hub finds settings that live nowhere near it.
            if (onSearchResult != null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            val query = searchQuery.trim()
            if (onSearchResult != null && query.isNotEmpty()) {
                val results = SettingsCatalog.entries.filter { entry ->
                    val title = stringResource(entry.titleRes)
                    val screen = stringResource(entry.screenTitleRes)
                    title.contains(query, ignoreCase = true) ||
                        screen.contains(query, ignoreCase = true) ||
                        entry.keywords.contains(query, ignoreCase = true)
                }
                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_no_results, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                results.forEach { entry ->
                    val title = stringResource(entry.titleRes)
                    HubRowView(
                        HubRow(
                            icon = Icons.Filled.Search,
                            title = title,
                            description = stringResource(entry.screenTitleRes),
                            onClick = { searchQuery = ""; onSearchResult(entry.target) }
                        )
                    )
                }
                return@Column
            }

            Text(
                text = intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            header?.invoke()
            rows.forEach { row ->
                HubRowView(row)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HubRowView(row: HubRow) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = row.onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
