package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager

class BounceKeyFilter {
    data class SuppressedEvent(
        val category: Category,
        val deltaMs: Long,
        val thresholdMs: Long,
        val identity: String
    ) {
        fun debugOutput(): String =
            "bounce_keys:ignored:category=${category.debugName}:delta=${deltaMs}ms:threshold=${thresholdMs}ms:id=$identity"
    }

    enum class Category(val debugName: String) {
        CHARACTER("character"),
        MODIFIER("modifier"),
        SPACE("space"),
        ENTER("enter"),
        BACKSPACE("backspace"),
        UNSUPPORTED("unsupported")
    }

    private data class KeyIdentity(
        val deviceId: Int,
        val scanCode: Int,
        val keyCode: Int
    ) {
        override fun toString(): String = "$deviceId:$scanCode:$keyCode"
    }

    private data class LastAcceptedDown(
        val eventTime: Long,
        val category: Category
    )

    private val lastAcceptedDownByKey = mutableMapOf<KeyIdentity, LastAcceptedDown>()
    private val activeAcceptedDowns = mutableSetOf<KeyIdentity>()
    private val suppressedKeyUps = mutableSetOf<KeyIdentity>()

    fun shouldConsumeKeyDown(context: Context, keyCode: Int, event: KeyEvent?): SuppressedEvent? {
        if (!SettingsManager.getBounceKeysEnabled(context) || event == null || event.repeatCount > 0) {
            return null
        }

        val category = categoryFor(keyCode)
        if (!SettingsManager.getBounceKeysCategoryEnabled(context, category)) {
            return null
        }

        val identity = identityFor(keyCode, event)
        val now = event.eventTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val threshold = SettingsManager.getBounceKeysDelayMs(context)
        val previous = lastAcceptedDownByKey[identity]
        if (previous != null) {
            val delta = now - previous.eventTime
            if (delta in 0 until threshold) {
                if (identity !in activeAcceptedDowns) {
                    suppressedKeyUps.add(identity)
                }
                return SuppressedEvent(
                    category = category,
                    deltaMs = delta,
                    thresholdMs = threshold,
                    identity = identity.toString()
                )
            }
        }

        lastAcceptedDownByKey[identity] = LastAcceptedDown(now, category)
        activeAcceptedDowns.add(identity)
        return null
    }

    fun shouldConsumeKeyUp(keyCode: Int, event: KeyEvent?): SuppressedEvent? {
        if (event == null) return null

        val identity = identityFor(keyCode, event)
        if (!suppressedKeyUps.remove(identity)) {
            activeAcceptedDowns.remove(identity)
            return null
        }

        return SuppressedEvent(
            category = categoryFor(keyCode),
            deltaMs = 0,
            thresholdMs = 0,
            identity = identity.toString()
        )
    }

    fun reset() {
        lastAcceptedDownByKey.clear()
        activeAcceptedDowns.clear()
        suppressedKeyUps.clear()
    }

    private fun identityFor(keyCode: Int, event: KeyEvent): KeyIdentity =
        KeyIdentity(
            deviceId = event.deviceId,
            scanCode = event.scanCode,
            keyCode = keyCode
        )

    companion object {
        fun categoryFor(keyCode: Int): Category {
            return when (keyCode) {
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_SYM -> Category.MODIFIER
                KeyEvent.KEYCODE_SPACE -> Category.SPACE
                KeyEvent.KEYCODE_ENTER -> Category.ENTER
                KeyEvent.KEYCODE_DEL -> Category.BACKSPACE
                KeyEvent.KEYCODE_COMMA,
                KeyEvent.KEYCODE_PERIOD,
                KeyEvent.KEYCODE_MINUS,
                KeyEvent.KEYCODE_EQUALS,
                KeyEvent.KEYCODE_LEFT_BRACKET,
                KeyEvent.KEYCODE_RIGHT_BRACKET,
                KeyEvent.KEYCODE_BACKSLASH,
                KeyEvent.KEYCODE_SEMICOLON,
                KeyEvent.KEYCODE_APOSTROPHE,
                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_AT,
                KeyEvent.KEYCODE_PLUS,
                KeyEvent.KEYCODE_STAR,
                KeyEvent.KEYCODE_POUND -> Category.CHARACTER
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_POWER -> Category.UNSUPPORTED
                else -> Category.CHARACTER
            }
        }
    }
}
