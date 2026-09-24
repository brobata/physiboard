package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/** "Extras" (settings-catalog.md SS9.2): rows in the catalogue's order and labels. No search field: the catalogue does not give this hub one. */
@Composable
fun ExtrasHubScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SettingsScreenScaffold(title = "Extras", onBack = onBack) {
        RowList { extrasHubRows(onNavigate) }
    }
}

private fun LazyListScope.extrasHubRows(onNavigate: (String) -> Unit) {
    item { NavigateRow("PhysiBoard-QuickLauncher", "Quick launching, SYM shortcuts, and launcher key assignments") { onNavigate(Routes.QUICK_LAUNCHER) } }
    item { NavigateRow("Input Languages", "Input languages and layouts") { onNavigate(Routes.INPUT_LANGUAGES) } }
    item { NavigateRow("Text expansion", "Type a short trigger and have it expand into whatever you saved.") { onNavigate(Routes.TEXT_EXPANSION) } }
}
