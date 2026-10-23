package brobata.physiboard.app.settings.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.device.titan.AndroidScreenSaver
import brobata.physiboard.device.titan.ScreenSaverConflict

/**
 * Whether Android's screen saver runs while charging, re-read every time the screen resumes:
 * the user is sent to Android's own Display settings to change it and comes straight back.
 * spec: device-backlight-ring.md SS2.3.
 */
@Composable
fun rememberScreenSaverRunsWhileCharging(): Boolean {
    val context = LocalContext.current
    var runs by remember { mutableStateOf(readScreenSaverRunsWhileCharging(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) runs = readScreenSaverRunsWhileCharging(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return runs
}

/**
 * The warning for one broken promise, in the error colour, with the one button that fixes it:
 * Android's screen saver page. spec: device-backlight-ring.md SS2.3, SS3.4, SS5.11.5.
 */
@Composable
fun ScreenSaverConflictNotice(conflict: ScreenSaverConflict) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("! ${conflictMessage(conflict)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { openScreenSaverSettings(context) }) { Text("Open screen saver settings") }
    }
}

private fun conflictMessage(conflict: ScreenSaverConflict): String = when (conflict) {
    ScreenSaverConflict.KEYBOARD_LIGHT ->
        "Android's screen saver is set to run while charging. On this phone the screen saver turns the keyboard light off the moment it appears, " +
            "and nothing turns it back on when a touch ends the screen saver, so the keyboard sits dark with the screen on until you press a key. " +
            "Turn the screen saver off, or set it to never, under Display > Screen saver."
    ScreenSaverConflict.CHARGING_CLOCK ->
        "Android's screen saver is set to run while charging, so it comes up instead of this clock: on the charger the screen never goes off. " +
            "Turn the screen saver off under Display > Screen saver."
}

/** Both rows are readable without a permission on Android 16; a refusal is logged, never hidden, and reads as "not running". */
private fun readScreenSaverRunsWhileCharging(context: Context): Boolean = runCatching {
    val resolver = context.contentResolver
    AndroidScreenSaver.runsWhileCharging(
        enabled = AndroidSettings.Secure.getInt(resolver, AndroidScreenSaver.ENABLED_KEY, 0) != 0,
        activateOnSleep = AndroidSettings.Secure.getInt(resolver, AndroidScreenSaver.ACTIVATE_ON_SLEEP_KEY, 0) != 0,
    )
}.onFailure { Log.e(TAG, "cannot read the screen saver rows", it) }.getOrDefault(false)

private const val TAG = "ScreenSaverNotice"

private fun openScreenSaverSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_DREAM_SETTINGS)
    val resolves = intent.resolveActivity(context.packageManager) != null
    runCatching { context.startActivity(if (resolves) intent else Intent(AndroidSettings.ACTION_DISPLAY_SETTINGS)) }
}
