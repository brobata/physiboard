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
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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
