package brobata.physiboard.core.text.eval

import brobata.physiboard.core.text.TitanKeyGeometry
import brobata.physiboard.core.text.WordChars
import brobata.physiboard.core.text.WordMixups
import kotlin.random.Random

/**
 * One word of a sentence as the keyboard would see it: its spelling, the boundary key that ends
 * it (null when the next character is something the keyboard never evaluates a boundary for, a
 * hyphen say), and where it sits in the sentence so the replay can rebuild the text around it.
 */
data class Token(val word: String, val start: Int, val end: Int, val boundary: Char?)

/** A sentence to replay: [typed] is what the fingers produce, [intended] what the sentence should read; they differ in at most one token. */
data class SentenceCase(val id: Int, val typed: String, val intended: String, val errorToken: Int = -1, val label: String = "")

/**
 * Loads the held-out Tatoeba sentences and derives the four sentence-level corpora
 * autocorrect-suggestions.md §12 describes from them: the clean text, the synthetic Titan typos,
 * and the mix-up flips; the classic misspellings come from a TSV of their own.
 */
object SentenceCorpus {

    /** Tokenises the way `CurrentWordTracker`/`WordChars` would: letters and digits, an apostrophe only between two of them. */
    fun tokenize(sentence: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < sentence.length) {
            if (!sentence[i].isLetterOrDigit()) { i++; continue }
            val start = i
            while (i < sentence.length && WordChars.isWordChar(sentence, i)) i++
            val end = i
            val next = sentence.getOrNull(end)
            val boundary = when {
                next == null -> ' '
                next.isWhitespace() -> ' '
                next in WordChars.BOUNDARY_PUNCTUATION -> next
                else -> null
            }
            tokens.add(Token(sentence.substring(start, end), start, end, boundary))
        }
        return tokens
    }

    fun heldOutSentences(text: String): List<String> = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    /** Corpus a: every sentence typed as written. */
    fun clean(sentences: List<String>): List<SentenceCase> = sentences.mapIndexed { i, s -> SentenceCase(i, s, s) }

    enum class TypoKind { ADJACENT_SUBSTITUTION, DROPPED_LETTER, DOUBLED_LETTER, TRANSPOSITION, ADJACENT_INSERTION }

    /**
     * Corpus b: one typo per sentence, in a word of three or more letters, the five kinds in
     * equal measure, deterministic for [seed]. A word with a digit or an apostrophe is skipped; a
     * sentence with no eligible word is dropped.
     */
    fun withTitanTypos(sentences: List<String>, seed: Int = 20261005): List<SentenceCase> {
        val random = Random(seed)
        val cases = ArrayList<SentenceCase>()
        for ((i, sentence) in sentences.withIndex()) {
            val tokens = tokenize(sentence)
            val eligible = tokens.withIndex().filter { (_, t) -> t.boundary != null && t.word.length >= 3 && t.word.all { it.lowercaseChar() in 'a'..'z' } }
            if (eligible.isEmpty()) continue
            val (index, token) = eligible[random.nextInt(eligible.size)]
            val kind = TypoKind.entries[random.nextInt(TypoKind.entries.size)]
            val typo = inject(token.word, kind, random) ?: continue
            val typed = sentence.substring(0, token.start) + typo + sentence.substring(token.end)
            cases.add(SentenceCase(i, typed, sentence, errorToken = index, label = kind.name))
        }
        return cases
    }

    private fun inject(word: String, kind: TypoKind, random: Random): String? {
        fun recase(replacement: Char, like: Char): Char = if (like.isUpperCase()) replacement.uppercaseChar() else replacement
        return when (kind) {
            TypoKind.ADJACENT_SUBSTITUTION -> {
                val i = random.nextInt(word.length)
                val neighbours = TitanKeyGeometry.neighbours(word[i])
                if (neighbours.isEmpty()) return null
                word.substring(0, i) + recase(neighbours[random.nextInt(neighbours.size)], word[i]) + word.substring(i + 1)
            }
            TypoKind.DROPPED_LETTER -> {
                val i = random.nextInt(word.length)
                word.substring(0, i) + word.substring(i + 1)
            }
            TypoKind.DOUBLED_LETTER -> {
                val i = random.nextInt(word.length)
                word.substring(0, i + 1) + word[i].lowercaseChar() + word.substring(i + 1)
            }
            TypoKind.TRANSPOSITION -> {
                val candidates = (0 until word.length - 1).filter { word[it].lowercaseChar() != word[it + 1].lowercaseChar() }
                if (candidates.isEmpty()) return null
                val i = candidates[random.nextInt(candidates.size)]
                word.substring(0, i) + recase(word[i + 1].lowercaseChar(), word[i]) + recase(word[i].lowercaseChar(), word[i + 1]) + word.substring(i + 2)
            }
            TypoKind.ADJACENT_INSERTION -> {
                val i = random.nextInt(word.length)
                val neighbours = TitanKeyGeometry.neighbours(word[i])
                if (neighbours.isEmpty()) return null
                word.substring(0, i + 1) + neighbours[random.nextInt(neighbours.size)] + word.substring(i + 1)
            }
        }
    }

    /**
     * Corpus d: for every confusion set, the held-out sentences that use one of its members, each
     * flipped to every other member (case preserved). The sentence as written is corpus a's own
     * row for that position; [SentenceCase.label] names the set so results group per pair.
     */
    fun mixupFlips(sentences: List<String>, perMemberLimit: Int = 100): List<SentenceCase> {
        val cases = ArrayList<SentenceCase>()
        for (set in WordMixups.measured) {
            val label = set.sorted().joinToString("/")
            val perMember = HashMap<String, Int>()
            for ((i, sentence) in sentences.withIndex()) {
                val tokens = tokenize(sentence)
                for ((index, token) in tokens.withIndex()) {
                    if (token.boundary == null) continue
                    val lower = WordChars.straightenAll(token.word).lowercase()
                    if (lower !in set) continue
                    val seen = perMember.getOrDefault(lower, 0)
                    if (seen >= perMemberLimit) continue
                    perMember[lower] = seen + 1
                    for (alternative in set) {
                        if (alternative == lower) continue
                        // Case is kept, except that "I'll" typed without its apostrophe comes out as the
                        // lowercase "ill" a thumb actually types, which the fix must turn back into "I'll".
                        val pronoun = lower.startsWith("i'") && token.start > 0
                        val flipped = if (token.word[0].isUpperCase() && !pronoun) alternative.replaceFirstChar { it.uppercaseChar() } else alternative
                        val typed = sentence.substring(0, token.start) + flipped + sentence.substring(token.end)
                        cases.add(SentenceCase(i, typed, sentence, errorToken = index, label = label))
                    }
                }
            }
        }
        return cases
    }

    /** Corpus c: `typed<TAB>intended` rows typed as a one-word sentence, which is how a lone word in an empty field reaches the engine. */
    fun standalone(text: String, label: String): List<SentenceCase> = text.lineSequence()
        .drop(1)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapIndexed { i, line ->
            val parts = line.split('\t')
            val typed = parts[0].trim()
            val intended = parts[1].trim()
            SentenceCase(i, typed, intended, errorToken = if (typed == intended) -1 else 0, label = label)
        }
        .toList()
}
