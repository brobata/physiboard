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
     * makes that change durable.
     */
    fun savePersonalAsync(personalWords: List<PersonalWord>) {
        Thread({
            runCatching { writeAtomically(personalFile, UserWordFileCodec.encodePersonalWords(personalWords)) }
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

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}
