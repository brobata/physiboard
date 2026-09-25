plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * The app shell as plain Kotlin (app-shell.md): version comparison and release-JSON parsing for
 * the update checker, the what's-new note's markdown parser and card-due decision, launch
 * routing, the first-run step state, the debug capture store, the backup file codec over
 * :core:settings' flat map, and the IME-id and device-detection predicates the shell's screens
 * and receivers key off. No Android import: every rule here is a JVM test.
 */
kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // The backup codec round-trips a Settings through SettingsCodec's flat map (SettingsKeys are
    // reused so a backup and a live store cannot disagree on a key's spelling).
    api(project(":core:settings"))
    // Hand-rolled JSON tree reads only, same as :core:settings: the GitHub release list and the
    // backup file are both read a field at a time so a malformed document degrades to "no update" /
    // "not a PhysiBoard backup" instead of throwing.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
