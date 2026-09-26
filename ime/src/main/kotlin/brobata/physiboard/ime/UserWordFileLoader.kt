package brobata.physiboard.ime

import android.content.Context
import android.os.Handler
import brobata.physiboard.core.dict.PersonalWord
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import java.io.File

/**
 * Reads, and persists an addition to, the two files the Personal Dictionary screen and the
 * keyboard share (`personal_dictionary.json`, `user_defaults.json`), off the caller's thread.
 * spec: dictionaries-languages.md SS7 ("Both lists are merged into every loaded dictionary...
 * at the end of each load and again whenever the broadcast `ACTION_USER_DICTIONARY_UPDATED`
 * arrives; a word added from the strip is merged into the primary dictionary at once").
 *
 * The decode/merge rules themselves stay in `:core:dict`'s [UserWordFileCodec] and [UserWordStore]
 * (already shared with `:app`'s `UserWordFileStore`); this class only supplies the file paths and
 * the background thread, the same split [DictionaryAssetLoader] uses for dictionaries.
 */
internal class UserWordFileLoader(
    private val context: Context,
    private val mainHandler: Handler,
) {
    private val personalFile = File(context.filesDir, UserWordFileCodec.PERSONAL_WORDS_FILE_NAME)
    private val defaultFile = File(context.filesDir, UserWordFileCodec.DEFAULT_WORDS_FILE_NAME)

    /** Loads both tiers into one [UserWordStore]; [onLoaded] runs on the main thread. */
    fun loadAsync(onLoaded: (UserWordStore) -> Unit) {
        Thread({
            ensureDefaultsFileExists()
            val personal = UserWordFileCodec.decodePersonalWords(readOrNull(personalFile))
            val defaults = UserWordFileCodec.decodeDefaultWords(readOrNull(defaultFile))
            val store = UserWordStore.of(defaults, personal)
            mainHandler.post { onLoaded(store) }
        }, "physiboard-userwords-loader").apply { isDaemon = true }.start()
    }

    /**
     * spec SS7: a word added from the strip is a real personal word, not just typed text; this
     * writes [personalWords] (the caller's already-updated [UserWordStore.personalWords]) back to
     * `personal_dictionary.json` so it survives a process restart and shows on the Personal
     * Dictionary screen. The in-memory store is updated by the caller immediately; this call only
     * makes that change durable. [onResult] (main thread) reports whether the write succeeded, the
     * same "save failed" surfacing SS6.3 asks for on the Personal Dictionary screen's own edits, so
     * a caller here is not left believing an addition survived a process restart when the file
     * write actually failed (a full disk, a revoked permission, ...).
     */
    fun savePersonalAsync(personalWords: List<PersonalWord>, onResult: (Boolean) -> Unit = {}) {
        Thread({
            // spec dictionaries-languages.md SS7: `:app`'s `UserWordFileStore` (the Personal
            // Dictionary screen's own edits) writes this identical file from an independent
            // thread in the same process; [UserWordFileCodec.PersonalDictionaryFileLock] is what
            // keeps the two writes from silently discarding one another.
            val ok = synchronized(UserWordFileCodec.PersonalDictionaryFileLock) {
                runCatching { writeAtomically(personalFile, UserWordFileCodec.encodePersonalWords(personalWords)) }.getOrDefault(false)
            }
            mainHandler.post { onResult(ok) }
        }, "physiboard-userwords-save").apply { isDaemon = true }.start()
    }

    private fun ensureDefaultsFileExists() {
        if (defaultFile.exists()) return
        val assetBytes = runCatching {
            context.assets.open(UserWordFileCodec.DEFAULT_WORDS_ASSET_PATH).use { it.readBytes() }
        }.getOrNull() ?: return
        runCatching { defaultFile.writeBytes(assetBytes) }
    }

    private fun readOrNull(file: File): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    /**
     * spec dictionaries-languages.md SS3: "a rename, or copy then delete when the rename fails" --
     * a cross-filesystem tmp dir, a full disk mid-rename or a locked destination all land in the
     * fallback. The caller relies on the return value to decide whether the save actually
     * succeeded (SS6.3's "save failed" surfacing), so it is never discarded here.
     */
    private fun writeAtomically(file: File, text: String): Boolean {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (tmp.renameTo(file)) return true
        return runCatching {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
            true
        }.getOrDefault(false)
    }
}
