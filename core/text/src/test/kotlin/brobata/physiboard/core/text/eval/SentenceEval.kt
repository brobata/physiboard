package brobata.physiboard.core.text.eval

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.text.AutocorrectMemory
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.BoundaryOutcome
import brobata.physiboard.core.text.ContextTuning
import brobata.physiboard.core.text.CurrentWordTracker
import brobata.physiboard.core.text.RankingOptions
import brobata.physiboard.core.text.WordChars
import java.io.File

/** How a word position's typed form relates to the dictionary, for splitting the clean-text count. */
enum class WordKind { KNOWN, UNKNOWN_LOWER, UNKNOWN_CAPITAL_START, UNKNOWN_CAPITAL_MID }

/** What happened to one word position after the whole sentence was typed. spec: autocorrect-suggestions.md §12. */
data class WordResult(
    val sentence: Int,
    val index: Int,
    val typed: String,
    val intended: String,
    val final: String,
    val outcome: CaseOutcome,
    val kind: WordKind,
    val label: String,
    /** The engine's own reason at this word's boundary (applied, or why it was left). */
    val reason: String = "",
) {
    val isControl: Boolean get() = typed == intended
    val typedKnown: Boolean get() = kind == WordKind.KNOWN
}

/** The replay's totals for one corpus; the per-word rows are kept so a regression can be read, not just counted. */
class SentenceSummary(val words: List<WordResult>, private val boundaryNanos: LongArray) {
    val fixed: Int get() = words.count { it.outcome == CaseOutcome.FIXED }
    val missed: Int get() = words.count { it.outcome == CaseOutcome.MISSED }
    val wrong: Int get() = words.count { it.outcome == CaseOutcome.WRONG }
    val clobbered: Int get() = words.count { it.outcome == CaseOutcome.CLOBBERED }
    val controls: Int get() = words.count { it.isControl }
    val typos: Int get() = words.count { !it.isControl }
    val recall: Double get() = if (typos == 0) 0.0 else fixed.toDouble() / typos

    /** Typos that happen to spell another real word: §10 forbids touching them, so only the mix-up fix ever can. */
    val realWordTypos: Int get() = words.count { !it.isControl && it.typedKnown }
    val nonWordRecall: Double get() {
        val nonWord = words.filter { !it.isControl && !it.typedKnown }
        return if (nonWord.isEmpty()) 0.0 else nonWord.count { it.outcome == CaseOutcome.FIXED }.toDouble() / nonWord.size
    }

    fun clobberedOf(kind: WordKind): Int = words.count { it.outcome == CaseOutcome.CLOBBERED && it.kind == kind }
    fun controlsOf(kind: WordKind): Int = words.count { it.isControl && it.kind == kind }

    /** Correct words changed, as "one per N words"; the headline for clean text. */
    val wordsPerClobber: String get() = if (clobbered == 0) "none in $controls" else "1 per ${controls / clobbered}"

    val boundaries: Int get() = boundaryNanos.size
    val meanMicros: Double get() = if (boundaryNanos.isEmpty()) 0.0 else boundaryNanos.average() / 1000.0
    fun percentileMicros(p: Int): Double = if (boundaryNanos.isEmpty()) 0.0 else boundaryNanos.sorted()[minOf(boundaryNanos.size - 1, boundaryNanos.size * p / 100)] / 1000.0
    val maxMicros: Double get() = (boundaryNanos.maxOrNull() ?: 0L) / 1000.0

    fun report(label: String): String =
        "$label: words=${words.size} typos=$typos fixed=$fixed missed=$missed wrong=$wrong recall=${"%.3f".format(recall)} " +
            "(non-word ${"%.3f".format(nonWordRecall)}, real-word typos $realWordTypos) controls=$controls clobbered=$clobbered " +
            "[known=${clobberedOf(WordKind.KNOWN)} lower=${clobberedOf(WordKind.UNKNOWN_LOWER)}/${controlsOf(WordKind.UNKNOWN_LOWER)} " +
            "capStart=${clobberedOf(WordKind.UNKNOWN_CAPITAL_START)}/${controlsOf(WordKind.UNKNOWN_CAPITAL_START)} " +
            "capMid=${clobberedOf(WordKind.UNKNOWN_CAPITAL_MID)}/${controlsOf(WordKind.UNKNOWN_CAPITAL_MID)}] ($wordsPerClobber) " +
            "boundary mean=${"%.0f".format(meanMicros)}us p50=${"%.0f".format(percentileMicros(50))}us p99=${"%.0f".format(percentileMicros(99))}us max=${"%.0f".format(maxMicros)}us"

    fun byLabel(): Map<String, SentenceSummary> = words.groupBy { it.label }.mapValues { (_, rows) -> SentenceSummary(rows, LongArray(0)) }
}

/**
 * Replays whole sentences word by word through the engine the keyboard runs at every boundary
 * ([EngineCall]), with the text typed so far (corrections included) as the context, exactly the
 * window the keyboard hands it, and compares the final text with the sentence intended. spec:
 * autocorrect-suggestions.md §12.
 */
class SentenceEval(
    private val dictionary: DictionaryIndex,
    private val contextModel: ContextModel?,
    private val settings: AutocorrectSettings = SHIPPED,
    private val tuning: ContextTuning = ContextTuning.DEFAULT,
    private val userWords: UserWordStore = UserWordStore.empty(),
) {
    private val rankingOptions = RankingOptions(useKeyboardProximity = settings.useKeyboardProximity, accentMatchingEnabled = settings.accentMatchingEnabled)

    /**
     * [stopAfterError], when set, replays each sentence only up to the token after its error
     * (the boundary at which a mix-up fix of the error would fire) and scores only those tokens;
     * the rest of the sentence is corpus a's own territory.
     */
    fun run(cases: List<SentenceCase>, stopAfterError: Boolean = false): SentenceSummary {
        val words = ArrayList<WordResult>()
        val durations = ArrayList<Long>()
        for (case in cases) {
            val typedTokens = SentenceCorpus.tokenize(case.typed)
            val intendedTokens = SentenceCorpus.tokenize(case.intended)
            val text = StringBuilder()
            var consumed = 0
            val reasons = HashMap<Int, String>()
            // Carried from boundary to boundary as TextInputPipeline carries it, so the mix-up fix
            // sees the previous word as typed here, in sequence; each sentence is a fresh field.
            var memory = AutocorrectMemory()
            val lastToken = if (stopAfterError && case.errorToken >= 0) minOf(case.errorToken + 1, typedTokens.size - 1) else typedTokens.size - 1
            for ((tokenIndex, token) in typedTokens.withIndex()) {
                if (tokenIndex > lastToken) break
                text.append(case.typed, consumed, token.end)
                consumed = token.end
                val boundary = token.boundary ?: continue
                val tracked = CurrentWordTracker.empty().syncedFrom(text.toString()).word
                val window = text.takeLast(tracked.length + EngineCall.CONTEXT_CHARS).toString()
                val started = System.nanoTime()
                val evaluation = EngineCall.boundary(tracked, window, boundary, dictionary, userWords, settings, rankingOptions, memory, contextModel, tuning)
                durations.add(System.nanoTime() - started)
                // The next word's first letter, as TextInputPipeline.handleLetter applies it.
                memory = evaluation.memory.afterAnyCharacterTyped().afterLetterOrDigitTyped()
                reasons[tokenIndex] = evaluation.debug.reason.ifEmpty { evaluation.debug.source ?: "" }
                val outcome = evaluation.outcome
                if (outcome is BoundaryOutcome.Replaced) {
                    // The pipeline deletes by length; the engine must only ever ask to delete what the field holds.
                    check(WordChars.straightenAll(text.takeLast(outcome.original.length).toString()) == WordChars.straightenAll(outcome.original)) {
                        "engine asked to delete '${outcome.original}' but the text ends '${text.takeLast(outcome.original.length)}'"
                    }
                    text.setLength(text.length - outcome.original.length)
                    text.append(outcome.replacement)
                }
            }
            text.append(case.typed, consumed, case.typed.length)
            val finalTokens = SentenceCorpus.tokenize(text.toString())
            if (finalTokens.size != intendedTokens.size || typedTokens.size != intendedTokens.size) {
                // A replacement changed the word count: every position counts against the engine rather than silently misaligning.
                for ((i, t) in intendedTokens.withIndex()) {
                    val typed = typedTokens.getOrNull(i)?.word ?: ""
                    words.add(WordResult(case.id, i, typed, t.word, text.toString(), if (typed == t.word) CaseOutcome.CLOBBERED else CaseOutcome.WRONG, kindOf(typed, case.typed, typedTokens.getOrNull(i)), case.label))
                }
                continue
            }
            for (i in 0..lastToken) {
                val typed = typedTokens[i].word
                val intended = intendedTokens[i].word
                val final = finalTokens[i].word
                val outcome = when {
                    typed == intended -> if (final == intended) CaseOutcome.UNTOUCHED else CaseOutcome.CLOBBERED
                    final == intended -> CaseOutcome.FIXED
                    final == typed -> CaseOutcome.MISSED
                    else -> CaseOutcome.WRONG
                }
                words.add(WordResult(case.id, i, typed, intended, final, outcome, kindOf(typed, case.typed, typedTokens[i]), case.label, reasons[i] ?: ""))
            }
        }
        return SentenceSummary(words, durations.toLongArray())
    }

    /** Known means spelled this way (ignoring case) in the dictionary or the word store: §10's own test, not just a shared key. */
    private fun kindOf(typed: String, sentence: String, token: Token?): WordKind {
        if (userWords.isKnown(typed)) return WordKind.KNOWN
        val entries = ArrayList<WordFrequency>()
        dictionary.entriesForExactKey(typed, limit = 8, into = entries)
        if (entries.any { it.word.equals(WordChars.straightenAll(typed), ignoreCase = true) || it.word.equals(typed, ignoreCase = true) }) return WordKind.KNOWN
        if (typed.firstOrNull()?.isUpperCase() != true) return WordKind.UNKNOWN_LOWER
        val before = token?.let { sentence.substring(0, it.start).trimEnd() } ?: ""
        val atStart = before.isEmpty() || before.last() in ".!?;:"
        return if (atStart) WordKind.UNKNOWN_CAPITAL_START else WordKind.UNKNOWN_CAPITAL_MID
    }

    companion object {
        /** The Titan's shipped baseline (autocorrect-suggestions.md §13): automatic correction on, distance 2, proximity on. */
        val SHIPPED = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2, useKeyboardProximity = true)

        /**
         * The shipped asset directory: Gradle passes it as `physiboard.assets.dictionaries` (core/text's build file), so the
         * 3.9 MB table is read where the app ships it rather than copied into test resources. Falls back to walking up from
         * the working directory for an IDE run.
         */
        fun assetDirectory(): File? {
            System.getProperty("physiboard.assets.dictionaries")?.let { return File(it).takeIf(File::isDirectory) }
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").isFile) return File(dir, "app/src/main/assets/dictionaries").takeIf(File::isDirectory)
                dir = dir.parentFile
            }
            return null
        }

        fun shippedDictionary(): DictionaryIndex? = assetDirectory()?.resolve("en.pbd")?.takeIf { it.isFile }?.readBytes()?.let(DictionaryIndex::fromPbdBytes)
        /** The default word list every install is seeded with (`common/dictionaries/user_defaults.json`): known words on the phone, so known here. */
        fun shippedUserWords(): UserWordStore {
            val file = assetDirectory()?.parentFile?.resolve("common/dictionaries/user_defaults.json")?.takeIf { it.isFile } ?: return UserWordStore.empty()
            return UserWordStore.of(UserWordFileCodec.decodeDefaultWords(file.readText()))
        }

        fun shippedContextModel(): ContextModel? = assetDirectory()?.resolve("en.bigrams")?.takeIf { it.isFile }?.readBytes()?.let(ContextModel::read)

        /** Rows formatted for a failure message or the plan's tables. */
        fun describe(rows: List<WordResult>, limit: Int = 40): String =
            rows.take(limit).joinToString("\n") { "  ${it.outcome} '${it.typed}' -> '${it.final}' (wanted '${it.intended}', ${it.kind}, ${it.reason}) [${it.label}] #${it.sentence}" }

        /** Lowercase, straight-apostrophe spelling, the form the context table and the confusion sets use. */
        fun key(word: String): String = WordChars.straightenAll(word).lowercase()
    }
}
