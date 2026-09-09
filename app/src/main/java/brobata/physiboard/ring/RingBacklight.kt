package brobata.physiboard.ring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import brobata.physiboard.SettingsManager
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import brobata.physiboard.inputmethod.PrivilegedDiagnostics
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps the keyboard dark while the ring is lit.
 *
 * The ring turns the screen on, and the vendor lights the keyboard whenever the screen comes
 * on — so a notification at 3am lights the whole keyboard to show a ring that exists precisely
 * so nothing else has to light up. With Smart Backlight on (`keyboard_brightness_timeout = -1`)
 * it then never goes off again.
 *
 * The switch is the vendor's own: `Settings.Global.agui_keyboard_background_light`, the same row
 * the Quick Settings tile toggles. Writing a Global needs WRITE_SECURE_SETTINGS, which the broker
 * grants ONCE with a `pm grant` ([grantViaBroker]) — after that every write here is an ordinary
 * in-process settings write. That matters: the keyboard has to go dark in the moment a
 * notification lands, and an ~8s broker round trip in that path would be useless. This is why
 * the backlight *timeout* (a vendor binder call, broker-only, every time) is the wrong lever and
 * this row is the right one.
 *
 * The switch is turned off BEFORE the ring is launched, not from the ring itself: the vendor
 * reads it when the screen comes on, and by the time the activity exists the screen already has.
 *
 * ## Putting it back
 *
 * This writes a system row that outlives the app, so every path has to end with the user's own
 * value restored. Three of them:
 *  - the ring ends — [NotificationRingActivity.onDestroy] calls [restore]; that one funnel covers
 *    expiry, a touch, a keypress, an unlock and the screen going off.
 *  - the ring never appears — the system can decline a full-screen launch, so [suppress] arms a
 *    timer that restores when no ring took ownership. [holdForRing] cancels it.
 *  - the process dies in between — the intent to restore is recorded in preferences BEFORE the
 *    write, and [restoreLater] replays it at every process start.
 */
object RingBacklight {
    private const val TAG = "NotificationRing"

    /** The vendor's keyboard-backlight switch. Shared with the Quick Settings tile. */
    const val SETTING = "agui_keyboard_background_light"

    private const val ON = 1
    private const val OFF = 0

    /**
     * Longer than the launcher's own 15s announcement timeout: if the system was ever going to
     * start the ring it has by now, and if it did not, the keyboard must not stay dark.
     */
    private const val ORPHAN_TIMEOUT_MS = 20_000L

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    /** True once a ring has taken ownership of the restore. */
    @Volatile
    private var heldByRing = false

    @Volatile
    private var orphanCheck: ScheduledFuture<*>? = null

    /** Whether the app can write the vendor switch itself, with no broker round trip. */
    fun canWrite(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Turn the keyboard off for the ring that is about to appear. Call OFF the main thread and
     * BEFORE the screen is woken. No-op when the feature is off, the permission is missing, the
     * keyboard is already dark, or a suppression is already outstanding.
     */
    fun suppress(context: Context) {
        val app = context.applicationContext
        if (!SettingsManager.isNotificationRingEnabled(app)) return
        if (!SettingsManager.isRingKeyboardDarkEnabled(app)) return
        if (!canWrite(app)) return
        if (SettingsManager.isRingBacklightSuppressed(app)) return

        val prior = runCatching {
            Settings.Global.getInt(app.contentResolver, SETTING, ON)
        }.getOrDefault(ON)
        // Already dark: there is nothing of ours to put back, and claiming otherwise would let
        // us "restore" a keyboard the user had switched off themselves.
        if (prior == OFF) return

        // Recorded BEFORE the write. A process death between the two must leave a restore still
        // to do; the other order leaves a dark keyboard with no record of who darkened it.
        SettingsManager.setRingBacklightSuppressed(app, prior)
        if (!write(app, OFF)) {
            SettingsManager.clearRingBacklightSuppressed(app)
            return
        }
        heldByRing = false
        armOrphanCheck(app)
    }

    /**
     * The ring is on screen and its own teardown owns the restore, so the orphan timer can go.
     * Called from [NotificationRingActivity.onCreate].
     */
    fun holdForRing() {
        heldByRing = true
        cancelOrphanCheck()
    }

    /**
     * Put the keyboard back the way it was. Safe to call at any time and from any thread; a
     * no-op when nothing is outstanding.
     *
     * When the permission has gone the record is deliberately KEPT — a later grant can still heal
     * it, and dropping it would strand the switch off with nothing left that knows to put it back.
     */
    fun restore(context: Context) {
        val app = context.applicationContext
        cancelOrphanCheck()
        heldByRing = false
        if (!SettingsManager.isRingBacklightSuppressed(app)) return
        if (!canWrite(app)) {
            Log.e(TAG, "cannot restore the keyboard backlight: permission gone")
            return
        }
        val prior = SettingsManager.getRingBacklightPrior(app)
        if (write(app, prior)) SettingsManager.clearRingBacklightSuppressed(app)
    }

    /** [restore] off the caller's thread, for start-up healing. */
    fun restoreLater(context: Context) {
        val app = context.applicationContext
        if (!SettingsManager.isRingBacklightSuppressed(app)) return
        runCatching { scheduler.execute { runCatching { restore(app) } } }
    }

    /**
     * Take the one permission this needs from the paired broker. Best-effort, off-thread, never
     * throws; a no-op once granted or when nothing is paired. Called from
     * [brobata.physiboard.inputmethod.PrivilegedSetup].
     */
    fun grantViaBroker(context: Context) {
        val app = context.applicationContext
        if (canWrite(app)) {
            PrivilegedDiagnostics.record(
                app, PrivilegedDiagnostics.Step.RING_BACKLIGHT, ok = true,
                reason = PrivilegedDiagnostics.REASON_OK
            )
            return
        }
        if (!EmbeddedAdbShell.isPaired(app)) {
            PrivilegedDiagnostics.record(
                app, PrivilegedDiagnostics.Step.RING_BACKLIGHT, ok = false,
                reason = PrivilegedDiagnostics.REASON_NOT_PAIRED
            )
            return
        }
        scheduler.execute {
            runCatching {
                EmbeddedAdbShell.runShell(
                    app,
                    "pm grant ${app.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
                )
            }
            // The shell's own exit code is not the answer — `pm grant` is quiet on success and
            // the permission is what actually matters, so ask for that instead.
            val granted = canWrite(app)
            PrivilegedDiagnostics.record(
                app, PrivilegedDiagnostics.Step.RING_BACKLIGHT, ok = granted,
                reason = if (granted) {
                    PrivilegedDiagnostics.REASON_OK
                } else {
                    EmbeddedAdbShell.lastError ?: PrivilegedDiagnostics.REASON_SHELL_FAILED
                }
            )
        }
    }

    private fun write(context: Context, value: Int): Boolean = runCatching {
        Settings.Global.putInt(context.contentResolver, SETTING, value)
    }.getOrDefault(false)

    private fun armOrphanCheck(context: Context) {
        val app = context.applicationContext
        cancelOrphanCheck()
        orphanCheck = runCatching {
            scheduler.schedule({
                if (!heldByRing) {
                    Log.e(TAG, "ring never appeared; restoring the keyboard backlight")
                    runCatching { restore(app) }
                }
            }, ORPHAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrNull()
    }

    private fun cancelOrphanCheck() {
        orphanCheck?.cancel(false)
        orphanCheck = null
    }
}
