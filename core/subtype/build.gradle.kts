plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The catalog is a pure decision over an immutable style list, so a warning here is
        // nearly always a real mistake.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // `LanguagePrefs` (`custom_input_styles`, `hidden_system_input_styles`,
    // `keyboard_layout_auto_by_locale`) is `:core:settings`'s own type; `LayoutDescription` (what
    // a style actually resolves to, the value `:core:keys`'s `LayerResolver` resolves a keystroke
    // against) is `:core:keys`'s. Reusing both keeps one vocabulary for "a stored language row"
    // and "a layout", not a parallel one that can drift (core/settings/build.gradle.kts's own
    // reasoning for depending on `:core:keys`).
    api(project(":core:settings"))
    api(project(":core:keys"))
    // Only the JSON tree API (no compiler plugin): `locale_layout_mapping.json`, the per-locale
    // layout override the Input Languages screen's "System" rows write, is hand-parsed like
    // `:core:keys`'s `ctrl_key_mappings.json`, so `:ime` can decode the identical file `:app` writes.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
