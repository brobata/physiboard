package brobata.physiboard.ime

import brobata.physiboard.core.settings.Settings
import kotlinx.coroutines.flow.Flow

/**
 * Where the keyboard reads its settings from. `:app` owns the store (rebuild-from-scratch.md,
 * "Settings": typed DataStore plus the one-shot importer) and `:app` depends on `:ime`, not the
 * other way round, so the keyboard sees the store only through this seam. Every emission is the
 * whole [Settings] value; the keyboard re-derives what it needs from each one, so a row changed
 * in the settings app takes effect in the running keyboard without a restart
 * (settings-catalog.md SS1, "change listeners on the store").
 */
interface SettingsSource {
    val settings: Flow<Settings>
}

/**
 * Implemented by the `Application` so [PhysiBoardInputMethodService] can find the store through
 * its application context, the only object both the service and the settings app share
 * (settings-catalog.md SS1.1: "the keyboard service and the app share one process").
 */
interface SettingsSourceOwner {
    val settingsSource: SettingsSource
}
