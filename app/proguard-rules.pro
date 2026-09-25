# PhysiBoard 3.0: R8 rules for the `release` build type of :app.
#
# Every module that is itself an Android library (:ime, :broker, :device:privileged) carries its
# own consumerProguardFiles for the classes the system instantiates by name (the InputMethodService
# subclass, the Quick Settings tile, the notification listener, the vendored ADB client); those
# merge in automatically and are not repeated here. This file only covers what R8 cannot see from
# :app's own code: libraries used by the two pure-Kotlin modules that have no proguard mechanism of
# their own (:core:actions, :core:settings), and DataStore's on-disk format.
#
# Jetpack Compose and Material 3 need no entries here: every androidx.compose.* artifact ships its
# own consumer rules, and this file must not blanket-keep them, or the whole point of enabling R8
# (shrinking the Compose-heavy settings app) is lost.

# kotlinx.serialization's JSON tree API (JsonObject/JsonElement), used by :core:actions and
# :core:settings to hand-parse stored JSON (snippets, launcher assignments, the settings
# catalogue, ctrl_key_mappings.json). No @Serializable class exists in this codebase; everything
# is built and read as a JsonElement tree by hand, so no generated-serializer keep rules apply;
# this only guards the library's own use of reflection when a Json {} instance is configured.
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class kotlinx.serialization.json.** { *; }

# DataStore Preferences (settings-catalog.md SS1, SS13: "one preference file with typed rows").
# Its on-disk format is a protobuf-lite message read back by field name; belt-and-suspenders
# alongside the datastore-preferences artifact's own bundled consumer rules.
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Crash guards (Log.e) stay meaningful in a release build: keep file names and line numbers in
# stack traces even though method and class names are still shortened.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
