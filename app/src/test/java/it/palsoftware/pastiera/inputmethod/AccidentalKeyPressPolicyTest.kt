package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AccidentalKeyPressPolicyTest {
    @Test
    fun globalStrictRuleOverridesClicksAdjacentRule() {
        val configuration = configuration(
            isPhysicalKeyboard = true,
            isClicksPowerKeyboard = true,
            globalOverlapEnabled = true,
            clicksOverlapMode = SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY
        )

        assertEquals(AccidentalKeyPressFilter.OverlapRule.ALL, configuration.overlapRule)
    }

    @Test
    fun clicksRuleDoesNotAffectOtherPhysicalKeyboards() {
        val configuration = configuration(
            isPhysicalKeyboard = true,
            isClicksPowerKeyboard = false,
            clicksOverlapMode = SettingsManager.ClicksOverlappingKeysMode.ALL_NON_MODIFIERS,
            clicksNumberRowMode = SettingsManager.ClicksNumberRowInputMode.IGNORE_ALL
        )

        assertEquals(AccidentalKeyPressFilter.OverlapRule.NONE, configuration.overlapRule)
        assertEquals(
            AccidentalKeyPressFilter.NumberRowPolicy(),
            configuration.numberRowPolicy
        )
    }

    @Test
    fun numberRowModesOnlyAddTheirSpecifiedRestriction() {
        assertEquals(
            AccidentalKeyPressFilter.OverlapRule.ADJACENT,
            configuration(
                isClicksPowerKeyboard = true,
                clicksNumberRowMode =
                    SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ADJACENT_KEY_HELD
            ).numberRowPolicy.overlapMinimum
        )
        assertEquals(
            AccidentalKeyPressFilter.OverlapRule.ALL,
            configuration(
                isClicksPowerKeyboard = true,
                clicksNumberRowMode =
                    SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ANY_KEY_HELD
            ).numberRowPolicy.overlapMinimum
        )
        assertEquals(
            AccidentalKeyPressFilter.NumberRowAcceptance.LONG_PRESS,
            configuration(
                isClicksPowerKeyboard = true,
                clicksNumberRowMode = SettingsManager.ClicksNumberRowInputMode.LONG_PRESS
            ).numberRowPolicy.acceptance
        )
        assertEquals(
            AccidentalKeyPressFilter.NumberRowAcceptance.NEVER,
            configuration(
                isClicksPowerKeyboard = true,
                clicksNumberRowMode = SettingsManager.ClicksNumberRowInputMode.IGNORE_ALL
            ).numberRowPolicy.acceptance
        )
    }

    @Test
    fun noAdditionalNumberRulePreservesGeneralClicksRule() {
        val configuration = configuration(
            isClicksPowerKeyboard = true,
            clicksOverlapMode = SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY,
            clicksNumberRowMode = SettingsManager.ClicksNumberRowInputMode.NORMAL
        )

        assertEquals(AccidentalKeyPressFilter.OverlapRule.ADJACENT, configuration.overlapRule)
        assertEquals(
            AccidentalKeyPressFilter.NumberRowPolicy(),
            configuration.numberRowPolicy
        )
    }

    @Test
    fun numberRowRepeatToggleOnlyAffectsClicksPowerKeyboard() {
        assertEquals(
            false,
            configuration(
                isClicksPowerKeyboard = true,
                clicksNumberRowRepeatEnabled = false
            ).numberRowRepeatEnabled
        )
        assertEquals(
            true,
            configuration(
                isClicksPowerKeyboard = false,
                clicksNumberRowRepeatEnabled = false
            ).numberRowRepeatEnabled
        )
    }

    private fun configuration(
        isPhysicalKeyboard: Boolean = true,
        isClicksPowerKeyboard: Boolean = false,
        globalOverlapEnabled: Boolean = false,
        clicksOverlapMode: SettingsManager.ClicksOverlappingKeysMode =
            SettingsManager.ClicksOverlappingKeysMode.OFF,
        clicksNumberRowMode: SettingsManager.ClicksNumberRowInputMode =
            SettingsManager.ClicksNumberRowInputMode.NORMAL,
        clicksNumberRowRepeatEnabled: Boolean = true
    ) = AccidentalKeyPressPolicy.configuration(
        isPhysicalKeyboard = isPhysicalKeyboard,
        isClicksPowerKeyboard = isClicksPowerKeyboard,
        globalOverlapEnabled = globalOverlapEnabled,
        clicksOverlapMode = clicksOverlapMode,
        clicksNumberRowMode = clicksNumberRowMode,
        clicksNumberRowRepeatEnabled = clicksNumberRowRepeatEnabled,
        longPressThresholdMs = 500L
    )
}
