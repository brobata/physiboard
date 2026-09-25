plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/*
 * Third-party code, kept apart on purpose.
 *
 * This module is a vendored subset of Shizuku by RikkaApps, under the Apache
 * License 2.0, which lets PhysiBoard pair with the phone's own Wireless
 * Debugging and run the few privileged commands the keyboard backlight and the
 * notification ring need. It keeps its original package name so attribution
 * survives, and it is NOT PhysiBoard's own work: see broker/NOTICE.
 *
 * It lives in its own module so the boundary is visible in the build rather
 * than only in a comment, and so the clean-room rule that governs the rest of
 * 3.0 plainly does not apply here.
 */
android {
    namespace = "brobata.physiboard.broker"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        // Carried into any app that depends on this module: the vendored client's JNI native
        // method names and its reflection into platform Conscrypt must survive R8 untouched
        // (docs/release.md, "Shrinking").
        consumerProguardFiles("proguard-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    // Upstream code, carried unchanged; its warnings are not ours to fix.
    lint { checkReleaseBuilds = false; abortOnError = false }
    // AdbMdns.kt reads this to compile its phone-testing Log.i trail to nothing outside
    // `sideload`/`debug` (docs/release.md, "Logging"); the vendored code is otherwise unchanged.
    buildFeatures { buildConfig = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.hiddenapibypass)
}
