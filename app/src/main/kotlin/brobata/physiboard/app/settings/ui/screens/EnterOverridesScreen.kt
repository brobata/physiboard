package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.AppCatalog
import brobata.physiboard.app.settings.ui.ExpandableSection
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceChipsRow
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.settings.EnterOverrideRow
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterSendMethod
import brobata.physiboard.core.text.ExtraSendShortcut

/**
 * "App overrides" (per-app-behavior.md SS3.11): a per-app on/off switch, and, once on, the same
 * "Wanted behaviour", "Send method" and "Extra shortcut" choosers the 2.x screen's three
 * dropdowns offered. This replaces [brobata.physiboard.app.settings.ui.AppPickerBody] for
 * [brobata.physiboard.app.settings.ui.PerAppListKind.ENTER_OVERRIDES] rather than extending it,
 * since [brobata.physiboard.app.settings.ui.AppPickerBody] is shared by three other per-app lists
 * that only ever need a plain switch.
 *
 * SPEC GAP: SS3.11's favourite/status-block distinction (a curated app's card shows a read-only
 * summary behind a "Manual override" reveal) needs the tested-package table `:core:text`'s Enter
 * resolver already owns; duplicating it here to gate the UI would drift from that table. Every
 * row here shows the three choosers directly once its switch is on, favourite or not.
 */
@Composable
fun EnterOverridesScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val perApp = controller.current.value.perApp
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    val apps = remember { AppCatalog.installedApps(context, alsoInclude = perApp.enterOverrides.map { it.packageName }.toSet()) }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    fun rowFor(packageName: String): EnterOverrideRow? = perApp.enterOverrides.firstOrNull { it.packageName == packageName }

    fun setRow(packageName: String, transform: (EnterOverrideRow) -> EnterOverrideRow) {
        controller.update { settings ->
            val overrides = settings.perApp.enterOverrides
            val updated = if (overrides.any { it.packageName == packageName }) {
                overrides.map { if (it.packageName == packageName) transform(it) else it }
            } else {
                overrides + transform(EnterOverrideRow(packageName))
            }
            settings.copy(perApp = settings.perApp.copy(enterOverrides = updated))
        }
    }

    SettingsScreenScaffold(title = "App overrides", onBack = onBack) {
        RowList {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(filtered, key = { it.packageName }) { app ->
                val row = rowFor(app.packageName)
                ExpandableSection(title = app.label, initiallyExpanded = false) {
                    SwitchRow(
                        label = "Override Enter for this app",
                        description = app.packageName,
                        checked = row != null,
                        onCheckedChange = { checked ->
                            controller.update { settings ->
                                val overrides = settings.perApp.enterOverrides
                                val updated = if (checked) {
                                    if (overrides.any { it.packageName == app.packageName }) overrides
                                    else overrides + EnterOverrideRow(app.packageName, EnterBehavior.SEND_SHIFT_NEWLINE)
                                } else {
                                    overrides.filterNot { it.packageName == app.packageName }
                                }
                                settings.copy(perApp = settings.perApp.copy(enterOverrides = updated))
                            }
                        },
                    )
                    if (row != null) {
                        // per-app-behavior.md SS3.11: "The 'Wanted behaviour' dropdown offers only
                        // app_default, enter_send_shift_newline, and enter_newline_ctrl_send."
                        SingleChoiceChipsRow(
                            label = "Wanted behaviour",
                            options = listOf(EnterBehavior.APP_DEFAULT, EnterBehavior.SEND_SHIFT_NEWLINE, EnterBehavior.NEWLINE_CTRL_SEND),
                            optionLabel = ::behaviorLabel,
                            selected = row.behavior,
                            onSelect = { value -> setRow(app.packageName) { it.copy(behavior = value) } },
                        )
                        SingleChoiceDropdownRow(
                            label = "Send method",
                            options = EnterSendMethod.entries,
                            optionLabel = ::sendMethodLabel,
                            selected = row.sendMethod,
                            onSelect = { value -> setRow(app.packageName) { it.copy(sendMethod = value) } },
                        )
                        SingleChoiceChipsRow(
                            label = "Extra shortcut",
                            options = ExtraSendShortcut.entries,
                            optionLabel = ::shortcutLabel,
                            selected = row.extraSendShortcut,
                            onSelect = { value -> setRow(app.packageName) { it.copy(extraSendShortcut = value) } },
                        )
                    }
                }
            }
        }
    }
}

private fun behaviorLabel(behavior: EnterBehavior): String = when (behavior) {
    EnterBehavior.APP_DEFAULT -> "App default"
    EnterBehavior.NEWLINE -> "Newline always"
    EnterBehavior.SEND_SHIFT_NEWLINE -> "Send with Enter, Shift+Enter newline"
    EnterBehavior.NEWLINE_CTRL_SEND -> "Newline with Enter, Ctrl+Enter sends"
}

private fun sendMethodLabel(method: EnterSendMethod): String = when (method) {
    EnterSendMethod.AUTO -> "Auto"
    EnterSendMethod.EDITOR_ACTION -> "Editor action"
    EnterSendMethod.CTRL_ENTER -> "Ctrl+Enter"
    EnterSendMethod.PLAIN_ENTER -> "Plain Enter"
}

private fun shortcutLabel(shortcut: ExtraSendShortcut): String = when (shortcut) {
    ExtraSendShortcut.NONE -> "None"
    ExtraSendShortcut.SYM_ENTER -> "Sym + Enter"
}
