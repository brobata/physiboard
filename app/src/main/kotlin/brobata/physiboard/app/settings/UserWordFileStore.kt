package brobata.physiboard.app.settings

import android.content.Context
import android.content.Intent
import brobata.physiboard.core.dict.DictionaryBroadcastActions
import brobata.physiboard.core.dict.UserWordFileCodec
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The file half of the personal dictionary: reads and writes `personal_dictionary.json` and
 * `user_defaults.json` in the app's private files directory, the same files the keyboard reads
 * (`:core:dict`'s `UserWordFileCodec` is the shared parser, see its KDoc's SPEC GAP on the
 * personal-word file path). Only `:app` touches [File] or [Context]; [UserWordStore] and the
 * codec stay pure. spec: autocorrect-suggestions.md SS6.1, SS6.3; dictionaries-languages.md SS7.
 */
class UserWordFileStore(private val context: Context) {

    private val personalFile = File(context.filesDir, UserWordFileCodec.PERSONAL_WORDS_FILE_NAME)
    private val defaultFile = File(context.filesDir, UserWordFileCodec.DEFAULT_WORDS_FILE_NAME)

    /** Loads both tiers, copying the shipped default-word asset to [defaultFile] on first use (SS7). */
    suspend fun load(): UserWordStore = withContext(Dispatchers.IO) {
        ensureDefaultsFileExists()
        val personal = UserWordFileCodec.decodePersonalWords(readOrNull(personalFile))
        val defaults = UserWordFileCodec.decodeDefaultWords(readOrNull(defaultFile))
        UserWordStore.of(defaults, personal)
    }

    /**
     * Applies [transform] to the personal words as the file holds them now and writes the result,
     * then sends the package-internal update broadcast (SS6.1). `:ime`'s `UserWordFileLoader`
     * writes this same file from another thread of the same process; both go through
     * [UserWordFileCodec.updatePersonal], which holds the lock from the read to the write, so
     * neither writes over a word the other saved. There is deliberately no "save this whole
     * list": a list read earlier is a stale copy. Returns the store written, or null when the
     * write failed or the file is there but unreadable or damaged (left as it is).
     */
    suspend fun updatePersonal(transform: (UserWordStore) -> UserWordStore): UserWordStore? = withContext(Dispatchers.IO) {
        ensureDefaultsFileExists()
        val updated = updatePersonalFile(personalFile, defaultFile, transform)
        if (updated != null) notifyUpdated()
        updated
    }

    /** The same for the default words (SS6.3: "Renaming or deleting a default word edits `user_defaults.json`"). */
    suspend fun updateDefaults(transform: (List<WordFrequency>) -> List<WordFrequency>): List<WordFrequency>? = withContext(Dispatchers.IO) {
        ensureDefaultsFileExists()
        val updated = UserWordFileCodec.updateDefaults({ readIfPresent(defaultFile) }, { writeAtomically(defaultFile, it) }, transform)
        if (updated != null) notifyUpdated()
        updated
    }

    private fun ensureDefaultsFileExists() = synchronized(UserWordFileCodec.PersonalDictionaryFileLock) {
        if (defaultFile.exists()) return@synchronized
        val assetBytes = runCatching { context.assets.open(UserWordFileCodec.DEFAULT_WORDS_ASSET_PATH).use { it.readBytes() } }.getOrNull() ?: return@synchronized
        runCatching { defaultFile.writeBytes(assetBytes) }
    }

    /** autocorrect-suggestions.md SS6.1: "Settings screens announce changes with the broadcast `brobata.physiboard.ACTION_USER_DICTIONARY_UPDATED` (package-internal)." */
    private fun notifyUpdated() {
        val intent = Intent(DictionaryBroadcastActions.USER_DICTIONARY_UPDATED).setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    companion object {
        /** Kept for source compatibility; the action itself now lives in `:core:dict` so `:ime` can share it. */
        const val ACTION_USER_DICTIONARY_UPDATED: String = DictionaryBroadcastActions.USER_DICTIONARY_UPDATED
    }
}

/** [UserWordFileStore.updatePersonal]'s file work, apart from [Context] so it is tested on the JVM. */
internal fun updatePersonalFile(personalFile: File, defaultFile: File, transform: (UserWordStore) -> UserWordStore): UserWordStore? =
    UserWordFileCodec.updatePersonal({ readIfPresent(personalFile) }, { readIfPresent(defaultFile) }, { writeAtomically(personalFile, it) }, transform)

/** The file's text, or null when there is no file; throws when the file is there but cannot be read (the update functions then write nothing). */
private fun readIfPresent(file: File): String? = if (file.exists()) file.readText() else null

private fun readOrNull(file: File): String? = runCatching { readIfPresent(file) }.getOrNull()

private fun writeAtomically(file: File, text: String): Boolean = runCatching {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(text)
    tmp.renameTo(file)
}.getOrDefault(false)

/** One row the personal-dictionary screen shows, either tier, merged and sorted case-insensitively (SS6.3). */
data class DictionaryWordRow(val word: String, val frequency: Int, val isPersonal: Boolean)

/** SS6.3: "Lists default and personal words together, sorted case-insensitively." */
fun UserWordStore.mergedRows(): List<DictionaryWordRow> =
    (personalWords().map { DictionaryWordRow(it.word, it.frequency, isPersonal = true) } +
        defaultWords().map { DictionaryWordRow(it.word, it.frequency, isPersonal = false) })
        .sortedBy { it.word.lowercase() }
