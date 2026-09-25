plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
}
