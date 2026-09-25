package brobata.physiboard.core.actions.snippets

/**
 * One user snippet: a shortcut and the text it expands to. spec: expansion-clipboard-pickers-
 * launcher.md SS1 ("Snippet"), SS2.1. [shortcut] is always stored lowercase; the rules that make a
 * shortcut valid live in [SnippetRules] so the editor and the loader apply one set.
 */
data class Snippet(val shortcut: String, val replacement: String)

/**
 * The validity rules every save and every load enforce. spec: SS2.1's table (shortcut
 * characters, length, case; replacement not blank; the prefix's one-character rule).
 */
object SnippetRules {
    const val MAX_SHORTCUT_LENGTH: Int = 40
    const val DEFAULT_PREFIX: Char = '!'

    /** spec SS2.1: ASCII letters, digits and underscore, 1 to 40 characters (T14). */
    fun isValidShortcut(shortcut: String): Boolean =
        shortcut.length in 1..MAX_SHORTCUT_LENGTH && shortcut.all { isShortcutChar(it) }

    fun isShortcutChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

    /** spec SS2.1: "exactly one character that is not whitespace, not a letter or digit, and not a colon" (T13). */
    fun isValidPrefix(prefix: String): Boolean {
        val c = prefix.singleOrNull() ?: return false
        return !c.isWhitespace() && !c.isLetterOrDigit() && c != ':'
    }

    /** The stored prefix, or `!` when the stored value fails the rule (SS2.1, "ignored and `!` is used"). */
    fun effectivePrefix(stored: String?): Char =
        if (stored != null && isValidPrefix(stored)) stored[0] else DEFAULT_PREFIX

    /** spec SS2.1: the editor lowercases and trims the shortcut on save. */
    fun normalizeShortcut(typed: String): String = typed.trim().lowercase()

    /**
     * Applies the load rules to a raw shortcut-to-replacement map: shortcuts lowercased and
     * trimmed, entries that fail the shortcut or blank-replacement rule dropped silently, a later
     * duplicate winning (SS2.1, "Entries that fail the rules on load: dropped silently";
     * "Duplicate shortcut on save: the later value wins").
     */
    fun sanitize(raw: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in raw) {
            val shortcut = normalizeShortcut(k)
            if (!isValidShortcut(shortcut) || v.isBlank()) continue
            out[shortcut] = v
        }
        return out
    }

    fun toSnippets(map: Map<String, String>): List<Snippet> = sanitize(map).map { (k, v) -> Snippet(k, v) }
}

/**
 * The token found before the caret: the prefix plus the shortcut typed after it, up to the caret.
 * spec SS1 ("Token"), SS2.2. [typedShortcut] is already lowercased for matching; [length] is how
 * many characters the token occupies, which is what a commit deletes.
 */
data class SnippetToken(val prefix: Char, val typedShortcut: String, val length: Int) {
    val text: String get() = "$prefix$typedShortcut"
}

/** spec SS2.2: the trigger detector, a pure function of the text before the caret. */
object SnippetTrigger {
    /** spec SS2.2: "the keyboard looks at the last 256 characters before the caret". */
    const val LOOKBACK: Int = 256

    private const val BOUNDARY_BEFORE_PREFIX = "([{\"'"

    /**
     * Finds the trigger token, or null when there is none. The last occurrence of [prefix] in the
     * lookback window must sit at the start of that text or after whitespace or one of `( [ { " '`
     * (so `mail!sig` is not a trigger, `Hello !sig` is), and everything after it must be empty or a
     * valid shortcut (a space ends the candidacy: `!sig ` is no longer a trigger).
     */
    fun detect(textBeforeCaret: String, prefix: Char): SnippetToken? {
        val window = if (textBeforeCaret.length > LOOKBACK) textBeforeCaret.takeLast(LOOKBACK) else textBeforeCaret
        val at = window.lastIndexOf(prefix)
        if (at < 0) return null
        if (at > 0) {
            val before = window[at - 1]
            if (!before.isWhitespace() && before !in BOUNDARY_BEFORE_PREFIX) return null
        }
        val after = window.substring(at + 1)
        if (after.isNotEmpty() && !SnippetRules.isValidShortcut(after)) return null
        return SnippetToken(prefix, after.lowercase(), after.length + 1)
    }
}

/** One snippet the typed shortcut matched, with the label the popup or bar shows. spec SS2.3. */
data class SnippetMatch(val snippet: Snippet) {
    /** spec SS2.3: `shortcut → first line of replacement`, an arrow with spaces around it. */
    val label: String get() = "${snippet.shortcut} → ${snippet.replacement.lineSequence().first()}"
}

/** spec SS2.3: which snippets a typed shortcut matches, in what order, and which one is exact. */
object SnippetMatcher {
    /** spec SS2.3: "cut to 10". */
    const val MAX_MATCHES: Int = 10

    /**
     * Every snippet whose shortcut starts with [typedShortcut], sorted by shortcut length then
     * alphabetically, cut to [MAX_MATCHES]. An empty typed shortcut (only the prefix typed) matches
     * everything, "which is how the whole list can be browsed" (SS2.2, T12).
     */
    fun matches(snippets: List<Snippet>, typedShortcut: String): List<SnippetMatch> =
        snippets.filter { it.shortcut.startsWith(typedShortcut) }
            .sortedWith(compareBy<Snippet> { it.shortcut.length }.thenBy { it.shortcut })
            .take(MAX_MATCHES)
            .map(::SnippetMatch)

    /**
     * The one match whose shortcut equals the typed shortcut, or null when there is none or when
     * two sources would give the same shortcut (SS1 "Exact match", T17: "no exact match when two
     * matches with shortcut `id` come from different sources").
     */
    fun exactMatch(matches: List<SnippetMatch>, typedShortcut: String): SnippetMatch? {
        val exact = matches.filter { it.snippet.shortcut == typedShortcut }
        return exact.singleOrNull()
    }
}
