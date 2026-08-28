package it.palsoftware.pastiera

import it.palsoftware.pastiera.BuildConfig

/**
 * Exposes information about the app build.
 */
object BuildInfo {
    /**
     * Returns the formatted version and release-channel string.
     */
    fun getBuildInfoString(): String {
        val version = BuildConfig.VERSION_NAME
        val channel = BuildConfig.RELEASE_CHANNEL.replaceFirstChar { it.uppercase() }
        return "Ver. $version - $channel"
    }
}
