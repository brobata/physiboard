plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * The settings schema: one typed, versioned, immutable value for every preference 3.0 keeps
 * (settings-catalog.md SS2, SS13), a pure codec to and from a flat string map so the storage layer
 * can stay a dumb key-value file, and the pure 2.x importer (rebuild-from-scratch.md, "Settings").
 * No Android import: every mapping row is a JVM test.
 */
kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // The schema reuses the pipeline's own enums (dash style, long-press mode, trigger key, Enter
    // behavior) so there is one vocabulary, not a parallel one that can drift.
    api(project(":core:keys"))
    api(project(":core:text"))
    api(project(":core:pointer"))
    // Only the JSON tree API is used (no compiler plugin): the catalogue's JSON-shaped rows
    // (sym pages, Enter overrides, themes, snippets, ring colours) are parsed and written by hand.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
