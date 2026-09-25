package brobata.physiboard.core.dict

/** Which tier of the user's own vocabulary a word came from. spec: autocorrect-suggestions.md §6.1, dictionaries-languages.md §7. */
enum class WordSource { PERSONAL, DEFAULT_USER }

/**
 * A word the user has added, with the usage tracking the personal dictionary keeps. spec:
 * autocorrect-suggestions.md §6.1 (`{"w": word, "f": count, "u": lastUsedMillis}`).
 */
data class PersonalWord(val word: String, val frequency: Int, val lastUsedMillis: Long)

/**
 * The user's own words, layered over a bundled dictionary: the default words shipped with the
 * app and the personal words the user has added. Both tiers are always known and can never be
 * filtered out of suggestions by frequency or capitalization; default words are additionally
 * excluded from starter suggestions, a distinction [sourceOf] exposes so a caller can apply
 * that exclusion itself (starter-word selection is scoring, not this module's job). spec:
 * autocorrect-suggestions.md §6.1, dictionaries-languages.md §7.
 *
 * This store answers queries about itself only; combining it with a bundled [DictionaryIndex]
 * (so that, say, "known" means personal-or-default-or-main) is one line at the call site:
 * `store.isKnown(word) || index.contains(word)`. Keeping that composition out of this class is
 * what lets [empty] and [of] build a store with no dictionary in hand at all, which the
 * personal-dictionary screen needs (it edits the list without loading anything).
 */
class UserWordStore private constructor(
    private val defaultWords: Map<String, WordFrequency>,
    private val personalWords: Map<String, PersonalWord>,
) {

    /** Whether [word] is known through either tier of this store. */
    fun isKnown(word: String): Boolean {
        val key = DictNormalization.normalizedKey(word)
        return personalWords.containsKey(key) || defaultWords.containsKey(key)
    }

    /** The raw frequency this store has for [word] (personal wins over default), or 0. */
    fun frequencyOf(word: String): Int {
        val key = DictNormalization.normalizedKey(word)
        return personalWords[key]?.frequency ?: defaultWords[key]?.frequency ?: 0
    }

    /** Which tier holds [word] (personal takes precedence), or null when neither does. */
    fun sourceOf(word: String): WordSource? {
        val key = DictNormalization.normalizedKey(word)
        return when {
            personalWords.containsKey(key) -> WordSource.PERSONAL
            defaultWords.containsKey(key) -> WordSource.DEFAULT_USER
            else -> null
        }
    }

    /**
     * Adds [word] to the personal dictionary, or, when its normalized key is already there,
     * increments its frequency and refreshes its last-used time instead. spec:
     * autocorrect-suggestions.md §6.1 ("Adding a word that already exists increments f and
     * refreshes u"); [nowMillis] is supplied by the caller so this stays a pure function.
     */
    fun withPersonalWordAdded(word: String, nowMillis: Long): UserWordStore {
        val key = DictNormalization.normalizedKey(word)
        val existing = personalWords[key]
        val updated = if (existing != null) {
            existing.copy(frequency = existing.frequency + 1, lastUsedMillis = nowMillis)
        } else {
            PersonalWord(word, frequency = 1, lastUsedMillis = nowMillis)
        }
        return UserWordStore(defaultWords, personalWords + (key to updated))
    }

    /**
     * Removes [word] from the personal dictionary: the action behind the strip's long-press
     * delete and the personal dictionary screen's delete button. A word not present is a
     * no-op. spec: autocorrect-suggestions.md §5, §6.3.
     */
    fun withPersonalWordRemoved(word: String): UserWordStore {
        val key = DictNormalization.normalizedKey(word)
        if (key !in personalWords) return this
        return UserWordStore(defaultWords, personalWords - key)
    }

    /**
     * The personal-dictionary screen's edit pencil (autocorrect-suggestions.md §6.3, "rename"):
     * [oldWord] keeps its frequency and last-used time under [newWord]'s key. A blank [newWord]
     * or an [oldWord] not present is a no-op, so a caller need not pre-validate either; when
     * [newWord] already names a different personal word, that entry is overwritten (the two rows
     * would otherwise collide on the same normalized key).
     */
    fun withPersonalWordRenamed(oldWord: String, newWord: String): UserWordStore {
        if (newWord.isBlank()) return this
        val oldKey = DictNormalization.normalizedKey(oldWord)
        val existing = personalWords[oldKey] ?: return this
        val newKey = DictNormalization.normalizedKey(newWord)
        val renamed = existing.copy(word = newWord)
        return UserWordStore(defaultWords, (personalWords - oldKey) + (newKey to renamed))
    }

    /** Every personal word, in no particular order; a screen sorts as it needs. */
    fun personalWords(): List<PersonalWord> = personalWords.values.toList()

    /** Every default user word. */
    fun defaultWords(): List<WordFrequency> = defaultWords.values.toList()

    companion object {
        fun empty(): UserWordStore = UserWordStore(emptyMap(), emptyMap())

        /** Builds a store from the shipped default words and any already-saved personal words. */
        fun of(defaultWords: List<WordFrequency>, personalWords: List<PersonalWord> = emptyList()): UserWordStore {
            val defaults = defaultWords.associateBy { DictNormalization.normalizedKey(it.word) }
            val personal = personalWords.associateBy { DictNormalization.normalizedKey(it.word) }
            return UserWordStore(defaults, personal)
        }
    }
}
