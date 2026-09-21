/*
 * PhysiBoard 3.0. The modules below hold no Android code: they are the typing
 * pipeline as plain Kotlin, so every rule in docs/spec can be tested on the JVM
 * without a device or a Robolectric shim. Android modules (:ime, :settings,
 * :app) join later and depend on these, never the other way round.
 */
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "PhysiBoard"

include(":core:keys")
include(":core:text")
include(":core:dict")
include(":device:titan")
