package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.ClicksPowerKeyboardState

/**
 * Resolves firmware output back to one or more possible physical source keys.
 *
 * A set is returned deliberately: current Clicks firmware output and user-configured GATT
 * remaps can be ambiguous. Consumers must fail open unless every possible source is safe to
 * suppress.
 */
class PhysicalKeyResolver {
    data class Resolution(
        val candidates: Set<PhysicalKeyId>,
        val profile: PhysicalKeyAdjacencyProfile?,
        val isModifier: Boolean
    ) {
        fun isDefinitelyNumberRowKey(): Boolean =
            candidates.isNotEmpty() && candidates.all { it in profile?.numberRowKeys.orEmpty() }
    }

    fun resolve(
        keyCode: Int,
        event: KeyEvent?,
        profile: PhysicalKeyAdjacencyProfile?,
        clicksState: ClicksPowerKeyboardState? = null
    ): Resolution {
        val isModifier = isModifierKey(keyCode)
        if (profile == null || event == null) {
            return Resolution(emptySet(), null, isModifier)
        }

        val candidates = linkedSetOf<PhysicalKeyId>()
        profile.resolveDirectKey(keyCode, event.scanCode)?.let(candidates::add)
        candidates += profile.resolveNormalizedFirmwareOutput(keyCode, event.metaState)

        val signature = OutputSignature(keyCode, broadModifiers(event.metaState))
        if (profile.profileId == ClicksPowerKeyboardLayout.profileId) {
            clicksState?.numberRemaps?.forEachIndexed { index, remap ->
                if (remap != null && outputSignatureForGattRemap(remap) == signature) {
                    NUMBER_KEYS.getOrNull(index)?.let(candidates::add)
                }
            }
        }

        return Resolution(candidates, profile, isModifier)
    }

    companion object {
        private const val RELEVANT_META_MASK =
            KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or
                KeyEvent.META_META_ON or KeyEvent.META_SYM_ON

        private data class OutputSignature(val keyCode: Int, val modifiers: Int)

        private val NUMBER_KEYS = listOf(
            ClicksPowerKeyboardLayout.DIGIT_1,
            ClicksPowerKeyboardLayout.DIGIT_2,
            ClicksPowerKeyboardLayout.DIGIT_3,
            ClicksPowerKeyboardLayout.DIGIT_4,
            ClicksPowerKeyboardLayout.DIGIT_5,
            ClicksPowerKeyboardLayout.DIGIT_6,
            ClicksPowerKeyboardLayout.DIGIT_7,
            ClicksPowerKeyboardLayout.DIGIT_8,
            ClicksPowerKeyboardLayout.DIGIT_9
        )

        fun isModifierKey(keyCode: Int): Boolean =
            KeyEvent.isModifierKey(keyCode) ||
                keyCode == KeyEvent.KEYCODE_SYM ||
                keyCode == KeyEvent.KEYCODE_FUNCTION

        private fun broadModifiers(metaState: Int): Int {
            val normalized = KeyEvent.normalizeMetaState(metaState)
            var result = 0
            if (normalized and KeyEvent.META_SHIFT_MASK != 0) result = result or KeyEvent.META_SHIFT_ON
            if (normalized and KeyEvent.META_CTRL_MASK != 0) result = result or KeyEvent.META_CTRL_ON
            if (normalized and KeyEvent.META_ALT_MASK != 0) result = result or KeyEvent.META_ALT_ON
            if (normalized and KeyEvent.META_META_MASK != 0) result = result or KeyEvent.META_META_ON
            if (normalized and KeyEvent.META_SYM_ON != 0) result = result or KeyEvent.META_SYM_ON
            return result and RELEVANT_META_MASK
        }

        private fun outputSignatureForGattRemap(bytes: ByteArray): OutputSignature? {
            if (bytes.size != 2 || bytes.all { it == 0.toByte() }) return null
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff

            if (second == 0xff) {
                return consumerUsageToKeyCode(first)?.let { OutputSignature(it, 0) }
            }

            val modifierUsage = first.takeIf { it in 0xe0..0xe7 }
            val usage = when {
                modifierUsage != null -> second.takeIf { it != 0 }
                first == 0 -> second.takeIf { it != 0 }
                second == 0 -> first
                else -> null
            } ?: return null
            val keyCode = keyboardUsageToKeyCode(usage) ?: return null
            return OutputSignature(keyCode, broadModifiers(modifierUsageToMetaState(modifierUsage)))
        }

        private fun modifierUsageToMetaState(usage: Int?): Int = when (usage) {
            0xe0, 0xe4 -> KeyEvent.META_CTRL_ON
            0xe1, 0xe5 -> KeyEvent.META_SHIFT_ON
            0xe2, 0xe6 -> KeyEvent.META_ALT_ON
            0xe3, 0xe7 -> KeyEvent.META_META_ON
            else -> 0
        }

        private fun keyboardUsageToKeyCode(usage: Int): Int? = when (usage) {
            in 0x04..0x1d -> LETTER_KEY_CODES[usage - 0x04]
            in 0x1e..0x26 -> DIGIT_KEY_CODES[usage - 0x1e]
            0x27 -> KeyEvent.KEYCODE_0
            0x28 -> KeyEvent.KEYCODE_ENTER
            0x29 -> KeyEvent.KEYCODE_ESCAPE
            0x2a -> KeyEvent.KEYCODE_DEL
            0x2b -> KeyEvent.KEYCODE_TAB
            0x2c -> KeyEvent.KEYCODE_SPACE
            0x2d -> KeyEvent.KEYCODE_MINUS
            0x2e -> KeyEvent.KEYCODE_EQUALS
            0x2f -> KeyEvent.KEYCODE_LEFT_BRACKET
            0x30 -> KeyEvent.KEYCODE_RIGHT_BRACKET
            0x31 -> KeyEvent.KEYCODE_BACKSLASH
            0x33 -> KeyEvent.KEYCODE_SEMICOLON
            0x34 -> KeyEvent.KEYCODE_APOSTROPHE
            0x35 -> KeyEvent.KEYCODE_GRAVE
            0x36 -> KeyEvent.KEYCODE_COMMA
            0x37 -> KeyEvent.KEYCODE_PERIOD
            0x38 -> KeyEvent.KEYCODE_SLASH
            0x4a -> KeyEvent.KEYCODE_MOVE_HOME
            0x4b -> KeyEvent.KEYCODE_PAGE_UP
            0x4d -> KeyEvent.KEYCODE_MOVE_END
            0x4e -> KeyEvent.KEYCODE_PAGE_DOWN
            0x4f -> KeyEvent.KEYCODE_DPAD_RIGHT
            0x50 -> KeyEvent.KEYCODE_DPAD_LEFT
            0x51 -> KeyEvent.KEYCODE_DPAD_DOWN
            0x52 -> KeyEvent.KEYCODE_DPAD_UP
            else -> null
        }

        private fun consumerUsageToKeyCode(usage: Int): Int? = when (usage) {
            0x7f -> KeyEvent.KEYCODE_VOLUME_MUTE
            0x80 -> KeyEvent.KEYCODE_VOLUME_UP
            0x81 -> KeyEvent.KEYCODE_VOLUME_DOWN
            0xb5 -> KeyEvent.KEYCODE_MEDIA_NEXT
            0xb6 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            0xcd -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> null
        }

        private val LETTER_KEY_CODES = intArrayOf(
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z
        )
        private val DIGIT_KEY_CODES = intArrayOf(
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9
        )
    }
}
