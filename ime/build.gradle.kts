import java.util.Properties

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
        // layers-sym-alt.md SS4.5: the KLIPY key for the GIF page. From the Gradle property
        // `klipy.apiKey` (-Pklipy.apiKey=..., or ~/.gradle/gradle.properties) or the same line in
        // the untracked local.properties. Blank means the page says GIF search is not set up.
        buildConfigField("String", "KLIPY_API_KEY", "\"${klipyApiKey().replace("\\", "").replace("\"", "")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // DiagnosticLog.i (KeyboardSession.kt, DictionaryAssetLoader.kt) reads this to compile the
    // phone-testing Log.i trail to nothing outside `sideload`/`debug` (docs/release.md, "Logging").
    buildFeatures { buildConfig = true }
    // The spell checker's session test runs on Robolectric (JUnit 4, through the vintage engine).
    testOptions { unitTests.isIncludeAndroidResources = true }
}

/** The KLIPY API key, never committed: a Gradle property first, then local.properties, else blank. */
fun klipyApiKey(): String {
    (findProperty("klipy.apiKey") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val local = rootProject.file("local.properties")
    if (!local.isFile) return ""
    val props = Properties()
    local.inputStream().use { props.load(it) }
    return props.getProperty("klipy.apiKey")?.trim().orEmpty()
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
    // app-shell.md SS10.2: the Diagnostics screen's debug capture store, reached only through the
    // DebugCaptureSink seam (mirrors SettingsSource), since :app owns the store and depends on :ime.
    api(project(":core:shell"))
    implementation(libs.androidx.core.ktx)
    // layers-sym-alt.md SS4.7: the inline suggestion style password managers read (Apache-2.0,
    // Android Jetpack). Without a style in this library's format they offer the keyboard nothing.
    implementation(libs.androidx.autofill)
    // The settings store is read as a Flow (SettingsSource); collection happens on the main looper.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("failed") }
}
