package brobata.physiboard.commands

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Toast
import brobata.physiboard.MainActivity
import brobata.physiboard.R
import brobata.physiboard.SettingsManager
import brobata.physiboard.SoftwareKeyboardModeActions
import brobata.physiboard.core.NavModeController
import brobata.physiboard.inputmethod.AssistantLauncher
import brobata.physiboard.inputmethod.QuickLauncherActivity
import rikka.shizuku.Shizuku

class CommandExecutor(
    private val context: Context,
    private val navModeController: NavModeController? = null,
    private val inputConnectionProvider: (() -> InputConnection?)? = null,
    private val showToast: Boolean = true
) {
    fun execute(command: CommandTarget): CommandExecutionResult {
        return execute(command.launch)
    }

    fun execute(launch: CommandLaunchSpec): CommandExecutionResult {
        return when (launch) {
            is CommandLaunchSpec.AppPackage -> launchPackage(launch.packageName)
            is CommandLaunchSpec.IntentUri -> startIntent(launch)
            is CommandLaunchSpec.InternalAction -> executeInternalAction(launch.actionId)
            is CommandLaunchSpec.NavAction -> executeNavAction(launch)
        }
    }

    private fun launchPackage(packageName: String): CommandExecutionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return fail("Package not available")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandExecutionResult.Success
        } catch (error: Exception) {
            Log.e(TAG, "Failed to launch package $packageName", error)
            fail("Could not open app")
        }
    }

    private fun startIntent(spec: CommandLaunchSpec.IntentUri): CommandExecutionResult {
        return try {
            val intent = Intent(spec.action, spec.data?.let(Uri::parse)).apply {
                spec.packageName?.let(::setPackage)
                spec.componentName?.let { component ->
                    ComponentName.unflattenFromString(component)?.let(::setComponent)
                }
                spec.categories.forEach(::addCategory)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (spec.flags.contains("clear_top")) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                spec.flags
                    .mapNotNull { flag -> flag.split("=", limit = 2).takeIf { it.size == 2 } }
                    .forEach { (key, value) -> putExtra(key, value) }
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return fail("Command not available")
            }
            context.startActivity(intent)
            CommandExecutionResult.Success
        } catch (error: SecurityException) {
            Log.e(TAG, "Security error starting command intent", error)
            fail("Command blocked")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start command intent", error)
            fail("Command failed")
        }
    }

    private fun executeInternalAction(actionId: String): CommandExecutionResult {
        return when (actionId) {
            PhysiBoardCommandSource.ACTION_OPEN_QUICK_LAUNCHER -> {
                try {
                    val intent = QuickLauncherActivity.createOpenIntent(context)
                    context.startActivity(intent)
                    CommandExecutionResult.Success
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to open QuickLauncher", error)
                    fail("Could not open QuickLauncher")
                }
            }
            PhysiBoardCommandSource.ACTION_START_VOICE_ASSISTANT -> {
                if (AssistantLauncher.launch(context)) {
                    CommandExecutionResult.Success
                } else {
                    fail(context.getString(R.string.assistant_unavailable))
                }
            }
            PhysiBoardCommandSource.ACTION_OPEN_MAIN_ACTIVITY -> {
                try {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CommandExecutionResult.Success
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to open PhysiBoard", error)
                    fail("Could not open PhysiBoard")
                }
            }
            PhysiBoardCommandSource.ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE -> toggleSoftwareKeyboardMode()
            DeviceControlCommandSource.ACTION_HOME_SCREEN -> goHome()
            DeviceControlCommandSource.ACTION_MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            DeviceControlCommandSource.ACTION_MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            DeviceControlCommandSource.ACTION_MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            DeviceControlCommandSource.ACTION_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            DeviceControlCommandSource.ACTION_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            DeviceControlCommandSource.ACTION_VOLUME_MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            DeviceControlCommandSource.ACTION_BRIGHTNESS_UP -> sendShellKeyEvent(KeyEvent.KEYCODE_BRIGHTNESS_UP)
            DeviceControlCommandSource.ACTION_BRIGHTNESS_DOWN -> sendShellKeyEvent(KeyEvent.KEYCODE_BRIGHTNESS_DOWN)
            DeviceControlCommandSource.ACTION_EXPAND_NOTIFICATIONS -> expandShade(quickSettings = false)
            DeviceControlCommandSource.ACTION_EXPAND_QUICK_SETTINGS -> expandShade(quickSettings = true)
            else -> fail("Unknown action")
        }
    }

    private fun goHome(): CommandExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandExecutionResult.Success
        } catch (error: Exception) {
            Log.e(TAG, "Failed to go home", error)
            fail("Could not go home")
        }
    }

    private fun toggleSoftwareKeyboardMode(): CommandExecutionResult {
        val next = SoftwareKeyboardModeActions.toggleTemporaryMode(context)
        if (showToast && SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context)) {
            val message = when (next) {
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL ->
                    context.getString(R.string.software_keyboard_mode_toggle_now_virtual)
                SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE ->
                    context.getString(R.string.software_keyboard_mode_toggle_now_hardware)
                SettingsManager.SoftwareKeyboardMode.AUTO ->
                    context.getString(R.string.software_keyboard_mode_auto_short)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        return CommandExecutionResult.Success
    }

    private fun dispatchMediaKey(keyCode: Int): CommandExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return fail("Audio unavailable")
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return CommandExecutionResult.Success
    }

    private fun adjustVolume(direction: Int): CommandExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return fail("Audio unavailable")
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return CommandExecutionResult.Success
    }

    /**
     * Pull the shade down from a key. StatusBarManager's expand calls are hidden but reachable
     * with the EXPAND_STATUS_BAR permission, which is a normal one; when the platform refuses
     * the reflection, the paired broker's `cmd statusbar` does the same thing a beat later.
     */
    private fun expandShade(quickSettings: Boolean): CommandExecutionResult {
        val method = if (quickSettings) "expandSettingsPanel" else "expandNotificationsPanel"
        val direct = runCatching {
            val manager = context.getSystemService("statusbar") ?: return@runCatching false
            manager.javaClass.getMethod(method).invoke(manager)
            true
        }.getOrDefault(false)
        if (direct) return CommandExecutionResult.Success
        if (!EmbeddedAdbShell.isPaired(context)) return fail("Shade unavailable")
        val verb = if (quickSettings) "expand-settings" else "expand-notifications"
        shadeExecutor.execute {
            runCatching { EmbeddedAdbShell.runShell(context, "cmd statusbar $verb") }
        }
        return CommandExecutionResult.Success
    }

    private fun sendShellKeyEvent(keyCode: Int): CommandExecutionResult {
        return try {
            val shizukuAvailable = Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!shizukuAvailable) {
                return fail("Shizuku required")
            }
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("input", "keyevent", keyCode.toString()),
                null,
                null
            ) as Process
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                CommandExecutionResult.Success
            } else {
                fail("Command failed")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to send shell keyevent $keyCode", error)
            fail("Command failed")
        }
    }

    private fun executeNavAction(launch: CommandLaunchSpec.NavAction): CommandExecutionResult {
        val controller = navModeController ?: return fail("Nav mode unavailable")
        val inputConnection = inputConnectionProvider?.invoke() ?: return fail("No input context")
        return if (controller.executeMapping(launch.mappingType, launch.value, null, inputConnection)) {
            CommandExecutionResult.Success
        } else {
            fail("Nav action failed")
        }
    }

    private fun fail(reason: String): CommandExecutionResult.Failed {
        if (showToast) {
            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        }
        return CommandExecutionResult.Failed(reason)
    }

    companion object {
        private val shadeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        private const val TAG = "CommandExecutor"
    }
}
