package brobata.physiboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import brobata.physiboard.ring.NotificationRingSetup
import brobata.physiboard.inputmethod.KeyboardBacklightManager

/**
 * One-button "Reset device settings to stock" safety net.
 *
 * PhysiBoard writes four things that live at the OS / vendor level and SURVIVE an uninstall.
 * Android gives an app no uninstall hook, so we cannot auto-revert — the supported path is the
 * user tapping this before uninstalling. [resetToStock] undoes ALL FOUR:
 *
 *   1. Fn -> Ctrl remap   — Settings.System `fn_programmable_key_enable` / `_function`.
 *   2. Backlight always-on — vendor `keyboard_brightness_timeout` (restored to stock 30000).
 *   3. QS tile backlight   — Settings.Global `agui_keyboard_background_light`.
 *   4. Orange side key     — Settings.System `func1_long_press_package` / `_activity`.
 *
 * Each revert runs INDEPENDENTLY (one failing never skips the others) and clears its own capture
 * log only on success. Every step is wrapped in runCatching, so this NEVER throws. It is BLOCKING
 * (the backlight/Fn broker paths do network IO) — callers MUST run it OFF the main thread.
 */
object SystemChangeManager {

    // --- Fn -> Ctrl (Settings.System) ------------------------------------------------------
    private const val FN_KEY_ENABLE = "fn_programmable_key_enable"
    private const val FN_KEY_FUNCTION = "fn_programmable_key_function"

    // --- QS tile backlight (Settings.Global) -----------------------------------------------
    // Same key as KeyboardBacklightTileService.VENDOR_BACKLIGHT_SETTING (physi flavor only).
    private const val QS_BACKLIGHT_KEY = "agui_keyboard_background_light"

    /** Outcome of a single revert step. */
    enum class StepOutcome { SUCCESS, NEEDS_PERMISSION, FAILED }

    /**
     * Per-item results of a reset. [allSucceeded] is true only when every step succeeded;
     * [needsPermission] is true when at least one step could not write because neither the
     * Settings API nor a paired wireless-debugging broker was available.
     */
    data class ResetResult(
        val fnCtrl: StepOutcome,
        val backlight: StepOutcome,
        val qsBacklight: StepOutcome,
        val sideKey: StepOutcome,
        val notificationRing: StepOutcome
    ) {
        private val steps get() = listOf(fnCtrl, backlight, qsBacklight, sideKey, notificationRing)

        val allSucceeded: Boolean
            get() = steps.all { it == StepOutcome.SUCCESS }

        val needsPermission: Boolean
            get() = steps.any { it == StepOutcome.NEEDS_PERMISSION }
    }

    /**
     * Revert every system-wide change PhysiBoard can make. BLOCKING — call OFF the main thread.
     * Never throws. Runs the four reverts independently and reports each outcome.
     */
    fun resetToStock(context: Context): ResetResult {
        val fn = runCatching { revertFnCtrl(context) }.getOrDefault(StepOutcome.FAILED)
        val backlight = runCatching { revertBacklight(context) }.getOrDefault(StepOutcome.FAILED)
        val qs = runCatching { revertQsBacklight(context) }.getOrDefault(StepOutcome.FAILED)
        val sideKey = runCatching { revertSideKey(context) }.getOrDefault(StepOutcome.FAILED)
        val ring = runCatching { revertNotificationRing(context) }.getOrDefault(StepOutcome.FAILED)
        return ResetResult(
            fnCtrl = fn, backlight = backlight, qsBacklight = qs, sideKey = sideKey, notificationRing = ring
        )
    }

    // ---------------------------------------------------------------------------------------
    // 5. Notification ring. Switch the feature off and hand back notification access and the
    //    full-screen permission; both were granted through the broker, so the broker is what
    //    takes them back. Never granted -> nothing to undo.
    // ---------------------------------------------------------------------------------------
    private fun revertNotificationRing(context: Context): StepOutcome {
        runCatching { SettingsManager.setNotificationRingEnabled(context, false) }
        val granted = NotificationRingSetup.isListenerGranted(context) ||
            NotificationRingSetup.canUseFullScreenIntent(context)
        if (!granted) return StepOutcome.SUCCESS
        if (!EmbeddedAdbShell.isPaired(context)) return StepOutcome.NEEDS_PERMISSION
        return if (NotificationRingSetup.revokeViaBroker(context)) StepOutcome.SUCCESS else StepOutcome.FAILED
    }

    // ---------------------------------------------------------------------------------------
    // 1. Fn -> Ctrl. Restore the captured original programmable-key values (or write 0/0 when
    //    the original was unset / never captured). Reuses SettingsManager's public capture
    //    getters — behavior mirrors NavModeSettingsScreen.revertFnCtrl (which is private).
    // ---------------------------------------------------------------------------------------
    private fun revertFnCtrl(context: Context): StepOutcome {
        val captured = SettingsManager.isFnCtrlOriginalCaptured(context)
        val prevEnable = SettingsManager.getFnCtrlOriginalEnable(context)
        val prevFunction = SettingsManager.getFnCtrlOriginalFunction(context)
        val targetEnable =
            if (captured && prevEnable != SettingsManager.FN_CTRL_VALUE_UNSET) prevEnable else 0
        val targetFunction =
            if (captured && prevFunction != SettingsManager.FN_CTRL_VALUE_UNSET) prevFunction else 0

        if (Settings.System.canWrite(context)) {
            val ok = runCatching {
                Settings.System.putInt(context.contentResolver, FN_KEY_ENABLE, targetEnable)
                Settings.System.putInt(context.contentResolver, FN_KEY_FUNCTION, targetFunction)
            }.isSuccess
            if (ok) SettingsManager.clearFnCtrlOriginal(context)
            return if (ok) StepOutcome.SUCCESS else StepOutcome.FAILED
        }
        if (EmbeddedAdbShell.isPaired(context)) {
            val enabled = runCatching {
                EmbeddedAdbShell.runShell(context, "settings put system $FN_KEY_ENABLE $targetEnable")
            }.getOrDefault(false)
            val functioned = runCatching {
                EmbeddedAdbShell.runShell(context, "settings put system $FN_KEY_FUNCTION $targetFunction")
            }.getOrDefault(false)
            if (enabled && functioned) SettingsManager.clearFnCtrlOriginal(context)
            return if (enabled && functioned) StepOutcome.SUCCESS else StepOutcome.FAILED
        }
        return StepOutcome.NEEDS_PERMISSION
    }

    // ---------------------------------------------------------------------------------------
    // 2. Backlight always-on. Restore the stock vendor timeout (30000) via the broker and clear
    //    the app-side smart-backlight flags so nothing re-arms it. Stock is a known constant, so
    //    there is no capture to restore. The vendor write needs the paired broker.
    // ---------------------------------------------------------------------------------------
    private fun revertBacklight(context: Context): StepOutcome {
        val wasActive = SettingsManager.getSmartBacklightEnabled(context) ||
            SettingsManager.getSmartBacklightApplied(context)

        // Always stop the app from re-applying always-on, regardless of whether the vendor
        // write below can land right now.
        runCatching { KeyboardBacklightManager.revertToDefault(context) }
        runCatching { SettingsManager.setSmartBacklightEnabled(context, false) }
        runCatching { SettingsManager.setSmartBacklightApplied(context, false) }

        // If we never armed it, there is nothing at the vendor level to undo.
        if (!wasActive) return StepOutcome.SUCCESS
        // The stock-30000 write travels through the broker; without a paired broker it can't land.
        return if (EmbeddedAdbShell.isPaired(context)) StepOutcome.SUCCESS else StepOutcome.NEEDS_PERMISSION
    }

    // ---------------------------------------------------------------------------------------
    // 3. QS tile backlight (Settings.Global). Restore the captured original value, or 0 when it
    //    was never captured. Writing a Global needs WRITE_SECURE_SETTINGS (held directly, or via
    //    the paired broker). Resilient when the value was never set (nothing captured -> 0).
    // ---------------------------------------------------------------------------------------
    private fun revertQsBacklight(context: Context): StepOutcome {
        val captured = SettingsManager.isQsBacklightOriginalCaptured(context)
        val prev = SettingsManager.getQsBacklightOriginal(context)
        val target = if (captured && prev != SettingsManager.QS_BACKLIGHT_VALUE_UNSET) prev else 0

        if (hasWriteSecureSettings(context)) {
            val ok = runCatching {
                Settings.Global.putInt(context.contentResolver, QS_BACKLIGHT_KEY, target)
            }.isSuccess
            if (ok) SettingsManager.clearQsBacklightOriginal(context)
            return if (ok) StepOutcome.SUCCESS else StepOutcome.FAILED
        }
        if (EmbeddedAdbShell.isPaired(context)) {
            val ok = runCatching {
                EmbeddedAdbShell.runShell(context, "settings put global $QS_BACKLIGHT_KEY $target")
            }.getOrDefault(false)
            if (ok) SettingsManager.clearQsBacklightOriginal(context)
            return if (ok) StepOutcome.SUCCESS else StepOutcome.FAILED
        }
        return StepOutcome.NEEDS_PERMISSION
    }

    // ---------------------------------------------------------------------------------------
    // 4. Orange side key. Hand the vendor's long-press slot back to whatever it pointed at
    //    before, and stop the app from re-binding it. Nothing captured means we never took it.
    // ---------------------------------------------------------------------------------------
    private fun revertSideKey(context: Context): StepOutcome {
        runCatching { SettingsManager.setSideKeyAssistantEnabled(context, false) }
        return when (VendorSideKeyManager.restoreLongPress(context)) {
            VendorSideKeyManager.Outcome.SUCCESS -> StepOutcome.SUCCESS
            VendorSideKeyManager.Outcome.NEEDS_PERMISSION -> StepOutcome.NEEDS_PERMISSION
            VendorSideKeyManager.Outcome.FAILED -> StepOutcome.FAILED
        }
    }

    private fun hasWriteSecureSettings(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}
