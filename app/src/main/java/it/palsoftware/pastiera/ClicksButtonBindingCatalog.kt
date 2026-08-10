package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.directActionOrNull

internal data class ClicksButtonBindingChoice(
    val id: String,
    val label: String,
    val description: String,
    val softwareMode: SettingsManager.ClicksPowerButtonMode? = null,
    val firmwareOutput: ByteArray? = null,
    val pastieraFunction: ClicksBindingPastieraFunction? = null
)

internal enum class ClicksBindingPastieraFunction { LANGUAGE_SWITCH, DICTATION }

internal const val CLICKS_BINDING_RED = "red"
internal const val CLICKS_BINDING_ALT = "alt"
internal const val CLICKS_BINDING_MICROPHONE = "microphone"
internal const val CLICKS_BINDING_RECOMMENDED = "recommended"

/** Pure catalog and selection policy shared by the localized Clicks button UI. */
internal object ClicksButtonBindingCatalog {
    fun shortcutOutputs(): List<Pair<String, ByteArray>> = listOf(
        "Alt + D" to byteArrayOf(0xe2.toByte(), 0x07),
        "Alt + K" to byteArrayOf(0xe2.toByte(), 0x0e),
        "Alt + S" to byteArrayOf(0xe2.toByte(), 0x16),
        "Alt + ." to byteArrayOf(0xe2.toByte(), 0x37)
    )

    fun desiredSelection(
        desired: ClicksDesiredButtonBinding?,
        choices: List<ClicksButtonBindingChoice>
    ): ClicksButtonBindingChoice? = desired?.let { binding ->
        choices.firstOrNull { it.id == binding.choiceId }
    }

    fun directActionSelection(
        mode: SettingsManager.ClicksPowerButtonMode,
        choices: List<ClicksButtonBindingChoice>
    ): ClicksButtonBindingChoice? = mode
        .takeIf { it.directActionOrNull() != null }
        ?.let { directMode -> choices.firstOrNull { it.softwareMode == directMode } }

    fun firmwareSelection(
        output: ByteArray,
        choices: List<ClicksButtonBindingChoice>
    ): ClicksButtonBindingChoice? = choices.firstOrNull {
        it.firmwareOutput?.contentEquals(output) == true
    }
}
