package it.palsoftware.pastiera

internal data class ClicksRemapWrite(
    val command: Int,
    val output: ByteArray
)

internal data class ClicksRecommendedSettingsSnapshot(
    val tabRemap: ByteArray?,
    val microphoneRemap: ByteArray?,
    val altRemap: ByteArray?
)

internal data class ClicksRecommendedSettingsPlan(
    val remapWrites: List<ClicksRemapWrite>
)

/**
 * Builds the firmware part of the single recommended-settings action.
 *
 * The recommended state keeps the keyboard's native Tab and Alt events, while the microphone
 * button emits Pastiera's Alt+Ctrl dictation trigger instead of Android's native voice action.
 * Pastiera's software settings are persisted only after these writes have completed successfully.
 */
internal object ClicksRecommendedSettingsPlanner {
    fun plan(snapshot: ClicksRecommendedSettingsSnapshot): ClicksRecommendedSettingsPlan =
        ClicksRecommendedSettingsPlan(
            remapWrites = buildList {
                if (snapshot.tabRemap != null) {
                    add(
                        ClicksRemapWrite(
                            ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP,
                            ClicksPowerKeyboardProtocol.nativeRemapOutput()
                        )
                    )
                }
                val dictationOutput = ClicksPowerKeyboardProtocol.dictationRemapOutput()
                if (snapshot.microphoneRemap?.contentEquals(dictationOutput) != true) {
                    add(
                        ClicksRemapWrite(
                            ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP,
                            dictationOutput
                        )
                    )
                }
                if (snapshot.altRemap != null) {
                    add(
                        ClicksRemapWrite(
                            ClicksPowerKeyboardProtocol.COMMAND_ALT_REMAP,
                            ClicksPowerKeyboardProtocol.nativeRemapOutput()
                        )
                    )
                }
            }
        )

    fun isVerified(
        featureFlags: Int,
        specialKeyEnableFlags: Int,
        microphoneRemap: ByteArray
    ): Boolean {
        val disabledFeatureFlags = ClicksPowerKeyboardProtocol.FLAG_CAPS_LOCK or
            ClicksPowerKeyboardProtocol.FLAG_CURSOR_MODE
        val nativeButtonFlags = ClicksPowerKeyboardProtocol.FLAG_TAB_REMAP_ENABLED or
            ClicksPowerKeyboardProtocol.FLAG_ALT_REMAP_ENABLED
        return featureFlags and disabledFeatureFlags == 0 &&
            specialKeyEnableFlags and nativeButtonFlags == 0 &&
            specialKeyEnableFlags and ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED != 0 &&
            microphoneRemap.contentEquals(ClicksPowerKeyboardProtocol.dictationRemapOutput())
    }
}
