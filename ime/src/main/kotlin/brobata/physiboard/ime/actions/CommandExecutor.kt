package brobata.physiboard.ime.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.CommandFailure
import brobata.physiboard.core.actions.commands.InternalActions
import brobata.physiboard.core.actions.commands.LaunchSpec
import brobata.physiboard.core.actions.launcher.ShortcutRun
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.ime.access.AccessibilityBridge

/**
 * Runs a command against the device. spec: expansion-clipboard-pickers-launcher.md SS8.1 (the
 * four launch specs), SS8.2 (what each device control does), SS8.4 (every failure shows a short
 * toast with the reason unless the caller asks for silence). Which command to run and how it was
 * resolved is `:core:actions`' ([ShortcutRun]); this class only performs the Android calls.
 *
 * Keep/Drop decisions taken here (SS13): brightness goes through the embedded broker instead of
 * Shizuku ("route brightness through it instead of Shizuku"), the shade uses the broker as its
 * fallback, and the software-keyboard toggle is gone with the soft keyboard.
 */
internal class CommandExecutor(
    private val service: InputMethodService,
    private val openQuickLauncher: () -> Boolean,
    private val startVoiceAssistant: () -> Boolean,
    private val runNavAction: (mappingType: String, value: String) -> Boolean,
    /** app-shell.md SS31.3: flips `private_mode`; the keyboard shows its own toast. */
    private val togglePrivateMode: () -> Boolean,
    /** layers-sym-alt.md SS5.10: false when there is no text field to open a page for. */
    private val openSymPageChooser: () -> Boolean = { false },
    /** dictation.md SS2.2: the catalog's "Dictation" command, the same action as the Fn burst. */
    private val toggleDictation: () -> Boolean = { false },
) {

    fun run(resolved: ShortcutRun, silent: Boolean = false): Boolean = when (resolved) {
        ShortcutRun.OpenQuickLauncher -> openQuickLauncher().also { if (!it) fail(CommandFailure.COULD_NOT_OPEN_QUICK_LAUNCHER, silent) }
        is ShortcutRun.RunCommand -> run(resolved.command, silent)
        is ShortcutRun.RunLaunchSpec -> {
            val ok = runSpec(resolved.launch, silent)
            // spec SS6.3: "an `app` type entry whose command fails falls back to a plain launch of `packageName`"
            val fallback = resolved.fallbackPackage
            if (!ok && fallback != null) launchPackage(fallback, silent) else ok
        }
        ShortcutRun.Nothing -> false
    }

    fun run(command: Command, silent: Boolean = false): Boolean = runSpec(command.launch, silent)

    fun runSpec(spec: LaunchSpec, silent: Boolean = false): Boolean = runCatching {
        when (spec) {
            is LaunchSpec.AppPackage -> launchPackage(spec.packageName, silent)
            is LaunchSpec.IntentUri -> startIntent(spec, silent)
            is LaunchSpec.InternalAction -> internal(spec.actionId, silent)
            is LaunchSpec.NavAction -> {
                if (service.currentInputConnection == null) return fail(CommandFailure.NO_INPUT_CONTEXT, silent)
                runNavAction(spec.mappingType, spec.value) || fail(CommandFailure.NAV_ACTION_FAILED, silent)
            }
        }
    }.getOrElse { error ->
        Log.e(TAG, "command crashed", error)
        fail(CommandFailure.COMMAND_FAILED, silent)
    }

    /** spec SS8.1: "launch a package (its launcher intent, new task)". */
    private fun launchPackage(packageName: String, silent: Boolean): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return fail(CommandFailure.PACKAGE_NOT_AVAILABLE, silent)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { service.startActivity(intent); true }.getOrElse { fail(CommandFailure.COULD_NOT_OPEN_APP, silent) }
    }

    /** spec SS8.1: "an unresolvable intent fails with 'Command not available', a security refusal with 'Command blocked'". */
    private fun startIntent(spec: LaunchSpec.IntentUri, silent: Boolean): Boolean {
        val intent = spec.toIntent()
        @Suppress("DEPRECATION")
        if (service.packageManager.resolveActivity(intent, 0) == null) return fail(CommandFailure.COMMAND_NOT_AVAILABLE, silent)
        return try {
            service.startActivity(intent)
            true
        } catch (_: SecurityException) {
            fail(CommandFailure.COMMAND_BLOCKED, silent)
        } catch (_: ActivityNotFoundException) {
            fail(CommandFailure.COMMAND_NOT_AVAILABLE, silent)
        }
    }

    private fun internal(actionId: String, silent: Boolean): Boolean = when (actionId) {
        InternalActions.OPEN_QUICK_LAUNCHER -> openQuickLauncher() || fail(CommandFailure.COULD_NOT_OPEN_QUICK_LAUNCHER, silent)
        InternalActions.OPEN_MAIN_ACTIVITY -> openOwnApp() || fail(CommandFailure.COULD_NOT_OPEN_PHYSIBOARD, silent)
        InternalActions.START_VOICE_ASSISTANT -> startVoiceAssistant() || fail(CommandFailure.NO_VOICE_ASSISTANT, silent)
        InternalActions.TOGGLE_SOFTWARE_KEYBOARD_MODE -> fail(CommandFailure.COMMAND_NOT_AVAILABLE, silent)
        InternalActions.TOGGLE_PRIVATE_MODE -> togglePrivateMode() || fail(CommandFailure.COMMAND_FAILED, silent)
        InternalActions.TOGGLE_DICTATION -> toggleDictation() || fail(CommandFailure.NO_INPUT_CONTEXT, silent)
        InternalActions.OPEN_SYM_PAGE_CHOOSER -> openSymPageChooser() || fail(CommandFailure.NO_INPUT_CONTEXT, silent)
        // per-app-behavior.md SS16: with the accessibility service on, Home is the Home key's own action.
        InternalActions.OPEN_HOME -> AccessibilityBridge.goHome() || startIntent(LaunchSpec.IntentUri(Intent.ACTION_MAIN, categories = listOf(Intent.CATEGORY_HOME)), silent)
        InternalActions.MEDIA_PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, silent)
        InternalActions.MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, silent)
        InternalActions.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, silent)
        InternalActions.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE, silent)
        InternalActions.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER, silent)
        InternalActions.VOLUME_MUTE -> volume(AudioManager.ADJUST_TOGGLE_MUTE, silent)
        InternalActions.BRIGHTNESS_UP -> shell("input keyevent ${KeyEvent.KEYCODE_BRIGHTNESS_UP}", CommandFailure.COMMAND_NOT_AVAILABLE, silent)
        InternalActions.BRIGHTNESS_DOWN -> shell("input keyevent ${KeyEvent.KEYCODE_BRIGHTNESS_DOWN}", CommandFailure.COMMAND_NOT_AVAILABLE, silent)
        InternalActions.SHADE_NOTIFICATIONS -> shade("expandNotificationsPanel", "cmd statusbar expand-notifications", silent)
        InternalActions.SHADE_QUICK_SETTINGS -> shade("expandSettingsPanel", "cmd statusbar expand-settings", silent)
        else -> fail(CommandFailure.UNKNOWN_ACTION, silent)
    }

    private fun openOwnApp(): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { service.startActivity(intent); true }.getOrDefault(false)
    }

    /** spec SS8.2: "Dispatches the media key down and up through the audio service"; reported as success even with no player listening (SS11). */
    private fun mediaKey(keyCode: Int, silent: Boolean): Boolean {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return fail(CommandFailure.AUDIO_UNAVAILABLE, silent)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return true
    }

    /** spec SS8.2: "Adjusts the music stream, showing the system volume panel". */
    private fun volume(direction: Int, silent: Boolean): Boolean {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return fail(CommandFailure.AUDIO_UNAVAILABLE, silent)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return true
    }

    /** spec SS8.2: the hidden status-bar expand method first; refused, the embedded broker runs the `cmd statusbar` line; otherwise "Shade unavailable". */
    private fun shade(method: String, shellLine: String, silent: Boolean): Boolean {
        val viaHiddenApi = runCatching {
            val manager = service.getSystemService("statusbar") ?: return@runCatching false
            manager.javaClass.getMethod(method).invoke(manager)
            true
        }.getOrDefault(false)
        if (viaHiddenApi) return true
        return shell(shellLine, CommandFailure.SHADE_UNAVAILABLE, silent)
    }

    /** A broker shell line on the privileged worker; success only means the line was sent (SS8.2, brightness). */
    private fun shell(line: String, failure: String, silent: Boolean): Boolean {
        val services = PrivilegedServices.from(service) ?: return fail(failure, silent)
        val broker = services.broker
        if (broker.blocker() != null) return fail(failure, silent)
        services.worker.execute {
            val result = runCatching { broker.run(line) }.getOrElse { ShellResult.Failed(it.message.orEmpty()) }
            if (!result.isOk) service.mainExecutor.execute { fail(failure, silent) }
        }
        return true
    }

    private fun fail(reason: String, silent: Boolean): Boolean {
        if (!silent) runCatching { Toast.makeText(service, reason, Toast.LENGTH_SHORT).show() }
        return false
    }

    private companion object {
        const val TAG = "PhysiBoardCommands"
    }
}
