package brobata.physiboard.core.actions.feedback

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId

/** `typing_sound_mode`. spec: expansion-clipboard-pickers-launcher.md SS9.1; "anything else reads as `off`" (T56). `custom` is dropped with the pack import (SS13). */
enum class TypingSoundMode(val storedValue: String) {
    OFF("off"), CLICK("click"), TYPEWRITER("typewriter");

    companion object {
        fun fromStored(value: String?): TypingSoundMode = entries.firstOrNull { it.storedValue == value } ?: OFF
    }
}

/** The five sound groups of a pack. spec SS9.1. */
enum class SoundGroup(val folder: String, val fileCount: Int) {
    NORMAL("normal", 24), SPACE("space", 5), BACKSPACE("backspace", 5), ENTER("enter", 5), MODIFIER("modifier", 5);

    companion object {
        /** spec SS9.1: "The group is chosen by keycode: Space; Backspace; Enter; Shift, Ctrl, Alt and Sym as modifier; everything else normal" (T53). Fn arrives as Ctrl (D3). */
        fun forKey(key: KeyId): SoundGroup = when (key) {
            KeyId.Control(ControlKey.SPACE) -> SPACE
            KeyId.Control(ControlKey.BACKSPACE) -> BACKSPACE
            KeyId.Control(ControlKey.ENTER) -> ENTER
            is KeyId.Modifier -> MODIFIER
            else -> NORMAL
        }
    }
}

/** spec SS9.1: when a key sounds and how it is played. */
object TypingSounds {
    const val MIN_VOLUME: Float = 0.82f
    const val MAX_VOLUME: Float = 1.0f
    const val MIN_RATE: Float = 0.965f
    const val MAX_RATE: Float = 1.035f
    const val MAX_STREAMS: Int = 8

    /** "on every hardware key down with repeat count 0 while an editable field is active, for every key except Back (modifiers included)" (T54). */
    fun shouldPlay(mode: TypingSoundMode, key: KeyId, repeatCount: Int, editableFieldActive: Boolean): Boolean {
        if (mode == TypingSoundMode.OFF) return false
        if (!editableFieldActive || repeatCount != 0) return false
        return key != KeyId.Control(ControlKey.BACK)
    }

    /** The resource name of one file of a pack: `typing_<pack>_<group>_<n>` (SS9.1). */
    fun resourceName(mode: TypingSoundMode, group: SoundGroup, index: Int): String? {
        val pack = when (mode) {
            TypingSoundMode.OFF -> return null
            TypingSoundMode.CLICK -> "click"
            TypingSoundMode.TYPEWRITER -> "typewriter"
        }
        return "typing_${pack}_${group.folder}_$index"
    }
}

/** spec SS9.2: the suggestion-slot tap vibration. */
object TapVibration {
    const val DEFAULT_DURATION_MS: Long = 25
    const val MIN_DURATION_MS: Long = 5
    const val MAX_DURATION_MS: Long = 80
    const val STEP_MS: Long = 5

    /** "`tap_haptic_duration_ms` (default 25, clamped 5..80)" (T55). */
    fun clampDuration(stored: Long): Long = stored.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

    /** What a suggestion-slot tap should do: the system keyboard-tap haptic, or a one-shot of [durationMs] (skipped when the device reports no vibrator). */
    sealed class Effect {
        data object SystemKeyboardTap : Effect()
        data class OneShot(val durationMs: Long) : Effect()
    }

    fun slotTapEffect(useSystem: Boolean, storedDurationMs: Long): Effect =
        if (useSystem) Effect.SystemKeyboardTap else Effect.OneShot(clampDuration(storedDurationMs))
}
