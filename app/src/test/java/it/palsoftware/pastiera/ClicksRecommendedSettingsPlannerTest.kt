package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClicksRecommendedSettingsPlannerTest {

    @Test
    fun verificationRequiresConfirmedFeatureFlagsAndDictationRemap() {
        val dictation = ClicksPowerKeyboardProtocol.dictationRemapOutput()

        assertTrue(
            ClicksRecommendedSettingsPlanner.isVerified(
                featureFlags = 0,
                specialKeyEnableFlags = ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED,
                microphoneRemap = dictation
            )
        )
        assertFalse(
            ClicksRecommendedSettingsPlanner.isVerified(
                featureFlags = ClicksPowerKeyboardProtocol.FLAG_CURSOR_MODE,
                specialKeyEnableFlags = ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED,
                microphoneRemap = dictation
            )
        )
        assertFalse(
            ClicksRecommendedSettingsPlanner.isVerified(
                featureFlags = 0,
                specialKeyEnableFlags = ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED or
                    ClicksPowerKeyboardProtocol.FLAG_TAB_REMAP_ENABLED,
                microphoneRemap = dictation
            )
        )
        assertFalse(
            ClicksRecommendedSettingsPlanner.isVerified(
                featureFlags = 0,
                specialKeyEnableFlags = ClicksPowerKeyboardProtocol.FLAG_GEMINI_REMAP_ENABLED,
                microphoneRemap = ClicksPowerKeyboardProtocol.nativeRemapOutput()
            )
        )
    }
    @Test
    fun nativeButtonsStillRemapMicrophoneToPastieraDictation() {
        val plan = ClicksRecommendedSettingsPlanner.plan(snapshot())

        assertEquals(1, plan.remapWrites.size)
        assertEquals(
            ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP,
            plan.remapWrites.single().command
        )
        assertTrue(
            plan.remapWrites.single().output.contentEquals(
                ClicksPowerKeyboardProtocol.dictationRemapOutput()
            )
        )
    }

    @Test
    fun recommendedStateRestoresTabAndAltButMapsMicrophoneToDictation() {
        val plan = ClicksRecommendedSettingsPlanner.plan(
            snapshot(
                tabRemap = byteArrayOf(0xe2.toByte(), 0x07),
                microphoneRemap = byteArrayOf(0xe2.toByte(), 0x0e),
                altRemap = byteArrayOf(0xe0.toByte(), 0x2c)
            )
        )

        assertEquals(
            listOf(
                ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP,
                ClicksPowerKeyboardProtocol.COMMAND_GEMINI_REMAP,
                ClicksPowerKeyboardProtocol.COMMAND_ALT_REMAP
            ),
            plan.remapWrites.map(ClicksRemapWrite::command)
        )
        assertTrue(
            plan.remapWrites[0].output.contentEquals(ClicksPowerKeyboardProtocol.nativeRemapOutput())
        )
        assertTrue(
            plan.remapWrites[1].output.contentEquals(ClicksPowerKeyboardProtocol.dictationRemapOutput())
        )
        assertTrue(
            plan.remapWrites[2].output.contentEquals(ClicksPowerKeyboardProtocol.nativeRemapOutput())
        )
    }

    @Test
    fun alreadyConfiguredDictationNeedsNoMicrophoneRewrite() {
        val plan = ClicksRecommendedSettingsPlanner.plan(
            snapshot(microphoneRemap = ClicksPowerKeyboardProtocol.dictationRemapOutput())
        )

        assertTrue(plan.remapWrites.isEmpty())
    }

    private fun snapshot(
        tabRemap: ByteArray? = null,
        microphoneRemap: ByteArray? = null,
        altRemap: ByteArray? = null
    ) = ClicksRecommendedSettingsSnapshot(
        tabRemap = tabRemap,
        microphoneRemap = microphoneRemap,
        altRemap = altRemap
    )
}
