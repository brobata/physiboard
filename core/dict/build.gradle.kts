plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The pipeline is pure functions over immutable state, so a warning
        // here is nearly always a real mistake.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Only the JSON tree API (no compiler plugin): the personal-word file and the hosted
    // dictionary manifest are both hand-parsed, same pattern as `:core:settings`' JsonRows.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
