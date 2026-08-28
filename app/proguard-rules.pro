# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Log stripping lives in proguard-rules-strip-logs.pro so the test build type can keep logs.

# Keep org.json classes (though they're in Android SDK, better safe than sorry)
-keep class org.json.** { *; }

# Keep BuildConfig for runtime checks if needed
-keep class it.palsoftware.pastiera.BuildConfig { *; }
# --- PhysiBoard: embedded wireless-ADB (smart backlight) ---
# libadb.so binds JNI to the hardcoded class moe/shizuku/manager/adb/PairingContext,
# and the vendored ADB stack uses reflection/crypto — keep it all so R8 can't strip/rename it.
-keep class moe.shizuku.manager.adb.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
