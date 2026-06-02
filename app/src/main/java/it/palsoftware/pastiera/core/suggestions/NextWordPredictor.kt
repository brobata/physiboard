package it.palsoftware.pastiera.core.suggestions

import java.text.Normalizer
import java.util.Locale

class NextWordPredictor(
    private val store: UserNGramStore
) {

    fun learn(locale: Locale, previousWord: String?, nextWord: String?) {
        val prefix = normalizedKey(previousWord, locale) ?: return
        val displayNextWord = cleanDisplayWord(nextWord) ?: return
        store.learn(locale.toLanguageTag(), prefix, displayNextWord)
    }

    fun learnSentenceStart(locale: Locale, firstWord: String?) {
        val displayWord = cleanDisplayWord(firstWord) ?: return
        store.learn(locale.toLanguageTag(), SENTENCE_START_PREFIX, displayWord)
    }

    fun predict(locale: Locale, previousWord: String?, limit: Int = 3): List<SuggestionResult> {
        val prefix = normalizedKey(previousWord, locale) ?: return emptyList()
        return predictForPrefix(locale, prefix, limit)
    }

    fun predictSentenceStart(locale: Locale, limit: Int = 3): List<SuggestionResult> {
        return predictForPrefix(locale, SENTENCE_START_PREFIX, limit)
    }

    fun forget(locale: Locale, previousWord: String?, nextWord: String?): Boolean {
        val prefix = normalizedKey(previousWord, locale) ?: return false
        val displayNextWord = cleanDisplayWord(nextWord) ?: return false
        return store.delete(locale.toLanguageTag(), prefix, displayNextWord) > 0
    }

    fun forgetSentenceStart(locale: Locale, firstWord: String?): Boolean {
        val displayWord = cleanDisplayWord(firstWord) ?: return false
        return store.delete(locale.toLanguageTag(), SENTENCE_START_PREFIX, displayWord) > 0
    }

    fun forgetNextWordEverywhere(locale: Locale, nextWord: String?): Boolean {
        val displayNextWord = cleanDisplayWord(nextWord) ?: return false
        return store.deleteNextWord(locale.toLanguageTag(), displayNextWord) > 0
    }

    private fun predictForPrefix(locale: Locale, prefix: String, limit: Int): List<SuggestionResult> {
        return store.predict(locale.toLanguageTag(), prefix, limit)
            .map { prediction ->
                SuggestionResult(
                    candidate = prediction.word,
                    distance = 0,
                    score = prediction.count.toDouble(),
                    source = SuggestionSource.USER,
                    kind = SuggestionKind.NEXT_WORD
                )
            }
    }

    internal fun clearAll() {
        store.clearAll()
    }

    private fun cleanDisplayWord(word: String?): String? {
        val trimmed = word?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.none { it.isLetterOrDigit() }) return null
        return trimmed
    }

    private fun normalizedKey(word: String?, locale: Locale): String? {
        val cleaned = cleanDisplayWord(word) ?: return null
        val withAsciiCompat = WordNormalization
            .foldCompatibilityLetters(WordNormalization.normalizeApostrophes(cleaned).lowercase(locale))
        val normalized = Normalizer.normalize(withAsciiCompat, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS_REGEX, "")
            .replace(NON_WORD_KEY_REGEX, "")
        return normalized.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val SENTENCE_START_PREFIX = "__sentence_start__"
        private val COMBINING_MARKS_REGEX = "\\p{Mn}".toRegex()
        private val NON_WORD_KEY_REGEX = "[^\\p{L}\\p{N}]".toRegex()
    }
}
