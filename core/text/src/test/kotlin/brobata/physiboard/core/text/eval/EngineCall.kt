package brobata.physiboard.core.text.eval

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.text.AutocorrectMemory
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.BoundaryEngine
import brobata.physiboard.core.text.BoundaryEvaluation
import brobata.physiboard.core.text.ContextTuning
import brobata.physiboard.core.text.RankingOptions

/**
 * The harness's one call into the engine, kept in a file of its own so the same replay can be
 * pointed at an older engine (the BEFORE column of docs/plans/autocorrect-context.md was measured
 * by swapping this file into a checkout of a357dee, whose engine had no context parameters).
 */
internal object EngineCall {
    fun boundary(
        trackedWord: String,
        window: String,
        boundary: Char,
        dictionary: DictionaryIndex,
        userWords: UserWordStore,
        settings: AutocorrectSettings,
        rankingOptions: RankingOptions,
        memory: AutocorrectMemory,
        model: ContextModel?,
        tuning: ContextTuning,
    ): BoundaryEvaluation = BoundaryEngine.evaluate(
        trackedWord = trackedWord,
        textBeforeCursor = window,
        boundaryChar = boundary,
        ruleSets = emptyList(),
        dictionaries = listOf(dictionary),
        userWords = userWords,
        settings = settings,
        rankingOptions = rankingOptions,
        lengthChangeAllowance = 2,
        memory = memory,
        contextModel = model,
        contextTuning = tuning,
    )

    /** The window the keyboard hands the engine: the word and this many characters before it (TextInputPipeline). */
    const val CONTEXT_CHARS: Int = BoundaryEngine.CONTEXT_WINDOW
}
