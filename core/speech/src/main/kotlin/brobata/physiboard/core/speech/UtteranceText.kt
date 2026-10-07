package brobata.physiboard.core.speech

/**
 * The cursor rule shared by partial display and final capitalisation: "capitalise here" when the
 * text so far is empty, ends a paragraph, or ends a sentence followed by whitespace. spec:
 * dictation.md SS7.1, SS7.5: "cursor at the very start of the document, right after a newline, or
 * right after `.`, `!` or `?` followed by whitespace". Always evaluated against a frozen
 * [UtteranceContext], never a live read (see that type's own KDoc).
 */
internal fun shouldCapitaliseAt(textBefore: String?): Boolean {
    if (textBefore.isNullOrEmpty()) return true
    if (textBefore.endsWith("\n")) return true
    val trimmed = textBefore.trimEnd(' ', '\t')
    if (trimmed.length == textBefore.length) return false // no trailing whitespace to be "after" a boundary
    return trimmed.lastOrNull()?.let { it == '.' || it == '!' || it == '?' } == true
}

private fun titleCaseFirst(text: String): String =
    text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

/**
 * spec: dictation.md SS7.5: the engine capitalises the first word of every result it formats,
 * because to it each utterance is a sentence; mid-sentence (the frozen context does not say
 * "capitalise here") that capital is undone. Only the first word, only when it looks like an
 * ordinary capitalised word (one upper-case letter, then lower-case letters or an apostrophe),
 * and never the pronoun "I" and its contractions. A proper noun opening a segment loses its
 * capital too; accepted, since nothing can tell it from the engine's own.
 */
internal fun lowerEngineSegmentCapital(text: String): String {
    val first = text.takeWhile { !it.isWhitespace() }
    if (first.length < 2) return text
    if (!first[0].isUpperCase()) return text
    if (first[0] == 'I' && (first[1] == '\'' || first[1] == '\u2019')) return text
    if (!first.drop(1).all { it.isLowerCase() || it == '\'' || it == '\u2019' }) return text
    return first[0].lowercaseChar() + text.substring(1)
}

/**
 * spec: dictation.md SS7.1: "a partial gets first-letter capitalisation only... No punctuation-word
 * replacement and no after-sentence capitalisation is applied to partials." Evaluated once, against
 * the utterance's frozen [UtteranceContext], so a second or later partial in the same utterance
 * keeps the same answer a naive re-read would have lost the moment earlier partials of this same
 * utterance were on screen (the fragility SS7.1 itself documents as the reason 2.x's capital was
 * lost on later partials).
 */
object DictationPartialDisplay {
    fun display(rawPartial: String, context: UtteranceContext, settings: DictationTextSettings): String {
        if (rawPartial.isEmpty()) return rawPartial
        if (!settings.capitalizationAllowed) return rawPartial
        if (!shouldCapitaliseAt(context.textBeforeUtterance)) return if (settings.undoEngineSegmentCapitals) lowerEngineSegmentCapital(rawPartial) else rawPartial
        return if (settings.capitalizeFirstLetter) titleCaseFirst(rawPartial) else rawPartial
    }
}

/** spec: dictation.md SS7.5: first-letter and after-sentence-end capitalisation of a finished utterance. */
object DictationCapitalization {
    private val AFTER_SENTENCE_END = Regex("([.!?]\\s+)([a-z])")

    fun apply(text: String, context: UtteranceContext, settings: DictationTextSettings): String {
        if (text.isEmpty() || !settings.capitalizationAllowed) return text
        var result = text
        if (shouldCapitaliseAt(context.textBeforeUtterance)) {
            if (settings.capitalizeFirstLetter) result = titleCaseFirst(result)
        } else if (settings.undoEngineSegmentCapitals) {
            result = lowerEngineSegmentCapital(result)
        }
        if (settings.capitalizeAfterSentenceEnd) {
            result = AFTER_SENTENCE_END.replace(result) { it.groupValues[1] + it.groupValues[2].uppercase() }
        }
        return result
    }
}

/**
 * spec: dictation.md SS7.6: a leading space if the frozen context ends in a letter (digits and
 * punctuation do not count), and always a trailing space. Reading [UtteranceContext] rather than
 * the live field is what removes the leading-space quirk SS7.6 itself documents ("the 10-character
 * read before the cursor includes the composing partial, whose last character is a letter"): the
 * frozen snapshot never contains this utterance's own partial, so it cannot trigger that.
 */
object DictationSpacing {
    private const val LOOKBACK = 10

    fun apply(text: String, context: UtteranceContext): String {
        val leadingSpace = context.textBeforeUtterance?.takeLast(LOOKBACK)?.lastOrNull()?.isLetter() == true
        return (if (leadingSpace) " " else "") + text + " "
    }
}

/**
 * spec: dictation.md SS7.3, "Finishing the utterance with text T": capitalise, then space, then
 * write. The punctuation-word table (SS7.4) is dropped for 3.0 per rebuild-from-scratch.md SS17
 * ("Google punctuates itself; the table is substring-based and English-less").
 */
object UtteranceFinisher {
    /** [plainText] is the transformed text with no ops attached, carried forward so the next utterance's [UtteranceContext] can be computed by simple append rather than a fresh read. */
    data class Finished(val ops: List<DictationTextOp>, val plainText: String?)

    val NOTHING = Finished(emptyList(), null)

    fun finish(rawText: String, context: UtteranceContext, settings: DictationTextSettings): Finished {
        val capitalized = DictationCapitalization.apply(rawText, context, settings)
        val spaced = DictationSpacing.apply(capitalized, context)
        return Finished(ops = listOf(DictationTextOp.SetComposingText(spaced), DictationTextOp.FinishComposing), plainText = spaced)
    }
}

/**
 * spec: dictation.md SS7.2: whether two hypotheses inside one request are the same utterance
 * still in progress or a fresh one starting. "Either string is empty; or one is a case-insensitive
 * prefix of the other; or their first words (up to the first space) are equal ignoring case."
 *
 * spec: rebuild-from-scratch.md SS17 marks this rule "undecided" for 3.0 ("written for a
 * pre-segmented world; check whether Google still restarts hypotheses inside one request"), so
 * [DictationEngine] does not wire this into the session yet; it is provided here, tested against
 * the spec's own rows, as the primitive a future decision would use rather than rediscover.
 * SPEC GAP: left unwired pending that device check.
 */
object SameUtteranceCheck {
    fun isSameUtterance(previous: String, next: String): Boolean {
        val a = previous.trim()
        val b = next.trim()
        if (a.isEmpty() || b.isEmpty()) return true
        if (a.startsWith(b, ignoreCase = true) || b.startsWith(a, ignoreCase = true)) return true
        return a.substringBefore(' ').equals(b.substringBefore(' '), ignoreCase = true)
    }
}

/**
 * spec: dictation.md SS7.3: whether a final (or segment) that arrives while a partial is still
 * composing is that partial's own final, or a different utterance whose own partial the engine
 * never showed. Google's continuous session on the Titan dropped one utterance's delivery and the
 * next final arrived with a different partial on screen (D24); replacing the composing region
 * with it threw the partial's words away. A partial of three words or more, fewer than half of
 * whose words appear in the final, is a different utterance and is committed first.
 */
object UtteranceOverlap {
    private const val MIN_WORDS = 3

    /**
     * Different only when (almost) no word is shared and the final is plain words: a final that
     * reformats ("twenty five dollars" to "$25") or corrects a homophone ("I scream for" to
     * "ice cream for you", sharing "for") is the partial's own and replaces it. A partial of
     * five words or more may share one word (D24's "can't be accessed from my house" and "to
     * go grab my daughter" share "my") and still be a different utterance.
     */
    fun isDifferentUtterance(partial: String, final: String): Boolean {
        val partialWords = bareWords(partial)
        if (partialWords.size < MIN_WORDS) return false
        if (final.any { it.isDigit() || (!it.isLetter() && !it.isWhitespace() && it !in PLAIN_PUNCTUATION) }) return false
        val finalWords = bareWords(final).toSet()
        val shared = partialWords.count { it in finalWords }
        return shared == 0 || (partialWords.size >= LONG_PARTIAL_WORDS && shared == 1)
    }

    private const val LONG_PARTIAL_WORDS = 5

    private const val PLAIN_PUNCTUATION = ".,!?;:'\u2019\"-"

    private fun bareWords(text: String): List<String> =
        text.trim().split(Regex("\\s+")).map { w -> w.trim { !it.isLetterOrDigit() && it != '\'' }.lowercase() }.filter { it.isNotEmpty() }
}
