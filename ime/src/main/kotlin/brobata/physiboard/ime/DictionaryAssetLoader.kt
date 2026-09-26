package brobata.physiboard.ime

import android.content.Context
import android.os.Handler
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.DictionaryOrigin
import brobata.physiboard.core.dict.DictionaryTierResolver
import brobata.physiboard.core.dict.LanguageCode
import java.io.File
import java.io.IOException

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

    /** Starts one background read of the resolved tier for [language]; [onLoaded] runs on the main thread, once, only on success. */
    fun loadAsync(language: LanguageCode, onLoaded: (DictionaryIndex) -> Unit) {
        Thread({
            val bytes = readResolvedBytes(language)
            val index = bytes?.let(DictionaryIndex::fromPbdBytes)
            // The phone drops verbose logs, and a silently missing dictionary reads as "autocorrect is broken".
            DiagnosticLog.i(TAG) { "dictionary $language: bytes=${bytes?.size ?: "missing"}, index=${if (index != null) "loaded" else "FAILED"}" }
            if (index != null) mainHandler.post { onLoaded(index) }
        }, "physiboard-dict-loader-$language").apply { isDaemon = true }.start()
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
