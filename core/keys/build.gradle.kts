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
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
