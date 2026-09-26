package brobata.physiboard.ime

import brobata.physiboard.core.shell.ImeContextSnapshot
import brobata.physiboard.core.shell.KeyboardEventRecord

/**
 * Where the keyboard reports key events and field attaches for the Diagnostics screen
 * (app-shell.md SS10.2, SS10.7). `:app` owns the process-wide
 * `brobata.physiboard.core.shell.DebugCaptureStore` and `:app` depends on `:ime`, not the other
 * way round, so [KeyboardSession] reaches it only through this seam, the same pattern
 * [SettingsSource] uses for the settings store.
 */
interface DebugCaptureSink {
    /** spec SS10.2: forwarded to the store's one registered listener, a no-op while none is registered. */
    fun report(event: KeyboardEventRecord)

    /** spec SS10.7: recorded every time the keyboard attaches to a field, so a bug report describes the app being used. */
    fun reportFieldAttach(snapshot: ImeContextSnapshot, isPhysiBoardOwnPackage: Boolean)
}

/** Implemented by the `Application` so [PhysiBoardInputMethodService] can find the sink through its application context. */
interface DebugCaptureSinkOwner {
    val debugCaptureSink: DebugCaptureSink
}
