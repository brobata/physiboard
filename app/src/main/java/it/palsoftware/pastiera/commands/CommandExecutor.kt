package it.palsoftware.pastiera.commands

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Toast
import it.palsoftware.pastiera.MainActivity
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.inputmethod.QuickLauncherActivity
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
            PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER -> {
                try {
                    val intent = Intent(context, QuickLauncherActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                    CommandExecutionResult.Success
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to open QuickLauncher", error)
                    fail("Could not open QuickLauncher")
                }
            }
            PastieraCommandSource.ACTION_OPEN_MAIN_ACTIVITY -> {
                try {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CommandExecutionResult.Success
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to open Pastiera", error)
                    fail("Could not open Pastiera")
                }
            }
            DeviceControlCommandSource.ACTION_MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            DeviceControlCommandSource.ACTION_MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            DeviceControlCommandSource.ACTION_MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            DeviceControlCommandSource.ACTION_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            DeviceControlCommandSource.ACTION_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            DeviceControlCommandSource.ACTION_VOLUME_MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            DeviceControlCommandSource.ACTION_BRIGHTNESS_UP -> sendShellKeyEvent(KeyEvent.KEYCODE_BRIGHTNESS_UP)
            DeviceControlCommandSource.ACTION_BRIGHTNESS_DOWN -> sendShellKeyEvent(KeyEvent.KEYCODE_BRIGHTNESS_DOWN)
            else -> fail("Unknown action")
        }
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
        private const val TAG = "CommandExecutor"
    }
}
