package brobata.physiboard.inputmethod

import android.view.KeyEvent

/**
 * Suppresses a key that goes down while another key on the same device is still held - the
 * classic fat-finger overlap on a small physical keyboard. Modifiers are never suppressed, and a
 * suppressed key's matching key-up is swallowed too so the two stay balanced.
 */
class AccidentalKeyPressFilter {
    enum class OverlapRule {
        NONE,
        ALL
    }

    data class Configuration(
        val overlapRule: OverlapRule = OverlapRule.NONE
    ) {
        val needsHeldKeyTracking: Boolean
            get() = overlapRule != OverlapRule.NONE
    }

    enum class Reason(val debugName: String) {
        OVERLAPPING_KEY("overlapping_key")
    }

    data class SuppressedEvent(
        val reason: Reason,
        val identity: String
    ) {
        fun debugOutput(): String =
            "accidental_keys:ignored:reason=${reason.debugName}:id=$identity"
    }

    sealed interface KeyUpResult {
        data class Suppressed(val event: SuppressedEvent) : KeyUpResult
    }

    private data class KeyIdentity(
        val deviceId: Int,
        val scanCode: Int,
        val keyCode: Int
    ) {
        override fun toString(): String = "$deviceId:$scanCode:$keyCode"
    }

    private val activeKeysByDevice = mutableMapOf<Int, MutableMap<KeyIdentity, KeyIdentity>>()
    private val suppressedKeyUps = mutableMapOf<KeyIdentity, SuppressedEvent>()

    fun shouldConsumeKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        isModifier: Boolean,
        configuration: Configuration
    ): SuppressedEvent? {
        if (event == null || isModifier) return null

        val identity = identityFor(keyCode, event)
        suppressedKeyUps[identity]?.let { return it }
        if (event.repeatCount > 0) return null

        val activeKeys = activeKeysByDevice.getOrPut(event.deviceId) { linkedMapOf() }
        val otherActiveKeys = activeKeys.values.filter { it != identity }
        if (configuration.overlapRule == OverlapRule.ALL && otherActiveKeys.isNotEmpty()) {
            return suppressUntilKeyUp(Reason.OVERLAPPING_KEY, identity)
        }

        if (configuration.needsHeldKeyTracking) {
            activeKeys[identity] = identity
        } else if (activeKeys.isEmpty()) {
            activeKeysByDevice.remove(event.deviceId)
        }
        return null
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): KeyUpResult? {
        if (event == null || isModifierKey(keyCode)) return null

        val identity = identityFor(keyCode, event)
        removeActive(identity)
        return suppressedKeyUps.remove(identity)?.let(KeyUpResult::Suppressed)
    }

    fun resetDevice(deviceId: Int) {
        activeKeysByDevice.remove(deviceId)
        suppressedKeyUps.keys.removeAll { it.deviceId == deviceId }
    }

    fun reset() {
        activeKeysByDevice.clear()
        suppressedKeyUps.clear()
    }

    private fun suppressUntilKeyUp(reason: Reason, identity: KeyIdentity): SuppressedEvent {
        return suppressed(reason, identity).also { suppressedKeyUps[identity] = it }
    }

    private fun suppressed(reason: Reason, identity: KeyIdentity): SuppressedEvent =
        SuppressedEvent(reason = reason, identity = identity.toString())

    private fun removeActive(identity: KeyIdentity) {
        activeKeysByDevice[identity.deviceId]?.let { activeKeys ->
            activeKeys.remove(identity)
            if (activeKeys.isEmpty()) activeKeysByDevice.remove(identity.deviceId)
        }
    }

    private fun identityFor(keyCode: Int, event: KeyEvent): KeyIdentity =
        KeyIdentity(
            deviceId = event.deviceId,
            scanCode = event.scanCode,
            keyCode = keyCode
        )

    companion object {
        fun isModifierKey(keyCode: Int): Boolean =
            KeyEvent.isModifierKey(keyCode) ||
                keyCode == KeyEvent.KEYCODE_SYM ||
                keyCode == KeyEvent.KEYCODE_FUNCTION
    }
}
