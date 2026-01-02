package it.palsoftware.pastiera.data.emoji

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONArray

/**
 * Manages recent emoji history.
 * Stores up to 40 recently used emojis in SharedPreferences.
 */
object RecentEmojiManager {
    private const val TAG = "RecentEmojiManager"
    private const val PREF_KEY_RECENT_EMOJIS = "recent_emojis"
    private const val MAX_RECENT_EMOJIS = 40

    /**
     * Adds an emoji to the recent list if it's not already present.
     * If the emoji is already in the list, it maintains its current position.
     * If not present, it's added at the top (most recent first).
     * @return true if the emoji was added (new), false if it was already present
     */
    fun addRecentEmoji(context: Context, emoji: String): Boolean {
        if (emoji.isBlank()) return false

        val currentList = getRecentEmojis(context, MAX_RECENT_EMOJIS)

        // If emoji is already present, don't do anything (maintain position)
        if (currentList.contains(emoji)) {
            return false
        }

        // Add new emoji at the top
        val updatedList = listOf(emoji) + currentList

        // Limit to MAX_RECENT_EMOJIS (remove oldest)
        val limitedList = updatedList.take(MAX_RECENT_EMOJIS)

        saveRecentEmojis(context, limitedList)
        return true
    }

    /**
     * Retrieves the list of recent emojis.
     * @param maxCount Maximum number of emojis to return (default: MAX_RECENT_EMOJIS)
     * @return List of emoji strings, ordered from most recent to oldest
     */
    fun getRecentEmojis(context: Context, maxCount: Int = MAX_RECENT_EMOJIS): List<String> {
        val prefs = SettingsManager.getPreferences(context)
        val jsonString = prefs.getString(PREF_KEY_RECENT_EMOJIS, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(jsonString)
            val emojis = mutableListOf<String>()
            for (i in 0 until jsonArray.length().coerceAtMost(maxCount)) {
                val emoji = jsonArray.getString(i)
                if (emoji.isNotBlank()) {
                    emojis.add(emoji)
                }
            }
            emojis
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent emojis", e)
            emptyList()
        }
    }

    /**
     * Clears all recent emojis.
     */
    fun clearRecentEmojis(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        prefs.edit().remove(PREF_KEY_RECENT_EMOJIS).apply()
    }

    /**
     * Creates an EmojiCategory for the recent emojis.
     * Returns null if there are no recent emojis.
     * Looks up variants from the emoji repository cache.
     */
    fun getRecentEmojiCategory(context: Context): EmojiRepository.EmojiCategory? {
        val recentEmojis = getRecentEmojis(context)
        if (recentEmojis.isEmpty()) {
            return null
        }

        // Convert list of strings to EmojiEntry list with variants from repository
        val emojiEntries = recentEmojis.map { emoji ->
            val variants = EmojiRepository.getVariantsForEmoji(emoji)
            EmojiRepository.EmojiEntry(base = emoji, variants = variants)
        }

        return EmojiRepository.EmojiCategory(
            id = EmojiRepository.RECENTS_CATEGORY_ID,
            displayNameRes = R.string.emoji_category_recents,
            emojis = emojiEntries
        )
    }

    private fun saveRecentEmojis(context: Context, emojis: List<String>) {
        try {
            val jsonArray = JSONArray()
            emojis.forEach { emoji ->
                jsonArray.put(emoji)
            }
            SettingsManager.getPreferences(context).edit()
                .putString(PREF_KEY_RECENT_EMOJIS, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recent emojis", e)
        }
    }

}

