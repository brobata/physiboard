package it.palsoftware.pastiera.inputmethod.aospkeyboard

import android.view.KeyEvent

internal object SoftwareKeyboardSymLabels {
    fun buildContentByChar(
        page: Int,
        rows: List<String>,
        symMappings: Map<Int, String>,
        layoutName: String
    ): Map<Char, String> {
        val contentByChar = linkedMapOf<Char, String>()
        val usedContent = symMappings.values.toMutableSet()
        val supplementalContent = supplementalContentPool(page)
            .filterNot { it in usedContent }
            .iterator()

        rows.flatMap { it.toList() }.distinct().forEach { char ->
            val keyCode = keyCodeForChar(char, layoutName)
            val isSupplementalLetter = char.isLetter() && char.lowercaseChar() !in 'a'..'z'
            val content = if (isSupplementalLetter) {
                supplementalContent.nextOrNull()
            } else {
                keyCode?.let { symMappings[it] }
            } ?: symbolFallback(char)

            if (content.isNotEmpty()) {
                contentByChar[char] = content
                usedContent += content
            }
        }
        return contentByChar
    }

    fun keyCodeForChar(char: Char, layoutName: String): Int? {
        val normalizedChar = char.lowercaseChar()
        if (SoftwareKeyboardLayoutTemplates.familyFor(layoutName) == SoftwareKeyboardLayoutTemplates.Family.QWERTZ) {
            when (normalizedChar) {
                'z' -> return KeyEvent.KEYCODE_Y
                'y' -> return KeyEvent.KEYCODE_Z
            }
        }
        return keyCodeForQwertyPosition(normalizedChar)
    }

    private fun keyCodeForQwertyPosition(char: Char): Int? = when (char) {
        'q' -> KeyEvent.KEYCODE_Q
        'w' -> KeyEvent.KEYCODE_W
        'e' -> KeyEvent.KEYCODE_E
        'r' -> KeyEvent.KEYCODE_R
        't' -> KeyEvent.KEYCODE_T
        'y' -> KeyEvent.KEYCODE_Y
        'u', 'ü', 'ù' -> KeyEvent.KEYCODE_U
        'i' -> KeyEvent.KEYCODE_I
        'o', 'ö' -> KeyEvent.KEYCODE_O
        'p' -> KeyEvent.KEYCODE_P
        'a', 'ä' -> KeyEvent.KEYCODE_A
        's' -> KeyEvent.KEYCODE_S
        'd' -> KeyEvent.KEYCODE_D
        'f' -> KeyEvent.KEYCODE_F
        'g' -> KeyEvent.KEYCODE_G
        'h' -> KeyEvent.KEYCODE_H
        'j' -> KeyEvent.KEYCODE_J
        'k' -> KeyEvent.KEYCODE_K
        'l' -> KeyEvent.KEYCODE_L
        'z' -> KeyEvent.KEYCODE_Z
        'x' -> KeyEvent.KEYCODE_X
        'c' -> KeyEvent.KEYCODE_C
        'v' -> KeyEvent.KEYCODE_V
        'b' -> KeyEvent.KEYCODE_B
        'n' -> KeyEvent.KEYCODE_N
        'm' -> KeyEvent.KEYCODE_M
        ',' -> KeyEvent.KEYCODE_COMMA
        '.' -> KeyEvent.KEYCODE_PERIOD
        else -> null
    }

    private fun supplementalContentPool(page: Int): List<String> =
        when (page) {
            1 -> listOf("🙃", "🧐", "🍎", "☔", "✨", "⭐", "✅", "🔥", "💡", "🎯")
            2 -> listOf("€", "@", "#", "£", "¥", "§", "±", "×", "÷", "≈", "…")
            else -> emptyList()
        }

    private fun symbolFallback(char: Char): String =
        when {
            char.isLetterOrDigit() -> ""
            else -> char.toString()
        }

    private fun <T> Iterator<T>.nextOrNull(): T? =
        if (hasNext()) next() else null
}
