package brobata.physiboard.core.dict

/**
 * The result of a successful substitution match. [matchedText] is the exact text that matched
 * (case as typed); [replacement] already has the trigger's casing applied and is ready to
 * insert in place of it. spec: autocorrect-suggestions.md §8.3.
 */
data class SubstitutionMatch(
    val ruleSetCode: String,
    val trigger: String,
    val matchedText: String,
    val replacement: String,
)

/**
 * The pure matching logic behind text replacements: given the text immediately before the
 * cursor (including the boundary key just typed) and the rule sets currently enabled, finds
 * the one rule that applies, in the precedence order the spec defines: a symbol trigger first,
 * then a two-word sequence, then the last word alone; within each step [ruleSets] is searched
 * in the order given, so a caller wanting "custom before bundled" passes the custom set first.
 * spec: autocorrect-suggestions.md §8.1-§8.3.
 *
 * Persistence, the settings screens, the add-substitution sheet and session-only rejection
 * memory (§7.5, which the spec's step 4 also gestures at with "a rejected trigger is skipped")
 * are all out of scope: rejection is external, mutable, per-session state, and a caller can
 * apply it by leaving a just-rejected trigger out of [ruleSets] on the next call.
 */
object SubstitutionMatcher {

    // SPEC GAP: §8.3 step 1 says a symbol trigger is one containing "any non-alphanumeric
    // character", which taken literally would also catch the space inside a two-word
    // trigger like "cos e" (step 3's own category). A trigger is classified as a symbol
    // trigger here only when it has a character that is neither a letter/digit nor a plain
    // space, so word-sequence triggers are left for matchTwoWordSequence as step 3 clearly
    // intends.
    private const val BOUNDARY_PUNCTUATION = ".,;:!?()[]{}\\/\""
    private val CURLY_APOSTROPHES = charArrayOf('\'', '’', '‘', 'ʼ')

    /**
     * Finds the applying rule, or null when none does. [isKnownWord] backs the guard on a
     * last-word match (§8.3 step 4): this implementation enforces it (the Keep/Drop decision
     * in autocorrect-suggestions.md §18 calls for the guard to be "actually enforced", since in
     * 2.x an always-non-empty enabled-language list made it a permanent no-op).
     */
    fun match(textBeforeCursor: String, ruleSets: List<RuleSet>, isKnownWord: (String) -> Boolean): SubstitutionMatch? {
        matchSymbolTrigger(textBeforeCursor, ruleSets)?.let { return it }

        val trimmed = trimTrailingBoundary(textBeforeCursor) ?: return null
        matchTwoWordSequence(trimmed, ruleSets)?.let { return it }
        return matchLastWord(trimmed, ruleSets, isKnownWord)
    }

    private fun matchSymbolTrigger(text: String, ruleSets: List<RuleSet>): SubstitutionMatch? {
        val trimmedEnd = text.trimEnd { it.isWhitespace() }
        var best: SubstitutionMatch? = null
        for (ruleSet in ruleSets) {
            for ((trigger, replacement) in ruleSet.rules) {
                if (!isSymbolTrigger(trigger)) continue
                if (!trimmedEnd.endsWith(trigger, ignoreCase = true)) continue
                val start = trimmedEnd.length - trigger.length
                val before = trimmedEnd.getOrNull(start - 1)
                if (before != null && !isBoundary(before)) continue
                if (best != null && trigger.length <= best.trigger.length) continue
                val matchedText = trimmedEnd.substring(start)
                best = SubstitutionMatch(ruleSet.code, trigger, matchedText, applyCasing(matchedText, replacement))
            }
        }
        return best
    }

    /**
     * Trims a trailing run of whitespace and boundary punctuation. spec: autocorrect-
     * suggestions.md §8.3 step 2 ("if anything trimmed was neither whitespace nor boundary
     * punctuation ... no rule applies").
     *
     * SPEC GAP: the spec does not say precisely which character the "anything trimmed"
     * check inspects when nothing is trimmed at all (an emoji boundary, say). This treats
     * the very last character of the text as the boundary under test: when it is neither
     * whitespace nor boundary punctuation, matching is abandoned outright, rather than
     * silently falling through to word-based matching on whatever precedes it.
     */
    private fun trimTrailingBoundary(text: String): String? {
        if (text.isEmpty()) return null
        val last = text.last()
        if (!(last.isWhitespace() || last in BOUNDARY_PUNCTUATION)) return null
        var end = text.length
        while (end > 0) {
            val ch = text[end - 1]
            if (ch.isWhitespace() || ch in BOUNDARY_PUNCTUATION) end-- else break
        }
        return if (end == 0) null else text.substring(0, end)
    }

    private fun matchTwoWordSequence(text: String, ruleSets: List<RuleSet>): SubstitutionMatch? {
        val ws = words(text)
        if (ws.size < 2) return null
        val sequence = "${ws[ws.size - 2]} ${ws[ws.size - 1]}"
        val lookupKey = sequence.lowercase()
        for (ruleSet in ruleSets) {
            val replacement = ruleSet.rules[lookupKey] ?: continue
            return SubstitutionMatch(ruleSet.code, lookupKey, sequence, applyCasing(sequence, replacement))
        }
        return null
    }

    private fun matchLastWord(text: String, ruleSets: List<RuleSet>, isKnownWord: (String) -> Boolean): SubstitutionMatch? {
        val lastWord = words(text).lastOrNull() ?: return null
        val lookupKey = lastWord.lowercase()
        for (ruleSet in ruleSets) {
            val replacement = ruleSet.rules[lookupKey] ?: continue
            val guardPasses = !isKnownWord(lastWord) || onlyDiffersInAccentCaseOrPunctuation(lastWord, replacement)
            if (!guardPasses) continue
            return SubstitutionMatch(ruleSet.code, lookupKey, lastWord, applyCasing(lastWord, replacement))
        }
        return null
    }

    private fun onlyDiffersInAccentCaseOrPunctuation(word: String, replacement: String): Boolean =
        DictNormalization.normalizedKey(word) == DictNormalization.normalizedKey(replacement)

    private fun isSymbolTrigger(trigger: String): Boolean =
        trigger.any { !it.isLetterOrDigit() && it != ' ' }

    private fun isBoundary(ch: Char): Boolean =
        ch.isWhitespace() || ch in BOUNDARY_PUNCTUATION || !ch.isLetterOrDigit()

    private fun isWordChar(text: String, index: Int): Boolean {
        val ch = text[index]
        if (ch.isLetterOrDigit()) return true
        if (ch in CURLY_APOSTROPHES) {
            val prev = text.getOrNull(index - 1)
            val next = text.getOrNull(index + 1)
            return prev != null && prev.isLetterOrDigit() && next != null && next.isLetterOrDigit()
        }
        return false
    }

    /** Splits [text] into words by the same word-character rule as autocorrect-suggestions.md §1.1. */
    private fun words(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (i in text.indices) {
            if (isWordChar(text, i)) {
                current.append(text[i])
            } else if (current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    /**
     * Applies the trigger's casing to [replacement]: all-uppercase (with at least one letter)
     * uppercases the whole replacement; a leading capital capitalizes the replacement's first
     * letter; otherwise the replacement is used as stored. spec: autocorrect-suggestions.md
     * §8.3.
     */
    private fun applyCasing(typed: String, replacement: String): String {
        val letters = typed.filter { it.isLetter() }
        if (letters.isEmpty()) return replacement
        return when {
            letters.all { it.isUpperCase() } -> replacement.uppercase()
            typed.first().isUpperCase() -> capitalizeFirstLetter(replacement)
            else -> replacement
        }
    }

    private fun capitalizeFirstLetter(text: String): String {
        val index = text.indexOfFirst { it.isLetter() }
        if (index < 0) return text
        return text.substring(0, index) + text[index].uppercase() + text.substring(index + 1)
    }
}
