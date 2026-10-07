package brobata.physiboard.ime

import android.content.Context
import android.os.Handler
import android.util.Log
import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.DictionaryOrigin
import brobata.physiboard.core.dict.DictionaryTierResolver
import brobata.physiboard.core.dict.LanguageCode
import java.io.File
import java.io.IOException

/**
 * The order one background load runs in, apart from the reads themselves (which [readDictionary]
 * and [readContextModel] do), so it can be checked on a plain JVM. The dictionary is handed over
 * the moment it is built, before the word-pair table is even read: typing waits on the dictionary,
 * never on the table (autocorrect-suggestions.md SS16 W5). [onDictionary] is always posted, with
 * null when no dictionary was built, so the caller's in-flight marker clears whatever happened.
 * A table that fails to read, to parse or to fit in memory (it is several megabytes of arrays,
 * and an [OutOfMemoryError] there must not take the keyboard down with it) only means no context:
 * [onContextModel] is posted only for a table that loaded. [onFailure] hears every caught failure.
 * [onFinished] is posted last, once the load has done all it will (dictionary and table, or
 * neither), so a caller waiting for the language to be complete knows when to stop waiting.
 */
internal object DictionaryLoadSequence {
    fun <D : Any, C : Any> run(
        readDictionary: () -> D?,
        readContextModel: () -> C?,
        post: (() -> Unit) -> Unit,
        onDictionary: (D?) -> Unit,
        onContextModel: (C) -> Unit,
        onFailure: (what: String, error: Throwable) -> Unit = { _, _ -> },
        onFinished: () -> Unit = {},
    ) {
        var dictionary: D? = null
        try {
            dictionary = readDictionary()
        } catch (e: Exception) {
            onFailure("dictionary", e)
        } catch (e: OutOfMemoryError) {
            onFailure("dictionary", e)
        } finally {
            // Posted even if an error nobody should catch is on its way out of this thread.
            val built = dictionary
            post { onDictionary(built) }
        }
        if (dictionary == null) {
            post(onFinished)
            return
        }
        val model = try {
            readContextModel()
        } catch (e: Exception) {
            onFailure("context", e)
            null
        } catch (e: OutOfMemoryError) {
            onFailure("context", e)
            null
        }
        if (model != null) post { onContextModel(model) }
        post(onFinished)
    }
}

/**
 * Reads a dictionary for a language and builds a [DictionaryIndex] from it, off the caller's
 * thread. spec: dictionaries-languages.md SS3's three-tier resolution (imported, then downloaded,
 * then the bundled `dictionaries/<language>.pbd` asset), decided purely by
 * [DictionaryTierResolver] so this class only turns that decision into real file and asset reads;
 * and autocorrect-suggestions.md §2 point 3 ("if [the primary dictionary] has not [finished
 * loading], the load is scheduled in the background and the strip refreshes when it completes").
 *
 * The two writable tiers live where `:app`'s `DictionaryFileStore` writes them
 * (`files/dictionaries/downloaded/<lang>.pbd`, `files/dictionaries/imported/<lang>.pbd`; see that
 * class's own SPEC GAP note on why this build uses `.pbd` under `dictionaries/`, not SS3's literal
 * `dictionaries_serialized/<lang>_base.dict` path, for every tier alike).
 *
 * The word-pair table (autocorrect-suggestions.md SS16 W5) rides the same background thread, after
 * the dictionary has been handed over: the bundled `dictionaries/<language>.bigrams` asset, read
 * whichever tier the dictionary itself came from, since the table is keyed by words and not by the
 * dictionary file. A missing, damaged or too-large table just means no context.
 *
 * A dictionary can be tens of megabytes once other languages are added, so this never reads or
 * parses on the caller's thread: [loadAsync] returns immediately, and [onLoaded] fires later, on
 * the thread [mainHandler] is bound to, only when a dictionary was actually built. Any failure
 * along the way (nothing resolves, an [IOException] mid-read, [DictionaryIndex.fromPbdBytes]
 * refusing corrupt or truncated bytes) is silent by design (SS4.2 step 2, "the load fails
 * silently"): the keyboard already types with no dictionary loaded
 * ([brobata.physiboard.core.text.TextInputResources]'s default is an empty list), so "stay in
 * that state" is exactly the right degradation, not a special case.
 */
internal class DictionaryAssetLoader(
    private val context: Context,
    private val mainHandler: Handler,
) {
    private val downloadedDir = File(context.filesDir, "dictionaries/downloaded")
    private val importedDir = File(context.filesDir, "dictionaries/imported")

    /**
     * Starts one background read of the resolved tier for [language]. [onDictionary] runs on the
     * main thread exactly once, with the dictionary or null when none was built; [onContextModel]
     * runs after it, only when the language's word-pair table loaded ([DictionaryLoadSequence]).
     */
    fun loadAsync(language: LanguageCode, onDictionary: (DictionaryIndex?) -> Unit, onContextModel: (ContextModel) -> Unit, onFinished: () -> Unit = {}) {
        Thread({
            DictionaryLoadSequence.run(
                readDictionary = {
                    val bytes = readResolvedBytes(language)
                    val index = bytes?.let(DictionaryIndex::fromPbdBytes)
                    // The phone drops verbose logs, and a silently missing dictionary reads as "autocorrect is broken".
                    DiagnosticLog.i(TAG) { "dictionary $language: bytes=${bytes?.size ?: "missing"}, index=${if (index != null) "loaded" else "FAILED"}" }
                    index
                },
                readContextModel = { readContextModel(language) },
                post = { mainHandler.post(it) },
                onDictionary = onDictionary,
                onContextModel = onContextModel,
                onFailure = { what, error -> Log.e(TAG, "$what $language failed to load", error) },
                onFinished = onFinished,
            )
        }, "physiboard-dict-loader-$language").apply { isDaemon = true }.start()
    }

    private fun readContextModel(language: LanguageCode): ContextModel? {
        val started = System.nanoTime()
        val bytes = try {
            context.assets.open("dictionaries/$language.bigrams").use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
        val model = bytes?.let(ContextModel::read)
        val millis = (System.nanoTime() - started) / 1_000_000
        DiagnosticLog.i(TAG) {
            "context $language: ${if (model != null) "loaded, vocabulary=${model.vocabularySize}, pairs=${model.pairCount}" else "none (${if (bytes == null) "no asset" else "unreadable"})"}, ${millis} ms"
        }
        return model
    }

    /** spec SS3's resolution, decided by [DictionaryTierResolver] and then read from wherever it points. */
    private fun readResolvedBytes(language: LanguageCode): ByteArray? {
        val importedFile = File(importedDir, "$language.pbd")
        val downloadedFile = File(downloadedDir, "$language.pbd")
        // The bundled asset's own existence is answered by the read attempt itself (SS3: "or
        // nothing" when it is not there either), so it is always offered as a candidate here.
        return when (
            DictionaryTierResolver.resolve(
                imported = importedFile.isFile && importedFile.length() > 0L,
                downloaded = downloadedFile.isFile && downloadedFile.length() > 0L,
                bundled = true,
            )
        ) {
            DictionaryOrigin.IMPORTED -> readFileBytes(importedFile)
            DictionaryOrigin.DOWNLOADED -> readFileBytes(downloadedFile)
            DictionaryOrigin.BUNDLED -> readAssetBytes(language)
            null -> null
        }
    }

    private fun readFileBytes(file: File): ByteArray? = try {
        file.readBytes()
    } catch (e: IOException) {
        null
    }

    private fun readAssetBytes(language: LanguageCode): ByteArray? = try {
        context.assets.open("dictionaries/$language.pbd").use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val TAG = "PhysiBoardDict"
    }
}
