package it.palsoftware.pastiera

internal enum class ClicksButtonBindingTarget(val firmwareCommand: Int) {
    RED(ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP),
    KEYBOARD(ClicksPowerKeyboardProtocol.COMMAND_ALT_REMAP),
    MICROPHONE(ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP)
}

internal data class ClicksDesiredButtonBinding(
    val choiceId: String,
    val firmwareOutput: ByteArray
)

internal enum class ClicksButtonBindingRequestStatus {
    CONFIRMED,
    APPLYING,
    PENDING_CONNECTION
}

internal enum class ClicksButtonBindingCompletion {
    SUCCESS,
    FAILURE,
    KEEP_PENDING
}

/** Resolves an in-flight firmware write against the most recently requested binding. */
internal object ClicksButtonBindingCompletionPolicy {
    fun resolve(
        attemptedOutput: ByteArray,
        latestDesiredOutput: ByteArray?,
        latestDesiredConfirmed: Boolean
    ): ClicksButtonBindingCompletion = when {
        latestDesiredOutput == null -> ClicksButtonBindingCompletion.FAILURE
        latestDesiredConfirmed -> ClicksButtonBindingCompletion.SUCCESS
        attemptedOutput.contentEquals(latestDesiredOutput) -> ClicksButtonBindingCompletion.FAILURE
        else -> ClicksButtonBindingCompletion.KEEP_PENDING
    }
}

internal object ClicksButtonBindingSyncPolicy {
    fun canWrite(state: ClicksPowerKeyboardState): Boolean =
        state.ready &&
            state.sessionValidated &&
            !state.stale &&
            state.specialKeyEnableFlags != null &&
            state.firmwareVersion?.let(ClicksFirmwareVersionReader::isSupported) == true

    fun isConfirmed(
        state: ClicksPowerKeyboardState,
        target: ClicksButtonBindingTarget,
        desiredOutput: ByteArray
    ): Boolean {
        require(desiredOutput.size == 2)
        val currentOutput = when (target) {
            ClicksButtonBindingTarget.RED -> state.tabRemap
            ClicksButtonBindingTarget.KEYBOARD -> state.altRemap
            ClicksButtonBindingTarget.MICROPHONE -> state.geminiRemap
        }
        return if (desiredOutput.isNativeOutput()) {
            currentOutput == null
        } else {
            currentOutput?.contentEquals(desiredOutput) == true
        }
    }

    private fun ByteArray.isNativeOutput(): Boolean = all { it == 0.toByte() }
}
