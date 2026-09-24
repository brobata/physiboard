package brobata.physiboard.core.pointer.trackpad

/**
 * The physical key that can open the screen trackpad.
 *
 * spec: trackpad-caret-nav.md SS2.2. This intentionally does not reuse `:core:keys`'
 * `ModifierKey.SHIFT`: that type deliberately collapses Left and Right Shift into one identity for
 * the modifier state machine, but the trigger-key table and its double-tap rule (SS2.3, T9: "a
 * second tap of the *other* Shift key is just a first tap of that key") need the two kept apart.
 * The trackpad also intercepts its trigger "before everything else in the key pipeline" (SS2.2),
 * upstream of wherever a device module would normally collapse that distinction, so this module
 * keeps its own small vocabulary rather than waiting for one that has already lost the fact it
 * needs.
 */
enum class TrackpadPhysicalKey {
    SPACE, SHIFT_LEFT, SHIFT_RIGHT, SYM, BACK;

    companion object {
        /** spec: trackpad-caret-nav.md SS2.2 (the `screen_trackpad_trigger_key` value table). */
        fun matchesTrigger(key: TrackpadPhysicalKey, trigger: TriggerKey): Boolean = when (trigger) {
            TriggerKey.SPACE -> key == SPACE
            TriggerKey.SHIFT_LEFT -> key == SHIFT_LEFT
            TriggerKey.SHIFT_RIGHT -> key == SHIFT_RIGHT
            TriggerKey.SHIFT_EITHER -> key == SHIFT_LEFT || key == SHIFT_RIGHT
            TriggerKey.SYM -> key == SYM
        }
    }
}

/** spec: trackpad-caret-nav.md SS2.2, the `screen_trackpad_trigger_key` preference. */
enum class TriggerKey(val preferenceValue: String) {
    SPACE("space"), SHIFT_LEFT("shift_left"), SHIFT_RIGHT("shift_right"), SHIFT_EITHER("shift_either"), SYM("sym");

    companion object {
        /** spec: SS7 test table T22 ("trigger preference written as 'bogus' -> read back as 'space'"). */
        fun fromPreference(value: String?): TriggerKey = entries.firstOrNull { it.preferenceValue == value } ?: SPACE
    }
}

/** spec: trackpad-caret-nav.md SS2.3, the `screen_trackpad_activation` preference. */
enum class ActivationMode(val preferenceValue: String) {
    HOLD("hold"), DOUBLE_TAP("double_tap"), SINGLE_TAP("single_tap");

    companion object {
        /** spec: SS7 test table T22 ("activation 'bogus' reads as 'hold'"). */
        fun fromPreference(value: String?): ActivationMode = entries.firstOrNull { it.preferenceValue == value } ?: HOLD
    }
}

/**
 * The tunables the activation state machine reads. spec: trackpad-caret-nav.md SS2.2, SS2.3,
 * SS2.8.
 */
data class TrackpadActivationSettings(
    val triggerKey: TriggerKey = TriggerKey.SPACE,
    val activationMode: ActivationMode = ActivationMode.HOLD,
    val holdThresholdMs: Long = 250,
    val doubleTapMinGapMs: Long = 1,
    val doubleTapMaxGapMs: Long = 400,
)
