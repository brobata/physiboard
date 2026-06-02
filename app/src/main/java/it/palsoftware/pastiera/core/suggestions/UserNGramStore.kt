package it.palsoftware.pastiera.core.suggestions

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class UserNGramStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    data class Prediction(
        val word: String,
        val count: Int,
        val lastUsed: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BIGRAMS (
                $COL_LOCALE TEXT NOT NULL,
                $COL_PREFIX TEXT NOT NULL,
                $COL_NEXT_WORD TEXT NOT NULL,
                $COL_COUNT INTEGER NOT NULL,
                $COL_LAST_USED INTEGER NOT NULL,
                PRIMARY KEY ($COL_LOCALE, $COL_PREFIX, $COL_NEXT_WORD)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX ${TABLE_BIGRAMS}_lookup ON $TABLE_BIGRAMS " +
                "($COL_LOCALE, $COL_PREFIX, $COL_COUNT DESC, $COL_LAST_USED DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_BIGRAMS")
            onCreate(db)
        }
    }

    fun learn(locale: String, prefix: String, nextWord: String, nowMs: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        val updated = db.update(
            TABLE_BIGRAMS,
            ContentValues().apply {
                put(COL_COUNT, currentCount(db, locale, prefix, nextWord) + 1)
                put(COL_LAST_USED, nowMs)
            },
            "$COL_LOCALE = ? AND $COL_PREFIX = ? AND $COL_NEXT_WORD = ?",
            arrayOf(locale, prefix, nextWord)
        )
        if (updated > 0) return

        db.insertWithOnConflict(
            TABLE_BIGRAMS,
            null,
            ContentValues().apply {
                put(COL_LOCALE, locale)
                put(COL_PREFIX, prefix)
                put(COL_NEXT_WORD, nextWord)
                put(COL_COUNT, 1)
                put(COL_LAST_USED, nowMs)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun predict(locale: String, prefix: String, limit: Int): List<Prediction> {
        if (limit <= 0) return emptyList()
        val cursor = readableDatabase.query(
            TABLE_BIGRAMS,
            arrayOf(COL_NEXT_WORD, COL_COUNT, COL_LAST_USED),
            "$COL_LOCALE = ? AND $COL_PREFIX = ?",
            arrayOf(locale, prefix),
            null,
            null,
            "$COL_COUNT DESC, $COL_LAST_USED DESC",
            limit.toString()
        )
        cursor.use {
            val results = ArrayList<Prediction>(limit)
            val wordIndex = it.getColumnIndexOrThrow(COL_NEXT_WORD)
            val countIndex = it.getColumnIndexOrThrow(COL_COUNT)
            val lastUsedIndex = it.getColumnIndexOrThrow(COL_LAST_USED)
            while (it.moveToNext()) {
                results.add(
                    Prediction(
                        word = it.getString(wordIndex),
                        count = it.getInt(countIndex),
                        lastUsed = it.getLong(lastUsedIndex)
                    )
                )
            }
            return results
        }
    }

    fun delete(locale: String, prefix: String, nextWord: String): Int {
        return writableDatabase.delete(
            TABLE_BIGRAMS,
            "$COL_LOCALE = ? AND $COL_PREFIX = ? AND $COL_NEXT_WORD = ? COLLATE NOCASE",
            arrayOf(locale, prefix, nextWord)
        )
    }

    fun deleteNextWord(locale: String, nextWord: String): Int {
        return writableDatabase.delete(
            TABLE_BIGRAMS,
            "$COL_LOCALE = ? AND $COL_NEXT_WORD = ? COLLATE NOCASE",
            arrayOf(locale, nextWord)
        )
    }

    internal fun clearAll() {
        writableDatabase.delete(TABLE_BIGRAMS, null, null)
    }

    private fun currentCount(db: SQLiteDatabase, locale: String, prefix: String, nextWord: String): Int {
        val cursor = db.query(
            TABLE_BIGRAMS,
            arrayOf(COL_COUNT),
            "$COL_LOCALE = ? AND $COL_PREFIX = ? AND $COL_NEXT_WORD = ?",
            arrayOf(locale, prefix, nextWord),
            null,
            null,
            null,
            "1"
        )
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    companion object {
        private const val DATABASE_NAME = "user_ngrams.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_BIGRAMS = "bigrams"
        private const val COL_LOCALE = "locale"
        private const val COL_PREFIX = "prefix"
        private const val COL_NEXT_WORD = "next_word"
        private const val COL_COUNT = "count"
        private const val COL_LAST_USED = "last_used"
    }
}
