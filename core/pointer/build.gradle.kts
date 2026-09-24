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
    // Nav mode's key map (the CtrlMapping/CtrlMappingTable/KeyId/EditEffect/Action vocabulary) is
    // the same 26-key map keys-and-modifiers.md SS12.2 already gives :core:keys for the in-field
    // Ctrl-hold path; reusing it here instead of a second parallel enum keeps the two call sites
    // agreeing about what "expand_selection_word_left" or a Keycode mapping means.
    api(project(":core:keys"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
