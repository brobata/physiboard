plugins {
    alias(libs.plugins.kotlin.jvm)
}

/*
 * Everything that inserts or launches something other than the typed character, as pure
 * decisions: snippet expansion, the clipboard history model, the emoji catalogue and search,
 * launcher key assignments, the quick launcher's ranking and key handling, the command
 * catalogue, and the typing-sound / tap-vibration rules (expansion-clipboard-pickers-launcher.md).
 * No Android import: `:ime` and `:app` supply the clipboard, the package list, the windows and
 * the intents, and read the answers from here.
 */
kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Keys are named by :core:keys' physical KeyId so a launcher assignment, an expansion key
    // and a typing-sound group all speak the pipeline's own vocabulary.
    api(project(":core:keys"))
    // Only the JSON tree API (no compiler plugin): the launcher's two JSON documents and the
    // source-visibility row are parsed and written by hand, as :core:settings does.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
