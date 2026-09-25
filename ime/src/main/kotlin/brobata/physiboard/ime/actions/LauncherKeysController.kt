package brobata.physiboard.ime.actions

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import brobata.physiboard.core.actions.launcher.AssignmentSheet
import brobata.physiboard.core.actions.launcher.LauncherKeyDecision
import brobata.physiboard.core.actions.launcher.PowerShortcutMode
import brobata.physiboard.core.actions.launcher.ShortcutRun

/**
 * The Android side of the assigned launcher keys. spec: expansion-clipboard-pickers-launcher.md
 * SS6.2 (the 500 ms toast and the 5000 ms disarm of the Sym-armed mode), SS6.3 (what runs), SS6.4
 * (the assignment sheet, opened by intent). The decisions (which key fires, which path, how an
 * entry resolves) are `KeyboardPipeline`'s and `:core:actions`'; this class owns the two timers,
 * the toast and the intents.
 *
 * The sheet itself is a settings-app screen (it writes the store, which only `:app` can), reached
 * through [AssignmentSheet.ACTION_ASSIGN_KEY] with the spec's `key_code` and `skip_launch` extras so the keyboard
 * never names an `:app` class.
 */
internal class LauncherKeysController(
    private val service: InputMethodService,
    private val handler: Handler,
    private val catalog: AndroidCommandCatalog,
    private val executor: CommandExecutor,
    private val quickLauncher: QuickLauncherController,
    private val onPowerModeTimeout: (nowMs: Long) -> Unit,
) {
    private var lastToastMs: Long? = null
    private val timeoutRunnable = Runnable { runCatching { onPowerModeTimeout(SystemClock.uptimeMillis()) }.onFailure { Log.e(TAG, "power mode timeout crashed", it) } }

    /** spec SS6.2 B: the mode just armed at [armedAtMs]; the toast shows 500 ms later if still armed, the mode disarms at 5000 ms. */
    fun onPowerModeArmed(armedAtMs: Long, stillArmed: (Long) -> Boolean) {
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, PowerShortcutMode.DISARM_AFTER_MS)
        handler.postDelayed({ if (stillArmed(armedAtMs)) toast(PowerShortcutMode.TOAST_TEXT) }, PowerShortcutMode.TOAST_DELAY_MS)
    }

    fun onPowerModeDisarmed() = handler.removeCallbacks(timeoutRunnable)

    /** spec SS6.2 A/B/C and SS6.3: run the assignment, or open the sheet from a key press (which also launches the choice). */
    fun perform(decision: LauncherKeyDecision) {
        when (decision) {
            is LauncherKeyDecision.Run -> {
                val resolved = ShortcutRun.resolve(decision.entry) { id -> catalog.build().find(id) }
                if (resolved == ShortcutRun.OpenQuickLauncher) quickLauncher.toggle() else executor.run(resolved)
            }
            is LauncherKeyDecision.OpenAssignmentSheet -> openAssignmentSheet(decision.keycode, skipLaunch = false)
            LauncherKeyDecision.FallThrough -> Unit
        }
    }

    fun openAssignmentSheet(keycode: Int, skipLaunch: Boolean) {
        val intent = Intent(AssignmentSheet.ACTION_ASSIGN_KEY).apply {
            setPackage(service.packageName)
            putExtra(AssignmentSheet.EXTRA_KEY_CODE, keycode)
            putExtra(AssignmentSheet.EXTRA_SKIP_LAUNCH, skipLaunch)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        runCatching { service.startActivity(intent) }.onFailure { Log.e(TAG, "assignment sheet not available", it) }
    }

    fun onServiceDestroyed() = handler.removeCallbacks(timeoutRunnable)

    /** spec SS6.2 B: "Toasts of the same text within 1000 ms are collapsed into one." */
    private fun toast(text: String) {
        val now = SystemClock.uptimeMillis()
        val last = lastToastMs
        if (last != null && now - last < PowerShortcutMode.TOAST_COLLAPSE_MS) return
        lastToastMs = now
        runCatching { Toast.makeText(service, text, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val TAG = "PhysiBoardLauncherKeys"
    }
}
