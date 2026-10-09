package brobata.physiboard.ime.access

import android.view.KeyEvent
import brobata.physiboard.core.keys.KeyEventIdentity
import brobata.physiboard.core.text.FieldFocusRescue

/**
 * Where the keyboard and PhysiBoard's accessibility service find each other. Both run in the
 * app's one process (no `android:process` on either), each registers itself here while it is
 * alive, and each reaches the other only through this object, so neither depends on the other
 * existing: with the service off, every call here is a no-op answering "not handled".
 *
 * Everything is called on the main thread except [focusField], which only hands work to the
 * service's own background thread.
 *
 * spec: per-app-behavior.md SS16.
 */
object AccessibilityBridge {

    /** The keyboard's side: its no-field key path, while the keyboard service is running. */
    fun interface KeyboardHook {
        /** True when the keyboard consumed [event] and nothing else may see it. */
        fun onKeyFromAccessibility(event: KeyEvent): Boolean
    }

    @Volatile
    private var service: PhysiBoardAccessibilityService? = null

    @Volatile
    var keyboard: KeyboardHook? = null

    /** Whether PhysiBoard's accessibility service is running: the user turned it on in Android's settings. */
    val connected: Boolean get() = service != null

    internal fun attach(running: PhysiBoardAccessibilityService) {
        service = running
    }

    internal fun detach(running: PhysiBoardAccessibilityService) {
        if (service === running) service = null
    }

    /** Asks the service to focus the box the keyboard is typing into. Returns at once; the work runs on the service's own thread. */
    fun focusField(request: FocusRequest) {
        service?.focusField(request)
    }

    /** Home as the Home key does it, through the service; false when the service is off, so the caller falls back. */
    fun goHome(): Boolean = service?.goHome() ?: false
}

/**
 * One request to focus a field of [packageName]'s. The service checks the cheap things first (is
 * the app's window active, does any view already have focus) and only then asks [readField], on
 * the main thread, for what the keyboard knows about the field; so the common case, a box that
 * already has focus, never costs the app a read. [readField] answers null when the field has
 * changed since the request.
 */
data class FocusRequest(val packageName: String, val readField: () -> FieldReading?)

/** What the keyboard knows about its field. [keyboardSelection] is null for a password box. */
data class FieldReading(val expected: FieldFocusRescue.Expected, val keyboardSelection: FieldFocusRescue.Selection?)

/** The values a [KeyEvent] and the accessibility service's copy of it share. */
fun KeyEvent.relayIdentity(): KeyEventIdentity = KeyEventIdentity(
    downTimeMs = downTime,
    eventTimeMs = eventTime,
    action = action,
    keyCode = keyCode,
    scanCode = scanCode,
    repeatCount = repeatCount,
)
