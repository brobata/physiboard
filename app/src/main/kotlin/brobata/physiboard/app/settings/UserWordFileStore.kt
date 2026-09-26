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

    /** Persists [store]'s personal tier, then sends the package-internal update broadcast (SS6.1). */
    suspend fun savePersonal(store: UserWordStore): Boolean = withContext(Dispatchers.IO) {
        val ok = writeAtomically(personalFile, UserWordFileCodec.encodePersonalWords(store.personalWords()))
        if (ok) notifyUpdated()
        ok
    }

    /** Persists an edited default-word list (SS6.3: "Renaming or deleting a default word edits `user_defaults.json`"). */
    suspend fun saveDefaults(words: List<WordFrequency>): Boolean = withContext(Dispatchers.IO) {
        val ok = writeAtomically(defaultFile, UserWordFileCodec.encodeDefaultWords(words))
        if (ok) notifyUpdated()
        ok
    }

    private fun ensureDefaultsFileExists() {
        if (defaultFile.exists()) return
        val assetBytes = runCatching { context.assets.open(UserWordFileCodec.DEFAULT_WORDS_ASSET_PATH).use { it.readBytes() } }.getOrNull() ?: return
        runCatching { defaultFile.writeBytes(assetBytes) }
    }

    private fun readOrNull(file: File): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

    private fun writeAtomically(file: File, text: String): Boolean = runCatching {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }.getOrDefault(false)

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

/** One row the personal-dictionary screen shows, either tier, merged and sorted case-insensitively (SS6.3). */
data class DictionaryWordRow(val word: String, val frequency: Int, val isPersonal: Boolean)

/** SS6.3: "Lists default and personal words together, sorted case-insensitively." */
fun UserWordStore.mergedRows(): List<DictionaryWordRow> =
    (personalWords().map { DictionaryWordRow(it.word, it.frequency, isPersonal = true) } +
        defaultWords().map { DictionaryWordRow(it.word, it.frequency, isPersonal = false) })
        .sortedBy { it.word.lowercase() }
