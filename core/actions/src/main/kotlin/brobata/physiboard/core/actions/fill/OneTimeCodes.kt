package brobata.physiboard.core.actions.fill

/**
 * One code the Fill page offers. spec: layers-sym-alt.md SS4.7. [source] is the name of the app
 * whose notification carried it ("Messages", "Gmail"); [sourceKey] tells apart two apps with the
 * same name (their package), so the same code from two apps is two entries.
 */
data class OneTimeCode(val code: String, val source: String, val sourceKey: String, val receivedAtMs: Long)

/**
 * The few codes the Fill page offers, newest first. spec: layers-sym-alt.md SS4.7.
 *
 * Kept in memory only (the caller holds one of these for the life of the process and never
 * writes it anywhere), at most [MAX_CODES], each for [LIFETIME_MS] after it arrived. The same
 * code from the same app again (a notification updated or posted twice) moves to the front
 * instead of being listed twice, and a code that was typed stays gone for the rest of its
 * lifetime even when its notification is posted again (a messaging app re-posts its
 * conversation on every change). Not thread-safe: the caller confines it to one thread.
 *
 * Pure: every rule takes the clock as an argument.
 */
class OneTimeCodeStore(private val maxCodes: Int = MAX_CODES, private val lifetimeMs: Long = LIFETIME_MS) {

    private val codes = ArrayList<OneTimeCode>()

    /** Codes that were typed, kept (code and source only) until they would have expired. */
    private val used = ArrayList<OneTimeCode>()

    /** A code received at or before this was cleared (screen off, private mode...) and is not taken again. */
    private var clearedThroughMs: Long = Long.MIN_VALUE

    /** Adds [code]; returns true when what [current] would answer changed. */
    fun add(code: OneTimeCode, nowMs: Long): Boolean {
        prune(nowMs)
        if (isExpired(code, nowMs)) return false
        if (code.receivedAtMs <= clearedThroughMs) return false
        if (used.any { it.code == code.code && it.sourceKey == code.sourceKey }) return false
        codes.removeAll { it.code == code.code && it.sourceKey == code.sourceKey }
        // Newest first; a code posted earlier than one already held (a backlog read at connect)
        // goes behind it.
        val index = codes.indexOfFirst { it.receivedAtMs <= code.receivedAtMs }.let { if (it < 0) codes.size else it }
        codes.add(index, code)
        while (codes.size > maxCodes) codes.removeAt(codes.lastIndex)
        return codes.contains(code)
    }

    /** The codes still valid at [nowMs], newest first. */
    fun current(nowMs: Long): List<OneTimeCode> {
        prune(nowMs)
        return codes.toList()
    }

    /** Removes [code] because it was just typed; it is not taken again while it would still be valid. Returns true when it was there. */
    fun remove(code: OneTimeCode): Boolean {
        val had = codes.remove(code)
        if (had) used.add(code)
        return had
    }

    /**
     * Forgets every code: the screen locked, private mode came on, or the feature was switched
     * off. A code received up to [nowMs] is not taken again either, so a conversation posted
     * again after the screen comes back does not bring an old code back.
     */
    fun clear(nowMs: Long): Boolean {
        val had = codes.isNotEmpty()
        codes.clear()
        clearedThroughMs = maxOf(clearedThroughMs, nowMs)
        return had
    }

    /** When the oldest code held expires, or null when none is held; for a caller that wants to redraw then. */
    fun nextExpiryAtMs(): Long? = codes.minOfOrNull { it.receivedAtMs + lifetimeMs }

    private fun prune(nowMs: Long) {
        codes.removeAll { isExpired(it, nowMs) }
        used.removeAll { isExpired(it, nowMs) }
    }

    /** A code from the future (the clock moved back) counts as just received, never as expired. */
    private fun isExpired(code: OneTimeCode, nowMs: Long): Boolean = nowMs - code.receivedAtMs >= lifetimeMs

    companion object {
        /** spec SS4.7: "at most the last few codes". */
        const val MAX_CODES: Int = 3

        /** spec SS4.7: each code is offered for 10 minutes. */
        const val LIFETIME_MS: Long = 10 * 60 * 1000L
    }
}

/** The Fill page's words. spec: layers-sym-alt.md SS4.7. */
object FillLabels {

    /** "Code from Messages · 2 min ago". */
    fun codeLine(code: OneTimeCode, nowMs: Long): String = "Code from ${code.source} · ${age(nowMs - code.receivedAtMs)}"

    /** "just now" under a minute, then whole minutes. */
    fun age(elapsedMs: Long): String {
        val minutes = (elapsedMs.coerceAtLeast(0) / 60_000L).toInt()
        return if (minutes < 1) "just now" else "$minutes min ago"
    }

    /**
     * An app name for a package when the system will not say: the last part of the package that
     * is not a generic word, capitalised ("com.google.android.apps.messaging" is "Messaging").
     */
    fun fallbackSourceName(packageName: String): String {
        val parts = packageName.split('.').filter { it.isNotBlank() && it !in GENERIC_PACKAGE_PARTS }
        val last = parts.lastOrNull() ?: return packageName
        return last.replaceFirstChar { it.uppercaseChar() }
    }

    private val GENERIC_PACKAGE_PARTS = setOf("com", "org", "net", "android", "apps", "app", "mobile", "client", "google")
}
