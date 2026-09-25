plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * The T2E toolbox's decision logic, as plain Kotlin (broker-privileged-toolbox.md SS12 to SS15):
 * the bloat catalog and its census/journal bookkeeping, the density arithmetic and its pending
 * revert record, the system tweaks' read/write lines, and the key mapping inventory's per-row
 * text. None of it touches a shell, a broker or a preferences file; `:device:privileged` executes
 * the shell lines this module renders and persists the JSON this module encodes.
 */
kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The toolbox is pure decisions over immutable snapshots, so a warning here is
        // nearly always a real mistake.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // The journal and pending-revert records are small fixed-shape JSON. Only the JSON tree API is
    // used (no compiler plugin), the same way core/settings/JsonRows.kt reads its own stored JSON.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
