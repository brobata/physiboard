package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.SettingsManager

/** Pure settings-to-filter policy. Rules may strengthen one another, never weaken. */
internal object AccidentalKeyPressPolicy {
    fun configuration(
        isPhysicalKeyboard: Boolean,
        isClicksPowerKeyboard: Boolean,
        globalOverlapEnabled: Boolean,
        clicksOverlapMode: SettingsManager.ClicksOverlappingKeysMode,
        clicksNumberRowMode: SettingsManager.ClicksNumberRowInputMode,
        clicksNumberRowRepeatEnabled: Boolean,
        longPressThresholdMs: Long
    ): AccidentalKeyPressFilter.Configuration {
        val overlapRule = when {
            isPhysicalKeyboard && globalOverlapEnabled -> AccidentalKeyPressFilter.OverlapRule.ALL
            isClicksPowerKeyboard -> when (clicksOverlapMode) {
                SettingsManager.ClicksOverlappingKeysMode.OFF ->
                    AccidentalKeyPressFilter.OverlapRule.NONE
                SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY ->
                    AccidentalKeyPressFilter.OverlapRule.ADJACENT
                SettingsManager.ClicksOverlappingKeysMode.ALL_NON_MODIFIERS ->
                    AccidentalKeyPressFilter.OverlapRule.ALL
            }
            else -> AccidentalKeyPressFilter.OverlapRule.NONE
        }
        val numberRowPolicy = if (isClicksPowerKeyboard) {
            when (clicksNumberRowMode) {
                SettingsManager.ClicksNumberRowInputMode.NORMAL ->
                    AccidentalKeyPressFilter.NumberRowPolicy()
                SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ADJACENT_KEY_HELD ->
                    AccidentalKeyPressFilter.NumberRowPolicy(
                        overlapMinimum = AccidentalKeyPressFilter.OverlapRule.ADJACENT
                    )
                SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ANY_KEY_HELD ->
                    AccidentalKeyPressFilter.NumberRowPolicy(
                        overlapMinimum = AccidentalKeyPressFilter.OverlapRule.ALL
                    )
                SettingsManager.ClicksNumberRowInputMode.LONG_PRESS ->
                    AccidentalKeyPressFilter.NumberRowPolicy(
                        acceptance = AccidentalKeyPressFilter.NumberRowAcceptance.LONG_PRESS
                    )
                SettingsManager.ClicksNumberRowInputMode.IGNORE_ALL ->
                    AccidentalKeyPressFilter.NumberRowPolicy(
                        acceptance = AccidentalKeyPressFilter.NumberRowAcceptance.NEVER
                    )
            }
        } else {
            AccidentalKeyPressFilter.NumberRowPolicy()
        }
        return AccidentalKeyPressFilter.Configuration(
            overlapRule = overlapRule,
            numberRowPolicy = numberRowPolicy,
            longPressThresholdMs = longPressThresholdMs,
            numberRowRepeatEnabled = !isClicksPowerKeyboard || clicksNumberRowRepeatEnabled
        )
    }
}
