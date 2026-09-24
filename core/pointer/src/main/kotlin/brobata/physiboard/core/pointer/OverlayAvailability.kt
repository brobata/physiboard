package brobata.physiboard.core.pointer

/**
 * Whether the platform has granted PhysiBoard the overlay window it needs.
 *
 * spec: trackpad-caret-nav.md SS2.1, SS4.1 ("both need... `SYSTEM_ALERT_WINDOW`"). The permission
 * check itself is a platform fact `:ime` supplies (`Settings.canDrawOverlays`, in the broker-grant
 * case a real broker round trip per SS2.9); this module only ever reads the answer. Every pure
 * decision here that would otherwise open a window degrades to [PERMISSION_MISSING] instead of
 * assuming success, so the feature does nothing visible rather than crashing when the window
 * manager would have refused it, and a settings screen can read the same value later to explain
 * why (SS2.8's "Permission not granted" row).
 */
enum class OverlayAvailability {
    AVAILABLE,
    PERMISSION_MISSING,
}
