package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Global tracker for keyboard events.
 * Allows input method service to communicate events to MainActivity.
 */
object KeyboardEventTracker {
    private var _keyEventState: MutableState<KeyEventInfo?>? = null
    val keyEventState: MutableState<KeyEventInfo?>?
        get() = _keyEventState
    
    data class KeyEventInfo(
        val keyCode: Int,
        val keyCodeName: String,
        val origin: String,
        val action: String,
        val scanCode: Int,
        val deviceId: Int,
        val source: Int,
        val flags: Int,
        val repeatCount: Int,
        val metaState: Int,
        val unicodeChar: Int,
        val rawUnicodeChar: Int,
        val effectiveUnicodeChar: Int,
        val isAltPressed: Boolean,
        val isShiftPressed: Boolean,
        val isCtrlPressed: Boolean,
        val altLatchActive: Boolean? = null,
        val altOneShot: Boolean? = null,
        val shiftLatchActive: Boolean? = null,
        val ctrlLatchActive: Boolean? = null,
        val symPage: Int? = null,
        val resolvedLayout: String? = null,
        val outputKeyCode: Int? = null,
        val outputKeyCodeName: String? = null,
        val eventTimeUptimeMs: Long = 0L
    )

    
    fun registerState(state: MutableState<KeyEventInfo?>) {
        _keyEventState = state
    }

    fun unregisterState() {
        _keyEventState = null
    }

    fun notifySyntheticGestureKeyEvent(
        provider: String,
        origin: String,
        phase: String,
        action: String,
        direction: String? = null,
        outcome: String,
        startX: Float? = null,
        startY: Float? = null,
        x: Float? = null,
        y: Float? = null,
        deltaX: Float? = null,
        deltaY: Float? = null,
        threshold: Float? = null,
        deviceId: Int = -1,
        source: Int = 0,
        eventTimeUptimeMs: Long = 0L
    ) {
        _keyEventState?.value = KeyEventInfo(
            keyCode = 0,
            keyCodeName = direction?.let { "GESTURE_${it.uppercase()}" } ?: "GESTURE_TRACKPAD",
            origin = origin,
            action = "GESTURE_$phase",
            scanCode = 0,
            deviceId = deviceId,
            source = source,
            flags = 0,
            repeatCount = 0,
            metaState = 0,
            unicodeChar = 0,
            rawUnicodeChar = 0,
            effectiveUnicodeChar = 0,
            isAltPressed = false,
            isShiftPressed = false,
            isCtrlPressed = false,
            outputKeyCode = null,
            outputKeyCodeName = listOfNotNull(
                provider,
                action,
                outcome,
                startX?.let { "sx=${it.toInt()}" },
                startY?.let { "sy=${it.toInt()}" },
                x?.let { "x=${it.toInt()}" },
                y?.let { "y=${it.toInt()}" },
                deltaX?.let { "dx=${it.toInt()}" },
                deltaY?.let { "dy=${it.toInt()}" },
                threshold?.let { "threshold=${it.toInt()}" }
            ).joinToString(":"),
            eventTimeUptimeMs = eventTimeUptimeMs
        )
    }
    
    fun notifyKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        action: String,
        origin: String = "unknown",
        altLatchActive: Boolean? = null,
        altOneShot: Boolean? = null,
        shiftLatchActive: Boolean? = null,
        ctrlLatchActive: Boolean? = null,
        symPage: Int? = null,
        resolvedLayout: String? = null,
        outputKeyCode: Int? = null,
        outputKeyCodeName: String? = null,
        unicodeCharOverride: Int? = null
    ) {
        if (event != null) {
            val rawUnicodeChar = event.unicodeChar
            val effectiveUnicodeChar = unicodeCharOverride ?: rawUnicodeChar
            val keyEventInfo = KeyEventInfo(
                keyCode = keyCode,
                keyCodeName = getKeyCodeName(keyCode),
                origin = origin,
                action = action,
                scanCode = event.scanCode,
                deviceId = event.deviceId,
                source = event.source,
                flags = event.flags,
                repeatCount = event.repeatCount,
                metaState = event.metaState,
                unicodeChar = effectiveUnicodeChar,
                rawUnicodeChar = rawUnicodeChar,
                effectiveUnicodeChar = effectiveUnicodeChar,
                isAltPressed = event.isAltPressed,
                isShiftPressed = event.isShiftPressed,
                isCtrlPressed = event.isCtrlPressed,
                altLatchActive = altLatchActive,
                altOneShot = altOneShot,
                shiftLatchActive = shiftLatchActive,
                ctrlLatchActive = ctrlLatchActive,
                symPage = symPage,
                resolvedLayout = resolvedLayout,
                outputKeyCode = outputKeyCode,
                outputKeyCodeName = outputKeyCodeName,
                eventTimeUptimeMs = event.eventTime
            )
            _keyEventState?.value = keyEventInfo
        }
    }
    
    private fun getKeyCodeName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> "KEYCODE_Q"
            KeyEvent.KEYCODE_W -> "KEYCODE_W"
            KeyEvent.KEYCODE_E -> "KEYCODE_E"
            KeyEvent.KEYCODE_R -> "KEYCODE_R"
            KeyEvent.KEYCODE_T -> "KEYCODE_T"
            KeyEvent.KEYCODE_Y -> "KEYCODE_Y"
            KeyEvent.KEYCODE_U -> "KEYCODE_U"
            KeyEvent.KEYCODE_I -> "KEYCODE_I"
            KeyEvent.KEYCODE_O -> "KEYCODE_O"
            KeyEvent.KEYCODE_P -> "KEYCODE_P"
            KeyEvent.KEYCODE_A -> "KEYCODE_A"
            KeyEvent.KEYCODE_S -> "KEYCODE_S"
            KeyEvent.KEYCODE_D -> "KEYCODE_D"
            KeyEvent.KEYCODE_F -> "KEYCODE_F"
            KeyEvent.KEYCODE_G -> "KEYCODE_G"
            KeyEvent.KEYCODE_H -> "KEYCODE_H"
            KeyEvent.KEYCODE_J -> "KEYCODE_J"
            KeyEvent.KEYCODE_K -> "KEYCODE_K"
            KeyEvent.KEYCODE_L -> "KEYCODE_L"
            KeyEvent.KEYCODE_Z -> "KEYCODE_Z"
            KeyEvent.KEYCODE_X -> "KEYCODE_X"
            KeyEvent.KEYCODE_C -> "KEYCODE_C"
            KeyEvent.KEYCODE_V -> "KEYCODE_V"
            KeyEvent.KEYCODE_B -> "KEYCODE_B"
            KeyEvent.KEYCODE_N -> "KEYCODE_N"
            KeyEvent.KEYCODE_M -> "KEYCODE_M"
            KeyEvent.KEYCODE_SPACE -> "KEYCODE_SPACE"
            KeyEvent.KEYCODE_ENTER -> "KEYCODE_ENTER"
            KeyEvent.KEYCODE_DEL -> "KEYCODE_DEL"
            KeyEvent.KEYCODE_BACK -> "KEYCODE_BACK"
            KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
            KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
            KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
            KeyEvent.KEYCODE_TAB -> "TAB"
            KeyEvent.KEYCODE_PAGE_UP -> "PAGE_UP"
            KeyEvent.KEYCODE_PAGE_DOWN -> "PAGE_DOWN"
            KeyEvent.KEYCODE_ESCAPE -> "ESCAPE"
            KeyEvent.KEYCODE_FORWARD_DEL -> "FORWARD_DEL"
            63 -> "KEYCODE_SYM"
            else -> "KEYCODE_$keyCode"
        }
    }
    
    fun getOutputKeyCodeName(keyCode: Int): String {
        return getKeyCodeName(keyCode)
    }
}
