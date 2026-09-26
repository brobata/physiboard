package brobata.physiboard.app.shell

import brobata.physiboard.core.shell.DebugCaptureStore

/**
 * The process-wide capture store (app-shell.md SS11): one instance for the whole process, cleared
 * only by "Clear" or the process dying. `:ime` reaches it through
 * `PhysiBoardApplication.debugCaptureSink` (SS10.2, SS10.7), the same seam shape `SettingsSource`
 * uses for the settings store.
 */
object AppDebugCaptureStore {
    val instance = DebugCaptureStore()
}
