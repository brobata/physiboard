plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The strip's model is pure decisions over immutable snapshots, so a warning
        // here is nearly always a real mistake.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // No project dependency on purpose: the strip is told what the modifiers, the field and the
    // suggestion engine say through its own small input types, so it can be tested and reasoned
    // about without pulling the key or text pipelines in (status-bar.md SS1, "Refresh": the strip
    // receives a snapshot; it never computes one).
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
