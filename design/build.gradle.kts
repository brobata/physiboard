plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/*
 * The design system both the settings app and the keyboard draw with (docs/design/design-system.md):
 * the two vendored typefaces, the shared icon family, and the tokens (colours, type sizes,
 * spacing, radii, motion) as plain numbers. `DesignTokens` imports nothing from Android, so the
 * numbers can be checked on the JVM; the Android half (fonts, icons, the motion helper) sits beside it.
 */
android {
    namespace = "brobata.physiboard.design"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // The open and close springs (DesignMotion): Jetpack's physics-based animation, Apache-2.0.
    api(libs.androidx.dynamicanimation)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("failed") }
}
