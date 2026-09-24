plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "brobata.physiboard.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "brobata.physiboard"
        minSdk = 31
        targetSdk = 36
        versionCode = 30000
        versionName = "3.0.0-dev"
    }

    buildTypes {
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
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // The settings store (settings-catalog.md SS1, SS13 "one preference file with typed rows") and
    // the one-shot 2.x importer that fills it (rebuild-from-scratch.md, "Settings").
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
    // The one process-wide wiring of the broker, pairing, setup pass and ring (PrivilegedServicesOwner).
    implementation(project(":device:privileged"))
