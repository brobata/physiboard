package brobata.physiboard.core.text

/** The pair remembered for Backspace undo: what was typed and what replaced it. spec: autocorrect-suggestions.md SS7.5. */
data class LastReplacement(val original: String, val replacement: String)

/**
 * Undo memory and the rejected-word set behind autocorrect-suggestions.md SS7.5. 3.0 keeps one of
 * each, rather than 2.x's separate legacy-path bookkeeping (spec SS18, "Separate rejected sets
 * and undo memories: Drop; W4: one of each").
 *
 * It also carries the three facts the mix-up fix of the previous word (SS10's exception) needs
 * about how the text before the cursor came to be, since that fix is the one correction that
 * rewrites a word the user has already moved past:
 *
 * - [previousWordTyped]: the word before the one being typed was typed here, letter by letter
 *   from an empty word, and ended at a boundary the engine evaluated on a field read that agreed,
 *   with nothing since then (a cursor move, an input restart, a new field, an undo, a paste, a
 *   Backspace behind the word being typed) that could make "the word before" something else.
 * - [currentWordTyped]: the same for the word being typed now, decided when its first letter
 *   is typed ([afterWordStarted]): only a letter typed onto an empty word, with a trusted read
 *   showing nothing word-like right before it, starts a word typed here. At its boundary it becomes
 *   [previousWordTyped] for the next one. A boundary with no word finishes nothing and moves neither.
 * - the pinned words: words the user chose on purpose, restored by Backspace undo or accepted
 *   from a suggestion. Unlike a rejection they survive the next word's letters, and they stay
 *   pinned while they are the word being typed or the word before it.
 */
data class AutocorrectMemory(
    val lastReplacement: LastReplacement? = null,
    private val rejectedWords: Set<String> = emptySet(),
    private val pinnedWords: Set<String> = emptySet(),
    val previousWordTyped: Boolean = false,
    val currentWordTyped: Boolean = false,
) {
    /** A boundary replaced a word: remember the pair for a following Backspace. */
    fun afterReplacement(original: String, replacement: String): AutocorrectMemory =
        copy(lastReplacement = LastReplacement(original, replacement))

    /** A boundary passed without a replacement: the undo memory is cleared. spec: SS7.5. */
    fun afterBoundaryWithoutReplacement(): AutocorrectMemory = copy(lastReplacement = null)

    /** Any character was typed: the undo memory is cleared (spec: "cleared when any character is typed"). */
    fun afterAnyCharacterTyped(): AutocorrectMemory = copy(lastReplacement = null)

    /** A letter or digit was typed: the rejected set empties, so a rejection survives only until the next word starts. Pins are kept. */
    fun afterLetterOrDigitTyped(): AutocorrectMemory = copy(rejectedWords = emptySet())

    /** Whether [word] (case-insensitively, any apostrophe style) is currently in the rejected set. */
    fun isRejected(word: String): Boolean = rejectionKey(word) in rejectedWords

    /** Whether [word] is one the user chose on purpose and the mix-up fix must leave alone. */
    fun isPinned(word: String): Boolean = rejectionKey(word) in pinnedWords

    /**
     * The engine evaluated the boundary that ended [trackedWord] on a field read that agreed:
     * that word is now the word before, typed here if it was typed from empty, and the next word
     * starts from empty. A pin outlives the boundary only on the word just finished, which is the
     * word before from now on.
     */
    fun afterEvaluatedBoundary(trackedWord: String): AutocorrectMemory {
        val key = rejectionKey(trackedWord)
        // An empty word (a second Space, a Space in a field just opened on old text) finished no
        // word: promoting here would vouch for whatever the field happens to end with.
        if (key.isEmpty()) return copy(pinnedWords = emptySet())
        return copy(
            previousWordTyped = currentWordTyped,
            currentWordTyped = false,
            pinnedWords = if (key.isNotEmpty() && key in pinnedWords) setOf(key) else emptySet(),
        )
    }

    /**
     * The keyboard no longer knows how the text before the cursor came to be: the cursor moved,
     * the input restarted, text arrived that was not typed letter by letter, a boundary passed
     * without a trustworthy read, or Backspace went behind the word being typed. Neither the word
     * before nor any word in progress is one typed here; the next word started from empty may be
     * ([afterWordStarted]). Pins describe the text as it was and go too.
     */
    fun afterTrackingLost(): AutocorrectMemory =
        copy(previousWordTyped = false, currentWordTyped = false, pinnedWords = emptySet())

    /**
     * The first letter of a word was typed onto an empty word. [fresh] says the field, read and
     * trusted, showed the start of the text, a space or a mark before it, nothing a letter would
     * join: the word is typed here from its first letter. Otherwise (no read, or the letter lands
     * against old text, `it` + `s`) it is not.
     */
    fun afterWordStarted(fresh: Boolean): AutocorrectMemory = copy(currentWordTyped = fresh)

    /** [words] were chosen by the user on purpose (accepted from a suggestion, or put back by an undo). */
    fun withPinned(words: Collection<String>): AutocorrectMemory =
        copy(pinnedWords = pinnedWords + words.map(::rejectionKey).filter { it.isNotEmpty() })

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
        // The words put back are the user's choice: pinned past the next word's letters, so the mix-up
        // fix cannot redo what this undo just took back once the rejection empties (SS10's exception).
        val newMemory = memory.copy(lastReplacement = null).withRejected(rejected)
            .afterTrackingLost().withPinned(words)
        return Result(ops, newMemory, addWordCandidate = last.original)
    }
}
