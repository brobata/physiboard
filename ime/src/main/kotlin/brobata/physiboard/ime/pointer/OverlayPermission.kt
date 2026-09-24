package brobata.physiboard.ime.pointer

import android.content.Context
import android.provider.Settings
import brobata.physiboard.core.pointer.OverlayAvailability

/**
 * Reads the real "Display over other apps" permission the screen trackpad and caret badge need.
 *
 * spec: trackpad-caret-nav.md SS2.1, SS4.1 (`android.permission.SYSTEM_ALERT_WINDOW`). This is the
 * one `android.*` fact `:core:pointer`'s [OverlayAvailability] never assumes for itself; every
 * caller re-checks it here rather than caching a stale yes (SS4.6: "the overlay permission is
 * re-checked on every show, never cached"), so granting it while PhysiBoard is already running
 * takes effect on the next check with no restart.
 */
object OverlayPermission {
    fun availability(context: Context): OverlayAvailability =
        if (Settings.canDrawOverlays(context)) OverlayAvailability.AVAILABLE else OverlayAvailability.PERMISSION_MISSING
}
