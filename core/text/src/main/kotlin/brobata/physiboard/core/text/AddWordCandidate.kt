package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.UserWordStore

/**
 * spec: autocorrect-suggestions.md SS6.2, first clause: "An add-word candidate exists when the
 * current word (trimmed) contains a letter or digit, the primary dictionary is loaded, and the
 * word is not known." This is the live-typing half of the rule that lets the strip offer to add a
 * genuinely unknown word (SS3.8's left slot); the two clauses that extend the candidate past an
 * automatic correction or an undo ("It also exists after an automatic correction whose result is
 * unknown, and after undoing a correction") are `:core:text`'s boundary/undo machinery's own
 * concern ([BoundaryOutcome.Replaced.addWordCandidate], [AutocorrectMemory.Result.addWordCandidate]),
 * not this function, which only ever looks at the word being typed right now.
 */
object AddWordCandidate {

    fun forCurrentWord(
        word: String,
        primaryDictionaryLoaded: Boolean,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
    ): String? {
        if (!primaryDictionaryLoaded) return null
        val trimmed = word.trim()
        if (trimmed.none { it.isLetterOrDigit() }) return null
        val known = dictionaries.any { it.contains(trimmed) } || userWords.isKnown(trimmed)
        return if (known) null else trimmed
    }
}
