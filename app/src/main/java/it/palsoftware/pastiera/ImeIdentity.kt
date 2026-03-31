package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService

object ImeIdentity {
    val packageName: String = BuildConfig.APPLICATION_ID
    val serviceClassName: String = PhysicalKeyboardInputMethodService::class.java.name
    val imeId: String = imeIdForPackage(packageName)

    fun imeIdForPackage(appPackageName: String): String {
        return "$appPackageName/$serviceClassName"
    }

    private fun shortImeIdForPackage(appPackageName: String): String {
        return "$appPackageName/.inputmethod.PhysicalKeyboardInputMethodService"
    }

    fun matchesImeId(value: String?, appPackageName: String = packageName): Boolean {
        if (value.isNullOrBlank()) return false
        return value == imeIdForPackage(appPackageName) || value == shortImeIdForPackage(appPackageName)
    }
}
