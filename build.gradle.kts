/*
 * Nothing is applied at the root. Each module says what it is; a convention
 * plugin arrives when the Android modules do and there is real duplication to
 * remove.
 */
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
