package it.palsoftware.pastiera

/**
 * App-internal broadcast actions shared across UI, IME and restore flows.
 * Keep action strings stable for backwards compatibility with existing receivers/senders.
 */
object AppBroadcastActions {
    const val USER_DICTIONARY_UPDATED = "it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED"
}
