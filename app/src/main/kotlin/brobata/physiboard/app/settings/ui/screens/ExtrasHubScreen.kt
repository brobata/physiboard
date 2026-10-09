package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/** "Extras" (settings-catalog.md SS9.2): rows in the catalogue's order and labels. No search field: the catalogue does not give this hub one. */
@Composable
fun ExtrasHubScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScreenScaffold(title = "Extras", onBack = onBack) {
        RowList { extrasHubRows(onNavigate) }
    }
}

private fun LazyListScope.extrasHubRows(onNavigate: (String) -> Unit) {
    item { NavigateRow("PhysiBoard-QuickLauncher", "Quick launching, SYM shortcuts, and launcher key assignments", icon = Icons.Outlined.RocketLaunch) { onNavigate(Routes.QUICK_LAUNCHER) } }
    item { NavigateRow("Input Languages", "Input languages and layouts", icon = Icons.Outlined.Language) { onNavigate(Routes.INPUT_LANGUAGES) } }
    item { NavigateRow("Text expansion", "Type a short trigger and have it expand into whatever you saved.", icon = Icons.Outlined.Bolt) { onNavigate(Routes.TEXT_EXPANSION) } }
    // expansion-clipboard-pickers-launcher.md SS13: the clipboard rows 2.x never built.
    item { NavigateRow("Clipboard history", "Keep copied text on the clipboard panel; how long unpinned clips stay.", icon = Icons.Outlined.ContentPaste) { onNavigate(Routes.CLIPBOARD_HISTORY) } }
}
