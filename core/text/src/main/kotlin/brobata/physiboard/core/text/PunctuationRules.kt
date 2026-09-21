package brobata.physiboard.core.text

/**
 * The double-space-to-period timer: measured between Space key-downs, not between committed
 * characters, since a suppressed second space (SS6.1) still has to start or continue the window.
 * spec: text-input.md SS6.7, SS13 (500 ms).
 */
data class DoubleSpaceTimer(private val lastSpaceDownAtMs: Long? = null) {
    /** Any other key resets the timer outright. */
    fun reset(): DoubleSpaceTimer = DoubleSpaceTimer(null)

    /** Whether a Space key-down at [nowMs] falls inside the window opened by the previous one. */
    fun isSecondPress(nowMs: Long, windowMs: Long = 500): Boolean =
        lastSpaceDownAtMs != null && nowMs - lastSpaceDownAtMs < windowMs

    /** Records this Space key-down as the new window anchor. */
    fun recordSpaceDown(nowMs: Long): DoubleSpaceTimer = DoubleSpaceTimer(nowMs)
}

/** The result of evaluating double-space-to-period for one Space press. spec: text-input.md SS6.7. */
sealed class DoubleSpacePeriodOutcome {
    /** Converts the trailing space(s) to ". " with this batch edit. */
    data class Fires(val ops: List<EditorOp>) : DoubleSpacePeriodOutcome()

    /**
     * The space count matched a genuine double-space, but the text already ends in sentence
     * punctuation before the space(s), so no period is inserted. spec: text-input.md SS6.7 ("Does
     * not fire if the text already ends in sentence punctuation") and the edge-case table row
     * "Double Space after 'hello.' -> 'hello.  ' (second space normal)": unlike an ordinary
     * standalone second space, this one is *not* suppressed by the trailing-space rule, so the
     * caller commits a second, perfectly normal space here rather than nothing (spec test T3).
     */
    object BlockedBySentenceEnd : DoubleSpacePeriodOutcome()

    /** Not a double-space attempt at all (feature off, outside the window, or wrong space count): the ordinary Space path runs. */
    object NotDue : DoubleSpacePeriodOutcome()
}

/** spec: text-input.md SS6.7 (`double_space_to_period`). */
object DoubleSpacePeriod {

    /**
     * [textBeforeCursor] is up to 100 characters before the cursor, read fresh for this press.
     * [isSecondPressWithinWindow] comes from [DoubleSpaceTimer.isSecondPress]. An auto-space
     * already pending satisfies the "one space" requirement the same way a plain typed trailing
     * space does, since the field can hold at most one trailing space at a time either way.
     */
    fun apply(enabled: Boolean, textBeforeCursor: String, isSecondPressWithinWindow: Boolean): DoubleSpacePeriodOutcome {
        if (!enabled || !isSecondPressWithinWindow) return DoubleSpacePeriodOutcome.NotDue
        val trimmed = textBeforeCursor.trimEnd(' ')
        val trailingSpaces = textBeforeCursor.length - trimmed.length
        if (trailingSpaces != 1) return DoubleSpacePeriodOutcome.NotDue
        if (WordChars.endsSentence(trimmed)) return DoubleSpacePeriodOutcome.BlockedBySentenceEnd
        return DoubleSpacePeriodOutcome.Fires(listOf(EditorOp.DeleteSurrounding(trailingSpaces, 0), EditorOp.CommitText(". ")))
    }
}

/** spec: text-input.md SS6.8 (`spaced_hyphen_to_en_dash`, `spaced_hyphen_dash_style`). */
object SpacedHyphenDash {

    /**
     * [textBeforeCursor] is up to 100 characters before the cursor, ending in the current line
     * only (no characters from a previous line, so a list marker "- " at true line start is never
     * mistaken for a spaced hyphen from an earlier line).
     */
    fun apply(textBeforeCursor: String, style: DashStyle): List<EditorOp>? {
        if (!textBeforeCursor.endsWith(" -")) return null
        val beforePair = textBeforeCursor.dropLast(2)
        val currentLine = beforePair.substringAfterLast('\n')
        if (currentLine.isBlank()) return null
        return listOf(EditorOp.DeleteSurrounding(1, 0), EditorOp.CommitText("${style.char} "))
    }
}

/** spec: text-input.md SS6.5 (`french_punctuation_spacing`, `french_punctuation_only_french`). */
object FrenchSpacing {
    private val SPACE_CHARS = charArrayOf(' ', ' ', ' ')

    /** Whether the feature applies at all, given the primary input language. spec: SS6.5. */
    fun isEnabled(settings: SpacingSettings, isPrimaryLanguageFrench: Boolean): Boolean =
        settings.frenchPunctuationSpacing && (!settings.frenchPunctuationOnlyFrench || isPrimaryLanguageFrench)

    /** [mark] must be one of `? ! ; :`. Returns null when there is nothing to attach the mark to. */
    fun apply(textBeforeCursor: String, mark: Char): List<EditorOp>? {
        require(mark in "?!;:") { "French spacing only applies to ? ! ; :, got '$mark'" }
        var end = textBeforeCursor.length
        while (end > 0 && textBeforeCursor[end - 1] in SPACE_CHARS) end--
        val spacesRemoved = textBeforeCursor.length - end
        val remaining = textBeforeCursor.substring(0, end)
        if (remaining.isEmpty() || remaining.last().isWhitespace()) return null
        return listOf(EditorOp.DeleteSurrounding(spacesRemoved, 0), EditorOp.CommitText(" $mark"))
    }
}

/** spec: text-input.md SS6.4 (`comma_space`). */
object CommaSpace {

    /**
     * [textBeforeCursor] is up to 16 characters before the cursor. Covers both invocation shapes
     * the spec describes: before a comma is committed on the Alt layer (the comma is not yet in
     * [textBeforeCursor]) and after a boundary comma has already been committed (it is the last
     * non-space character). Returns an empty list when the text already reads "&lt;word&gt;, "
     * and nothing needs to change (the caller still marks the auto-space flag).
     */
    fun apply(textBeforeCursor: String): List<EditorOp> {
        val trimmed = textBeforeCursor.trimEnd(' ')
        val trailingSpaces = textBeforeCursor.length - trimmed.length
        val commaAlreadyCommitted = trimmed.endsWith(",")
        if (commaAlreadyCommitted) {
            if (trailingSpaces == 1) return emptyList()
            val beforeComma = trimmed.dropLast(1)
            val spacesBeforeComma = beforeComma.length - beforeComma.trimEnd(' ').length
            val deleteCount = trailingSpaces + spacesBeforeComma + 1
            return listOf(EditorOp.DeleteSurrounding(deleteCount, 0), EditorOp.CommitText(", "))
        }
        return if (trailingSpaces == 0) {
            listOf(EditorOp.CommitText(", "))
        } else {
            listOf(EditorOp.DeleteSurrounding(trailingSpaces, 0), EditorOp.CommitText(", "))
        }
    }
}

/**
 * The look-back that decides whether a straight double quote closes an open one. spec: text-
 * input.md SS6.3 ("An opening quote is one at line start or preceded by whitespace, an opening
 * bracket, a guillemet, a low or high curly quote, or a dash") and SS6.10 (the same rule, reused
 * for smart quotes).
 */
object QuoteScan {
    private const val OPENER_CHARS = "([{«»‘’“”„‚-–—"

    private fun isOpener(precedingChar: Char?): Boolean =
        precedingChar == null || precedingChar.isWhitespace() || precedingChar in OPENER_CHARS

    /**
     * Whether [lineBeforeCursor] (the current line, up to 240 characters, quote not yet committed)
     * holds an unclosed opening quote. A quote attached to a word ("Erster Versuch" mid-sentence)
     * is neither an opener nor a closer: it is skipped entirely, as SS6.3's example requires.
     */
    fun hasUnclosedOpeningQuote(lineBeforeCursor: String): Boolean {
        var open = false
        for (i in lineBeforeCursor.indices) {
            if (lineBeforeCursor[i] != '"') continue
            if (open) {
                open = false
            } else if (isOpener(lineBeforeCursor.getOrNull(i - 1))) {
                open = true
            }
        }
        return open
    }

    /**
     * For smart quotes (SS6.10): finds the span between the nearest preceding quote and the
     * closing quote already at the end of [textBeforeCursor], when that preceding quote is an
     * eligible opener and the span holds no newline and is not blank. Returns the 0-based index of
     * the opening quote, or null when no rewrite applies.
     */
    fun findMatchingOpenQuote(textBeforeCursor: String): Int? {
        if (!textBeforeCursor.endsWith("\"")) return null
        val closeIndex = textBeforeCursor.length - 1
        var openIndex = -1
        for (i in closeIndex - 1 downTo 0) {
            if (textBeforeCursor[i] == '"') {
                openIndex = i
                break
            }
        }
        if (openIndex < 0) return null
        if (!isOpener(textBeforeCursor.getOrNull(openIndex - 1))) return null
        val span = textBeforeCursor.substring(openIndex + 1, closeIndex)
        if (span.isBlank() || '\n' in span) return null
        return openIndex
    }
}

/** spec: text-input.md SS6.10 (`smart_quotes`, `smart_quotes_style`). */
object SmartQuotes {
    private const val DELIMITERS = "-–—.,;:!?)]}»›"

    /** A trailing delimiter is whitespace or one of `- – — . , ; : ! ? ) ] } » ›`. */
    fun isDelimiter(ch: Char): Boolean = ch.isWhitespace() || ch in DELIMITERS

    /**
     * [textBeforeCursor] should hold up to 240 characters; [delimiter] has not been committed yet.
     * Returns null when [delimiter] is not a delimiter, the text does not end in a straight double
     * quote, or [QuoteScan.findMatchingOpenQuote] finds no eligible opener.
     */
    fun apply(textBeforeCursor: String, delimiter: Char, style: SmartQuoteStyle): List<EditorOp>? {
        if (!isDelimiter(delimiter)) return null
        val openIndex = QuoteScan.findMatchingOpenQuote(textBeforeCursor) ?: return null
        val closeIndex = textBeforeCursor.length - 1
        val span = textBeforeCursor.substring(openIndex + 1, closeIndex)
        val wholeSpanLength = closeIndex - openIndex + 1
        val replacement = style.open + span + style.close + delimiter
        return listOf(EditorOp.DeleteSurrounding(wholeSpanLength, 0), EditorOp.CommitText(replacement))
    }
}

/** spec: text-input.md SS6.3, the "Remove before" (`auto_space_punctuation`) list and its special cases. */
object AutoSpaceReplacement {

    /**
     * Evaluated only for an Alt-layer punctuation or digit commit while an auto-space is
     * [pending]; a plain letter leaves the flag untouched and should not call this function at
     * all (spec: "Letters do not clear it"). [twoCharsBeforeCursor] is the two characters
     * immediately before the cursor (fewer if the field holds less). [hasUnclosedOpeningQuote] is
     * only consulted when [typed] is `"`; compute it with [QuoteScan.hasUnclosedOpeningQuote] over
     * the current line, up to 240 characters, excluding the pending space itself.
     *
     * Returns null when [typed] should be committed normally with no further edit (the flag
     * still clears): not in the list, a bracket that is not in the list, or a quote with no open
     * partner. Returns the batch edit when the space is actually replaced.
     */
    fun apply(
        pending: Boolean,
        typed: Char,
        twoCharsBeforeCursor: String,
        removeBeforeList: String,
        hasUnclosedOpeningQuote: Boolean,
    ): List<EditorOp>? {
        if (!pending) return null
        if (typed !in removeBeforeList) return null
        if (typed == '"' && !hasUnclosedOpeningQuote) return null
        val matches = twoCharsBeforeCursor.length == 2 &&
            twoCharsBeforeCursor[0].isLetterOrDigit() &&
            twoCharsBeforeCursor[1] == ' '
        if (!matches) return null
        return listOf(EditorOp.DeleteSurrounding(1, 0), EditorOp.CommitText("$typed "))
    }
}
