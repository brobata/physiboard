package brobata.physiboard.core.keys

/** Whether a [KeyStroke] is the key going down or coming back up. spec: keys-and-modifiers.md SS1.1. */
enum class KeyEdge { DOWN, UP }

/**
 * The meta-state bits the system computed for an event, carried through unchanged.
 *
 * spec: keys-and-modifiers.md SS1.1 ("a meta state bit set (Shift, Ctrl, Alt, Meta, Sym flags as
 * the system computed them)"). These are read-only facts about the physical chord in effect when
 * the event fired; they are not this module's own one-shot/latched bookkeeping, which lives in
 * [ModifierState].
 */
data class ModifierFlags(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val sym: Boolean = false,
)

/**
 * One already-normalised physical key event.
 *
 * spec: keys-and-modifiers.md SS1.1 defines the fields this module needs from a raw hardware
 * event: which key, press or release, the repeat count (0 for the initial down, 1, 2, ... for
 * auto-repeat while held), and the event time. A device module is responsible for producing this
 * type from whatever the platform delivers: Android keycodes and scancodes never appear here
 * (see the module's clean room and device-agnostic requirements).
 *
 * [deviceId] distinguishes physically separate input devices for the bounce and accidental-press
 * filters (keys-and-modifiers.md SS10, SS11: "same device id", "keys on different devices do not
 * interact"); it is an opaque identifier the caller assigns, not an Android device id.
 */
data class KeyStroke(
    val key: KeyId,
    val edge: KeyEdge,
    val repeatCount: Int,
    val timeMs: Long,
    val meta: ModifierFlags = ModifierFlags(),
    val deviceId: Int = 0,
) {
    init {
        require(repeatCount >= 0) { "repeatCount must not be negative, was $repeatCount" }
    }

    /** spec: keys-and-modifiers.md SS1.1 ("repeat count 0 for the initial press"). */
    val isInitialPress: Boolean get() = edge == KeyEdge.DOWN && repeatCount == 0

    /** spec: keys-and-modifiers.md SS8.1 (system auto-repeat: repeat count above 0). */
    val isRepeat: Boolean get() = edge == KeyEdge.DOWN && repeatCount > 0
}
