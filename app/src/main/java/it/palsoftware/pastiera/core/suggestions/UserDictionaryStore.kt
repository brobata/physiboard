package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class UserDictionaryStore {

    data class UserEntry(
        val word: String,
        val frequency: Int,
        val lastUsed: Long
    )

    private fun prefs(context: Context): SharedPreferences = SettingsManager.getPreferences(context)

    fun loadUserEntries(context: Context): List<DictionaryEntry> {
        return try {
            val json = prefs(context).getString(KEY_USER_DICTIONARY, "[]") ?: "[]"
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val word = obj.getString(KEY_WORD)
                    val frequency = obj.optInt(KEY_FREQ, 1)
                    val lastUsed = obj.optLong(KEY_LAST_USED, 0L)
                    add(
                        DictionaryEntry(
                            word = word,
                            frequency = frequency,
                            source = SuggestionSource.USER
                        )
                    )
                    // Store in cache using lowercase for case-insensitive lookup
                    // The actual normalization (with locale and accent stripping) is done by DictionaryRepository
                    cache[word.lowercase()] = UserEntry(word, frequency, lastUsed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user dictionary", e)
            emptyList()
        }
    }

    fun addWord(context: Context, word: String) {
        // Store word as-is (case preserved), but use lowercase for cache key
        // DictionaryRepository will normalize it properly with baseLocale
        val cacheKey = word.lowercase()
        val entry = cache[cacheKey]
        val updated = if (entry != null) {
            entry.copy(frequency = entry.frequency + 1, lastUsed = System.currentTimeMillis())
        } else {
            UserEntry(word, 1, System.currentTimeMillis())
        }
        cache[cacheKey] = updated
        persist(context)
    }

    fun removeWord(context: Context, word: String) {
        // Remove using lowercase key for case-insensitive lookup
        cache.remove(word.lowercase())
        persist(context)
    }

    fun markUsed(context: Context, word: String) {
        val cacheKey = word.lowercase()
        cache[cacheKey]?.let {
            cache[cacheKey] = it.copy(lastUsed = System.currentTimeMillis(), frequency = it.frequency + 1)
            persist(context)
        }
    }

    fun getSnapshot(): List<UserEntry> = cache.values.sortedByDescending { it.lastUsed }

    private fun persist(context: Context) {
        try {
            val array = JSONArray()
            cache.values.forEach { entry ->
                val obj = JSONObject()
                obj.put(KEY_WORD, entry.word)
                obj.put(KEY_FREQ, entry.frequency)
                obj.put(KEY_LAST_USED, entry.lastUsed)
                array.put(obj)
            }
            prefs(context).edit().putString(KEY_USER_DICTIONARY, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to persist user dictionary", e)
        }
    }

    companion object {
        private const val TAG = "UserDictionaryStore"
        private const val KEY_USER_DICTIONARY = "user_dictionary_entries"
        private const val KEY_WORD = "w"
        private const val KEY_FREQ = "f"
        private const val KEY_LAST_USED = "u"
        private val cache: MutableMap<String, UserEntry> = mutableMapOf()
    }
}
