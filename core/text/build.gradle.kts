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
    api(project(":core:keys"))
    api(project(":core:dict"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
    // The sentence harness (autocorrect-suggestions.md SS12) replays the shipped English dictionary
    // and word-pair table where the app ships them, rather than a copy in test resources.
    val shippedDictionaries = rootProject.file("app/src/main/assets/dictionaries")
    inputs.dir(shippedDictionaries).withPropertyName("shippedDictionaries").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("physiboard.assets.dictionaries", shippedDictionaries.absolutePath)
    // Opt-in tuning sweep: -Pphysiboard.eval.sweep=true
    providers.gradleProperty("physiboard.eval.sweep").orNull?.let { systemProperty("physiboard.eval.sweep", it) }
    providers.gradleProperty("physiboard.eval.grid").orNull?.let { systemProperty("physiboard.eval.grid", it) }
    providers.gradleProperty("physiboard.eval.split").orNull?.let { systemProperty("physiboard.eval.split", it) }
    // Opt-in wall-clock ratchet: -Pphysiboard.eval.timing=true (a loaded build machine fails it).
    providers.gradleProperty("physiboard.eval.timing").orNull?.let { systemProperty("physiboard.eval.timing", it) }
    maxHeapSize = "6g"
}
