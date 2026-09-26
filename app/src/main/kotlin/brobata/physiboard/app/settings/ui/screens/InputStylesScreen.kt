package brobata.physiboard.app.settings.ui.screens

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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import brobata.physiboard.app.settings.DictionaryFileStore
import brobata.physiboard.app.settings.LocaleLayoutOverrideStore
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.settings.LanguagePrefs
import brobata.physiboard.core.subtype.BundledLayoutCatalog
import brobata.physiboard.core.subtype.BundledLayoutIds
import brobata.physiboard.core.subtype.LocaleLayoutMapping
import kotlinx.coroutines.launch
import java.util.Locale

/** One `custom_input_styles` entry, parsed. spec: dictionaries-languages.md SS8.2's `locale:layout[:extra]` format. */
private data class InputStyleEntry(val locale: String, val layout: String, val extra: String?) {
    fun encoded(): String = if (extra.isNullOrBlank()) "$locale:$layout" else "$locale:$layout:$extra"
    val key: String get() = "$locale:$layout"
}

private fun parseEntry(raw: String): InputStyleEntry? {
    val parts = raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    return InputStyleEntry(parts[0], parts[1], parts.getOrNull(2))
}

private val LOCALE_PATTERN = Regex("^[a-zA-Z]{2,3}([_-][a-zA-Z]{2,3})?$")

/** SS8.2's "<locale>" formatting of a system locale: `xx_YY`, or `xx` when it has no region. */
private fun systemLocaleString(locale: Locale): String =
    if (locale.country.isNotEmpty()) "${locale.language}_${locale.country}" else locale.language

/** The language part of a locale string, matched against the installed-dictionary language codes. */
private fun languageOf(locale: String): String = locale.replace('-', '_').substringBefore('_').lowercase()

private fun normalizedKey(key: String): String = key.lowercase().replace('_', '-')

/** SS6/SS8.2: "the language's own name in its own language with the first letter capitalized". */
private fun ownLanguageDisplayName(code: String): String {
    val locale = Locale(code)
    val name = locale.getDisplayLanguage(locale)
    if (name.isBlank() || name.equals(code, ignoreCase = true)) return code.uppercase()
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** One row this screen renders: either a `custom_input_styles` entry or a device system locale (SS8.2). */
private sealed interface StyleRow {
    val locale: String
    val layout: String
    val key: String get() = "$locale:$layout"

    data class Custom(val raw: String, val entry: InputStyleEntry) : StyleRow {
        override val locale get() = entry.locale
        override val layout get() = entry.layout
    }

    data class System(override val locale: String, override val layout: String, val hidden: Boolean) : StyleRow
}

/**
 * "Input styles" (dictionaries-languages.md SS8.2): the user's own `custom_input_styles` rows plus
 * one "System" row per device language (hide/show, bound to `hiddenSystemInputStyles`), each
 * editable through one Add/Edit dialog: a language dropdown sourced from every language with a
 * dictionary in a local tier, an "Add Custom Locale..." flow, a layout picker over this build's
 * layout catalogue, a "No dictionary available" warning, and one suggestion-dictionary switch per
 * other installed language, with a confirmation before a custom row is deleted.
 *
 * The layout field is a picker over [BundledLayoutIds.ALL] (layers-sym-alt.md SS9.2's eighteen
 * bundled names, shown with [BundledLayoutCatalog]'s own display names), not free text: every one
 * of those ids is now a real [brobata.physiboard.core.subtype.ShippedLayout] `:device:titan`'s
 * `TitanLayouts.bundled()` ships, so a custom style naming any of them types with that layout's
 * own key map (`:core:subtype`'s `InputStyleCatalog.layoutFor`). A system row's layout edit is
 * persisted to `files/locale_layout_mapping.json` ([LocaleLayoutOverrideStore]), the file SS10
 * names, but `:ime` resolves a system row's layout from `custom_input_styles`/[InputStyle] only
 * (SS10 step 2), not from this file (a pre-existing gap this task did not touch), so a system row's
 * layout edit is saved but has no live effect yet.
 */
@Composable
fun InputStylesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    val languages = controller.current.value.languages
    fun set(transform: (LanguagePrefs) -> LanguagePrefs) = controller.update { it.copy(languages = transform(it.languages)) }

    val scope = rememberCoroutineScope()
    val fileStore = remember { DictionaryFileStore(context) }
    val overrideStore = remember { LocaleLayoutOverrideStore(context) }

    var installedLanguages by remember { mutableStateOf<List<String>>(emptyList()) }
    var localeOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        installedLanguages = fileStore.listLocal().map { it.languageCode.lowercase() }.distinct().sorted()
        localeOverrides = overrideStore.read()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<StyleRow?>(null) }
    var deleteTarget by remember { mutableStateOf<StyleRow.Custom?>(null) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    val customEntries = remember(languages.inputStyles) { languages.inputStyles.mapNotNull { raw -> parseEntry(raw)?.let { StyleRow.Custom(raw, it) } } }
    val customKeys = customEntries.map { normalizedKey(it.key) }.toSet()

    // SS8.2: "every system language (each entry of the device's locale list)".
    // LocalConfiguration, not the context's resources: Compose tracks this one, so the list
    // follows a locale change instead of going stale (and lint refuses the other read).
    val configuration = LocalConfiguration.current
    val systemLocales = remember(configuration) {
        val list = ConfigurationCompat.getLocales(configuration)
        (0 until list.size()).mapNotNull { i -> list.get(i) }
    }
    val hiddenSet = remember(languages.hiddenSystemInputStyles) { languages.hiddenSystemInputStyles.map(::normalizedKey).toSet() }
    val systemRows = remember(systemLocales, localeOverrides, customKeys, hiddenSet) {
        systemLocales.map { locale ->
            val localeString = systemLocaleString(locale)
            val layout = LocaleLayoutMapping.resolve(localeString, override = localeOverrides)
            StyleRow.System(localeString, layout, hidden = normalizedKey("$localeString:$layout") in hiddenSet)
        }.distinctBy { it.locale.lowercase() }.filterNot { normalizedKey(it.key) in customKeys }
    }

    val allRows: List<StyleRow> = systemRows + customEntries

    fun toggleHidden(row: StyleRow.System) {
        val entryKey = "${row.locale.replace('_', '-')}:${row.layout}"
        val visibleCount = allRows.count { r -> if (r is StyleRow.System) !r.hidden else true }
        if (!row.hidden && visibleCount <= 1) {
            snackbar = "Keep at least one input style visible"
            return
        }
        set { p -> p.copy(hiddenSystemInputStyles = if (row.hidden) p.hiddenSystemInputStyles - entryKey else p.hiddenSystemInputStyles + entryKey) }
    }

    SettingsScreenScaffold(
        title = "Input styles",
        onBack = onBack,
        trailingAction = {
            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                Icon(Icons.Filled.Add, contentDescription = "Add Input Style")
            }
        },
    ) {
        RowList {
            if (snackbar != null) {
                item { Text(snackbar!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
            items(allRows, key = { (if (it is StyleRow.Custom) "c:" else "s:") + it.key }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget)
                        .clickable { editingRow = row }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val badge = if (row is StyleRow.System) "System" else "Custom"
                        Text("${ownLanguageDisplayName(languageOf(row.locale))} - ${row.layout}")
                        Text("${row.locale} - ${row.layout} - $badge", style = MaterialTheme.typography.bodySmall)
                    }
                    when (row) {
                        is StyleRow.System -> IconButton(onClick = { toggleHidden(row) }) {
                            Icon(if (row.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (row.hidden) "Show" else "Hide")
                        }
                        is StyleRow.Custom -> IconButton(onClick = { deleteTarget = row }) { Icon(Icons.Filled.Delete, contentDescription = "Delete Input Style") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        InputStyleEditDialog(
            title = "Add Input Style",
            isSystemRow = false,
            initial = null,
            existingKeys = allRows.map { normalizedKey(it.key) }.toSet(),
            initialSuggestions = emptyList(),
            installedLanguages = installedLanguages,
            onDismiss = { showAddDialog = false },
            onSave = { entry, suggestions ->
                set { p ->
                    p.copy(
                        inputStyles = p.inputStyles + entry.encoded(),
                        suggestionLocales = if (suggestions.isEmpty()) p.suggestionLocales else p.suggestionLocales + (entry.key to suggestions),
                    )
                }
                snackbar = "Input style added: ${ownLanguageDisplayName(languageOf(entry.locale))} - ${entry.layout}"
                showAddDialog = false
            },
        )
    }

    editingRow?.let { row ->
        when (row) {
            is StyleRow.Custom -> InputStyleEditDialog(
                title = "Edit Input Style",
                isSystemRow = false,
                initial = row.entry,
                existingKeys = allRows.map { normalizedKey(it.key) }.toSet() - normalizedKey(row.key),
                initialSuggestions = languages.suggestionLocales[row.entry.key].orEmpty(),
                installedLanguages = installedLanguages,
                onDismiss = { editingRow = null },
                onSave = { updated, suggestions ->
                    set { p ->
                        val withoutOld = p.suggestionLocales - row.entry.key
                        p.copy(
                            inputStyles = p.inputStyles.map { if (it == row.raw) updated.encoded() else it },
                            suggestionLocales = if (suggestions.isEmpty()) withoutOld else withoutOld + (updated.key to suggestions),
                        )
                    }
                    snackbar = "Input style updated: ${ownLanguageDisplayName(languageOf(updated.locale))} - ${updated.layout}"
                    editingRow = null
                },
            )
            is StyleRow.System -> InputStyleEditDialog(
                title = "Edit System Locale Layout",
                isSystemRow = true,
                initial = InputStyleEntry(row.locale, row.layout, null),
                existingKeys = emptySet(),
                initialSuggestions = languages.suggestionLocales["${row.locale}:${row.layout}"].orEmpty(),
                installedLanguages = installedLanguages,
                onDismiss = { editingRow = null },
                onSave = { updated, suggestions ->
                    scope.launch {
                        val ok = overrideStore.setLayout(row.locale, updated.layout)
                        localeOverrides = overrideStore.read()
                        set { p ->
                            val withoutOld = p.suggestionLocales - "${row.locale}:${row.layout}"
                            p.copy(suggestionLocales = if (suggestions.isEmpty()) withoutOld else withoutOld + ("${row.locale}:${updated.layout}" to suggestions))
                        }
                        snackbar = if (ok) "Layout mapping updated: ${ownLanguageDisplayName(languageOf(row.locale))} - ${updated.layout}" else "save failed"
                    }
                    editingRow = null
                },
            )
        }
    }

    // SS8.2: "Delete Input Style" / "Are you sure you want to delete this input style?".
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Input Style") },
            text = { Text("Are you sure you want to delete this input style?") },
            confirmButton = {
                TextButton(onClick = {
                    set { p -> p.copy(inputStyles = p.inputStyles - target.raw, suggestionLocales = p.suggestionLocales - target.entry.key) }
                    snackbar = "Input style deleted"
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InputStyleEditDialog(
    title: String,
    isSystemRow: Boolean,
    initial: InputStyleEntry?,
    existingKeys: Set<String>,
    initialSuggestions: List<String>,
    installedLanguages: List<String>,
    onDismiss: () -> Unit,
    onSave: (InputStyleEntry, List<String>) -> Unit,
) {
    var locale by remember { mutableStateOf(initial?.locale.orEmpty()) }
    var layout by remember { mutableStateOf(initial?.layout ?: LocaleLayoutMapping.resolve(locale.ifBlank { "en_US" })) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }
    var customLocaleMode by remember { mutableStateOf(!isSystemRow && initial != null && languageOf(initial.locale) !in installedLanguages) }
    var suggestionLanguages by remember { mutableStateOf(initialSuggestions.map(::languageOf).toSet()) }

    val localeValid = LOCALE_PATTERN.matches(locale.trim())
    val duplicate = localeValid && layout.isNotBlank() && normalizedKey("${locale.trim()}:${layout.trim()}") in existingKeys
    val hasDictionary = languageOf(locale) in installedLanguages
    val error = when {
        isSystemRow -> null
        locale.isBlank() -> "Locale code cannot be empty"
        !localeValid -> "Invalid locale code format. Use format: xx_XX or xx"
        duplicate -> "This language and layout combination already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (isSystemRow) {
                    Text("System locale - cannot be changed")
                    Text(locale)
                } else if (customLocaleMode) {
                    OutlinedTextField(
                        value = locale,
                        onValueChange = { locale = it; layout = LocaleLayoutMapping.resolve(it.ifBlank { "en_US" }) },
                        label = { Text("Custom locale (xx_XX or xx)") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = { if (error != null) Text(error) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // SS8.2: "a dropdown of every language code that has a dictionary in any
                    // tier... plus 'Add Custom Locale...'".
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showLanguageMenu = true }) {
                            Text(if (locale.isBlank()) "Choose language" else "${ownLanguageDisplayName(languageOf(locale))} ($locale)")
                        }
                        DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
                            installedLanguages.forEach { code ->
                                DropdownMenuItem(
                                    text = { Text("${ownLanguageDisplayName(code)} ($code)") },
                                    onClick = { locale = code; layout = LocaleLayoutMapping.resolve(code); showLanguageMenu = false },
                                )
                            }
                            DropdownMenuItem(text = { Text("Add Custom Locale...") }, onClick = { customLocaleMode = true; showLanguageMenu = false })
                        }
                    }
                }
                if (!hasDictionary && locale.isNotBlank()) {
                    Text("No dictionary available for this locale. Suggestions and auto-correction will be disabled.")
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showLayoutMenu = true }) { Text("Layout: ${BundledLayoutCatalog.infoFor(layout).displayName} (tap to change)") }
                    DropdownMenu(expanded = showLayoutMenu, onDismissRequest = { showLayoutMenu = false }) {
                        BundledLayoutCatalog.ALL.sortedBy { it.displayName }.forEach { info ->
                            DropdownMenuItem(text = { Text(info.displayName) }, onClick = { layout = info.id; showLayoutMenu = false })
                        }
                    }
                }
                Text("Suggestion dictionaries")
                Text("Primary: ${ownLanguageDisplayName(languageOf(locale.ifBlank { "en" }))}")
                val otherLanguages = installedLanguages.filter { it != languageOf(locale) }
                if (otherLanguages.isEmpty()) {
                    Text("No other installed dictionaries available.")
                } else {
                    otherLanguages.forEach { code ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(ownLanguageDisplayName(code), modifier = Modifier.weight(1f))
                            Switch(
                                checked = code in suggestionLanguages,
                                onCheckedChange = { checked -> suggestionLanguages = if (checked) suggestionLanguages + code else suggestionLanguages - code },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(InputStyleEntry(locale.trim(), layout.trim(), initial?.extra), suggestionLanguages.toList()) },
                enabled = isSystemRow || (locale.isNotBlank() && layout.isNotBlank() && localeValid && !duplicate),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
