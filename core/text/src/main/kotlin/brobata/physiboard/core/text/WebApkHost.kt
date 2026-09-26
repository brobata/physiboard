package brobata.physiboard.core.text

/**
 * The pure half of "which browser hosts this WebAPK's fields". spec: per-app-behavior.md SS2.2.
 * `:ime` is the only place allowed to touch `PackageManager` (rebuild-from-scratch.md, "`:ime`
 * ... reads, it calls, it applies"), so it reads the one manifest fact this rule needs, the shell
 * package's own `org.chromium.webapk.shell_apk.runtimeHost` metadata value, or answers null when
 * the package is not installed or declares no such metadata, and hands that raw value to
 * [resolve]. This module owns the two facts the spec states about package names themselves, not
 * about the manifest: which packages are WebAPKs at all, and what host to assume when the
 * manifest does not say. [AppProfileResolver.resolve]'s `webApkHost` parameter is exactly a
 * `:ime`-supplied instance of [resolve] with [rawRuntimeHost] already folded in.
 */
object WebApkHost {
    private const val SHELL_PREFIX = "org.chromium.webapk."

    /** The assumed host: not installed, missing metadata, or a blank value all fall back here. spec: SS2.2. */
    const val DEFAULT_HOST: String = "com.android.chrome"

    /** spec SS2.2: "A package is a WebAPK when its name starts with `org.chromium.webapk.`." */
    fun isWebApk(packageName: String): Boolean = packageName.startsWith(SHELL_PREFIX)

    /**
     * spec SS2.2: "The host browser of a WebAPK is read from the shell package's manifest
     * metadata entry `org.chromium.webapk.shell_apk.runtimeHost`. If the package is not
     * installed, the metadata is missing, or the value is blank, the host is assumed to be
     * `com.android.chrome`." "A package that is not a WebAPK has no host (the lookup answers
     * 'none')." [rawRuntimeHost] is whatever `:ime` read for that metadata key, or null for any
     * of the three "assume the default" cases; this function does not distinguish between them,
     * matching the spec's own wording.
     */
    fun resolve(packageName: String, rawRuntimeHost: String?): String? {
        if (!isWebApk(packageName)) return null
        return rawRuntimeHost?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
    }
}
