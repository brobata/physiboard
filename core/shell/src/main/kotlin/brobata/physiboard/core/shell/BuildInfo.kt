package brobata.physiboard.core.shell

/**
 * The build-time constants the update checker and the About screen key off (app-shell.md SS1,
 * SS23.1). `:app` supplies the live values (there is exactly one build of each); this object
 * exists so a JVM test can pin them the way T24 pins 2.x's.
 */
data class BuildFacts(
    val applicationId: String,
    val releaseChannel: String,
    val githubRepo: String,
    val fdroidBuild: Boolean,
    val githubUpdateChecksBuildFlag: Boolean,
)

/** spec: SS1, "GitHub checks allowed": the build flag is on and the installer was not an F-Droid client. */
object GithubChecks {
    private val fdroidInstallers = setOf("org.fdroid.fdroid", "org.fdroid.basic")

    fun allowed(buildFlagOn: Boolean, installerPackageName: String?): Boolean =
        buildFlagOn && installerPackageName !in fdroidInstallers
}
