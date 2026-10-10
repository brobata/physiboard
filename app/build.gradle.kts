plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/*
 * The maintainer's signing keystore lives OUTSIDE this repo (docs/release.md). These four
 * variables are read from the environment rather than a checked-in file; when any is missing the
 * release build type below is left unsigned instead of failing, so `:app:assembleRelease` still
 * works for anyone measuring shrink or running CI without the real keystore.
 */
val releaseKeystorePath = System.getenv("PASTIERA_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("PASTIERA_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("PASTIERA_KEY_ALIAS")
val releaseKeyPassword = System.getenv("PASTIERA_KEY_PASSWORD")
val releaseSigningReady = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

if (!releaseSigningReady) {
    logger.warn(
        "PhysiBoard release: PASTIERA_KEYSTORE_PATH/PASTIERA_KEYSTORE_PASSWORD/" +
            "PASTIERA_KEY_ALIAS/PASTIERA_KEY_PASSWORD are not all set; :app:assembleRelease will " +
            "produce an unsigned APK (docs/release.md)."
    )
}

android {
    namespace = "brobata.physiboard.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "brobata.physiboard"
        minSdk = 31
        targetSdk = 36
        versionCode = 30100
        versionName = "3.1.0"
        // app-shell.md SS23.1 (D3): the phone this ships to is arm64 only, same as the embedded
        // ADB library; an x86 or armeabi-v7a build would carry native code that silently never runs.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // The 76 MB unshrunk build (measured from `sideload`) is mostly Compose and unused
            // framework code R8 can remove once it can see the whole app; docs/release.md SS
            // "Shrinking" has the before/after. Every module this depends on that is itself an
            // Android library ships its own consumerProguardFiles, so this file only needs the
            // keeps that R8 cannot infer from :app's own code (docs/release.md has the reasoning
            // for each block).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
        }
        /*
         * The only build safe to put on the maintainer's phone while 2.x is the
         * daily driver. It needs an id of its own for TWO reasons: it must not
         * touch the 2.x release install, and it must not collide with the 2.x
         * test build, which already owns `.sideload` and is signed with a
         * different key. Installing over that fails outright.
         */
        create("sideload") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev3"
            versionNameSuffix = "-dev3"
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The settings app (rebuild-from-scratch.md, "Settings") is Compose/Material 3; the rest of
    // `:app` (the importer, the store) has no UI and needs none of this.
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME feeds the launch-routing decision and the update checker
        // (app-shell.md SS3, SS13): the app shell needs the live version name, not a duplicate copy.
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ime"))
    // The one process-wide wiring of the broker, pairing, setup pass and ring (PrivilegedServicesOwner).
    implementation(project(":device:privileged"))
    // The app shell as plain Kotlin (app-shell.md): update checker, what's-new note, launch
    // routing, first-run steps, the debug capture store, the backup codec.
    implementation(project(":core:shell"))
    // layers-sym-alt.md SS5.7: the Theme preview draws the Symbols page from `:core:strip`'s own
    // `SymGridLayout`, so it matches the keyboard's grid key for key.
    implementation(project(":core:strip"))
    // The design system shared with the keyboard: fonts, icons, tokens (docs/design/design-system.md).
    implementation(project(":design"))
    // The Android 12+ splash screen, backported API (app-shell.md SS22.6).
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // The settings store (settings-catalog.md SS1, SS13 "one preference file with typed rows") and
    // the one-shot 2.x importer that fills it (rebuild-from-scratch.md, "Settings").
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // The settings app itself: Jetpack Compose plus Material 3 (settings-catalog.md SS9, "the
    // settings app"). The BOM pins every Compose artifact's version in one place.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // The personal-dictionary and Fn Layer file sidecars, and the dictionary manifest, are hand-
    // parsed JSON on this module's own side (the settings-screens agent's feature work); only the
    // JSON tree API is needed, same as every other module that parses stored JSON by hand.
    implementation(libs.kotlinx.serialization.json)
    // app-shell.md SS13.7: the daily background update check, a periodic job constrained to a
    // connected network. Scheduled/cancelled from PhysiBoardApplication only; :ime never touches it.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("failed") }
}

/*
 * app-shell.md SS5.1: builds the What's New asset from CHANGELOG.md before assets are merged, so
 * WhatsNewScreen never falls back to its "up to date" copy for a real release. This reimplements
 * brobata.physiboard.core.shell.ChangeRecordSection.extractCard's rule (the pure, JVM-tested
 * version lives there) rather than loading :core:shell's compiled output into the build script's
 * own classpath, which is awkward for a project dependency; see this module's report for the
 * duplication risk that creates between the two copies of the rule.
 *
 * The asset is written into the main source set (`common/whats_new.md`, read straight from
 * `assets/` at runtime), which every variant's merge-assets step, its lint model and several
 * other tasks all read; hooking `dependsOn` onto each one by name is brittle and Gradle's own
 * implicit-dependency validation rejects a task output that some of those consumers read without
 * declaring the edge. Instead this small, deterministic text transform of a checked-in file runs
 * once, synchronously, while this script is configured (the same phase AGP itself reads
 * `defaultConfig.versionName` in), so the asset already exists on disk before any task graph is
 * built. `generateWhatsNewAsset` still exists as an explicit, independently runnable task for
 * `./gradlew generateWhatsNewAsset` and for CI to depend on by name.
 */
val generateWhatsNewAsset = tasks.register("generateWhatsNewAsset") {
    group = "physiboard"
    description = "Rebuilds app/src/main/assets/common/whats_new.md from CHANGELOG.md (app-shell.md SS5.1)."
    doLast { writeWhatsNewAsset() }
}

writeWhatsNewAsset()

fun writeWhatsNewAsset() {
    val changelogFile = rootProject.file("CHANGELOG.md")
    val outputFile = file("src/main/assets/common/whats_new.md")
    val versionName = android.defaultConfig.versionName.orEmpty()
    outputFile.parentFile.mkdirs()
    outputFile.writeText(extractWhatsNewCard(changelogFile.readText(), versionName))
}

/**
 * Gradle-side mirror of [brobata.physiboard.core.shell.ChangeRecordSection.extractCard] (app-shell.md
 * SS5.1). Keep the two in step: this copy exists only because the build script cannot cheaply
 * depend on :core:shell's compiled classes; the pure, tested rule stays there.
 */
fun extractWhatsNewCard(changeRecord: String, versionName: String): String {
    val versionHeading = Regex("^##\\s+(\\d+\\.\\d+(?:\\.\\d+)?)")
    val lines = changeRecord.lines()
    val headingIndex = lines.indexOfFirst { it.startsWith("## $versionName ") }
    val startIndex = if (headingIndex >= 0) headingIndex else lines.indexOfFirst { versionHeading.containsMatchIn(it) }
    if (startIndex < 0) return ""

    val body = mutableListOf<String>()
    for (i in (startIndex + 1) until lines.size) {
        val line = lines[i]
        if (line.startsWith("## ")) break
        if (line.trim().startsWith("<!-- /card -->")) break
        body += line
    }
    return body.joinToString("\n").trim().let { if (it.isEmpty()) "" else it + "\n" }
}
