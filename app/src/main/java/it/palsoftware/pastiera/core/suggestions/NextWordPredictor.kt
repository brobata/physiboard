package it.palsoftware.pastiera.core.suggestions

import android.util.Log
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NextWordPredictor(
    private val store: UserNGramRepository,
    asyncLearning: Boolean = true
) {
    private val pendingLearning = PendingLearningOverlay()
    private val learningQueue = if (asyncLearning) {
        AsyncLearningQueue(store)
    } else {
        null
    }

    fun learn(locale: Locale, previousWord: String?, nextWord: String?) {
        val prefix = normalizedKey(previousWord, locale) ?: return
        val displayNextWord = cleanDisplayWord(nextWord) ?: return
        learnNormalized(locale.toLanguageTag(), prefix, displayNextWord)
    }

    fun learnSentenceStart(locale: Locale, firstWord: String?) {
        val displayWord = cleanDisplayWord(firstWord) ?: return
        learnNormalized(locale.toLanguageTag(), SENTENCE_START_PREFIX, displayWord)
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
        val localeTag = locale.toLanguageTag()
        val removedPending = pendingLearning.removeAll(localeTag, prefix, displayNextWord)
        return store.delete(localeTag, prefix, displayNextWord) > 0 || removedPending
    }

    fun forgetSentenceStart(locale: Locale, firstWord: String?): Boolean {
        val displayWord = cleanDisplayWord(firstWord) ?: return false
        val localeTag = locale.toLanguageTag()
        val removedPending = pendingLearning.removeAll(localeTag, SENTENCE_START_PREFIX, displayWord)
        return store.delete(localeTag, SENTENCE_START_PREFIX, displayWord) > 0 || removedPending
    }

    fun forgetNextWordEverywhere(locale: Locale, nextWord: String?): Boolean {
        val displayNextWord = cleanDisplayWord(nextWord) ?: return false
        val localeTag = locale.toLanguageTag()
        val removedPending = pendingLearning.removeNextWord(localeTag, displayNextWord)
        return store.deleteNextWord(localeTag, displayNextWord) > 0 || removedPending
    }

    private fun learnNormalized(localeTag: String, prefix: String, displayNextWord: String) {
        val nowMs = System.currentTimeMillis()
        if (learningQueue == null) {
            store.learn(localeTag, prefix, displayNextWord, nowMs)
            return
        }

        pendingLearning.add(localeTag, prefix, displayNextWord, nowMs)
        val queued = learningQueue.enqueue(
            localeTag = localeTag,
            prefix = prefix,
            nextWord = displayNextWord,
            nowMs = nowMs,
            shouldPersist = {
                pendingLearning.contains(localeTag, prefix, displayNextWord)
            },
            onFinished = {
                pendingLearning.removeOne(localeTag, prefix, displayNextWord)
            }
        )
        if (!queued) {
            pendingLearning.removeOne(localeTag, prefix, displayNextWord)
        }
    }

    private fun predictForPrefix(locale: Locale, prefix: String, limit: Int): List<SuggestionResult> {
        val localeTag = locale.toLanguageTag()
        val stored = store.predict(localeTag, prefix, limit)
        val pending = pendingLearning.predict(localeTag, prefix)
        return mergePredictions(stored, pending)
            .take(limit)
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
        flushLearningForTests()
        pendingLearning.clear()
        store.clearAll()
    }

    internal fun flushLearningForTests() {
        runBlocking {
            learningQueue?.flush()
        }
    }

    fun destroy() {
        learningQueue?.destroy()
        pendingLearning.clear()
    }

    private fun mergePredictions(
        stored: List<UserNGramStore.Prediction>,
        pending: List<UserNGramStore.Prediction>
    ): List<UserNGramStore.Prediction> {
        if (pending.isEmpty()) return stored
        val merged = linkedMapOf<String, UserNGramStore.Prediction>()
        (stored + pending).forEach { prediction ->
            val existing = merged[prediction.word]
            merged[prediction.word] = if (existing == null) {
                prediction
            } else {
                existing.copy(
                    count = existing.count + prediction.count,
                    lastUsed = maxOf(existing.lastUsed, prediction.lastUsed)
                )
            }
        }
        return merged.values.sortedWith(
            compareByDescending<UserNGramStore.Prediction> { it.count }
                .thenByDescending { it.lastUsed }
        )
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
        private const val TAG = "NextWordPredictor"
        private const val SENTENCE_START_PREFIX = "__sentence_start__"
        private val COMBINING_MARKS_REGEX = "\\p{Mn}".toRegex()
        private val NON_WORD_KEY_REGEX = "[^\\p{L}\\p{N}]".toRegex()
    }

    private class PendingLearningOverlay {
        private val lock = Any()
        private val pending = mutableMapOf<Key, PendingPrediction>()

        fun add(localeTag: String, prefix: String, nextWord: String, nowMs: Long) {
            synchronized(lock) {
                val key = Key(localeTag, prefix, nextWord)
                val existing = pending[key]
                pending[key] = PendingPrediction(
                    count = (existing?.count ?: 0) + 1,
                    lastUsed = nowMs
                )
            }
        }

        fun removeOne(localeTag: String, prefix: String, nextWord: String): Boolean {
            synchronized(lock) {
                val key = Key(localeTag, prefix, nextWord)
                val existing = pending[key] ?: return false
                if (existing.count <= 1) {
                    pending.remove(key)
                } else {
                    pending[key] = existing.copy(count = existing.count - 1)
                }
                return true
            }
        }

        fun removeAll(localeTag: String, prefix: String, nextWord: String): Boolean {
            synchronized(lock) {
                return pending.remove(Key(localeTag, prefix, nextWord)) != null
            }
        }

        fun contains(localeTag: String, prefix: String, nextWord: String): Boolean {
            synchronized(lock) {
                return pending.containsKey(Key(localeTag, prefix, nextWord))
            }
        }

        fun removeNextWord(localeTag: String, nextWord: String): Boolean {
            synchronized(lock) {
                val keys = pending.keys
                    .filter { it.localeTag == localeTag && it.nextWord.equals(nextWord, ignoreCase = true) }
                keys.forEach { pending.remove(it) }
                return keys.isNotEmpty()
            }
        }

        fun predict(localeTag: String, prefix: String): List<UserNGramStore.Prediction> {
            synchronized(lock) {
                return pending
                    .filterKeys { it.localeTag == localeTag && it.prefix == prefix }
                    .map { (key, value) ->
                        UserNGramStore.Prediction(
                            word = key.nextWord,
                            count = value.count,
                            lastUsed = value.lastUsed
                        )
                    }
                    .sortedWith(
                        compareByDescending<UserNGramStore.Prediction> { it.count }
                            .thenByDescending { it.lastUsed }
                    )
            }
        }

        fun clear() {
            synchronized(lock) {
                pending.clear()
            }
        }

        private data class Key(
            val localeTag: String,
            val prefix: String,
            val nextWord: String
        )

        private data class PendingPrediction(
            val count: Int,
            val lastUsed: Long
        )
    }

    private class AsyncLearningQueue(
        private val store: UserNGramRepository
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val channel = Channel<Command>(Channel.UNLIMITED)

        init {
            scope.launch {
                for (command in channel) {
                    when (command) {
                        is Command.Learn -> handleLearn(command)
                        is Command.Flush -> command.completed.complete(Unit)
                    }
                }
            }
        }

        fun enqueue(
            localeTag: String,
            prefix: String,
            nextWord: String,
            nowMs: Long,
            shouldPersist: () -> Boolean,
            onFinished: () -> Unit
        ): Boolean {
            return channel.trySend(
                Command.Learn(localeTag, prefix, nextWord, nowMs, shouldPersist, onFinished)
            ).isSuccess
        }

        suspend fun flush() {
            val completed = CompletableDeferred<Unit>()
            if (!channel.trySend(Command.Flush(completed)).isSuccess) return
            completed.await()
        }

        fun destroy() {
            channel.close()
            scope.cancel()
        }

        private fun handleLearn(command: Command.Learn) {
            try {
                if (command.shouldPersist()) {
                    store.learn(command.localeTag, command.prefix, command.nextWord, command.nowMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist next-word learning", e)
            } finally {
                command.onFinished()
            }
        }

        private sealed class Command {
            data class Learn(
                val localeTag: String,
                val prefix: String,
                val nextWord: String,
                val nowMs: Long,
                val shouldPersist: () -> Boolean,
                val onFinished: () -> Unit
            ) : Command()

            data class Flush(
                val completed: CompletableDeferred<Unit>
            ) : Command()
        }
    }
}
