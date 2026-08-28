plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
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

fun shouldValidateNightlySigning(taskNames: List<String>): Boolean {
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

fun shouldValidateReleaseSigning(taskNames: List<String>): Boolean {
    if (taskNames.isEmpty()) {
        return true
    }
    val signingTaskHints = listOf(
        "assembleStableRelease",
        "bundleStableRelease",
        "packageStableRelease",
        "publishStableRelease",
        "installStableRelease"
    )
    return taskNames.any { task ->
        signingTaskHints.any { hint -> task.contains(hint, ignoreCase = true) }
    }
}

android {
    namespace = "brobata.physiboard"
    compileSdk = 36

    val defaultVersionCode = 10204
    val defaultVersionName = "1.2.4"
    val ciVersionCode = renamedGradleProperty("PHYSIBOARD_VERSION_CODE")?.toIntOrNull()
    val ciVersionName = renamedGradleProperty("PHYSIBOARD_VERSION_NAME")
    val nightlyVersionCode = renamedGradleProperty("PHYSIBOARD_NIGHTLY_VERSION_CODE")?.toIntOrNull()
    val nightlyVersionNameSuffix = renamedGradleProperty("PHYSIBOARD_NIGHTLY_VERSION_SUFFIX") ?: "-nightly"
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
    
    // Validate signing config only when building release
    tasks.whenTaskAdded {
        if (!isFdroidBuild && name.equals("preReleaseBuild", ignoreCase = true)) {
            doFirst {
                if (!shouldValidateReleaseSigning(gradle.startParameter.taskNames)) {
                    logger.lifecycle("Skipping stable signing validation for non-packaging task(s): ${gradle.startParameter.taskNames}")
                    return@doFirst
                }
                val storePath = signingProp("storeFile", "PHYSIBOARD_KEYSTORE_PATH")
                val storePass = signingProp("storePassword", "PHYSIBOARD_KEYSTORE_PASSWORD")
                val alias = signingProp("keyAlias", "PHYSIBOARD_KEY_ALIAS")
                val keyPass = signingProp("keyPassword", "PHYSIBOARD_KEY_PASSWORD")

                if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
                    throw GradleException(
                        "Missing signing config for release build. Define storeFile, storePassword, keyAlias e keyPassword in " +
                            "keystore.properties (non tracciato) o nelle variabili d'ambiente PHYSIBOARD_KEYSTORE_PATH, " +
                            "PHYSIBOARD_KEYSTORE_PASSWORD, PHYSIBOARD_KEY_ALIAS, PHYSIBOARD_KEY_PASSWORD. " +
                            "Use -PPHYSIBOARD_FDROID_BUILD=true only for the unsigned stable F-Droid release path."
                    )
                }
            }
        }
        if (name.equals("preNightlyReleaseBuild", ignoreCase = true)) {
            doFirst {
                if (!shouldValidateNightlySigning(gradle.startParameter.taskNames)) {
                    logger.lifecycle("Skipping nightly signing validation for non-packaging task(s): ${gradle.startParameter.taskNames}")
                    return@doFirst
                }
                val storePath = signingProp("nightlyStoreFile", "PHYSIBOARD_NIGHTLY_KEYSTORE_PATH")
                val storePass = signingProp("nightlyStorePassword", "PHYSIBOARD_NIGHTLY_KEYSTORE_PASSWORD")
                val alias = signingProp("nightlyKeyAlias", "PHYSIBOARD_NIGHTLY_KEY_ALIAS")
                val keyPass = signingProp("nightlyKeyPassword", "PHYSIBOARD_NIGHTLY_KEY_PASSWORD")

                if (!hasSigningConfig(storePath, storePass, alias, keyPass)) {
                    throw GradleException(
                        "Missing signing config for nightly build. Define nightlyStoreFile, nightlyStorePassword, nightlyKeyAlias e nightlyKeyPassword in " +
                            "keystore.properties (non tracciato) o nelle variabili d'ambiente PHYSIBOARD_NIGHTLY_KEYSTORE_PATH, " +
                            "PHYSIBOARD_NIGHTLY_KEYSTORE_PASSWORD, PHYSIBOARD_NIGHTLY_KEY_ALIAS, PHYSIBOARD_NIGHTLY_KEY_PASSWORD."
                    )
                }
            }
        }
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // RecyclerView per performance ottimali nella griglia emoji
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Emoji2 per supporto emoji future-proof
    implementation("androidx.emoji2:emoji2:1.4.0")
    implementation("androidx.emoji2:emoji2-views:1.4.0")
    implementation("androidx.emoji2:emoji2-views-helper:1.4.0")
    // Kotlinx Serialization for dictionary optimization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.3")
    // Shizuku for ADB shell access
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Embedded wireless-ADB pairing (vendored moe.shizuku.manager.adb, physi test entry point)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.2")
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("org.mockito:mockito-core:5.11.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
