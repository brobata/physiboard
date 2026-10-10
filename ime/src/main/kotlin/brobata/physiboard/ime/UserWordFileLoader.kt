package brobata.physiboard.ime

import android.content.Context
import android.os.Handler
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import java.io.File
import java.util.concurrent.Executors

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
    private val seededFile = File(context.filesDir, UserWordFileCodec.DEFAULT_WORDS_SEEDED_FILE_NAME)

    /** Loads both tiers into one [UserWordStore]; [onLoaded] runs on the main thread. */
    fun loadAsync(onLoaded: (UserWordStore) -> Unit) {
        Thread({
            ensureDefaultsFileCurrent()
            val personal = UserWordFileCodec.decodePersonalWords(readOrNull(personalFile))
            val defaults = UserWordFileCodec.decodeDefaultWords(readOrNull(defaultFile))
            val store = UserWordStore.of(defaults, personal)
            mainHandler.post { onLoaded(store) }
        }, "physiboard-userwords-loader").apply { isDaemon = true }.start()
    }

    /**
     * spec SS7: a word added from the strip is a real personal word, not just typed text; this
     * applies [change] (the same edit the caller already made to its in-memory store) to
     * `personal_dictionary.json` so it survives a process restart and shows on the Personal
     * Dictionary screen. [onResult] (main thread) reports whether the write succeeded, the same
     * "save failed" surfacing SS6.3 asks for on the Personal Dictionary screen's own edits, so a
     * caller here is not left believing an addition survived a process restart when the file
     * write actually failed (a full disk, a revoked permission, ...).
     *
     * The change is applied to the file as it is at the moment of writing, never the caller's
     * in-memory list written over it: that list can be older than the file (the Personal
     * Dictionary screen saved a word and the reload its broadcast started has not landed yet),
     * and writing it would erase that word. `:app`'s `UserWordFileStore` goes through the same
     * [UserWordFileCodec.updatePersonal], which holds the shared lock from the read to the write.
     * Saves run one at a time on one thread, in the order they were asked for, so a word added
     * and then deleted at once is deleted, not added back by a save that overtook the delete.
     */
    fun savePersonalAsync(change: (UserWordStore) -> UserWordStore, onResult: (Boolean) -> Unit = {}) {
        saves.execute {
            val ok = runCatching { updatePersonal(change) != null }.getOrDefault(false)
            mainHandler.post { onResult(ok) }
        }
    }

    private val saves = Executors.newSingleThreadExecutor { task -> Thread(task, "physiboard-userwords-save").apply { isDaemon = true } }

    /** [savePersonalAsync]'s work on the calling thread; the store written, or null when the write failed. */
    internal fun updatePersonal(change: (UserWordStore) -> UserWordStore): UserWordStore? =
        UserWordFileCodec.updatePersonal(
            readPersonal = { readOrNull(personalFile) },
            readDefaults = { readOrNull(defaultFile) },
            writePersonal = { text -> runCatching { writeAtomically(personalFile, text) }.getOrDefault(false) },
            transform = change,
        )

    /**
     * Seeds `user_defaults.json` from the asset, and on a later run adds whatever default words
     * this build has gained since the last one. Copying only when the file was missing meant an
     * install that had already run never saw a new default word again: the maintainer's phone
     * still held the original three while the build on it shipped sixty-four, so `haha` kept
     * being corrected away (2026-09-29). Words they deleted stay deleted, which is what
     * `user_defaults_seeded.json` is for; see [UserWordFileCodec.DEFAULT_WORDS_SEEDED_FILE_NAME].
     */
    private fun ensureDefaultsFileCurrent() = synchronized(UserWordFileCodec.PersonalDictionaryFileLock) {
        // The same lock as every other write of these files: this merge reads the default-word
        // file and writes it back, and the Personal Dictionary screen edits the same file.
        mergeAssetDefaults()
    }

    private fun mergeAssetDefaults() {
        val assetText = runCatching {
            context.assets.open(UserWordFileCodec.DEFAULT_WORDS_ASSET_PATH).use { it.readBytes().decodeToString() }
        }.getOrNull() ?: return
        val assetWords = UserWordFileCodec.decodeDefaultWords(assetText)
        if (assetWords.isEmpty()) return

        if (!defaultFile.exists()) {
            runCatching { defaultFile.writeBytes(assetText.encodeToByteArray()) }
                .onSuccess { recordSeeded(assetWords) }
            return
        }

        val stored = UserWordFileCodec.decodeDefaultWords(readOrNull(defaultFile))
        val seeded = UserWordFileCodec.decodeSeededSpellings(readOrNull(seededFile))
        val toAdd = UserWordFileCodec.defaultWordsToMergeIn(assetWords, stored, seeded)
        if (toAdd.isEmpty()) {
            if (!seededFile.exists()) recordSeeded(assetWords)
            return
        }
        runCatching { writeAtomically(defaultFile, UserWordFileCodec.encodeDefaultWords(stored + toAdd)) }
            .onSuccess { recordSeeded(assetWords) }
    }

    private fun recordSeeded(assetWords: List<WordFrequency>) {
        runCatching { writeAtomically(seededFile, UserWordFileCodec.encodeSeededSpellings(assetWords)) }
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
