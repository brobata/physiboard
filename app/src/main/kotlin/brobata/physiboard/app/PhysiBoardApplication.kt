package brobata.physiboard.app

import android.app.Application
import android.util.Log
import brobata.physiboard.app.settings.LegacyImporter
import brobata.physiboard.app.settings.SettingsStore
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.ime.SettingsSource
import brobata.physiboard.ime.SettingsSourceOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * The process's one home for the settings store. The keyboard service and the app share this
 * process (settings-catalog.md SS1.1), so this is where the store is opened once and where the
 * 2.x import runs before anything reads a setting, the same "before the first read" ordering
 * 2.x's start-up steps had (SS1.1), now as one step instead of four.
 *
 * [settingsSource] does not emit until the import has settled (ran, found nothing, or failed):
 * a keyboard that read the empty store first would type with the first-run defaults for a
 * moment and then flip to the imported values mid-word. The keyboard keeps its shipped defaults
 * in the meantime, so typing never waits on this.
 */
class PhysiBoardApplication : Application(), SettingsSourceOwner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val importSettled = CompletableDeferred<Unit>()
    lateinit var settingsStore: SettingsStore
        private set

    override val settingsSource: SettingsSource = object : SettingsSource {
        override val settings: Flow<Settings> = flow {
            importSettled.await()
            emitAll(settingsStore.settings)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore.open(this)
        scope.launch {
            try {
                when (val outcome = LegacyImporter(this@PhysiBoardApplication, settingsStore).runOnce()) {
                    LegacyImporter.Outcome.AlreadyRan -> Unit
                    LegacyImporter.Outcome.NoLegacyStore -> Log.i(TAG, "no 2.x settings to import")
                    is LegacyImporter.Outcome.Imported ->
                        Log.i(TAG, "imported 2.x settings: carried ${outcome.carried.size}, ignored ${outcome.ignored.size}, side files ${outcome.sideFilesFound}")
                }
            } catch (error: Exception) {
                // The store stays as it is (empty on a first run, so the defaults); the marker is
                // not written, so the import is tried again on the next start.
                Log.e(TAG, "2.x settings import failed", error)
            } finally {
                importSettled.complete(Unit)
            }
        }
    }

    private companion object {
        const val TAG = "PhysiBoardApp"
    }
}
