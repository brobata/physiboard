package brobata.physiboard.ime

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import brobata.physiboard.core.dict.Bigram
import brobata.physiboard.core.dict.DictNormalization
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The real `user_ngrams.db` SQLite database behind the bigram store's persistence. spec:
 * autocorrect-suggestions.md SS4: "a local SQLite database `user_ngrams.db` (table `bigrams`:
 * locale, prefix, next word, count, last used)". `next_word_key` is this table's own addition
 * (not named in the spec's prose), the normalized key [NgramStore] already uses to decide whether
 * a pair is "the same bigram again"; storing it lets a row be found and incremented with one
 * indexed lookup instead of reading every row under a prefix back into Kotlin first.
 */
private class NgramDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE bigrams (" +
                "locale TEXT NOT NULL, " +
                "prefix TEXT NOT NULL, " +
                "next_word TEXT NOT NULL, " +
                "next_word_key TEXT NOT NULL, " +
                "count INTEGER NOT NULL, " +
                "last_used INTEGER NOT NULL, " +
                "PRIMARY KEY (locale, prefix, next_word_key))",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val DB_NAME = "user_ngrams.db"
        private const val DB_VERSION = 1
    }
}

/**
 * Reads, and persists a learn/forget to, `user_ngrams.db`, off the caller's thread, the same split
 * [UserWordFileLoader] and [DictionaryAssetLoader] use. spec: autocorrect-suggestions.md SS4:
 * "Learning is queued off the main thread; a just-learned pair is visible immediately through an
 * in-memory overlay" (the overlay is [brobata.physiboard.ime.KeyboardPipeline]'s own `ngramStore`
 * field; this class only makes a write durable, never the source of truth for the running session).
 */
internal class UserNgramLoader(context: Context, private val mainHandler: Handler) {
    private val helper = NgramDatabaseHelper(context.applicationContext)

    /**
     * Every write goes through one thread, in the order the keyboard made it: a mix-up fix takes
     * back a learn made one word earlier ([unlearnAsync]), which must not overtake that learn. The
     * thread ends after a short idle spell rather than living as long as the process.
     */
    private val writes = ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "physiboard-ngram-write").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    /** Loads every row into a plain list; [onLoaded] runs on the main thread. */
    fun loadAsync(onLoaded: (List<Bigram>) -> Unit) {
        Thread({
            val rows = runCatching { readAll() }.getOrDefault(emptyList())
            mainHandler.post { onLoaded(rows) }
        }, "physiboard-ngram-loader").apply { isDaemon = true }.start()
    }

    /**
     * spec SS4: a new pair starts at count 1; the same pair again increments count and refreshes
     * last-used. One upsert, so this never has to read the row back into Kotlin first.
     */
    fun learnAsync(locale: String, prefix: String, nextWord: String, nowMillis: Long) {
        writes.execute {
            runCatching {
                val db = helper.writableDatabase
                val key = DictNormalization.normalizedKey(nextWord)
                db.execSQL(
                    "INSERT INTO bigrams (locale, prefix, next_word, next_word_key, count, last_used) VALUES (?, ?, ?, ?, 1, ?) " +
                        "ON CONFLICT(locale, prefix, next_word_key) DO UPDATE SET count = count + 1, last_used = excluded.last_used, next_word = excluded.next_word",
                    arrayOf<Any>(locale, prefix, nextWord, key, nowMillis),
                )
            }
        }
    }

    /** Takes back one learn of a pair ([brobata.physiboard.core.dict.NgramStore.unlearn]): the count drops by one, and a row at zero goes. */
    fun unlearnAsync(locale: String, prefix: String, nextWord: String) {
        writes.execute {
            runCatching {
                val db = helper.writableDatabase
                val key = DictNormalization.normalizedKey(nextWord)
                val where = "locale = ? AND prefix = ? AND next_word_key = ?"
                val args = arrayOf(locale, prefix, key)
                db.beginTransaction()
                try {
                    db.execSQL("UPDATE bigrams SET count = count - 1 WHERE $where", arrayOf<Any>(locale, prefix, key))
                    db.delete("bigrams", "$where AND count <= 0", args)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    /** spec SS5: hiding a next-word suggestion "forgets that bigram". */
    fun forgetAsync(locale: String, prefix: String, nextWord: String) {
        writes.execute {
            runCatching {
                val key = DictNormalization.normalizedKey(nextWord)
                helper.writableDatabase.delete("bigrams", "locale = ? AND prefix = ? AND next_word_key = ?", arrayOf(locale, prefix, key))
            }
        }
    }

    /** spec SS4: "deleting a user word forgets it as a next word under every prefix" (SS5: "forgets it as a next word everywhere"). */
    fun forgetEverywhereAsync(word: String) {
        writes.execute {
            runCatching {
                val key = DictNormalization.normalizedKey(word)
                helper.writableDatabase.delete("bigrams", "next_word_key = ?", arrayOf(key))
            }
        }
    }

    private fun readAll(): List<Bigram> {
        val rows = mutableListOf<Bigram>()
        helper.readableDatabase.rawQuery("SELECT locale, prefix, next_word, count, last_used FROM bigrams", null).use { cursor ->
            while (cursor.moveToNext()) {
                rows += Bigram(
                    locale = cursor.getString(0),
                    prefix = cursor.getString(1),
                    nextWord = cursor.getString(2),
                    count = cursor.getInt(3),
                    lastUsedMillis = cursor.getLong(4),
                )
            }
        }
        return rows
    }
}
