package it.palsoftware.pastiera.inputmethod.expansion

import java.util.Locale

class TextExpansionEngine {
    companion object {
        const val MAX_CONTEXT_CHARS = 256
        private const val MAX_SHORTCUT_CHARS = 40
        private val SHORTCUT_REGEX = Regex("[a-zA-Z0-9_]+")
        private val TOKEN_BOUNDARIES = setOf('(', '[', '{', '"', '\'')

        fun isValidSnippetShortcut(value: String): Boolean =
            value.length in 1..MAX_SHORTCUT_CHARS && value.matches(SHORTCUT_REGEX)

        fun isValidSnippetPrefix(value: String): Boolean {
            if (value.length != 1) return false
            val character = value[0]
            return !character.isWhitespace() && !character.isLetterOrDigit() && character != ':'
        }
    }

    fun snippetQuery(textBeforeCursor: String, prefix: Char): ExpansionQuery? {
        val prefixIndex = textBeforeCursor.lastIndexOf(prefix)
        if (prefixIndex < 0 || !hasBoundaryBefore(textBeforeCursor, prefixIndex)) return null
        val shortcut = textBeforeCursor.substring(prefixIndex + 1)
        if (shortcut.isNotEmpty() && !isValidSnippetShortcut(shortcut)) return null
        return ExpansionQuery(
            token = textBeforeCursor.substring(prefixIndex),
            prefix = prefix.toString(),
            typedShortcut = shortcut.lowercase(Locale.ROOT),
            tokenStart = prefixIndex,
            closed = false
        )
    }

    fun shortcodeQuery(textBeforeCursor: String): ExpansionQuery? {
        val colonIndex = textBeforeCursor.lastIndexOf(':')
        if (colonIndex < 0) return null
        val trailingColon = colonIndex == textBeforeCursor.lastIndex && colonIndex > 0
        val openingIndex = if (trailingColon) textBeforeCursor.lastIndexOf(':', colonIndex - 1) else colonIndex
        if (openingIndex < 0 || !hasBoundaryBefore(textBeforeCursor, openingIndex)) return null
        val end = if (trailingColon) colonIndex else textBeforeCursor.length
        val shortcut = textBeforeCursor.substring(openingIndex + 1, end)
        if (shortcut.isEmpty() || !isValidSnippetShortcut(shortcut)) return null
        return ExpansionQuery(
            token = textBeforeCursor.substring(openingIndex),
            prefix = ":",
            typedShortcut = shortcut.lowercase(Locale.ROOT),
            tokenStart = openingIndex,
            closed = trailingColon
        )
    }

    fun collect(query: ExpansionQuery, sources: List<ExpansionSource>, limit: Int = 10): List<ExpansionMatch> =
        sources.asSequence()
            .flatMap { it.matches(query, limit).asSequence() }
            .sortedWith(compareBy<ExpansionMatch> { it.shortcut.length }.thenBy { it.shortcut })
            .take(limit)
            .toList()

    fun uniqueExact(query: ExpansionQuery, matches: List<ExpansionMatch>): ExpansionMatch? {
        val exact = matches.filter { it.shortcut.equals(query.typedShortcut, ignoreCase = true) }
        return exact.singleOrNull()
    }

    private fun hasBoundaryBefore(text: String, index: Int): Boolean {
        if (index == 0) return true
        val previous = text[index - 1]
        return previous.isWhitespace() || previous in TOKEN_BOUNDARIES
    }
}
