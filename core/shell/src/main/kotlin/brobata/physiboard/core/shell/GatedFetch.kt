package brobata.physiboard.core.shell

/** What one gated request came back with. spec: app-shell.md SS31.2. */
sealed class FetchResult {
    class Ok(val body: ByteArray) : FetchResult()

    /** The gate refused: nothing was sent. [reason] is the user-facing sentence. */
    data class Blocked(val reason: String) : FetchResult()

    /** The request went out and failed (no network, an HTTP error, a body over the size limit). */
    data class Failed(val message: String) : FetchResult()
}

/**
 * A network read that passes the gate. The keyboard (`:ime`) cannot reach `:app`'s one HTTP
 * opener directly, since `:app` depends on it; `:app` hands it this seam instead, built on that
 * opener, so every request the keyboard makes is still gated and still opened in one file.
 */
interface GatedFetcher {
    /** GET [url] for [purpose], reading at most [maxBytes]. The implementation does its I/O off the caller's thread. */
    suspend fun get(purpose: NetworkPurpose, url: String, maxBytes: Int): FetchResult
}

/** Implemented by the application object that owns a [GatedFetcher]. */
interface GatedFetcherOwner {
    val gatedFetcher: GatedFetcher
}
