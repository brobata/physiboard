package brobata.physiboard.app.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import brobata.physiboard.app.settings.SettingsStore
import brobata.physiboard.core.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Every settings row's one seam onto the store (rebuild-from-scratch.md: "Reading the store is a
 * Flow; writes go through `SettingsStore.update`"). A row never touches [SettingsStore] directly:
 * it reads [current] and calls [update], so nothing in the UI layer can bypass the codec or race
 * a write against the read it was drawn from.
 */
class SettingsController(
    val current: State<Settings>,
    private val store: SettingsStore,
    private val launchUpdate: ((Settings) -> Settings) -> Unit,
) {
    fun update(transform: (Settings) -> Settings) = launchUpdate(transform)

    /** "Reset to defaults" (rebuild-from-scratch.md): restores the whole schema's baseline in one write. */
    fun resetToDefaults() = update { Settings() }
}

val LocalSettingsController = compositionLocalOf<SettingsController> {
    error("SettingsController not provided: wrap the settings app in ProvideSettingsController")
}

/** [settings] is the app's gated flow (waits out the 2.x import) so the UI never flashes bare defaults before an import lands. */
@Composable
fun rememberSettingsController(settings: Flow<Settings>, store: SettingsStore): SettingsController {
    val state = settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    return remember(store) {
        SettingsController(state, store) { transform -> scope.launch { store.update(transform) } }
    }
}
