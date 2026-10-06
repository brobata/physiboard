package brobata.physiboard.core.text

/** The pair remembered for Backspace undo: what was typed and what replaced it. spec: autocorrect-suggestions.md SS7.5. */
data class LastReplacement(val original: String, val replacement: String)

/**
 * Undo memory and the rejected-word set behind autocorrect-suggestions.md SS7.5. 3.0 keeps one of
 * each, rather than 2.x's separate legacy-path bookkeeping (spec SS18, "Separate rejected sets
 * and undo memories: Drop; W4: one of each").
 */
data class AutocorrectMemory(
    val lastReplacement: LastReplacement? = null,
    private val rejectedWords: Set<String> = emptySet(),
) {
    /** A boundary replaced a word: remember the pair for a following Backspace. */
    fun afterReplacement(original: String, replacement: String): AutocorrectMemory =
        copy(lastReplacement = LastReplacement(original, replacement))

    /** A boundary passed without a replacement: the undo memory is cleared. spec: SS7.5. */
    fun afterBoundaryWithoutReplacement(): AutocorrectMemory = copy(lastReplacement = null)

    /** Any character was typed: the undo memory is cleared (spec: "cleared when any character is typed"). */
    fun afterAnyCharacterTyped(): AutocorrectMemory = copy(lastReplacement = null)

    /** A letter or digit was typed: the rejected set empties, so a rejection survives only until the next word starts. */
    fun afterLetterOrDigitTyped(): AutocorrectMemory = copy(rejectedWords = emptySet())

    /** Whether [word] (case-insensitively, any apostrophe style) is currently in the rejected set. */
    fun isRejected(word: String): Boolean = rejectionKey(word) in rejectedWords

    internal fun withRejected(words: Set<String>): AutocorrectMemory = copy(rejectedWords = rejectedWords + words.map(::rejectionKey))

    private fun rejectionKey(word: String): String = WordChars.straightenAll(word).lowercase()
}

/**
 * Undoing a replacement with Backspace. spec: autocorrect-suggestions.md SS7.5.
 */
object AutocorrectUndo {

    /** [ops] restores the original word; [memory] has the undo memory cleared and the rejection recorded; [addWordCandidate] is the original word, per step 4. */
    data class Result(val ops: List<EditorOp>, val memory: AutocorrectMemory, val addWordCandidate: String)

    /**
     * [textBeforeCursor] should be sized to the replacement's length plus 2 characters, per SS7.5
     * step 1. [apostropheRoot], when non-null, is also added to the rejected set (the apostrophe
     * root of the original word, when it has one, so `dell'amivo` rejected once also rejects
     * `amivo`'s correction path).
     */
    fun attempt(memory: AutocorrectMemory, textBeforeCursor: String, apostropheRoot: String? = null): Result? {
        val last = memory.lastReplacement ?: return null
        if (last.replacement.isEmpty()) return null
        val start = textBeforeCursor.lastIndexOf(last.replacement)
        if (start < 0) return null
        val trailing = textBeforeCursor.substring(start + last.replacement.length)
        if (trailing.any { !(it.isWhitespace() || it in WordChars.BOUNDARY_PUNCTUATION) }) return null

        val deleteCount = last.replacement.length + trailing.length
        val ops = listOf(EditorOp.DeleteSurrounding(deleteCount, 0), EditorOp.CommitText(last.original))
        // A mix-up fix replaces the previous word and the current one together ("it's tail" ->
        // "its tail"); undoing it rejects each word, so the next boundary neither redoes the
        // mix-up nor re-corrects the word after it. spec: SS7.5 step 3, SS10.
        val words = last.original.split(' ').filter { it.isNotEmpty() }
        val rejected = setOfNotNull(last.original, apostropheRoot) + words
        val newMemory = memory.copy(lastReplacement = null).withRejected(rejected)
        return Result(ops, newMemory, addWordCandidate = last.original)
    }
}
