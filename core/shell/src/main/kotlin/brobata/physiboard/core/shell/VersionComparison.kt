package brobata.physiboard.core.shell

/**
 * Whether a GitHub release tag is strictly newer than the installed version name. spec:
 * app-shell.md SS13.4. "Newer" is strict: equal is not an update, and a local build ahead of the
 * newest release is not an update either.
 */
enum class VersionComparisonResult { NEWER, NOT_NEWER }

/**
 * Compares a release tag against the installed version name the way the update checker does
 * (app-shell.md SS13.4, SS13.9). Both are normalized the same way before comparing, so the
 * function works equally for "is this release newer" and for T7's normalization check.
 */
object VersionComparison {

    /** Strips one leading `v` or `V` (SS13.4: "removing one leading v or V"). */
    fun normalize(raw: String): String = if (raw.isNotEmpty() && (raw[0] == 'v' || raw[0] == 'V')) raw.substring(1) else raw

    /**
     * Splits a normalized version at the first `-` or `+`, then on `.`, reducing each part to its
     * leading digits; parts with no digits are dropped. Returns null when no part carried a digit,
     * which signals the caller to fall back to plain string comparison (SS13.4).
     */
    private fun numericParts(normalized: String): List<Int>? {
        val core = normalized.substringBefore('-').substringBefore('+')
        val parts = core.split('.').mapNotNull { part ->
            val digits = part.takeWhile { it.isDigit() }
            digits.ifEmpty { null }
        }.map { it.toInt() }
        return parts.ifEmpty { null }
    }

    /**
     * spec: SS13.4. Compares [tag] (a release tag, `v` accepted) against [installedVersionName].
     * When either side has no numeric parts the comparison is plain string inequality; otherwise
     * parts are compared left to right with missing parts as 0.
     */
    fun compare(tag: String, installedVersionName: String): VersionComparisonResult {
        val latestNormalized = normalize(tag)
        val currentNormalized = normalize(installedVersionName)
        val latestParts = numericParts(latestNormalized)
        val currentParts = numericParts(currentNormalized)
        val isNewer = if (latestParts == null || currentParts == null) {
            latestNormalized != currentNormalized && latestNormalized > currentNormalized
        } else {
            val size = maxOf(latestParts.size, currentParts.size)
            var result = 0
            for (i in 0 until size) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l != c) {
                    result = l.compareTo(c)
                    break
                }
            }
            result > 0
        }
        return if (isNewer) VersionComparisonResult.NEWER else VersionComparisonResult.NOT_NEWER
    }
}
