package brobata.physiboard.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.UserDictionary
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryBroadcastActions
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.text.SpellResources
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The dictionaries, word-pair tables and user words loaded in this process, shared by the keyboard
 * ([KeyboardSession]) and the system spell checker ([PhysiBoardSpellCheckerService]), which Android
 * runs in the same process (neither declares one of its own). spec: dictionaries-languages.md SS4
 * ("one in-memory dictionary per language code per keyboard process") and autocorrect-suggestions.md
 * SS18. Before the spell checker existed the keyboard session kept its own copies; a second copy of
 * the English list and its table would cost about 30 MB and a second read of both files.
 *
 * Every change happens on the main thread, by the same rules the session used to keep: a language
 * loads once ([DictionaryAssetLoader], off the main thread), a load that built nothing is not
 * retried until a reload says something changed, and a load overtaken by [reloadAll] drops its
 * result. Each change publishes a new immutable [Snapshot] in a volatile field, so the spell
 * checker's binder threads read a consistent set without locking, and tells every listener.
 *
 * Owning the dictionary broadcasts here (`DICTIONARY_CHANGED`, `USER_DICTIONARY_UPDATED`, both
 * package-internal) rather than in the session means the spell checker hears about an install or
 * a personal-dictionary edit even while PhysiBoard is not the active keyboard.
 *
 * Android's own user dictionary (the words an app's "Add to dictionary" saves) is read only when
 * the spell checker asks ([requestSystemWords]): only the spell checker counts them as known, so
 * an underline goes away after "Add to dictionary". It is re-read when the dictionary changes.
 * Nothing here writes anything or leaves the phone.
 */
internal class SharedDictionaries private constructor(context: Context) {

    data class Snapshot(
        /**
         * Stamped afresh by every publish, so a reader can tell two snapshots apart by number
         * without holding on to the older one (and with it a whole dictionary and word-pair table).
         */
        val version: Long = 0,
        val generation: Int = 0,
        val dictionaries: Map<LanguageCode, DictionaryIndex> = emptyMap(),
        val contextModels: Map<LanguageCode, ContextModel> = emptyMap(),
        /** Languages whose load built no dictionary in this generation. */
        val failed: Set<LanguageCode> = emptySet(),
        /** Languages whose load has finished in this generation, word-pair table included (or known absent). */
        val complete: Set<LanguageCode> = emptySet(),
        /** Null until the first read of the user word files lands. */
        val userWords: UserWordStore? = null,
        /** Android's user dictionary, as [SpellResources.systemWordKey] keys; null until read. */
        val systemWords: Set<String>? = null,
    ) {
        /** Whether everything a judgement in [language] needs has arrived (or is known missing). */
        fun isReady(language: LanguageCode): Boolean = userWords != null && (language in complete || language in failed)
    }

    private val appContext: Context = context.applicationContext ?: context
    private val handler = Handler(Looper.getMainLooper())
    private val loader = DictionaryAssetLoader(appContext, handler)
    private val userWordLoader = UserWordFileLoader(appContext, handler)

    @Volatile
    var snapshot: Snapshot = Snapshot()
        private set

    private val readyLock = ReentrantLock()
    private val readyChanged = readyLock.newCondition()

    // Main thread only from here down.
    private val inFlight = mutableMapOf<LanguageCode, Int>()
    private var userWordLoads = 0
    /** Keyboard saves of the personal word file not yet landed ([editUserWords]); no read lands meanwhile. */
    private var pendingUserWordSaves = 0
    private var systemWordLoads = 0
    private var systemWordsWatched = false
    private val listeners = LinkedHashSet<() -> Unit>()

    private val changeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DictionaryBroadcastActions.DICTIONARY_CHANGED -> reloadAll()
                DictionaryBroadcastActions.USER_DICTIONARY_UPDATED -> reloadUserWords()
            }
        }
    }

    private val systemWordObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = reloadSystemWords()
    }

    init {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(DictionaryBroadcastActions.DICTIONARY_CHANGED)
                addAction(DictionaryBroadcastActions.USER_DICTIONARY_UPDATED)
            }
            // Registered for the life of the process, as this object is.
            ContextCompat.registerReceiver(appContext, changeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { error -> Log.e(TAG, "dictionary receiver registration failed", error) }
    }

    /** [listener] runs on the main thread after every change. */
    fun addListener(listener: () -> Unit) = onMain { listeners += listener }

    fun removeListener(listener: () -> Unit) = onMain { listeners -= listener }

    /** Loads [language] unless it is loaded, loading or failed in this generation. Any thread. */
    fun request(language: LanguageCode) = onMain {
        val current = snapshot
        if (language in current.dictionaries || language in inFlight || language in current.failed) return@onMain
        val generation = current.generation
        inFlight[language] = generation
        loader.loadAsync(
            language,
            onDictionary = onDictionary@{ index ->
                // Overtaken by [reloadAll] while it ran: neither its result nor its marker is ours any more.
                if (inFlight[language] != generation) return@onDictionary
                inFlight.remove(language)
                val s = snapshot
                publish(
                    if (index == null) s.copy(failed = s.failed + language, complete = s.complete + language)
                    else s.copy(dictionaries = s.dictionaries + (language to index)),
                )
            },
            onContextModel = onContextModel@{ model ->
                // The table belongs to the dictionary this same load built.
                val s = snapshot
                if (s.generation != generation || language !in s.dictionaries) return@onContextModel
                publish(s.copy(contextModels = s.contextModels + (language to model)))
            },
            onFinished = onFinished@{
                val s = snapshot
                if (s.generation != generation || language in s.complete) return@onFinished
                publish(s.copy(complete = s.complete + language))
            },
        )
    }

    /**
     * dictionaries-languages.md SS17's "reload on install, import, uninstall": every dictionary
     * is dropped; whoever wants one asks again (the session's listener does at once, the spell
     * checker at its next request).
     */
    fun reloadAll() = onMain {
        inFlight.clear()
        val s = snapshot
        publish(Snapshot(generation = s.generation + 1, userWords = s.userWords, systemWords = s.systemWords))
    }

    /** Reads the user word files unless they are already read. */
    fun requestUserWords() = onMain { if (snapshot.userWords == null && userWordLoads == 0) reloadUserWords() }

    /** Reads the user word files again; the newest read wins. */
    fun reloadUserWords() = onMain {
        val load = ++userWordLoads
        userWordLoader.loadAsync { store ->
            if (load == userWordLoads && pendingUserWordSaves == 0) publish(snapshot.copy(userWords = store))
        }
    }

    /**
     * An edit the keyboard makes itself (a word added from the strip, or deleted): [change] is
     * applied in memory at once, then saved to the file ([UserWordFileLoader.savePersonalAsync]
     * applies it to the file's current contents). [onResult] (main thread) says whether the save
     * landed.
     *
     * A read of the files that is running, or starts, before the save has landed may have missed
     * this edit, and landing after it would undo the edit in memory; so no read lands while a save
     * is pending, and once the last one has landed the files are read again, which brings in this
     * edit together with anything the dropped reads carried (a word the settings screen saved).
     */
    fun editUserWords(change: (UserWordStore) -> UserWordStore, onResult: (Boolean) -> Unit) = onMain {
        userWordLoads++
        publish(snapshot.copy(userWords = change(snapshot.userWords ?: UserWordStore.empty())))
        pendingUserWordSaves++
        userWordLoader.savePersonalAsync(change) { saved ->
            pendingUserWordSaves--
            if (pendingUserWordSaves == 0) reloadUserWords()
            onResult(saved)
        }
    }

    /** Reads Android's user dictionary unless already read, and watches it from then on. */
    fun requestSystemWords() = onMain {
        if (!systemWordsWatched) {
            systemWordsWatched = true
            runCatching { appContext.contentResolver.registerContentObserver(UserDictionary.Words.CONTENT_URI, true, systemWordObserver) }
                .onFailure { error -> Log.w(TAG, "user dictionary cannot be watched", error) }
        }
        if (snapshot.systemWords == null && systemWordLoads == 0) reloadSystemWords()
    }

    private fun reloadSystemWords() {
        val load = ++systemWordLoads
        Thread({
            val words = readSystemWords()
            handler.post { if (load == systemWordLoads) publish(snapshot.copy(systemWords = words)) }
        }, "physiboard-system-words").apply { isDaemon = true }.start()
    }

    /** Every word in Android's user dictionary, any locale: a word the user added is a word. Empty when the provider refuses or is missing. */
    private fun readSystemWords(): Set<String> = try {
        appContext.contentResolver.query(UserDictionary.Words.CONTENT_URI, arrayOf(UserDictionary.Words.WORD), null, null, null)?.use { cursor ->
            val words = HashSet<String>()
            val column = cursor.getColumnIndex(UserDictionary.Words.WORD)
            while (column >= 0 && cursor.moveToNext() && words.size < MAX_SYSTEM_WORDS) {
                cursor.getString(column)?.let { SpellResources.systemWordKey(it) }?.takeIf { it.isNotEmpty() }?.let(words::add)
            }
            words
        } ?: emptySet()
    } catch (e: Exception) {
        Log.w(TAG, "user dictionary unreadable", e)
        emptySet()
    }

    /**
     * Waits up to [timeoutMillis] for [language] to be [Snapshot.isReady]. For a binder thread
     * only: everything this waits for is posted to the main thread, so waiting there would only
     * waste the time. Returns whether it is ready.
     */
    fun awaitReady(language: LanguageCode, timeoutMillis: Long): Boolean {
        if (snapshot.isReady(language)) return true
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        // A monotonic clock of the JVM's own: the bound must hold wherever this runs, including
        // under a test framework that holds Android's SystemClock still.
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        readyLock.withLock {
            while (!snapshot.isReady(language)) {
                val left = deadline - System.nanoTime()
                if (left <= 0) return false
                try {
                    readyChanged.await(left, TimeUnit.NANOSECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return true
    }

    /** Puts a loaded language in place directly, for tests that have no assets to read. Main thread. */
    @VisibleForTesting
    fun install(language: LanguageCode, dictionary: DictionaryIndex, model: ContextModel?, userWords: UserWordStore, systemWords: Set<String> = emptySet()) {
        val s = snapshot
        publish(
            s.copy(
                dictionaries = s.dictionaries + (language to dictionary),
                contextModels = if (model != null) s.contextModels + (language to model) else s.contextModels - language,
                complete = s.complete + language,
                userWords = userWords,
                systemWords = systemWords,
            ),
        )
    }

    private var publishedVersions = 0L

    private fun publish(next: Snapshot) {
        // Main thread only, like every other change to the snapshot.
        publishedVersions++
        snapshot = next.copy(version = publishedVersions)
        readyLock.withLock { readyChanged.signalAll() }
        for (listener in listeners.toList()) {
            runCatching(listener).onFailure { error -> Log.e(TAG, "dictionary listener crashed", error) }
        }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post { block() }
    }

    companion object {
        private const val TAG = "PhysiBoardDict"
        private const val MAX_SYSTEM_WORDS = 20_000

        @Volatile
        private var instance: SharedDictionaries? = null

        fun get(context: Context): SharedDictionaries =
            instance ?: synchronized(this) { instance ?: SharedDictionaries(context).also { instance = it } }

        /** Forgets the process-wide instance, so each test starts from nothing. */
        @VisibleForTesting
        fun resetForTest() {
            synchronized(this) { instance = null }
        }
    }
}
