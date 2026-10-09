package brobata.physiboard.app.settings.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.CustomLayoutStore
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.keys.LetterEntry
import brobata.physiboard.core.settings.LanguagePrefs
import brobata.physiboard.core.subtype.BundledLayoutCatalog
import kotlinx.coroutines.launch
import java.util.Locale

/** One row this screen lists: a bundled name ([BundledLayoutCatalog]) or a custom import ([CustomLayoutStore.CustomLayout]). */
private sealed interface LayoutRow {
    val id: String
    val displayName: String
    val description: String
    val isMultiTap: Boolean
    val isCustom: Boolean

    data class Bundled(val info: brobata.physiboard.core.subtype.BundledLayoutInfo, val letters: Map<*, LetterEntry>) : LayoutRow {
        override val id get() = info.id
        override val displayName get() = info.displayName
        override val description get() = info.note
        override val isMultiTap get() = letters.values.any { it.isMultiTap }
        override val isCustom get() = false
    }

    data class Custom(val stored: CustomLayoutStore.CustomLayout) : LayoutRow {
        override val id get() = stored.name
        override val displayName get() = stored.parsed?.name?.takeIf { it.isNotBlank() } ?: stored.name
        override val description get() = stored.parsed?.description.orEmpty()
        override val isMultiTap get() = stored.parsed?.layout?.entries?.values?.any { it.isMultiTap } == true
        override val isCustom get() = true
    }
}

/**
 * "Keyboard Layout" (layers-sym-alt.md SS9.6): the "Standard" (`qwerty`) row, then every other
 * bundled and custom layout sorted by display name, each with a "multitap" badge, its description,
 * a delete icon for custom layouts, a view icon opening [LayoutViewerScreen] and a radio selecting
 * `keyboard_layout`. The "Online layout editor" link and "Download from cloud" (SS9.3) are third-
 * party-hosted and dropped (docs/spec keep/drop SS15); the add menu here offers only "Import from
 * file". Picking a row here is a manual override, so it also turns `keyboard_layout_auto_by_locale`
 * off, matching every other screen's live-write pattern (no separate save-icon/cancel flow exists
 * anywhere else in this build's settings UI).
 *
 * SPEC GAP: SS9.6 titles the screen "Keyboard Layout - <locale display name>" for the input style
 * whose layout is being edited; this build has no such caller context wired to it yet (no screen
 * navigates here in picker mode), so the title uses the device's own current display language.
 */
@Composable
fun KeyboardLayoutScreen(onBack: () -> Unit, onView: (String) -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    val languages = controller.current.value.languages
    fun set(transform: (LanguagePrefs) -> LanguagePrefs) = controller.update { it.copy(languages = transform(it.languages)) }

    val scope = rememberCoroutineScope()
    val store = remember { CustomLayoutStore(context) }
    var customLayouts by remember { mutableStateOf<List<CustomLayoutStore.CustomLayout>>(emptyList()) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<LayoutRow.Custom?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    suspend fun refresh() { customLayouts = store.list() }
    LaunchedEffect(Unit) { refresh() }

    val bundledLetters = remember { brobata.physiboard.device.titan.TitanLayouts.bundled().associate { (id, _, layout) -> id to layout.baseLayout.entries } }
    val standard = BundledLayoutCatalog.infoFor("qwerty")
    val otherBundled = BundledLayoutCatalog.ALL.filter { it.id != "qwerty" }
        .map { LayoutRow.Bundled(it, bundledLetters.getValue(it.id)) }
    val customRows = customLayouts.map { LayoutRow.Custom(it) }
    val otherRows = (otherBundled + customRows).sortedBy { it.displayName.lowercase() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.reader().readText() } }.getOrNull()
            if (text == null) {
                snackbar = "Failed to import layout"
                return@launch
            }
            val name = store.import(text)
            snackbar = if (name != null) "Layout imported successfully" else "Failed to import layout"
            refresh()
        }
    }

    fun selectLayout(id: String) {
        set { p -> p.copy(keyboardLayout = id, layoutAutoByLocale = false) }
    }

    SettingsScreenScaffold(
        title = "Keyboard Layout - ${Locale.getDefault().getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase(Locale.getDefault()) }}",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { showAddMenu = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add layout")
            }
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text("Import from file") }, onClick = { showAddMenu = false; importLauncher.launch(arrayOf("application/json")) })
            }
        },
    ) {
        RowList {
            item {
                SwitchRow(
                    "Follow the language",
                    description = "Use the layout that goes with the language you type in. Picking a layout below turns this off.",
                    checked = languages.layoutAutoByLocale,
                    onCheckedChange = { checked -> set { p -> p.copy(layoutAutoByLocale = checked) } },
                )
            }
            if (snackbar != null) {
                item { Text(snackbar!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
            item {
                LayoutRowView(
                    displayName = standard.displayName,
                    description = "Standard QWERTY layout (no conversion)",
                    isMultiTap = false,
                    isCustom = false,
                    selected = languages.keyboardLayout == "qwerty",
                    onSelect = { selectLayout("qwerty") },
                    onView = { onView("qwerty") },
                    onDelete = null,
                )
            }
            items(otherRows, key = { it.id }) { row ->
                LayoutRowView(
                    displayName = row.displayName,
                    description = row.description,
                    isMultiTap = row.isMultiTap,
                    isCustom = row.isCustom,
                    selected = languages.keyboardLayout == row.id,
                    onSelect = { selectLayout(row.id) },
                    onView = { onView(row.id) },
                    onDelete = if (row is LayoutRow.Custom) ({ deleteTarget = row }) else null,
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete layout") },
            text = { Text("Are you sure you want to delete this layout?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch {
                        store.delete(target.id)
                        if (languages.keyboardLayout == target.id) set { p -> p.copy(keyboardLayout = "qwerty") }
                        refresh()
                    }
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LayoutRowView(
    displayName: String,
    description: String,
    isMultiTap: Boolean,
    isCustom: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onView: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, style = MaterialTheme.typography.bodyLarge)
                if (isMultiTap) {
                    Text(" multitap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete layout")
            }
        }
        IconButton(onClick = onView, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
            Icon(Icons.Filled.Visibility, contentDescription = "View layout")
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}
