plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "brobata.physiboard.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "brobata.physiboard"
        minSdk = 31
        targetSdk = 36
        versionCode = 30000
        versionName = "3.0.0-dev"
    }

    buildTypes {
        /*
         * The only build safe to put on the maintainer's phone while 2.x is the
         * daily driver: a different applicationId means it cannot touch the
         * release install's data or its keyboard registration.
         */
        create("sideload") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".sideload"
            versionNameSuffix = "-sideload"
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
