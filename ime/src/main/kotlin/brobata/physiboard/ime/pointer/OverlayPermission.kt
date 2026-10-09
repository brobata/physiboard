package brobata.physiboard.ime.pointer

import android.content.Context
import android.widget.Toast
import android.util.Log
import android.os.SystemClock
import android.net.Uri
import android.content.Intent
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

    private var lastRequestAtMs = 0L

    /**
     * The permission is missing and the user just asked for something that needs it (Sym, the
     * trackpad): say so and open Android's "Display over other apps" screen for this app, so one
     * switch fixes it (maintainer, 2026-10-09). At most once every [REQUEST_INTERVAL_MS], so
     * pressing Sym again while deciding doesn't keep reopening it; in between, only the note.
     */
    fun explainAndOpenSettings(context: Context, what: String) {
        runCatching { Toast.makeText(context, "$what needs Display over other apps. Turn it on for this app, then come back.", Toast.LENGTH_LONG).show() }
        val now = SystemClock.uptimeMillis()
        if (lastRequestAtMs != 0L && now - lastRequestAtMs < REQUEST_INTERVAL_MS) return
        lastRequestAtMs = now
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { error -> Log.e("PhysiBoardOverlay", "overlay permission screen could not open", error) }
    }

    private const val REQUEST_INTERVAL_MS = 8_000L
}
