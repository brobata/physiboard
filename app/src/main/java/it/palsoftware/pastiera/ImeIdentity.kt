package it.palsoftware.pastiera

object ImeIdentity {
    val packageName: String = BuildConfig.APPLICATION_ID
    val serviceClassName: String = "$packageName.inputmethod.PhysicalKeyboardInputMethodService"
    val imeId: String = "$packageName/$serviceClassName"
    private val shortImeId: String = "$packageName/.inputmethod.PhysicalKeyboardInputMethodService"

    fun matchesImeId(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value == imeId || value == shortImeId
    }
}
