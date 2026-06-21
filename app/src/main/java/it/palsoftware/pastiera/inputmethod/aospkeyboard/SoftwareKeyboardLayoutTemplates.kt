package it.palsoftware.pastiera.inputmethod.aospkeyboard

internal object SoftwareKeyboardLayoutTemplates {
    enum class Family { QWERTY, QWERTZ, AZERTY }

    fun familyFor(layout: String): Family =
        when (layout) {
            "qwertz", "german_multitap_qwertz" -> Family.QWERTZ
            "azerty" -> Family.AZERTY
            else -> Family.QWERTY
        }

    fun rowTemplateFor(
        layout: String,
        style: AospKeyboardView.SoftwareLayoutStyle
    ): List<String> = rowTemplateFor(familyFor(layout), style)

    fun rowTemplateFor(
        family: Family,
        style: AospKeyboardView.SoftwareLayoutStyle
    ): List<String> =
        when (style) {
            AospKeyboardView.SoftwareLayoutStyle.COMPACT -> when (family) {
                Family.QWERTY -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
                Family.QWERTZ -> listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
                Family.AZERTY -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")
            }
            AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO -> when (family) {
                Family.QWERTY -> listOf("qwertyuiop[", "asdfghjkl;'", "zxcvbnm")
                Family.QWERTZ -> listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
                Family.AZERTY -> listOf("azertyuiop^", "qsdfghjklmù", "wxcvbn'")
            }
            AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI -> when (family) {
                Family.QWERTY -> listOf("qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./")
                Family.QWERTZ -> listOf("qwertzuiop[]", "asdfghjkl;'", "yxcvbnm,./")
                Family.AZERTY -> listOf("azertyuiop[]", "qsdfghjklm'", "wxcvbn,;:!")
            }
            AospKeyboardView.SoftwareLayoutStyle.FULL_ISO -> when (family) {
                Family.QWERTY -> listOf("qwertyuiop[]", "asdfghjkl;'#", "\\zxcvbnm,./")
                Family.QWERTZ -> listOf("qwertzuiopü+", "asdfghjklöä#", "<yxcvbnm,.-")
                Family.AZERTY -> listOf("azertyuiop^$", "qsdfghjklmù*", "<wxcvbn,;:!")
            }
        }
}
