# PhysiBoard 3.0: consumer keep rules for :broker, merged automatically into any app that depends
# on this module (docs/release.md, "Shrinking").
#
# This module is vendored, unmodified Shizuku code (see broker/NOTICE): AdbPairingClient's native
# methods are matched against libadb.so by exact name, and AdbClient reflects into the platform's
# own Conscrypt implementation. Either breaks the moment R8 renames or removes a member, so the
# whole vendored package is kept as-is rather than picked apart keep by keep.
-keep class moe.shizuku.manager.adb.** { *; }
-dontwarn moe.shizuku.manager.adb.**
