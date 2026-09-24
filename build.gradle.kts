/*
 * Every plugin the build uses is declared here once, and applied by the modules
 * that need it. Declaring a plugin in two places puts it on the classpath twice
 * and Gradle then refuses to check their versions against each other.
 */
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
