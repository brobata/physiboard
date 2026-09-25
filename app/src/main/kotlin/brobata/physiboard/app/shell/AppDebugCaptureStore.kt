package brobata.physiboard.app.shell

import brobata.physiboard.core.shell.DebugCaptureStore

/**
 * The process-wide capture store (app-shell.md SS11): one instance for the whole process, cleared
 * only by "Clear" or the process dying. Nothing in `:ime` reports into it yet (see
 * `DiagnosticsScreen`'s header comment); this object is the seam that wiring attaches to.
 */
object AppDebugCaptureStore {
    val instance = DebugCaptureStore()
}
