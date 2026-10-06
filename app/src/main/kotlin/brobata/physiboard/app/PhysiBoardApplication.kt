package brobata.physiboard.app

import android.app.Application
import android.util.Log
import brobata.physiboard.app.settings.LegacyImporter
import brobata.physiboard.app.settings.SettingsStore
import brobata.physiboard.app.settings.ui.AppPackageChangeMonitor
import brobata.physiboard.app.shell.AppDebugCaptureStore
import brobata.physiboard.app.shell.AppLocaleApplier
import brobata.physiboard.app.shell.GatedHttp
import brobata.physiboard.app.shell.UpdateCheckScheduler
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.shell.AutocorrectionRecord
import brobata.physiboard.core.shell.GithubChecks
import brobata.physiboard.core.shell.ImeContextSnapshot
import brobata.physiboard.core.shell.KeyboardEventRecord
import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.privileged.PrivilegedServicesOwner
import brobata.physiboard.ime.DebugCaptureSink
import brobata.physiboard.ime.DebugCaptureSinkOwner
import brobata.physiboard.ime.SettingsSource
import brobata.physiboard.ime.SettingsSourceOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
 *
 * It is also the process's one home for the privileged side ([privileged]): the pairing, the
 * broker, the setup pass, the backlight and the ring all reach the same instance through the
 * application context, which is what the components `:device:privileged` declares and the
 * settings screens both have (broker-privileged-toolbox.md SS5.2, SS6: one verdict, one lock).
 */
class PhysiBoardApplication : Application(), SettingsSourceOwner, PrivilegedServicesOwner, DebugCaptureSinkOwner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val importSettled = CompletableDeferred<Unit>()
    lateinit var settingsStore: SettingsStore
        private set

    override val settingsSource: SettingsSource = object : SettingsSource {
        override val settings: Flow<Settings> = flow {
            importSettled.await()
            emitAll(settingsStore.settings)
        }

        /** The keyboard's own write-back (the active layout after a switch); it runs on the app's scope, never on the caller's thread. */
        override fun write(transform: (Settings) -> Settings) {
            scope.launch { runCatching { settingsStore.update(transform) }.onFailure { Log.e(TAG, "keyboard settings write failed", it) } }
        }
    }

    /**
     * app-shell.md SS10.2, SS10.7: the keyboard's only way to reach the process-wide
     * [AppDebugCaptureStore], the same seam shape as [settingsSource] above.
     */
    override val debugCaptureSink: DebugCaptureSink = object : DebugCaptureSink {
        override fun report(event: KeyboardEventRecord) = AppDebugCaptureStore.instance.reportKeyboardEvent(event)
        override fun reportFieldAttach(snapshot: ImeContextSnapshot, isPhysiBoardOwnPackage: Boolean) =
            AppDebugCaptureStore.instance.recordFieldAttach(snapshot, isPhysiBoardOwnPackage)
        override fun recordAutocorrection(record: AutocorrectionRecord) = AppDebugCaptureStore.instance.recordAutocorrection(record)
    }

    /**
     * The entry point the settings screens use for pairing and every privileged feature:
     * `(application as PrivilegedServicesOwner).privileged`, then `.pairing` (arm, state, code
     * entry; `PairingWatcherService.arm(context)` for the notification route), `.broker`
     * (the shared verdict), `.setup`, `.backlight`, `.ring`, `.reset`.
     */
    override val privileged: PrivilegedServices by lazy { PrivilegedServices(this, StoreBridge()) }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore.open(this)
        // app-shell.md SS31.2: the network gate reads `private_mode` through [settingsSource];
        // until this line every request is refused.
        GatedHttp.install(this)
        // dictionaries-languages.md SS11: sync AppCompatDelegate to the stored `app_language_tag`.
        // A `runBlocking` read straight off `settingsStore` used to sit here, on the main thread,
        // in both this process's launcher-Activity role and the keyboard-service role (no
        // `android:process` split) -- risking an ANR on the very first field focus on a slow first
        // DataStore read, and it also ran before [LegacyImporter.runOnce] below, so the first
        // launch after upgrading from 2.x with a non-default app language applied the pre-migration
        // (empty/default) tag and would not pick up the migrated one until the next full process
        // restart. Reading through [settingsSource] instead waits for [importSettled] the same way
        // every other reader of it already does, off the main thread, so this now sees the
        // post-migration value on the very first launch it matters for; the trade is that the very
        // first screen can draw for one frame in the previous language while this resolves, instead
        // of ever showing the wrong one at all.
        scope.launch {
            runCatching { AppLocaleApplier.applyAtStartup(settingsSource.settings.first().languages.appLanguageTag) }
                .onFailure { Log.e(TAG, "app language apply at startup failed", it) }
        }
        // app-shell.md SS13.7: the daily background check is (re)armed or torn down once per
        // process start, from the one component that runs whether the launcher activity or the
        // keyboard service brought this process up, and never from `:ime` itself.
        scheduleUpdateCheck()
        // per-app-behavior.md SS7: the installed-app cache and launcher-shortcut cleanup react to
        // Android's own package-change broadcasts for as long as this process is alive.
        AppPackageChangeMonitor(settingsStore, scope).register(this)
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
                // settings-catalog.md SS4.2, SS1.1: after the import, before the first read. A
                // failure here should not block startup; the marker just stays behind and the
                // reset is retried on the next process start.
                runCatching { settingsStore.applyBaselineOnce() }.onFailure { Log.e(TAG, "settings baseline apply failed", it) }
                importSettled.complete(Unit)
                // device-backlight-ring.md SS5.8: heal a ring that darkened the keyboard and then
                // died with its process. After the import, so a 2.x record is seen too.
                runCatching { privileged.onProcessStart() }.onFailure { Log.e(TAG, "privileged start crashed", it) }
            }
        }
    }

    /**
     * spec: SS13.7, SS1's "GitHub checks allowed" gate. Matches the `buildFlagOn = true` every
     * other GitHub-checks call site uses today (no F-Droid flavor exists yet to flip it; app-shell.md
     * SS30 leaves that undecided): [GithubChecks.allowed] still runs so a build that later gains a
     * real "must not call GitHub" signal (an F-Droid install) never schedules this job.
     */
    private fun scheduleUpdateCheck() {
        val installer = runCatching { packageManager.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
        UpdateCheckScheduler.scheduleOrCancel(this, GithubChecks.allowed(buildFlagOn = true, installerPackageName = installer))
    }

    /**
     * `:device:privileged`'s synchronous view of the store. Its callers are the broker worker,
     * the ring listener's worker and the tile's worker, never the main thread, and the ring's
     * keyboard-dark record must be committed before the switch is written (SS5.8 step 2), so
     * blocking on the DataStore write here is the point, not a shortcut.
     */
    private inner class StoreBridge : DeviceStateStore {
        override fun snapshot(): Settings = runBlocking { settingsStore.current() }

        override fun update(transform: (Settings) -> Settings): Settings = runBlocking {
            settingsStore.update(transform)
            settingsStore.current()
        }
    }

    private companion object {
        const val TAG = "PhysiBoardApp"
    }
}
