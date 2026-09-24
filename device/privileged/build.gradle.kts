plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/*
 * Everything PhysiBoard does with shell privilege on the Titan 2 Elite
 * (broker-privileged-toolbox.md, device-backlight-ring.md): the one-time
 * Wireless debugging pairing, the serialised shell broker over the vendored
 * client in :broker, the idempotent setup pass and its reset-to-stock
 * counterpart, the keyboard backlight (vendor transaction and the Quick
 * Settings tile) and the notification ring.
 *
 * The decisions are :device:titan's and stay pure there. This module executes
 * them, so it is Android; but every class that decides an order of operations
 * (the broker's state and lock, the pairing flow, the setup steps, the reverts,
 * the ring's session) takes its Android edges as interfaces and is driven by a
 * JVM test against fakes. Only the thin adapters at the bottom of each package
 * import android.*.
 */
android {
    namespace = "brobata.physiboard.device.privileged"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":device:titan"))
    api(project(":core:settings"))
    // Third-party pairing and connect client, used and never modified (broker/NOTICE).
    implementation(project(":broker"))
    // AdbMdns reports its port through a lifecycle Observer; nothing else of lifecycle is used.
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("failed") }
}
