package brobata.physiboard.ime

import android.content.res.AssetManager
import android.os.Handler
import android.util.Log
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import java.io.IOException

/**
 * Reads a bundled `dictionaries/<language>.pbd` asset and builds a [DictionaryIndex] from it,
 * off the caller's thread. spec: autocorrect-suggestions.md §2 point 3 ("if [the primary
 * dictionary] has not [finished loading], the load is scheduled in the background and the strip
 * refreshes when it completes") and rebuild-from-scratch.md's dictionary-format section (only
 * `en` is bundled today; docs/dictionaries.md explains why the other eighteen are not).
 *
 * A dictionary can be tens of megabytes once other languages are added, so this never reads or
 * parses on the caller's thread: [loadAsync] returns immediately, and [onLoaded] fires later, on
 * the thread [mainHandler] is bound to, only when a dictionary was actually built. Any failure
 * along the way (the asset is not there, an [IOException] mid-read, [DictionaryIndex.fromPbdBytes]
 * refusing corrupt or truncated bytes) is silent by design: the keyboard already types with no
 * dictionary loaded ([brobata.physiboard.core.text.TextInputResources]'s default is an empty
 * list), so "stay in that state" is exactly the right degradation, not a special case.
 */
internal class DictionaryAssetLoader(
    private val assets: AssetManager,
    private val mainHandler: Handler,
) {
    /** Starts one background read of `dictionaries/<language>.pbd`; [onLoaded] runs on the main thread, once, only on success. */
    fun loadAsync(language: LanguageCode, onLoaded: (DictionaryIndex) -> Unit) {
        Thread({
            val bytes = readAssetBytes(language)
            val index = bytes?.let(DictionaryIndex::fromPbdBytes)
            // The phone drops verbose logs, and a silently missing dictionary reads as "autocorrect is broken".
            Log.i(TAG, "dictionary $language: asset=${bytes?.size ?: "missing"} bytes, index=${if (index != null) "loaded" else "FAILED"}")
            if (index != null) mainHandler.post { onLoaded(index) }
        }, "physiboard-dict-loader-$language").apply { isDaemon = true }.start()
    }

    private fun readAssetBytes(language: LanguageCode): ByteArray? = try {
        assets.open("dictionaries/$language.pbd").use { it.readBytes() }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val TAG = "PhysiBoardDict"
    }
}
