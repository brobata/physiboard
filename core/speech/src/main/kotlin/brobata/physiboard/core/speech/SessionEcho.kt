package brobata.physiboard.core.speech

/**
 * Removes from a recognizer result the words this session has already finished into the field.
 * spec: dictation.md SS7.1 ("successive partials overwrite each other in place") read with
 * SS7.3: once an utterance is finished it is ordinary text, so a later result that begins with
 * those same words is the engine repeating itself, not the user. Titan, 2026-09-25: Google's
 * segmented session reports the whole session's transcript in every partial and segment, and
 * composing it after the committed text made the field grow by the full transcript each time.
 * SPEC GAP: a user who deliberately repeats the exact words just dictated loses the repeat;
 * accepted, since the alternative duplicated every segment.
 */
object SessionEcho {
    /** [result] with the leading echo of [finished] removed, trimmed; [result] unchanged when it does not start with the echo. */
    fun strip(result: String, finished: String): String {
        val done = words(finished)
        if (done.isEmpty()) return result
        val said = words(result)
        if (said.size < done.size) return result
        // The words already in the field carry whatever the finisher added ("world." after
        // automatic punctuation); the engine re-reports them bare. Comparing them as written
        // failed to match, and the whole transcript was composed again.
        for (i in done.indices) if (!bare(said[i]).equals(bare(done[i]), ignoreCase = true)) return result
        return said.drop(done.size).joinToString(" ")
    }

    /** [finished] extended by the words of [plainText], the form [strip] compares against. */
    fun extend(finished: String, plainText: String?): String =
        if (plainText.isNullOrBlank()) finished else (words(finished) + words(plainText)).joinToString(" ")

    /** A word without the punctuation either side of it, which is all this comparison is about. */
    private fun bare(word: String): String = word.trim { !it.isLetterOrDigit() && it != '\'' }

    private fun words(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
