package brobata.physiboard.device.privileged.ring

import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.privileged.backlight.MasterSwitchAccess
import brobata.physiboard.device.privileged.setup.PermissionProbe
import brobata.physiboard.device.privileged.updateCaptures
import brobata.physiboard.device.titan.BacklightRestoreDecision
import brobata.physiboard.device.titan.BacklightSuppressionDecision
import brobata.physiboard.device.titan.BacklightSuppressionRecord
import brobata.physiboard.device.titan.KeyboardBacklight

/** A one-shot delayed action that can be cancelled; the orphan timer's clock. */
fun interface DelayedRunner {
    /** Schedules [action] after [delayMs]; the returned function cancels it. */
    fun schedule(delayMs: Long, action: () -> Unit): () -> Unit
}

/**
 * The keyboard-kept-dark contract: turn the vendor's master switch off for a ring, and put it
 * back exactly, including when the process died mid-ring.
 *
 * The record ([DeviceStateStore]'s `ring_backlight_prev*`) is committed BEFORE the switch is
 * written, so a death between the two leaves a restore still to do; restore runs from the
 * ring's teardown, from the 20 s orphan timer (the system may decline the full-screen launch,
 * and 20 s outlasts the 15 s announcement), and at every process start. When the permission
 * has gone the record is kept, never dropped: a later grant heals it, and dropping it would
 * strand the keyboard off with nothing that knows to put it back.
 *
 * The decisions are [KeyboardBacklight]'s; this class only runs them against the real store,
 * switch and timer. spec: device-backlight-ring.md SS5.8; T6 to T14.
 */
class RingBacklight(
    private val store: DeviceStateStore,
    private val masterSwitch: MasterSwitchAccess,
    private val permissions: PermissionProbe,
    private val timer: DelayedRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var cancelOrphanTimer: (() -> Unit)? = null

    @Volatile
    private var ringOwnsRestore = false

    /** spec: SS5.8 "Suppress", background thread, before the launch. Returns true when the switch was turned off for this ring. */
    @Synchronized
    fun suppress(): Boolean {
        val settings = store.snapshot()
        val decision = KeyboardBacklight.decideSuppression(
            ringEnabled = settings.device.ringEnabled,
            keyboardDarkEnabled = settings.device.ringKeyboardDark,
            hasWriteSecureSettingsPermission = permissions.hasWriteSecureSettings(),
            alreadySuppressed = settings.captures.ringBacklightPrevCaptured,
            currentSwitchValue = masterSwitch.read(),
            nowMs = clock(),
        )
        val suppress = decision as? BacklightSuppressionDecision.Suppress ?: return false
        // Step 2: commit the record first. Step 3: write; a failed write clears it again.
        store.updateCaptures { it.copy(ringBacklightPrevCaptured = true, ringBacklightPrev = suppress.record.priorSwitchValue) }
        if (!masterSwitch.write(suppress.write.value)) {
            clearRecord()
            return false
        }
        // Step 4: the orphan timer.
        ringOwnsRestore = false
        cancelOrphanTimer?.invoke()
        cancelOrphanTimer = timer.schedule(KeyboardBacklight.SUPPRESSION_ORPHAN_TIMEOUT_MS) { onOrphanTimer() }
        return true
    }

    /** spec: SS5.7 step 1 ("takes ownership of the keyboard restore, cancelling the orphan timer"). */
    @Synchronized
    fun takeOwnership() {
        ringOwnsRestore = true
        cancelOrphanTimer?.invoke()
        cancelOrphanTimer = null
    }

    /** spec: SS5.8 "Restore": any thread, any time, a no-op when no record exists. */
    @Synchronized
    fun restore() {
        cancelOrphanTimer?.invoke()
        cancelOrphanTimer = null
        ringOwnsRestore = false
        val captures = store.snapshot().captures
        val record = if (captures.ringBacklightPrevCaptured) {
            BacklightSuppressionRecord(priorSwitchValue = captures.ringBacklightPrev ?: KeyboardBacklight.MASTER_SWITCH_UNSET_VALUE, capturedAtMs = 0L)
        } else {
            null
        }
        when (val decision = KeyboardBacklight.decideRestore(record, permissions.hasWriteSecureSettings())) {
            BacklightRestoreDecision.NoRecord -> Unit
            BacklightRestoreDecision.KeepRecordPermissionMissing -> Unit
            is BacklightRestoreDecision.Restore -> if (masterSwitch.write(decision.write.value)) clearRecord()
        }
    }

    /** True while a record is outstanding. spec: SS5.8 ("a suppression is already outstanding"). */
    fun isSuppressed(): Boolean = store.snapshot().captures.ringBacklightPrevCaptured

    private fun onOrphanTimer() {
        if (ringOwnsRestore) return
        restore()
    }

    private fun clearRecord() {
        store.updateCaptures { it.copy(ringBacklightPrevCaptured = false, ringBacklightPrev = null) }
    }
}
