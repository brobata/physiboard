plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

// Config di firma letta da release/keystore.properties (non tracciato) o da env vars
val keystorePropertiesFileCandidates = listOf(
    rootProject.file("release/keystore.properties"),
    rootProject.file("keystore.properties")
)
val keystorePropertiesFile = keystorePropertiesFileCandidates.firstOrNull { it.exists() }
    ?: keystorePropertiesFileCandidates.last()
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

/**
 * Reads a signing value from keystore.properties, then the environment.
 *
 * The environment variables were named PASTIERA_* before the fork was renamed. Both spellings are
 * accepted so an existing release setup keeps working; PHYSIBOARD_* is the name to use from here.
 */
fun signingProp(key: String, env: String): String? =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env.replace("PHYSIBOARD_", "PASTIERA_"))?.takeIf { it.isNotBlank() }

/** Gradle property lookup with the same PASTIERA_* fallback. */
fun renamedGradleProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.gradleProperty(name.replace("PHYSIBOARD_", "PASTIERA_")).orNull

fun resolveSigningStoreFile(storePath: String): File =
    if (File(storePath).isAbsolute) {
        File(storePath)
    } else {
        keystorePropertiesFile.parentFile.resolve(storePath)
    }

fun hasSigningConfig(storePath: String?, storePass: String?, alias: String?, keyPass: String?): Boolean =
    storePath != null && storePass != null && alias != null && keyPass != null

fun gradleBooleanProperty(name: String): Boolean =
    (providers.gradleProperty(name).orNull
        ?: providers.gradleProperty(name.replace("PHYSIBOARD_", "PASTIERA_")).orNull)
        ?.equals("true", ignoreCase = true) == true

fun shouldValidateReleaseSigning(taskNames: List<String>): Boolean {
    if (taskNames.isEmpty()) {
        return true
    }
    val signingTaskHints = listOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "publishRelease",
        "installRelease"
    )
    return taskNames.any { task ->
        signingTaskHints.any { hint -> task.contains(hint, ignoreCase = true) }
    }
}

android {
    namespace = "brobata.physiboard"
    compileSdk = 36

    val defaultVersionCode = 20003
    val defaultVersionName = "2.0.3"
    val ciVersionCode = renamedGradleProperty("PHYSIBOARD_VERSION_CODE")?.toIntOrNull()
    val ciVersionName = renamedGradleProperty("PHYSIBOARD_VERSION_NAME")
    val isFdroidBuild = gradleBooleanProperty("PHYSIBOARD_FDROID_BUILD")

    defaultConfig {
        applicationId = "brobata.physiboard"
        manifestPlaceholders["appLabel"] = "PhysiBoard"
        manifestPlaceholders["imeLabel"] = "PhysiBoard"
        buildConfigField("String", "RELEASE_CHANNEL", "\"physi\"")
        buildConfigField("String", "GITHUB_REPO", "\"brobata/physiboard\"")
        buildConfigField("boolean", "IS_FDROID_BUILD", "false")
        buildConfigField("boolean", "ENABLE_GITHUB_UPDATE_CHECKS", "true")
        minSdk = 29
        targetSdk = 36
        // libadb.so ships for arm64 only; the Titan 2 Elite is arm64 and so is every device
        // this keyboard targets. Without the filter an x86 install silently has no native lib.
        ndk { abiFilters += "arm64-v8a" }
        versionCode = ciVersionCode ?: defaultVersionCode
        versionName = ciVersionName ?: defaultVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = signingProp("storeFile", "PHYSIBOARD_KEYSTORE_PATH")
            val storePass = signingProp("storePassword", "PHYSIBOARD_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "PHYSIBOARD_KEY_ALIAS")
            val keyPass = signingProp("keyPassword", "PHYSIBOARD_KEY_PASSWORD")

            // Only configure signing if all credentials are provided
            if (hasSigningConfig(storePath, storePass, alias, keyPass)) {
                val resolvedStoreFile = resolveSigningStoreFile(storePath!!)
                storeFile = resolvedStoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                    "proguard-rules-strip-logs.pro"
                )
            )
            // Only use signing config if it's properly configured
            val storePath = signingProp("storeFile", "PHYSIBOARD_KEYSTORE_PATH")
            val storePass = signingProp("storePassword", "PHYSIBOARD_KEYSTORE_PASSWORD")
            val alias = signingProp("keyAlias", "PHYSIBOARD_KEY_ALIAS")
            val keyPass = signingProp("keyPassword", "PHYSIBOARD_KEY_PASSWORD")
            
            if (!isFdroidBuild && hasSigningConfig(storePath, storePass, alias, keyPass)) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Disable lint for release to avoid file lock issues
            isDebuggable = false
        }

        // Side-by-side build for testing on the real phone (AGP forbids a build type
        // named 'test'): same R8 pipeline as release so it
        // catches ProGuard breakage, but with logging kept and its own applicationId so it can
        // never touch the daily driver's data or its IME registration.
        create("sideload") {
            initWith(getByName("release"))
            applicationIdSuffix = ".sideload"
            manifestPlaceholders["appLabel"] = "PhysiBoard (sideload)"
            manifestPlaceholders["imeLabel"] = "PhysiBoard (sideload)"
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
            matchingFallbacks += listOf("release")
        }
    }
    
    // Signing is validated here, at configuration time, rather than in a doFirst. The old hook
    // called script-level helpers from inside the task, which the configuration cache cannot
    // serialise, and it only ever failed on the release path - long after the rest looked green.
    if (!isFdroidBuild && shouldValidateReleaseSigning(gradle.startParameter.taskNames)) {
        val storePath = signingProp("storeFile", "PHYSIBOARD_KEYSTORE_PATH")
        val storePass = signingProp("storePassword", "PHYSIBOARD_KEYSTORE_PASSWORD")
        val alias = signingProp("keyAlias", "PHYSIBOARD_KEY_ALIAS")
        val keyPass = signingProp("keyPassword", "PHYSIBOARD_KEY_PASSWORD")

        if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
            throw GradleException(
                "Missing signing config for the release build. Set storeFile, storePassword, " +
                    "keyAlias and keyPassword in release/keystore.properties, or the environment " +
                    "variables PHYSIBOARD_KEYSTORE_PATH, PHYSIBOARD_KEYSTORE_PASSWORD, " +
                    "PHYSIBOARD_KEY_ALIAS and PHYSIBOARD_KEY_PASSWORD. " +
                    "-PPHYSIBOARD_FDROID_BUILD=true is only for the unsigned F-Droid path."
            )
        }
    }

    // Play-only dependency metadata; F-Droid style builds reject it and it is opaque to users.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    lint {
        // Findings that predate the gate live in the baseline; anything new fails CI.
        baseline = file("lint-baseline.xml")
        lintConfig = file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // Exclude legacy JSON base dictionaries; keep serialized .dict and user_defaults.json
            excludes += "assets/common/dictionaries/*_base.json"
            // BouncyCastle (bcpkix/bcutil/bcprov) + jspecify ship duplicate OSGI metadata
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/**/OSGI-INF/**"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/whatsnew"))
}

// The in-app "what's new" card shows the top section of the change record. Generating it here
// means a release cannot ship with last version's notes because someone forgot to copy them.
val generateWhatsNew by tasks.registering {
    val changeRecord = rootProject.file("PHYSIBOARD_CHANGES.md")
    val versionName = android.defaultConfig.versionName ?: ""
    val outDir = layout.buildDirectory.dir("generated/whatsnew/common")
    inputs.file(changeRecord)
    inputs.property("versionName", versionName)
    outputs.dir(outDir)
    doLast {
        val lines = changeRecord.readLines()
        // The section for this version; the newest released section when none matches, so an
        // "Unreleased" heading at the top never reaches users.
        val heading = lines.indexOfFirst { it.startsWith("## $versionName ") }
            .takeIf { it >= 0 }
            ?: lines.indexOfFirst { Regex("""^## \d+\.\d+""").containsMatchIn(it) }
        val next = if (heading < 0) -1 else lines.drop(heading + 1).indexOfFirst { it.startsWith("## ") }
        val section = when {
            heading < 0 -> emptyList()
            next < 0 -> lines.drop(heading + 1)
            else -> lines.subList(heading + 1, heading + 1 + next)
        }
        // The card is for users; the rest of the section is the GPLv3 change record and stays in
        // the file. Everything after the marker is left out of the app.
        val shown = section.takeWhile { !it.trimStart().startsWith("<!-- /card -->") }
        val dir = outDir.get().asFile.apply { mkdirs() }
        dir.resolve("whats_new.md").writeText(shown.joinToString("\n").trim() + "\n")
    }
}
tasks.named("preBuild") { dependsOn(generateWhatsNew) }

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Shizuku for ADB shell access
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // Embedded wireless-ADB pairing (vendored moe.shizuku.manager.adb)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.hiddenapibypass)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
