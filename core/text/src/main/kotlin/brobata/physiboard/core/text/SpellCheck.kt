package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.UserWordStore

/**
 * One stretch of a text the spell checker looked at, `[start, end)`. A [skipped] token is not
 * a word to judge at all (a URL, an address, a number, a hashtag, an @mention); it is still
 * reported, so the app clears any old underline there.
 */
data class SpellToken(val start: Int, val end: Int, val skipped: Boolean)

/**
 * Splits text into the words the system spell checker judges. spec: autocorrect-suggestions.md
 * §18 (the system spell checker), word characters as §1.1 has them.
 *
 * The text is first cut at whitespace. A piece that is a web address, an email address, a path,
 * an identifier, a hashtag or an @mention is one skipped token, whatever letters it holds:
 * `site.com/its` is not the word `its`. Every other piece is cut into runs of word characters
 * (letters, digits, and an apostrophe between two of them), so `well-known` is two words and
 * `"hello,"` is one. A run with a digit in it (`4pm`, `2nd`, `covid19`) is skipped. A closing
 * apostrophe stays on a word only after an `s`, where it is a plural possessive (`players'`);
 * anywhere else it is a closing quote and not part of the word.
 */
object SpellTokens {

    private const val OPENING = "\"'([{<“‘«‹*_"
    private const val CLOSING = "\"'.,;:!?)]}>”’»›*_…"

    fun split(text: String): List<SpellToken> {
        val tokens = ArrayList<SpellToken>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }
            var j = i
            while (j < n && !text[j].isWhitespace()) j++
            var start = i
            var end = j
            while (start < end && text[start] in OPENING) start++
            while (end > start && text[end - 1] in CLOSING) end--
            if (start < end && isNotProse(text, start, end)) {
                tokens.add(SpellToken(start, end, skipped = true))
            } else {
                splitWords(text, i, j, tokens)
            }
            i = j
        }
        return tokens
    }

    /** Whether `text[start, end)` (outer punctuation already trimmed) is an address, a path, a tag or a mention rather than prose. */
    private fun isNotProse(text: String, start: Int, end: Int): Boolean {
        val first = text[start]
        if (first == '#' || first == '@') return true
        val piece = text.substring(start, end)
        if (piece.contains("://") || piece.startsWith("www.", ignoreCase = true)) return true
        for (k in start until end) {
            val ch = text[k]
            if (ch == '@' || ch == '/' || ch == '\\' || ch == '_' || ch == '=' || ch == '~' || ch == '#') return true
            // A dot inside (`site.com`, `e.g`, `1.5`, `index.html`) is an address, a file or an abbreviation, never a word.
            if (ch == '.' && k > start && k < end - 1 && text[k - 1].isLetterOrDigit() && text[k + 1].isLetterOrDigit()) return true
        }
        return false
    }

    private fun splitWords(text: String, from: Int, to: Int, into: MutableList<SpellToken>) {
        var k = from
        while (k < to) {
            if (!text[k].isLetterOrDigit()) {
                k++
                continue
            }
            val start = k
            k++
            while (k < to && (text[k].isLetterOrDigit() || (WordChars.isApostrophe(text[k]) && text[k - 1].isLetterOrDigit()))) k++
            var end = k
            // `players'` keeps its apostrophe; `'hello'` does not.
            if (WordChars.isApostrophe(text[end - 1]) && !(end - start >= 3 && text[end - 2].lowercaseChar() == 's')) end--
            val hasDigit = (start until end).any { text[it].isDigit() }
            into.add(SpellToken(start, end, skipped = hasDigit))
        }
    }
}

/**
 * What the spell checker judges against: the language's dictionaries (primary first), the user's
 * own words, the language's word-pair table when it has one, and words the user added to
 * Android's own dictionary from an app's "Add to dictionary" (keys as [systemWordKey] makes them).
 */
data class SpellResources(
    val dictionaries: List<DictionaryIndex>,
    val userWords: UserWordStore,
    val contextModel: ContextModel?,
    val lengthChangeAllowance: Int,
    /** The script the language is written in; a word in another script is not judged against it. */
    val script: Character.UnicodeScript = Character.UnicodeScript.LATIN,
    val systemWords: Set<String> = emptySet(),
) {
    companion object {
        /** The script [language] (a two-letter code) is written in, as far as the keyboard's dictionaries go. */
        fun scriptFor(language: String): Character.UnicodeScript = when (language.lowercase()) {
            "ru", "uk", "sr", "bg", "be", "mk" -> Character.UnicodeScript.CYRILLIC
            "el" -> Character.UnicodeScript.GREEK
            else -> Character.UnicodeScript.LATIN
        }

        /** How a word from Android's user dictionary is held in [systemWords]: lowercase, straight apostrophes, so case and apostrophe style do not matter. */
        fun systemWordKey(word: String): String = WordChars.straightenAll(word.trim()).lowercase()
    }
}

/** What the spell checker makes of one token. */
enum class SpellKind {
    /** A word a dictionary or the user's own words spell this way. */
    KNOWN,

    /** Not a word to judge (a URL, a number, an acronym, another script). */
    SKIPPED,

    /** Unknown, but left alone: a likely name, a regional spelling, an inflection, a word too short to judge. */
    UNFLAGGED,

    /** Looks like a typo; [SpellFinding.suggestions] are the corrections, best first. */
    TYPO,

    /** A real word that reads as its twin's slip (`its` for `it's`); flagged as grammar, with the twin. */
    MIXUP,
}

/**
 * One judged token, `[start, end)` of the text. [confident] is true when the keyboard itself would
 * have made the first suggestion automatically (the noisy channel's share cleared the same bar),
 * which the spell checker passes on as a recommended suggestion.
 */
data class SpellFinding(val start: Int, val end: Int, val kind: SpellKind, val suggestions: List<String> = emptyList(), val confident: Boolean = false)

/**
 * The system spell checker's judgement, the keyboard's own engine read without changing any
 * text. spec: autocorrect-suggestions.md §18.
 *
 * - A word any dictionary, the personal or default words, or Android's user dictionary holds is
 *   known, as is a known word with a contraction or possessive ending (§10). A lowercase word
 *   the dictionary only has capitalised (`monday`, `english`) is flagged with its capitalised
 *   spelling, the same case repair the keyboard makes at a boundary (§7.2 step 8).
 * - URLs, addresses, numbers, hashtags and @mentions are never judged ([SpellTokens]); nor are
 *   acronyms, words with an inner capital (`iPhone`) or words in another script.
 * - An unknown word of fewer than three letters is left: its shape says too little.
 * - An unknown lowercase word is a typo. Its suggestions are ranked by the same noisy channel the
 *   keyboard corrects with ([ContextCorrection.rank]: the word before as context, the Titan's key
 *   geometry as the slip cost), unless the best of them is only the other regional spelling or
 *   the stem of an inflection, which the keyboard also treats as a word.
 * - An unknown capitalised word is usually a name, so it is flagged only when the engine is as
 *   sure as it must be to correct it automatically. Without a word-pair table (every language but
 *   English) a capitalised unknown word is never flagged, and a lowercase one gets the
 *   suggestion list the keyboard's path without a table ranks ([SuggestionRanking]), its
 *   completions left out.
 * - The mix-up sets the keyboard can fix (`its`/`it's`, `your`/`you're`, ...) are flagged as a
 *   grammar error only when [WordMixups.judge] clears the same bar it needs to fix one, with the
 *   word after it read as typed.
 */
object SpellCheck {

    /**
     * How far a suggestion may be from the typed word. The spell checker only offers; it never
     * replaces, so it reaches as far as the engine ever does, whatever
     * `max_auto_replace_distance` says, and always offers a missing apostrophe or accent.
     */
    private val OFFERING = AutocorrectSettings(maxAutoReplaceDistance = 2, accentMatchingEnabled = true)

    /** The most suggestions any app shows for one word. */
    const val MAX_SUGGESTIONS = 5

    /**
     * Every token of [text], in order, judged in its sentence. [cache] keeps the verdicts of
     * words seen before with the same word before them; the caller owns it and must empty it
     * whenever [resources] change.
     */
    fun checkSentence(text: String, resources: SpellResources, limit: Int, cache: MutableMap<String, SpellFinding>? = null): List<SpellFinding> {
        val max = limit.coerceIn(0, MAX_SUGGESTIONS)
        val tokens = SpellTokens.split(text)
        val findings = ArrayList<SpellFinding>(tokens.size)
        for (token in tokens) {
            if (token.skipped) {
                findings.add(SpellFinding(token.start, token.end, SpellKind.SKIPPED))
                continue
            }
            val word = text.substring(token.start, token.end)
            val previous = SentenceContext.before(text, token.start, windowTruncated = false)
            val key = cacheKey(previous, word, max)
            val judged = cache?.get(key) ?: judge(word, previous, resources, max).also { cache?.put(key, it) }
            findings.add(judged.copy(start = token.start, end = token.end))
        }
        resources.contextModel?.let { model -> flagMixups(text, tokens, findings, resources, model) }
        return findings
    }

    /** One word on its own, with no sentence around it ([Preceding.Unknown]). Text that is not exactly one word is [SpellKind.SKIPPED]. */
    fun checkWord(text: String, resources: SpellResources, limit: Int): SpellFinding {
        val tokens = SpellTokens.split(text)
        val token = tokens.singleOrNull()
        if (token == null || token.skipped) return SpellFinding(0, text.length, SpellKind.SKIPPED)
        val word = text.substring(token.start, token.end)
        return judge(word, Preceding.Unknown, resources, limit.coerceIn(0, MAX_SUGGESTIONS)).copy(start = token.start, end = token.end)
    }

    private fun cacheKey(previous: Preceding, word: String, limit: Int): String = when (previous) {
        Preceding.SentenceStart -> "^"
        Preceding.Unknown -> "?"
        is Preceding.Word -> previous.key
    } + '\u0000' + word + '\u0000' + limit

    private fun judge(word: String, previous: Preceding, r: SpellResources, limit: Int): SpellFinding {
        val letters = word.filter { it.isLetter() }
        if (letters.isEmpty() || word.any { it.isDigit() }) return finding(SpellKind.SKIPPED)
        if (!inScript(letters, r.script)) return finding(SpellKind.SKIPPED)
        val dictionaries = r.dictionaries
        if (isKnown(word, r)) {
            val repaired = BoundaryEngine.primaryCaseRepair(word, dictionaries.firstOrNull(), dictionaries, r.userWords)
            if (repaired != null && systemKey(word) !in r.systemWords) return finding(SpellKind.TYPO, listOf(repaired).take(limit), confident = true)
            return finding(SpellKind.KNOWN)
        }
        // Nothing to judge an unknown word against: the language has no dictionary loaded.
        if (dictionaries.isEmpty()) return finding(SpellKind.UNFLAGGED)
        // An acronym, or a deliberate inner capital (iPhone, McKay), is the user's own.
        if (letters.length >= 2 && letters.all { it.isUpperCase() }) return finding(SpellKind.SKIPPED)
        if (letters.drop(1).any { it.isUpperCase() }) return finding(SpellKind.SKIPPED)
        if (letters.length < 3) return finding(SpellKind.UNFLAGGED)
        val capitalised = letters.first().isUpperCase()
        val model = r.contextModel
        if (model == null) {
            if (capitalised) return finding(SpellKind.UNFLAGGED)
            val ranked = SuggestionRanking.suggest(word, dictionaries, r.userWords, RankingOptions(useKeyboardProximity = false, accentMatchingEnabled = true), limit = limit * 2)
                .filter { it.source != CandidateSource.COMPLETION }
                .map { CasingRules.forTypedWord(word, it.word) }
            return finding(SpellKind.TYPO, distinctSuggestions(word, ranked, limit))
        }
        val ranking = ContextCorrection.rank(word, previous, model, dictionaries, r.userWords, OFFERING, r.lengthChangeAllowance)
        val best = ranking.candidates.firstOrNull()
        if (best != null && (RegionalSpelling.isVariant(word, best.spelling) || ContextCorrection.isInflectionOf(word, best.spelling))) {
            return finding(SpellKind.UNFLAGGED)
        }
        val confident = ContextCorrection.verdict(word, ranking) is ContextCorrection.Result.Commit
        // A capitalised word the engine would not correct is most likely a name.
        if (capitalised && !confident) return finding(SpellKind.UNFLAGGED)
        val suggestions = distinctSuggestions(word, ranking.candidates.map { CasingRules.forTypedWord(word, it.spelling) }, limit)
        return finding(SpellKind.TYPO, suggestions, confident && suggestions.isNotEmpty())
    }

    private fun finding(kind: SpellKind, suggestions: List<String> = emptyList(), confident: Boolean = false) = SpellFinding(0, 0, kind, suggestions, confident)

    private fun distinctSuggestions(word: String, candidates: List<String>, limit: Int): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>(limit)
        for (candidate in candidates) {
            if (out.size >= limit) break
            if (candidate == word || !seen.add(candidate)) continue
            out.add(candidate)
        }
        return out
    }

    private fun isKnown(word: String, r: SpellResources): Boolean =
        ContextCorrection.isExactlyKnown(word, r.dictionaries, r.userWords) ||
            ContextCorrection.isKnownWithClitic(word, r.dictionaries, r.userWords) ||
            (r.systemWords.isNotEmpty() && systemKey(word) in r.systemWords)

    private fun systemKey(word: String): String = SpellResources.systemWordKey(word)

    private fun inScript(letters: String, script: Character.UnicodeScript): Boolean {
        var i = 0
        while (i < letters.length) {
            val cp = letters.codePointAt(i)
            val s = Character.UnicodeScript.of(cp)
            if (s != script && s != Character.UnicodeScript.COMMON && s != Character.UnicodeScript.INHERITED) return false
            i += Character.charCount(cp)
        }
        return true
    }

    /**
     * Marks a known word in one of the shipped confusion sets as a grammar error when the words on
     * both sides read it as its twin. The same conditions as the keyboard's fix, less the ones
     * about what was typed here: the word stands on its own, exactly one space separates it from
     * the next word, and [WordMixups.judge] clears its bar.
     */
    private fun flagMixups(text: String, tokens: List<SpellToken>, findings: MutableList<SpellFinding>, r: SpellResources, model: ContextModel) {
        for (i in 0 until tokens.size - 1) {
            val here = findings[i]
            val next = tokens[i + 1]
            if (here.kind != SpellKind.KNOWN || next.skipped) continue
            if (text.substring(here.end, next.start) != " ") continue
            if (!BoundaryEngine.standsAlone(text, here.start)) continue
            val word = text.substring(here.start, here.end)
            val key = WordChars.straightenAll(word).lowercase()
            if (WordMixups.setOf(key) == null) continue
            val beforeId = when (val before = SentenceContext.before(text, here.start, windowTruncated = false)) {
                Preceding.SentenceStart -> ContextModel.SENTENCE_START
                Preceding.Unknown -> ContextModel.NO_CONTEXT
                is Preceding.Word -> model.idOf(before.key)
            }
            val nextKey = WordChars.straightenAll(text.substring(next.start, next.end)).lowercase()
            val twin = WordMixups.judge(key, beforeId, nextKey, model) ?: continue
            val spelled = BoundaryEngine.twinAsTyped(word, twin, r.dictionaries, word)
            findings[i] = here.copy(kind = SpellKind.MIXUP, suggestions = listOf(spelled), confident = true)
        }
    }
}
