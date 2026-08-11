package it.palsoftware.pastiera.data.emoji

import android.content.res.AssetManager
import android.graphics.Paint
import android.os.Build

class EmojiAvailability internal constructor(
    private val minApiByEmoji: Map<String, Int>,
    private val sdk: Int = Build.VERSION.SDK_INT,
    private val hasGlyph: (String) -> Boolean = Paint()::hasGlyph
) {
    fun isAvailable(emoji: String): Boolean {
        val minApi = minApiByEmoji[emoji]
        return minApi != null && minApi > 0 && sdk >= minApi || hasGlyph(emoji)
    }

    companion object {
        private const val MIN_API_ASSET = "common/emoji/minApi.txt"

        fun fromAssets(assets: AssetManager): EmojiAvailability = EmojiAvailability(
            minApiByEmoji = runCatching {
                assets.open(MIN_API_ASSET).bufferedReader().useLines { lines ->
                    lines.flatMap { line ->
                        val parts = line.split(' ').filter(String::isNotBlank)
                        val api = parts.firstOrNull()?.toIntOrNull()
                            ?: return@flatMap emptySequence()
                        parts.drop(1).asSequence().map { emoji -> emoji to api }
                    }.toMap()
                }
            }.getOrElse { emptyMap() }
        )
    }
}
