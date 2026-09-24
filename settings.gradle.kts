/*
 * PhysiBoard 3.0. The modules below hold no Android code: they are the typing
 * pipeline as plain Kotlin, so every rule in docs/spec can be tested on the JVM
 * without a device or a Robolectric shim. Android modules (:ime, :settings,
 * :app) join later and depend on these, never the other way round.
 */
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhysiBoard"

include(":core:keys")
include(":core:text")
include(":core:dict")
include(":core:pointer")
include(":core:speech")
include(":core:settings")
include(":core:strip")
include(":device:titan")

// The Android side. It adapts the pipeline to the platform and owns nothing else.
// Third-party, Apache-2.0, kept at arm's length. See broker/NOTICE.
include(":broker")

include(":ime")
include(":app")
