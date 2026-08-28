package it.palsoftware.pastiera.inputmethod

/** Pure settings-to-filter policy. */
internal object AccidentalKeyPressPolicy {
    fun configuration(
        isPhysicalKeyboard: Boolean,
        globalOverlapEnabled: Boolean
    ): AccidentalKeyPressFilter.Configuration {
        val overlapRule = if (isPhysicalKeyboard && globalOverlapEnabled) {
            AccidentalKeyPressFilter.OverlapRule.ALL
        } else {
            AccidentalKeyPressFilter.OverlapRule.NONE
        }
        return AccidentalKeyPressFilter.Configuration(overlapRule = overlapRule)
    }
}
