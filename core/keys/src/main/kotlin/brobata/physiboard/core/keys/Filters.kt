package brobata.physiboard.core.keys

/** Identifies one physical key on one input device, for the two filters below. spec: keys-and-modifiers.md SS10, SS11. */
data class KeySignature(val deviceId: Int, val key: KeyId)

/** Whether a filter accepts a stroke or rejects (consumes) it, with the diagnostics text the spec assigns to a rejection. */
sealed class FilterVerdict {
    object Accept : FilterVerdict()
    data class Reject(val reason: String) : FilterVerdict()
}

// ---------------------------------------------------------------------------------------------
// Bounce filter (same-key debounce). spec: keys-and-modifiers.md SS10.
// ---------------------------------------------------------------------------------------------

/** spec: keys-and-modifiers.md SS10 (the category table). */
enum class BounceCategory { CHARACTER, MODIFIER, SPACE, ENTER, BACKSPACE, UNSUPPORTED }

/** spec: keys-and-modifiers.md SS10, SS18. */
data class BounceKeySettings(
    val enabled: Boolean = false,
    val delayMs: Long = 80,
    val characterKeysEnabled: Boolean = true,
    val modifierKeysEnabled: Boolean = false,
    val spaceEnabled: Boolean = true,
    val enterEnabled: Boolean = true,
    val backspaceEnabled: Boolean = true,
) {
    val clampedDelayMs: Long get() = delayMs.coerceIn(20, 500)
}

/**
 * Memory the bounce filter carries between strokes: the last accepted down time per key, whether
 * that accepted press is still held, and which keys owe a swallowed up. spec: keys-and-modifiers.md
 * SS10.1, SS10.3. Reset (a fresh, empty instance) on every start of input.
 */
data class BounceFilterState(
    val lastAcceptedDownAtMs: Map<KeySignature, Long> = emptyMap(),
    val heldSinceAccept: Set<KeySignature> = emptySet(),
    val swallowNextUp: Set<KeySignature> = emptySet(),
)

/**
 * The bounce/debounce filter: rejects a repeated key-down that arrives too soon after the same
 * key was last accepted. spec: keys-and-modifiers.md SS10.
 *
 * SPEC GAP: the category table (SS10) lists Shift, Ctrl, Alt and Sym under "modifier" but does
 * not mention Fn. This filter categorises Fn as [BounceCategory.MODIFIER] too, the closest match,
 * since Fn is one of this module's [ModifierKey] values and the spec's "unsupported" list is
 * given as a closed, unrelated set (Back, D-pad, volume, power).
 */
object BounceFilter {

    fun categoryOf(key: KeyId): BounceCategory = when (key) {
        is KeyId.Modifier -> BounceCategory.MODIFIER
        is KeyId.Letter, is KeyId.Digit, is KeyId.Punctuation -> BounceCategory.CHARACTER
        is KeyId.Control -> when (key.key) {
            ControlKey.SPACE -> BounceCategory.SPACE
            ControlKey.ENTER -> BounceCategory.ENTER
            ControlKey.BACKSPACE -> BounceCategory.BACKSPACE
            ControlKey.BACK, ControlKey.DPAD_UP, ControlKey.DPAD_DOWN, ControlKey.DPAD_LEFT, ControlKey.DPAD_RIGHT,
            ControlKey.VOLUME_UP, ControlKey.VOLUME_DOWN, ControlKey.POWER,
            -> BounceCategory.UNSUPPORTED
            else -> BounceCategory.CHARACTER
        }
    }

    private fun isEnabledFor(category: BounceCategory, settings: BounceKeySettings): Boolean = when (category) {
        BounceCategory.CHARACTER -> settings.characterKeysEnabled
        BounceCategory.MODIFIER -> settings.modifierKeysEnabled
        BounceCategory.SPACE -> settings.spaceEnabled
        BounceCategory.ENTER -> settings.enterEnabled
        BounceCategory.BACKSPACE -> settings.backspaceEnabled
        BounceCategory.UNSUPPORTED -> false
    }

    /** spec: keys-and-modifiers.md SS10.1, SS10.2 (repeats bypass the filter entirely). */
    fun onKeyDown(state: BounceFilterState, stroke: KeyStroke, settings: BounceKeySettings): Pair<BounceFilterState, FilterVerdict> {
        if (!settings.enabled || stroke.repeatCount > 0) return state to FilterVerdict.Accept

        val category = categoryOf(stroke.key)
        if (!isEnabledFor(category, settings)) return accept(state, stroke) to FilterVerdict.Accept

        val signature = KeySignature(stroke.deviceId, stroke.key)
        val lastAccepted = state.lastAcceptedDownAtMs[signature]
        val delay = settings.clampedDelayMs
        if (lastAccepted == null || stroke.timeMs - lastAccepted >= delay) {
            return accept(state, stroke) to FilterVerdict.Accept
        }

        // spec SS10.3: if the previously accepted press of this key is still held, its own
        // eventual up must not be swallowed, because that up belongs to the accepted press.
        val stillHeld = signature in state.heldSinceAccept
        val newSwallow = if (stillHeld) state.swallowNextUp else state.swallowNextUp + signature
        val delta = stroke.timeMs - lastAccepted
        val reason = "bounce_keys:ignored:category=${category.name.lowercase()}:delta=${delta}ms:threshold=${delay}ms:" +
            "id=${stroke.deviceId}:${stroke.key}"
        return state.copy(swallowNextUp = newSwallow) to FilterVerdict.Reject(reason)
    }

    private fun accept(state: BounceFilterState, stroke: KeyStroke): BounceFilterState {
        val signature = KeySignature(stroke.deviceId, stroke.key)
        return state.copy(
            lastAcceptedDownAtMs = state.lastAcceptedDownAtMs + (signature to stroke.timeMs),
            heldSinceAccept = state.heldSinceAccept + signature,
        )
    }

    /** spec: keys-and-modifiers.md SS10.3. */
    fun onKeyUp(state: BounceFilterState, stroke: KeyStroke): Pair<BounceFilterState, FilterVerdict> {
        val signature = KeySignature(stroke.deviceId, stroke.key)
        val shouldSwallow = signature in state.swallowNextUp
        val cleared = state.copy(
            heldSinceAccept = state.heldSinceAccept - signature,
            swallowNextUp = state.swallowNextUp - signature,
        )
        return cleared to if (shouldSwallow) FilterVerdict.Reject("bounce_keys:ignored_up:id=${stroke.deviceId}:${stroke.key}") else FilterVerdict.Accept
    }
}

// ---------------------------------------------------------------------------------------------
// Accidental-press filter (overlapping keys). spec: keys-and-modifiers.md SS11.
// ---------------------------------------------------------------------------------------------

/** spec: keys-and-modifiers.md SS11, SS18 (`overlapping_keys_enabled`). */
data class AccidentalPressSettings(val enabled: Boolean = false)

/**
 * Memory the accidental-press filter carries between strokes: which non-modifier keys are
 * currently held per device, and which keys owe a swallowed up. spec: keys-and-modifiers.md
 * SS11. Reset on start of input, finish of input, and any input device change.
 */
data class AccidentalPressFilterState(
    val heldNonModifierKeysByDevice: Map<Int, Set<KeyId>> = emptyMap(),
    val swallowedPending: Set<KeySignature> = emptySet(),
)

/**
 * Rejects a non-modifier key-down that arrives while another non-modifier key on the same device
 * is still held. spec: keys-and-modifiers.md SS11. Only meaningful for a physical, non-virtual
 * keyboard device; a caller simply never calls this for a virtual/software source, since this
 * module has no notion of "virtual" on its own.
 */
object AccidentalPressFilter {

    fun onKeyDown(
        state: AccidentalPressFilterState,
        stroke: KeyStroke,
        settings: AccidentalPressSettings,
    ): Pair<AccidentalPressFilterState, FilterVerdict> {
        if (!settings.enabled || stroke.repeatCount > 0 || stroke.key is KeyId.Modifier) {
            return state to FilterVerdict.Accept
        }

        val heldOnDevice = state.heldNonModifierKeysByDevice[stroke.deviceId] ?: emptySet()
        if (heldOnDevice.isNotEmpty()) {
            val signature = KeySignature(stroke.deviceId, stroke.key)
            val reason = "accidental_keys:ignored:reason=overlapping_key:id=${stroke.deviceId}:${stroke.key}"
            return state.copy(swallowedPending = state.swallowedPending + signature) to FilterVerdict.Reject(reason)
        }

        val newHeld = state.heldNonModifierKeysByDevice + (stroke.deviceId to (heldOnDevice + stroke.key))
        return state.copy(heldNonModifierKeysByDevice = newHeld) to FilterVerdict.Accept
    }

    fun onKeyUp(state: AccidentalPressFilterState, stroke: KeyStroke): Pair<AccidentalPressFilterState, FilterVerdict> {
        if (stroke.key is KeyId.Modifier) return state to FilterVerdict.Accept

        val signature = KeySignature(stroke.deviceId, stroke.key)
        if (signature in state.swallowedPending) {
            return state.copy(swallowedPending = state.swallowedPending - signature) to
                FilterVerdict.Reject("accidental_keys:ignored_up:id=${stroke.deviceId}:${stroke.key}")
        }

        val heldOnDevice = (state.heldNonModifierKeysByDevice[stroke.deviceId] ?: emptySet()) - stroke.key
        return state.copy(heldNonModifierKeysByDevice = state.heldNonModifierKeysByDevice + (stroke.deviceId to heldOnDevice)) to FilterVerdict.Accept
    }
}
