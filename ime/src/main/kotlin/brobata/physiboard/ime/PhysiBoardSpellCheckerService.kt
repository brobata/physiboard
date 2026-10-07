package brobata.physiboard.ime

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.text.LengthChangeAllowance
import brobata.physiboard.core.text.SpellCheck
import brobata.physiboard.core.text.SpellFinding
import brobata.physiboard.core.text.SpellKind
import brobata.physiboard.core.text.SpellResources
import java.util.Locale

/**
 * Android's system spell checker, answered with PhysiBoard's own dictionary and engine, so apps
 * underline misspellings and offer PhysiBoard's corrections in their own suggestion popup: with the
 * suggestion bar gone, this is where alternatives show up. spec: autocorrect-suggestions.md SS18.
 * The user picks it in Android Settings > System > Languages (or Keyboard) > Spell checker.
 *
 * The judgement is `:core:text`'s [SpellCheck]; this class only turns Android's requests into calls
 * to it and its findings into [SuggestionsInfo]. The dictionaries, the word-pair table and the user
 * words are the keyboard's own copies ([SharedDictionaries]), loaded once per process off the main
 * thread; a call never reads a file. Each call is made on a binder thread, on every edit in every
 * app that spell-checks, so nothing here may block for long: the only wait is on the very first
 * call for a language whose dictionary is still loading, at most [FIRST_LOAD_WAIT_MILLIS], so the
 * text on screen when a field opens is checked rather than passed as correct. Nothing is learned,
 * stored or sent anywhere.
 */
class PhysiBoardSpellCheckerService : SpellCheckerService() {

    override fun createSession(): Session = PhysiBoardSpellSession(SharedDictionaries.get(this))

    /**
     * One app's spell-checking session, in one language. [localeOverride] stands in for
     * [Session.getLocale] when a test builds the session without the framework's binder plumbing.
     */
    internal class PhysiBoardSpellSession(
        private val shared: SharedDictionaries,
        private val localeOverride: String? = null,
    ) : Session() {

        private var language: LanguageCode? = null

        /** Verdicts of words already judged, by the word before them; emptied whenever the shared resources change. */
        private val cache = object : LinkedHashMap<String, SpellFinding>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SpellFinding>?): Boolean = size > CACHE_SIZE
        }
        private var cachedFor: SharedDictionaries.Snapshot? = null

        override fun onCreate() {
            val tag = localeOverride ?: runCatching { locale }.getOrNull()
            language = LanguageCode.fromLocale(tag?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag())
            language?.let(shared::request)
            shared.requestUserWords()
            shared.requestSystemWords()
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val info = textInfo ?: return SuggestionsInfo(0, EMPTY)
            return guarded(info, unjudged(info)) {
                val resources = resources() ?: return@guarded unjudged(info)
                toSuggestionsInfo(SpellCheck.checkWord(info.text.orEmpty(), resources, suggestionsLimit), info)
            }
        }

        override fun onGetSentenceSuggestionsMultiple(textInfos: Array<out TextInfo>?, suggestionsLimit: Int): Array<SentenceSuggestionsInfo> {
            val infos = textInfos ?: return emptyArray()
            val resources = runCatching { resources() }.getOrNull()
            return Array(infos.size) { index ->
                val info = infos[index]
                val text = info.text.orEmpty()
                val whole = SentenceSuggestionsInfo(arrayOf(unjudged(info)), intArrayOf(0), intArrayOf(text.length))
                if (resources == null) return@Array whole
                try {
                    val findings = synchronized(cache) { SpellCheck.checkSentence(text, resources, suggestionsLimit, cache) }
                    // An answer with no entry at all leaves the app's range marked as still being checked.
                    if (findings.isEmpty()) return@Array whole
                    SentenceSuggestionsInfo(
                        Array(findings.size) { toSuggestionsInfo(findings[it], info) },
                        IntArray(findings.size) { findings[it].start },
                        IntArray(findings.size) { findings[it].end - findings[it].start },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "spell check failed", e)
                    whole
                }
            }
        }

        /**
         * What the language's judgement needs, or null when it has not loaded (an answer then
         * flags nothing). Waits only for a first load already under way.
         */
        private fun resources(): SpellResources? {
            val lang = language ?: return null
            var current = shared.snapshot
            if (!current.isReady(lang)) {
                shared.request(lang)
                shared.requestUserWords()
                if (!shared.awaitReady(lang, FIRST_LOAD_WAIT_MILLIS)) return null
                current = shared.snapshot
            }
            val dictionary = current.dictionaries[lang] ?: return null
            synchronized(cache) {
                if (cachedFor !== current) {
                    cache.clear()
                    cachedFor = current
                }
            }
            return SpellResources(
                dictionaries = listOf(dictionary),
                userWords = current.userWords ?: return null,
                contextModel = current.contextModels[lang],
                lengthChangeAllowance = LengthChangeAllowance.forLanguage(lang.value),
                script = SpellResources.scriptFor(lang.value),
                systemWords = current.systemWords.orEmpty(),
            )
        }

        private inline fun guarded(info: TextInfo, fallback: SuggestionsInfo, block: () -> SuggestionsInfo): SuggestionsInfo = try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "spell check failed", e)
            fallback.also { it.setCookieAndSequence(info.cookie, info.sequence) }
        }
    }

    internal companion object {
        private const val TAG = "PhysiBoardSpell"
        private val EMPTY = emptyArray<String>()
        private const val CACHE_SIZE = 512

        /** How long the first call for a language may wait for its dictionary to finish loading. */
        const val FIRST_LOAD_WAIT_MILLIS = 1_500L

        /** Nothing to say about [info]: no underline, and any old one there is cleared. */
        fun unjudged(info: TextInfo): SuggestionsInfo = SuggestionsInfo(0, EMPTY, info.cookie, info.sequence)

        /**
         * One finding as Android reads it. The cookie and sequence must be the request's, or the
         * app cannot match the answer to its text. A known word is "in the dictionary"; a typo
         * "looks like a typo", a mix-up "looks like a grammar error" (Android draws it in its own
         * colour), and either "has recommended suggestions" when the keyboard would have made the
         * first one itself.
         */
        fun toSuggestionsInfo(finding: SpellFinding, info: TextInfo): SuggestionsInfo {
            val attributes = when (finding.kind) {
                SpellKind.KNOWN -> SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY
                SpellKind.SKIPPED, SpellKind.UNFLAGGED -> 0
                SpellKind.TYPO -> SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                SpellKind.MIXUP -> SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR
            } or if (finding.confident && finding.suggestions.isNotEmpty()) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0
            return SuggestionsInfo(attributes, finding.suggestions.toTypedArray(), info.cookie, info.sequence)
        }
    }
}
