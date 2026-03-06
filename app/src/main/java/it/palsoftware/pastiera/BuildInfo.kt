package it.palsoftware.pastiera

import it.palsoftware.pastiera.BuildConfig

/**
 * Fornisce informazioni sulla build dell'app.
 */
object BuildInfo {
    /**
     * Ottiene la stringa formattata con versione e canale di release.
     */
    fun getBuildInfoString(): String {
        val version = BuildConfig.VERSION_NAME
        val channel = BuildConfig.RELEASE_CHANNEL.replaceFirstChar { it.uppercase() }
        return "Ver. $version - $channel"
    }
}
