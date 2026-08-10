package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/** Filters accidental physical-key overlaps and Clicks number-row presses. */
class AccidentalKeyPressFilter {
    enum class OverlapRule {
        NONE,
        ADJACENT,
        ALL
    }

    enum class NumberRowAcceptance {
        NORMAL,
        LONG_PRESS,
        NEVER
    }

    data class NumberRowPolicy(
        val acceptance: NumberRowAcceptance = NumberRowAcceptance.NORMAL,
        val overlapMinimum: OverlapRule = OverlapRule.NONE
    )

    data class Configuration(
        val overlapRule: OverlapRule = OverlapRule.NONE,
        val numberRowPolicy: NumberRowPolicy = NumberRowPolicy(),
        val longPressThresholdMs: Long = 500L,
        val numberRowRepeatEnabled: Boolean = true
    ) {
        val needsHeldKeyTracking: Boolean
            get() = overlapRule != OverlapRule.NONE ||
                numberRowPolicy.overlapMinimum != OverlapRule.NONE
    }

    enum class Reason(val debugName: String) {
        OVERLAPPING_KEY("overlapping_key"),
        ADJACENT_KEY("adjacent_key"),
        NUMBER_ROW_DISABLED("number_row_disabled"),
        NUMBER_ROW_SHORT_PRESS("number_row_short_press"),
        NUMBER_ROW_LONG_PRESS_PENDING("number_row_long_press_pending"),
        NUMBER_ROW_REPEAT_DISABLED("number_row_repeat_disabled")
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

        data class ReplayTap(
            val downEvent: KeyEvent,
            val upEvent: KeyEvent
        ) : KeyUpResult
    }

    private data class KeyIdentity(
        val deviceId: Int,
        val scanCode: Int,
        val keyCode: Int
    ) {
        override fun toString(): String = "$deviceId:$scanCode:$keyCode"
    }

    private data class ActiveKey(
        val identity: KeyIdentity,
        val resolution: PhysicalKeyResolver.Resolution
    )

    private data class PendingLongPress(
        val downEvent: KeyEvent,
        val thresholdMs: Long
    )

    private val activeKeysByDevice = mutableMapOf<Int, MutableMap<KeyIdentity, ActiveKey>>()
    private val suppressedKeyUps = mutableMapOf<KeyIdentity, SuppressedEvent>()
    private val pendingLongPresses = mutableMapOf<KeyIdentity, PendingLongPress>()
    private val heldNumberRowKeys = mutableSetOf<KeyIdentity>()

    fun shouldConsumeKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        resolution: PhysicalKeyResolver.Resolution,
        configuration: Configuration
    ): SuppressedEvent? {
        if (event == null || resolution.isModifier) return null

        val identity = identityFor(keyCode, event)
        suppressedKeyUps[identity]?.let { return it }
        pendingLongPresses[identity]?.let {
            return suppressed(Reason.NUMBER_ROW_LONG_PRESS_PENDING, identity)
        }

        val isNumberRowKey = resolution.isDefinitelyNumberRowKey() || identity in heldNumberRowKeys
        if (event.repeatCount > 0) {
            return if (isNumberRowKey && !configuration.numberRowRepeatEnabled) {
                suppressed(Reason.NUMBER_ROW_REPEAT_DISABLED, identity)
            } else {
                null
            }
        }

        if (isNumberRowKey) heldNumberRowKeys += identity

        val numberPolicy = configuration.numberRowPolicy
        if (isNumberRowKey && numberPolicy.acceptance == NumberRowAcceptance.NEVER) {
            return suppressUntilKeyUp(Reason.NUMBER_ROW_DISABLED, identity)
        }

        val activeKeys = activeKeysByDevice.getOrPut(event.deviceId) { linkedMapOf() }
        val otherActiveKeys = activeKeys.values.filter { it.identity != identity }
        val overlapRule = if (isNumberRowKey) {
            maxOf(configuration.overlapRule, numberPolicy.overlapMinimum)
        } else {
            configuration.overlapRule
        }
        when (overlapRule) {
            OverlapRule.ALL -> if (otherActiveKeys.isNotEmpty()) {
                return suppressUntilKeyUp(Reason.OVERLAPPING_KEY, identity)
            }
            OverlapRule.ADJACENT -> if (otherActiveKeys.any { active ->
                    areDefinitelyAdjacent(active.resolution, resolution)
                }
            ) {
                return suppressUntilKeyUp(Reason.ADJACENT_KEY, identity)
            }
            OverlapRule.NONE -> Unit
        }

        if (isNumberRowKey && numberPolicy.acceptance == NumberRowAcceptance.LONG_PRESS) {
            pendingLongPresses[identity] = PendingLongPress(
                downEvent = KeyEvent(event),
                thresholdMs = configuration.longPressThresholdMs.coerceAtLeast(1L)
            )
            if (configuration.needsHeldKeyTracking) {
                activeKeys[identity] = ActiveKey(identity, resolution)
            } else if (activeKeys.isEmpty()) {
                activeKeysByDevice.remove(event.deviceId)
            }
            return suppressed(Reason.NUMBER_ROW_LONG_PRESS_PENDING, identity)
        }

        if (configuration.needsHeldKeyTracking) {
            activeKeys[identity] = ActiveKey(identity, resolution)
        } else if (activeKeys.isEmpty()) {
            activeKeysByDevice.remove(event.deviceId)
        }
        return null
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): KeyUpResult? {
        if (event == null || PhysicalKeyResolver.isModifierKey(keyCode)) return null

        val identity = identityFor(keyCode, event)
        removeActive(identity)
        heldNumberRowKeys.remove(identity)

        pendingLongPresses.remove(identity)?.let { pending ->
            val durationMs = event.eventTime - pending.downEvent.eventTime
            return if (!event.isCanceled && durationMs >= pending.thresholdMs) {
                KeyUpResult.ReplayTap(
                    downEvent = KeyEvent(pending.downEvent),
                    upEvent = KeyEvent(event)
                )
            } else {
                KeyUpResult.Suppressed(suppressed(Reason.NUMBER_ROW_SHORT_PRESS, identity))
            }
        }

        return suppressedKeyUps.remove(identity)?.let(KeyUpResult::Suppressed)
    }

    fun resetDevice(deviceId: Int) {
        activeKeysByDevice.remove(deviceId)
        suppressedKeyUps.keys.removeAll { it.deviceId == deviceId }
        pendingLongPresses.keys.removeAll { it.deviceId == deviceId }
        heldNumberRowKeys.removeAll { it.deviceId == deviceId }
    }

    fun reset() {
        activeKeysByDevice.clear()
        suppressedKeyUps.clear()
        pendingLongPresses.clear()
        heldNumberRowKeys.clear()
    }

    private fun areDefinitelyAdjacent(
        active: PhysicalKeyResolver.Resolution,
        incoming: PhysicalKeyResolver.Resolution
    ): Boolean {
        val profile = incoming.profile ?: return false
        if (active.profile?.profileId != profile.profileId ||
            active.candidates.isEmpty() || incoming.candidates.isEmpty()
        ) {
            return false
        }
        return active.candidates.all { activeKey ->
            incoming.candidates.all { incomingKey ->
                profile.isAdjacent(activeKey, incomingKey)
            }
        }
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
}
