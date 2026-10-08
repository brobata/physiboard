package brobata.physiboard.ime.fill

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import brobata.physiboard.core.actions.fill.OneTimeCode
import brobata.physiboard.core.actions.fill.OneTimeCodeStore

/**
 * The process's one set of one-time codes, shared by the notification listener that finds them
 * and the keyboard that types them. spec: layers-sym-alt.md SS4.7.
 *
 * Memory only, by design: nothing here is written to disk, logged, traced or sent; a process
 * restart forgets every code. Every call is made on the main thread (both services' callbacks
 * run there); the lock only guards against a stray caller.
 *
 * Times are the device's uptime clock, which does not jump with the wall clock.
 */
internal object OneTimeCodeHolder {

    private val store = OneTimeCodeStore()
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun now(): Long = SystemClock.elapsedRealtime()

    fun add(code: OneTimeCode) {
        val changed = synchronized(this) { store.add(code, now()) }
        if (changed) notifyChanged()
    }

    fun current(): List<OneTimeCode> = synchronized(this) { store.current(now()) }

    /** The code was typed: it leaves the list, and the same notification read again does not bring it back. */
    fun consume(code: OneTimeCode) {
        val changed = synchronized(this) { store.remove(code) }
        if (changed) notifyChanged()
    }

    /** Screen locked, private mode on, the feature switched off, or access withdrawn. */
    fun clear() {
        val changed = synchronized(this) { store.clear(now()) }
        if (changed) notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        synchronized(this) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(this) { listeners.remove(listener) }
    }

    private val expiryToken = Any()

    /** Tells the listeners now, and again when the oldest code expires, so the page and the caret cue never outlive a code. */
    private fun notifyChanged() {
        val snapshot = synchronized(this) { listeners.toList() }
        main.post { snapshot.forEach { runCatching { it() } } }
        main.removeCallbacksAndMessages(expiryToken)
        val nextExpiry = synchronized(this) { store.nextExpiryAtMs() } ?: return
        main.postDelayed({ synchronized(this) { store.current(now()) }; notifyChanged() }, expiryToken, (nextExpiry - now()).coerceAtLeast(0) + 50)
    }
}
