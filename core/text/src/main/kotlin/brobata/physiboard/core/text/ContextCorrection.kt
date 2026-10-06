package brobata.physiboard.core.text

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictNormalization
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.EditDistance
import brobata.physiboard.core.dict.ScoredCandidate
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlin.math.exp
import kotlin.math.ln

/**
 * Correcting the word just finished when the keyboard can read the sentence: autocorrect-
 * suggestions.md §16 W2 (keyboard geometry in the score) and W5 (the previous word as a prior),
 * run by [BoundaryEngine] whenever a word-pair table is loaded.
 *
 * Each dictionary word near the typed one is scored as a noisy channel: how likely it is after
 * the word before it ([ContextModel.logProbability], sentence start included), times how likely
 * the slip from it to what was typed is ([ChannelCost], scaled by [Tuning.channelScale]). Against
 * them stands one more hypothesis, that the typed word is exactly what was meant and the
 * dictionary simply lacks it (a name, a new word); its weight is [Tuning.keepLowercase],
 * [Tuning.keepCapitalisedAtStart] or [Tuning.keepCapitalisedMidSentence] by how the word was typed,
 * since a capital in mid-sentence is a name far more often than a typo. The best candidate is
 * committed only when its share of all of that ([Tuning.commitShare]) is decisive. There is no
 * suggestion bar to offer a second choice on, so a close call is left alone: Backspace is the only
 * way back from a wrong fix, and a missed fix costs the user nothing they did not type.
 *
 * Only words no dictionary spells this way reach this ([BoundaryEngine] decides that), so a
 * correctly spelled word is never replaced by it (§10). A word whose letters a dictionary knows
 * in another spelling (`dont`, `cafe`) gets that spelling as one more candidate, which is §10's
 * accent repair decided by the same weighing rather than unconditionally.
 */
object ContextCorrection {

    /** The numbers the decision turns on; measured, not guessed: docs/plans/autocorrect-context.md has the sweep. */
    data class Tuning(
        /** Natural-log units per unit of [ChannelCost]: how fast a costlier slip loses to a likelier word. */
        val channelScale: Double = 7.0,
        /** `ln` weight of "meant as typed" for a lowercase word of three letters. */
        val keepLowercase: Double = -13.0,
        /**
         * How much less likely "meant as typed" gets per letter beyond three: a long string the
         * dictionary has never seen is far more often a slip than a word, a short one less so.
         */
        val keepPerLetter: Double = 1.5,
        /** The same for a capitalised word that starts a sentence. */
        val keepCapitalisedAtStart: Double = -12.0,
        /** The same for a capitalised word inside a sentence, which is usually a name. */
        val keepCapitalisedMidSentence: Double = -9.0,
        /** The share of the whole the best candidate needs before it replaces what was typed. */
        val commitShare: Double = 0.7,
        /**
         * The share of the prior taken from how common the word is anywhere rather than after
         * this particular word: the table has seen most words after only a few others, and a
         * lone word in an empty field (sentence start) should not lose `receive` just because
         * few sentences start with it.
         */
        val unigramMix: Double = 0.5,
        /** Prior given to a personal or default user word the table has never seen. */
        val userWordLogPrior: Double = -9.0,
        /** How many nearby dictionary keys are rescored. */
        val candidateLimit: Int = 24,
        /** Leave a word that is a known word plus an inflection ending ([isInflectionOf]). */
        val keepInflections: Boolean = true,
        val weights: ChannelCost.Weights = ChannelCost.DEFAULT,
    )

    val DEFAULT_TUNING: Tuning = Tuning()

    sealed class Result {
        data class Commit(val replacement: String, val share: Double, val cost: Double) : Result()
        data class Leave(val reason: String) : Result()
    }

    /** Contraction and possessive endings: a known word with one of these after an apostrophe is a word, not a typo (`must've`, `Sami's`). */
    private val CLITICS = setOf("s", "d", "ll", "re", "ve", "m", "t")

    /**
     * Decides for [typed], a word no loaded dictionary or word store spells this way. [previous] is what
     * precedes it ([SentenceContext]); [lengthChangeAllowance] is §9's per-language allowance.
     */
    fun decide(
        typed: String,
        previous: Preceding,
        model: ContextModel,
        dictionaries: List<DictionaryIndex>,
        userWords: UserWordStore,
        settings: AutocorrectSettings,
        lengthChangeAllowance: Int,
        tuning: Tuning = DEFAULT_TUNING,
        /** Sees every candidate weighed, with its prior and slip cost, and then "as typed" with its weight; for the harness and debugging. */
        trace: ((candidate: String, prior: Double, cost: Double) -> Unit)? = null,
    ): Result {
        // §9's minimum length, kept: a two-letter word is too short for its shape to say what was meant.
        if (typed.length < 3) return Result.Leave("word_too_short")
        if (typed.any { it.isDigit() }) return Result.Leave("not_a_word")
        val letters = typed.filter { it.isLetter() }
        if (letters.isEmpty()) return Result.Leave("not_a_word")
        // An all-capitals word is an acronym and an inner capital is deliberate (iPhone, McKay).
        if (letters.length >= 2 && letters.all { it.isUpperCase() }) return Result.Leave("acronym_typed")
        if (letters.drop(1).any { it.isUpperCase() }) return Result.Leave("inner_capital")
        if (isKnownWithClitic(typed, dictionaries, userWords)) return Result.Leave("known_word")

        // The typed word's own key may hold other spellings (`dont` -> `don't`, `cafe` -> `café`):
        // they compete like any other candidate, but only by adding marks, never removing them,
        // so `players'` and `Baháʼí` are never stripped back to a bare spelling; and only while
        // `accent_matching_enabled` is on (§13: "Accent & spelling marks"), as on the path
        // without a table.
        val mayAddMarks = settings.accentMatchingEnabled && typed.none { WordChars.isApostrophe(it) || (it.isLetter() && it.code >= 0x80) }
        // Two edits in a word of four letters or fewer leave too little of it to say what was meant (`telo` is not `to`).
        // `max_auto_replace_distance` 0 blocks every slip but not a repair of marks, which is distance 0 (§13).
        val maxDistance = minOf(if (letters.length <= 4) 1 else 2, settings.maxAutoReplaceDistance)
        if (maxDistance <= 0 && !mayAddMarks) return Result.Leave("distance_too_high")

        val typedCapitalised = letters.first().isUpperCase()
        val previousId = when (previous) {
            Preceding.SentenceStart -> ContextModel.SENTENCE_START
            Preceding.Unknown -> ContextModel.NO_CONTEXT
            is Preceding.Word -> model.idOf(previous.key)
        }
        val keep = when {
            !typedCapitalised -> tuning.keepLowercase
            previous == Preceding.SentenceStart -> tuning.keepCapitalisedAtStart
            else -> tuning.keepCapitalisedMidSentence
        } - tuning.keepPerLetter * maxOf(0, letters.length - 3)
        val typedKey = DictNormalization.normalizedKey(typed)

        val candidates = gatherCandidates(typed, dictionaries, userWords, maxOf(0, maxDistance), tuning.candidateLimit)
        var best: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var bestCost = 0.0
        var total = exp(keep)
        val seen = HashSet<String>()
        for (candidate in candidates) {
            val spelling = candidate.word
            if (!seen.add(spelling.lowercase())) continue
            if (spelling.equals(typed, ignoreCase = true)) continue
            if (!mayAddMarks && DictNormalization.normalizedKey(spelling) == typedKey) continue
            if (!shapeAllowed(typed, spelling, typedCapitalised, lengthChangeAllowance)) continue
            val cost = ChannelCost.cost(typed, spelling, tuning.weights)
            val key = WordChars.straightenAll(spelling).lowercase()
            val id = model.idOf(key)
            var prior = if (tuning.unigramMix <= 0.0) {
                model.logProbability(previousId, id)
            } else {
                ln((1 - tuning.unigramMix) * exp(model.logProbability(previousId, id)) + tuning.unigramMix * exp(model.logProbability(ContextModel.NO_CONTEXT, id)))
            }
            if (candidate.frequency < 0) prior = maxOf(prior, tuning.userWordLogPrior)
            val score = prior - tuning.channelScale * cost
            trace?.invoke(spelling, prior, cost)
            total += exp(score)
            if (score > bestScore) {
                bestScore = score
                best = spelling
                bestCost = cost
            }
        }
        trace?.invoke("", keep, 0.0)
        val chosen = best ?: return Result.Leave("no_suggestion")
        val share = exp(bestScore) / total
        if (share < tuning.commitShare) return Result.Leave(if (bestScore < keep) "kept_as_typed" else "too_close_to_call")
        if (RegionalSpelling.isVariant(typed, chosen)) return Result.Leave("regional_spelling")
        if (tuning.keepInflections && isInflectionOf(typed, chosen)) return Result.Leave("inflection_of_known_word")
        val recased = CasingRules.forTypedWord(typed, chosen)
        if (recased == typed) return Result.Leave("same_replacement")
        return Result.Commit(recased, share, bestCost)
    }

    /**
     * Whether [typed] is [candidate] plus an inflection ending (`millennials`, `rehydrated`,
     * `Afghanistani`): a dictionary holding the stem but not the form is missing a word, not
     * looking at a typo, which is §9's "pure affix change" rule kept for the endings English
     * actually adds. A doubled last letter (`toolss`) is still a slip.
     */
    private fun isInflectionOf(typed: String, candidate: String): Boolean {
        val t = WordChars.straightenAll(typed).lowercase()
        val c = WordChars.straightenAll(candidate).lowercase()
        if (!t.startsWith(c) || t.length == c.length) return false
        val ending = t.substring(c.length)
        if (ending.first() == c.last()) return false
        return ending in INFLECTIONS || (c.endsWith('e') && ending in E_INFLECTIONS)
    }

    private val INFLECTIONS = setOf("s", "es", "ed", "er", "ers", "ing", "ly", "i", "ish", "ness")
    private val E_INFLECTIONS = setOf("d", "r", "rs")

    /** Whether some loaded dictionary or word store holds [word] in this spelling, ignoring case only. spec: §10. */
    fun isExactlyKnown(word: String, dictionaries: List<DictionaryIndex>, userWords: UserWordStore): Boolean {
        if (userWords.isKnown(word)) return true
        val entries = ArrayList<WordFrequency>()
        for (dict in dictionaries) {
            entries.clear()
            dict.entriesForExactKey(word, limit = 8, into = entries)
            if (entries.any { it.word.equals(word, ignoreCase = true) }) return true
        }
        return false
    }

    /** `must've`, `should've`, `that'll`, `Sami's`, `players'`: a known word, or a name, before a contraction or possessive ending. */
    private fun isKnownWithClitic(typed: String, dictionaries: List<DictionaryIndex>, userWords: UserWordStore): Boolean {
        val straight = WordChars.straightenAll(typed)
        val at = straight.lastIndexOf('\'')
        if (at <= 0) return false
        // A plural possessive (`players'`, `scientists’`): the word before the apostrophe is the word.
        if (at == straight.length - 1) return isExactlyKnown(straight.substring(0, at), dictionaries, userWords) || straight.first().isUpperCase()
        val stem = straight.substring(0, at)
        val ending = straight.substring(at + 1).lowercase()
        if (ending !in CLITICS) return false
        if (stem.first().isUpperCase()) return true
        return isExactlyKnown(stem, dictionaries, userWords)
    }

    /** Candidates carry the entry's raw frequency, or -1 for a user word (which gets [Tuning.userWordLogPrior] when the table lacks it). */
    private fun gatherCandidates(typed: String, dictionaries: List<DictionaryIndex>, userWords: UserWordStore, maxDistance: Int, limit: Int): List<WordFrequency> {
        val found = ArrayList<WordFrequency>()
        val near = ArrayList<ScoredCandidate>()
        val variants = ArrayList<WordFrequency>()
        for (dict in dictionaries) {
            near.clear()
            dict.neighbours(typed, maxDistance = maxDistance, limit = limit, into = near)
            for (candidate in near) {
                variants.clear()
                // The fuzzy index returns one spelling per key; `its` and `it's` share one, and both are candidates.
                dict.entriesForExactKey(candidate.word, limit = 4, into = variants)
                if (variants.isEmpty()) found.add(WordFrequency(candidate.word, candidate.frequency)) else found.addAll(variants)
            }
        }
        val typedKey = DictNormalization.normalizedKey(typed)
        val userEntries = userWords.personalWords().map { it.word } + userWords.defaultWords().map { it.word }
        for (word in userEntries) {
            val key = DictNormalization.normalizedKey(word)
            if (key.isEmpty() || EditDistance.osaDistance(typedKey, key, maxDistance) > maxDistance) continue
            found.add(WordFrequency(word, -1))
        }
        return found
    }

    /**
     * The hard limits §9's safe-shape test keeps even with a probability to lean on: no
     * lowercase word becomes a capitalised one (`hallo` never becomes `Halle`), no lowercase word
     * becomes an acronym, no digits or symbols appear, and the length changes no more than the
     * language allows.
     */
    private fun shapeAllowed(typed: String, candidate: String, typedCapitalised: Boolean, lengthChangeAllowance: Int): Boolean {
        val candidateLetters = candidate.filter { it.isLetter() }
        if (candidateLetters.length < 2) return false
        if (candidate.any { !(it.isLetter() || WordChars.isApostrophe(it)) }) return false
        if (!typedCapitalised && candidateLetters.first().isUpperCase()) return false
        if (candidateLetters.length >= 2 && candidateLetters.all { it.isUpperCase() }) return false
        val lengthChange = kotlin.math.abs(candidate.length - typed.length)
        if (lengthChange > lengthChangeAllowance && !SafeShape.extraLetterDoublesNeighbour(typed, candidate)) return false
        return true
    }
}

/**
 * British and American spellings of one word are both right; a dictionary that carries only one
 * of them must not "correct" the other (`realised`, `neighbours`, `centre`, `travelled`). spec:
 * autocorrect-suggestions.md §10 (a correctly spelled word is never overwritten), applied to the
 * words the dictionary is known to lack for a regular reason.
 */
object RegionalSpelling {
    private val PAIRS = listOf(
        "our" to "or", "ise" to "ize", "isi" to "izi", "isa" to "iza", "yse" to "yze", "ysi" to "yzi",
        "tre" to "ter", "bre" to "ber", "lled" to "led", "lling" to "ling", "ller" to "ler", "ae" to "e", "oe" to "e", "ogue" to "og",
        "ence" to "ense", "mme" to "m", "eable" to "able", "dgement" to "dgment",
    )

    /** Whether [a] and [b] differ by exactly one regular British/American spelling difference. */
    fun isVariant(a: String, b: String): Boolean {
        val x = WordChars.straightenAll(a).lowercase()
        val y = WordChars.straightenAll(b).lowercase()
        if (x == y) return false
        for ((british, american) in PAIRS) {
            if (oneSwap(x, y, british, american) || oneSwap(y, x, british, american)) return true
        }
        return false
    }

    private fun oneSwap(from: String, to: String, piece: String, replacement: String): Boolean {
        var at = from.indexOf(piece)
        while (at >= 0) {
            if (from.substring(0, at) + replacement + from.substring(at + piece.length) == to) return true
            at = from.indexOf(piece, at + 1)
        }
        return false
    }
}
