package brobata.physiboard.ime

import brobata.physiboard.core.shell.AutocorrectionRecord
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

    /**
     * spec app-shell.md SS11, autocorrect-suggestions.md SS7.2 ("each attempt is recorded in the
     * debug capture with its outcome"): forwarded to the store's fixed-capacity autocorrection
     * ring buffer regardless of whether Diagnostics is open.
     */
    fun recordAutocorrection(record: AutocorrectionRecord)
}

/** Implemented by the `Application` so [PhysiBoardInputMethodService] can find the sink through its application context. */
interface DebugCaptureSinkOwner {
    val debugCaptureSink: DebugCaptureSink
}

/**
 * app-shell.md SS31: the debug capture records keys and corrected words, which is typed text.
 * While [learningAllowed] is false (private mode, or a field that asks for no personalized
 * learning) this drops everything instead of passing it to [delegate]: no key event, no field
 * attach (it names the app being typed in) and no autocorrection record.
 */
internal class PrivacyFilteringDebugCaptureSink(
    private val delegate: DebugCaptureSink,
    private val learningAllowed: () -> Boolean,
) : DebugCaptureSink {
    override fun report(event: KeyboardEventRecord) {
        if (learningAllowed()) delegate.report(event)
    }

    override fun reportFieldAttach(snapshot: ImeContextSnapshot, isPhysiBoardOwnPackage: Boolean) {
        if (learningAllowed()) delegate.reportFieldAttach(snapshot, isPhysiBoardOwnPackage)
    }

    override fun recordAutocorrection(record: AutocorrectionRecord) {
        if (learningAllowed()) delegate.recordAutocorrection(record)
    }
}
