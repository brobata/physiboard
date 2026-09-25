plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/*
 * The Android adapter. It owns no typing rules: it pulls fields off a real key
 * event, hands them to the pure pipeline, and applies the operations the
 * pipeline returns. Anything that decides what should happen belongs in :core.
 */
android {
    namespace = "brobata.physiboard.ime"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
        // Carried into any app that depends on this module, so R8 always keeps the service the
        // system instantiates by class name (docs/release.md, "Shrinking").
        consumerProguardFiles("proguard-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // DiagnosticLog.i (KeyboardSession.kt, DictionaryAssetLoader.kt) reads this to compile the
    // phone-testing Log.i trail to nothing outside `sideload`/`debug` (docs/release.md, "Logging").
    buildFeatures { buildConfig = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:text"))
    api(project(":core:speech"))
    api(project(":core:pointer"))
    api(project(":core:strip"))
    api(project(":device:titan"))
    api(project(":core:settings"))
    // The available input styles, the current one, the cycle order and what a switch changes (dictionaries-languages.md SS8, SS9).
    api(project(":core:subtype"))
    // Expansion, clipboard history, the pickers, launcher keys and the command catalogue (expansion-clipboard-pickers-launcher.md).
    api(project(":core:actions"))
    // The privileged setup pass runs at every IME start (broker-privileged-toolbox.md SS7, D17: the IME is what survives boot on this ROM).
    implementation(project(":device:privileged"))
    implementation(libs.androidx.core.ktx)
    // The settings store is read as a Flow (SettingsSource); collection happens on the main looper.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("failed") }
}
