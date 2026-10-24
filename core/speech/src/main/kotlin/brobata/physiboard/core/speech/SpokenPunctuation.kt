package brobata.physiboard.core.speech

/**
 * Spoken punctuation: the words "comma", "period", "question mark" and their kin, said while
 * dictating, become the marks they name. spec: dictation.md SS7.7.
 *
 * Why the keyboard does this itself: Google's recognizer, asked through the public API, writes
 * the words out. `EXTRA_ENABLE_FORMATTING` documents "unspoken punctuation" only (the marks the
 * engine infers from the speech), nothing in `RecognizerIntent` names spoken punctuation, and
 * Chromium's own continuous client sets no extra for it; Gboard's "say comma" runs inside Gboard
 * (D29). The maintainer's dictated report of 2026-10-23 is the symptom: "then a period" came out
 * as "then appear. Period", the word written, capitalised as a new sentence after the engine's
 * own full stop.
 *
 * The rules, in the order they are applied to the words of one result:
 *
 * - A phrase of the English table ([Vocabulary.ENGLISH]) becomes its mark. "Three exclamation
 *   points" (a count word or digits, then the plural) repeats it; a number before the singular
 *   is dictation ("see you at 5 period"); two phrases in a row join ("question mark exclamation
 *   point" is "?!").
 * - A phrase said directly after a determiner ("a period of time", "the comma in that sentence")
 *   is the noun, and stays a word. So does a plural with no count ("commas"). This is the whole
 *   of the ambiguity rule: Gboard converts every time; here one word of warning keeps the word.
 * - The spoken mark wins over the engine's: a mark the engine attached to the phrase itself
 *   ("comma," or "Period.") or to the word before it ("appear. Period") is dropped. When the
 *   dropped mark was a sentence end and the spoken mark is not, the capital the engine put on
 *   the next word is lowered ("comma. Then" reads ", then").
 * - Spacing: none before `, . ? ! : ;`, the closing quote and parenthesis, and the ellipsis; a
 *   space after. A space before the opening quote and parenthesis, none after. None either side
 *   of a hyphen or an apostrophe; a dash is spaced on both sides. A new line or paragraph takes
 *   the spaces before it with it and is followed by none.
 * - Capitalisation after a converted mark is [DictationCapitalization]'s business, by the same
 *   rule as typing: after `.`, `?`, `!` and a new line or paragraph, never after a comma.
 *
 * English only: the table is looked up by the dictation language, and any other language passes
 * through untouched (spec SS7.7 says so; the words of other languages are a later table, not a
 * guess made here). Pure Kotlin, so every row is a unit test.
 */
object SpokenPunctuation {

    /** How a mark sits against its neighbours. */
    enum class Placement {
        /** No space before, a space after: `, . ? ! : ;`, a closing quote or parenthesis, an ellipsis. */
        CLOSE,

        /** A space before, none after: an opening quote or parenthesis. */
        OPEN,

        /** No space either side: a hyphen, an apostrophe. */
        GLUE,

        /** A space either side: a dash said as such. */
        SPACED,

        /** Ends the line: spaces before it go, nothing follows on the same line. */
        BREAK,
    }

    data class Mark(val symbol: String, val placement: Placement) {
        /** A mark after which the next sentence starts: capitalised by [DictationCapitalization], and the engine's capital after it is left alone. */
        val endsSentence: Boolean get() = placement == Placement.BREAK || symbol in SENTENCE_ENDS
    }

    /** One language's table: phrases (lower case, single spaces) to marks, plus the plural forms a count may precede. */
    class Vocabulary(
        phrases: Map<String, Mark>,
        plurals: Map<String, Mark>,
        /** The words that make the following phrase a noun ("a period", "the comma"). */
        val determiners: Set<String>,
        /** The words that make the phrase before them a noun ("period of time"). */
        val nounFollowers: Set<String>,
        /** Count words that repeat the plural after them ("three exclamation points"); digits always count. */
        val counts: Map<String, Int>,
    ) {
        val phrases: Map<List<String>, Mark> = phrases.mapKeys { it.key.split(' ') }
        val plurals: Map<List<String>, Mark> = plurals.mapKeys { it.key.split(' ') }
        val longestPhrase: Int = (this.phrases.keys + this.plurals.keys).maxOf { it.size }

        companion object {
            private val COMMA = Mark(",", Placement.CLOSE)
            private val PERIOD = Mark(".", Placement.CLOSE)
            private val QUESTION = Mark("?", Placement.CLOSE)
            private val EXCLAMATION = Mark("!", Placement.CLOSE)
            private val COLON = Mark(":", Placement.CLOSE)
            private val SEMICOLON = Mark(";", Placement.CLOSE)
            private val ELLIPSIS = Mark("...", Placement.CLOSE)
            private val OPEN_QUOTE = Mark("\"", Placement.OPEN)
            private val CLOSE_QUOTE = Mark("\"", Placement.CLOSE)
            private val OPEN_PAREN = Mark("(", Placement.OPEN)
            private val CLOSE_PAREN = Mark(")", Placement.CLOSE)
            private val HYPHEN = Mark("-", Placement.GLUE)
            private val DASH = Mark("-", Placement.SPACED)
            private val APOSTROPHE = Mark("'", Placement.GLUE)
            private val NEW_LINE = Mark("\n", Placement.BREAK)
            private val NEW_PARAGRAPH = Mark("\n\n", Placement.BREAK)

            val ENGLISH = Vocabulary(
                phrases = mapOf(
                    "comma" to COMMA,
                    "period" to PERIOD, "full stop" to PERIOD, "fullstop" to PERIOD,
                    "question mark" to QUESTION,
                    "exclamation point" to EXCLAMATION, "exclamation mark" to EXCLAMATION,
                    "colon" to COLON,
                    "semicolon" to SEMICOLON, "semi colon" to SEMICOLON,
                    "ellipsis" to ELLIPSIS, "dot dot dot" to ELLIPSIS,
                    "open quote" to OPEN_QUOTE, "open quotes" to OPEN_QUOTE, "begin quote" to OPEN_QUOTE, "open quotation mark" to OPEN_QUOTE,
                    "close quote" to CLOSE_QUOTE, "close quotes" to CLOSE_QUOTE, "end quote" to CLOSE_QUOTE, "unquote" to CLOSE_QUOTE, "close quotation mark" to CLOSE_QUOTE,
                    "open paren" to OPEN_PAREN, "open parenthesis" to OPEN_PAREN, "left paren" to OPEN_PAREN, "left parenthesis" to OPEN_PAREN,
                    "close paren" to CLOSE_PAREN, "close parenthesis" to CLOSE_PAREN, "right paren" to CLOSE_PAREN, "right parenthesis" to CLOSE_PAREN,
                    "hyphen" to HYPHEN,
                    "dash" to DASH,
                    "apostrophe" to APOSTROPHE,
                    "new line" to NEW_LINE, "newline" to NEW_LINE,
                    "new paragraph" to NEW_PARAGRAPH,
                ),
                plurals = mapOf(
                    "commas" to COMMA,
                    "periods" to PERIOD, "full stops" to PERIOD,
                    "question marks" to QUESTION,
                    "exclamation points" to EXCLAMATION, "exclamation marks" to EXCLAMATION,
                    "colons" to COLON,
                    "semicolons" to SEMICOLON,
                    // not "new lines": "two new lines of code" is prose far more often than two breaks.
                ),
                // Only the words that are nothing but a determiner. "her", "that", "no" and
                // their kin end sentences too often ("I told her period", "don't do that
                // period", "the answer is no period") to be allowed to keep the word.
                determiners = setOf("a", "an", "the", "each", "every", "another", "my", "your", "our", "their"),
                nounFollowers = setOf("of"),
                counts = mapOf("two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10),
            )
        }
    }

    private const val SENTENCE_ENDS = ".?!"

    /** The engine's own marks a spoken one replaces when they touch it. */
    internal const val ENGINE_MARKS = ",.?!;:"

    /** The most repeats a count may ask for; "a hundred exclamation points" is not a sentence anyone wants typed. */
    private const val MAX_REPEAT = 10

    /** The table for [languageTag] (spec SS5.1's tag, or a bare language), or `null` when this module has none for it. */
    fun vocabularyFor(languageTag: String?): Vocabulary? {
        val language = languageTag?.trim()?.replace('_', '-')?.substringBefore('-')?.lowercase()
        // The keyboard ships one language, English; a missing tag is that language, as the rest of this module assumes.
        return if (language.isNullOrEmpty() || language == "en") Vocabulary.ENGLISH else null
    }

    /**
     * A result after conversion, with what the spacing of SS7.6 needs to know about its edges:
     * the placement of the mark it opens with and of the mark it ends with, `null` for a word.
     * Read from what was converted, never from the text's characters, so the engine's own
     * "'Cause" or "-5 degrees" opens with a word, not a mark.
     */
    data class Converted(val text: String, val opening: Placement? = null, val closing: Placement? = null) {
        /** Opens with a mark that sits against the word before it, or with a line break: no leading space, and the field's end is joined over. */
        val joinsToPrevious: Boolean get() = opening == Placement.CLOSE || opening == Placement.GLUE || opening == Placement.BREAK

        /** Ends where nothing may follow on the same line, or on a mark that wants its word right after it: no trailing space. */
        val endsWithoutSpace: Boolean get() = closing == Placement.OPEN || closing == Placement.GLUE || closing == Placement.BREAK

        /** Ends on a mark the user said, which the next utterance's spoken mark joins onto rather than replaces. */
        val endsWithSpokenMark: Boolean get() = closing != null
    }

    /** [text] with its spoken punctuation converted; [text] itself, as a plain word, when nothing in it is a phrase of [vocabulary]. */
    fun apply(text: String, vocabulary: Vocabulary): Converted {
        if (text.isEmpty()) return Converted(text)
        val tokens = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map(Token::parse)
        val out = Assembler()
        var i = 0
        var lowerNextCapital = false
        var converted = false
        while (i < tokens.size) {
            val match = matchAt(tokens, i, vocabulary)
            if (match == null) {
                val token = tokens[i]
                out.word(if (lowerNextCapital) token.lowerEngineCapital() else token)
                lowerNextCapital = false
                i++
                continue
            }
            converted = true
            // Only a mark that takes the engine's place replaces it; a line break, an opening
            // mark or a dash stands after it ("See you then. New paragraph" keeps the full stop).
            val replacesEngineMark = match.mark.placement == Placement.CLOSE || match.mark.placement == Placement.GLUE
            val absorbedSentenceEnd = (replacesEngineMark && out.dropTrailingEngineMark()) || match.attachedMark.any { it in SENTENCE_ENDS }
            repeat(match.repeat) { out.mark(match.mark) }
            // The engine's own mark on the phrase goes with the phrase; anything else on it (a closing quote) stays.
            out.append(match.attachedMark.filter { it !in ENGINE_MARKS })
            // The capital after an absorbed sentence end was the engine's; a comma does not start a sentence.
            lowerNextCapital = absorbedSentenceEnd && !match.mark.endsSentence
            i += match.consumed
        }
        return if (converted) out.result() else Converted(text)
    }

    private class Match(val mark: Mark, val repeat: Int, val consumed: Int, val attachedMark: String)

    /**
     * The phrase starting at [start], longest first, with a count before a plural only: a number
     * before the singular is dictation ("see you at 5 period", "step two period"), and the
     * plural is what says the number counts marks. `null` when the words are not a phrase or
     * the ambiguity rule keeps them as words: a determiner before them, a plural with no count,
     * or the engine's own opening punctuation on them.
     */
    private fun matchAt(tokens: List<Token>, start: Int, vocabulary: Vocabulary): Match? {
        val previous = tokens.getOrNull(start - 1)
        // A word the engine ended its sentence on cannot be this phrase's determiner ("That. Period" is a mark).
        val afterDeterminer = previous != null && previous.trailing.isEmpty() && previous.core.lowercase() in vocabulary.determiners
        if (tokens[start].leading.isNotEmpty()) return null
        fun isNoun(end: Int): Boolean {
            if (afterDeterminer) return true
            // "a long period of time": the noun's own complement follows it, with no mark between.
            val last = tokens[end - 1]
            val next = tokens.getOrNull(end) ?: return false
            return last.trailing.isEmpty() && next.core.lowercase() in vocabulary.nounFollowers
        }
        val count = countOf(tokens[start], vocabulary)
        val phraseStart = if (count != null) start + 1 else start
        if (count != null && tokens.getOrNull(phraseStart)?.leading?.isNotEmpty() == true) return null
        for (length in vocabulary.longestPhrase downTo 1) {
            if (phraseStart + length > tokens.size) continue
            val words = tokens.subList(phraseStart, phraseStart + length)
            // A phrase carries at most the engine's mark on its last word; one inside it is two words, not a phrase.
            if (words.dropLast(1).any { it.trailing.isNotEmpty() }) continue
            val key = words.map { it.core.lowercase() }
            if (count != null) {
                val mark = vocabulary.plurals[key] ?: continue
                if (isNoun(phraseStart + length)) return null
                return Match(mark, count, length + 1, words.last().trailing)
            }
            val mark = vocabulary.phrases[key] ?: continue
            if (isNoun(phraseStart + length)) return null
            return Match(mark, 1, length, words.last().trailing)
        }
        return null
    }

    private fun countOf(token: Token, vocabulary: Vocabulary): Int? {
        if (token.trailing.isNotEmpty()) return null
        val word = token.core.lowercase()
        val count = vocabulary.counts[word] ?: word.toIntOrNull()?.takeIf { word.all(Char::isDigit) } ?: return null
        return count.coerceIn(1, MAX_REPEAT)
    }

    /** One whitespace-delimited word of the result: the engine's punctuation either side of its letters, kept apart so a mark on it can be judged. */
    internal class Token(val leading: String, val core: String, val trailing: String) {
        fun lowerEngineCapital(): Token = Token(leading, lowerEngineSegmentCapital(core), trailing)

        override fun toString(): String = leading + core + trailing

        companion object {
            fun parse(word: String): Token {
                val coreStart = word.indexOfFirst { it.isLetterOrDigit() }
                if (coreStart < 0) return Token("", word, "")
                val coreEnd = word.indexOfLast { it.isLetterOrDigit() } + 1
                return Token(word.substring(0, coreStart), word.substring(coreStart, coreEnd), word.substring(coreEnd))
            }
        }
    }

    /** Builds the converted text, deciding the space before each piece from the piece before it. */
    private class Assembler {
        private val out = StringBuilder()
        private var last: Placement? = null // null: a word, or nothing yet
        private var lastWordEnd = -1 // where the last word's letters end, for taking back the engine's mark on it
        private var opening: Placement? = null // the first piece, when it was a mark
        private var closing: Placement? = null // the last piece, when it was a mark
        private var empty = true

        fun word(token: Token) {
            if (out.isNotEmpty() && last != Placement.OPEN && last != Placement.GLUE && last != Placement.BREAK) out.append(' ')
            out.append(token.leading).append(token.core)
            lastWordEnd = out.length
            out.append(token.trailing)
            last = null
            closing = null
            empty = false
        }

        /** Drops the engine's mark on the word just written (the spoken mark replaces it); true when that mark ended a sentence. */
        fun dropTrailingEngineMark(): Boolean {
            if (last != null || lastWordEnd < 0 || lastWordEnd >= out.length) return false
            val attached = out.substring(lastWordEnd)
            if (attached.isEmpty() || !attached.all { it in ENGINE_MARKS }) return false
            out.setLength(lastWordEnd)
            return attached.any { it in SENTENCE_ENDS }
        }

        fun mark(mark: Mark) {
            when (mark.placement) {
                Placement.CLOSE -> out.append(mark.symbol)
                Placement.OPEN, Placement.SPACED -> {
                    if (out.isNotEmpty() && last != Placement.OPEN && last != Placement.GLUE && last != Placement.BREAK) out.append(' ')
                    out.append(mark.symbol)
                }
                Placement.GLUE -> out.append(mark.symbol)
                Placement.BREAK -> {
                    while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
                    out.append(mark.symbol)
                }
            }
            last = if (mark.placement == Placement.SPACED) null else mark.placement
            lastWordEnd = -1
            if (empty) opening = mark.placement
            closing = mark.placement
            empty = false
        }

        /** Characters that ride on the mark just written, with no space and no change of placement. */
        fun append(text: String) {
            out.append(text)
        }

        fun result(): Converted = Converted(out.toString(), opening, closing)
    }

    private val WHITESPACE = Regex("\\s+")
}

/**
 * Texting-style spellings for the sounds people make and want written the way they text them.
 * spec: dictation.md SS7.8. Two conservative rewrites and nothing more:
 *
 * - A run of two or more laugh syllables ("ha ha ha", "Ha ha", "heh heh") is joined ("hahaha",
 *   "Haha", "hehheh"). One syllable alone, "lol", and anything already joined are left as they
 *   are. The syllables are the ones a recognizer can emit for laughter: `ha`, `hah`, `heh`,
 *   `hee`, and `haha`/`hehe` with any number of repeats; never `he`, a pronoun.
 * - "aw" and "aww" (not words otherwise) become "aww" wherever they stand. "awe" and "daw",
 *   which are words, become "aww" and "daww" only as an interjection: first in the sentence
 *   (the start of the result, or after `.`, `!`, `?` or a line break) or standing alone, and
 *   followed by nothing, a mark, a capitalised word (a name), or one of the words such a sigh
 *   leads into ("that's", "so", "poor", "thanks", ...). "In awe of" and "a jackdaw" are never
 *   touched, nor is "dog", which is what a long "dawww" most likely comes back as.
 *
 * One fixed spelling each. The transcript carries no durations, so how long a vowel was held
 * cannot be known; "aww" and "daww" are the texting spellings, and no heuristic guesses longer.
 */
object TextingStyle {
    private val LAUGH_SYLLABLE = Regex("(?i)^(ha|hah|heh|hee|(?:ha){2,}|(?:he){2,}h?)$")
    private val AW = Regex("(?i)^aw+$")
    private val AWE = Regex("(?i)^awe$")
    private val DAW = Regex("(?i)^daw+$")
    private val FOLLOWERS = setOf(
        "that's", "thats", "that", "this", "so", "poor", "thanks", "thank", "you", "how", "what", "he's", "she's", "it's", "they're",
        "look", "bless", "sweet", "cute", "man", "shucks", "no", "yes", "yeah", "well", "come", "okay", "ok", "babe", "baby", "honey", "i",
    )

    /**
     * Line by line, so a spoken "new line" (SS7.7) survives and starts a sentence of its own.
     * [atSentenceStart] is whether the result itself begins a sentence (the frozen context's
     * answer, [shouldCapitaliseAt]): "I was in", a pause, "awe." is not a sigh.
     */
    fun apply(text: String, atSentenceStart: Boolean = true): String {
        if (text.isEmpty() || !text.contains('\n')) return applyLine(text, atSentenceStart)
        return text.split('\n').mapIndexed { index, line -> applyLine(line, atSentenceStart || index > 0) }.joinToString("\n")
    }

    private fun applyLine(text: String, atSentenceStart: Boolean): String {
        if (text.isEmpty()) return text
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(SpokenPunctuation.Token::parse)
        val out = mutableListOf<String>()
        var changed = false
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            // A laugh run: syllables with no mark between them; the last may carry the engine's mark.
            var end = i
            while (end < tokens.size &&
                LAUGH_SYLLABLE.matches(tokens[end].core) &&
                (end == i || (tokens[end].leading.isEmpty() && tokens[end - 1].trailing.isEmpty()))
            ) end++
            if (end - i >= 2) {
                val joined = tokens.subList(i, end)
                out += joined.first().leading + joined.first().core + joined.drop(1).joinToString("") { it.core.lowercase() } + joined.last().trailing
                changed = true
                i = end
                continue
            }
            val sigh = sighFor(tokens, i, atSentenceStart)
            if (sigh != null) {
                out += token.leading + matchCase(token.core, sigh) + token.trailing
                changed = true
            } else {
                out += token.toString()
            }
            i++
        }
        return if (changed) out.joinToString(" ") else text
    }

    private fun sighFor(tokens: List<SpokenPunctuation.Token>, i: Int, atSentenceStart: Boolean): String? {
        val core = tokens[i].core
        if (AW.matches(core)) return "aww"
        val replacement = when {
            AWE.matches(core) -> "aww"
            DAW.matches(core) -> "daww"
            else -> return null
        }
        if (!startsSentence(tokens, i, atSentenceStart) || !leadsIntoSigh(tokens, i)) return null
        return replacement
    }

    private fun startsSentence(tokens: List<SpokenPunctuation.Token>, i: Int, atSentenceStart: Boolean): Boolean {
        if (i == 0) return atSentenceStart
        return tokens[i - 1].trailing.any { it == '.' || it == '!' || it == '?' }
    }

    private fun leadsIntoSigh(tokens: List<SpokenPunctuation.Token>, i: Int): Boolean {
        if (tokens[i].trailing.isNotEmpty()) return true
        val next = tokens.getOrNull(i + 1) ?: return true
        if (next.leading.isNotEmpty()) return true
        if (next.core.firstOrNull()?.isUpperCase() == true) return true
        return next.core.lowercase() in FOLLOWERS
    }

    /** [replacement] in the case of [original]'s first letter, so "Aw" stays a sentence start. */
    private fun matchCase(original: String, replacement: String): String =
        if (original.firstOrNull()?.isUpperCase() == true) replacement.replaceFirstChar { it.titlecase() } else replacement
}
