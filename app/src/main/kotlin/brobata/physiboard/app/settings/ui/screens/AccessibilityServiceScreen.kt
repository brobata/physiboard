package brobata.physiboard.app.settings.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.AboutExpander
import brobata.physiboard.app.settings.ui.ButtonRow
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.app.settings.ui.WatchBrokerVerdict
import brobata.physiboard.app.settings.ui.brokerBlockerState
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.setup.AccessibilityTurnOnOutcome
import brobata.physiboard.device.privileged.setup.AndroidPermissionProbe
import brobata.physiboard.ime.access.PhysiBoardAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Accessibility service" (Keys & shortcuts; per-app-behavior.md SS16, settings-catalog.md): the
 * optional service's state, the way to turn it on, and its two switches. Both switches default
 * on and do nothing until the service is on, which only the user can do, in Android's settings
 * (or with one tap through the paired broker, offered, never done silently).
 */
@Composable
fun AccessibilityServiceScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val keys = controller.current.value.keys
    val context = LocalContext.current
    val privileged = (context.applicationContext as PhysiBoardApplication).privileged
    val scope = rememberCoroutineScope()

    // Re-read on every return: the choice is made in Android's own settings.
    var serviceOn by remember { mutableStateOf(AndroidPermissionProbe.accessibilityServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceOn = AndroidPermissionProbe.accessibilityServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    WatchBrokerVerdict(privileged)
    val blocker = brokerBlockerState(privileged)
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    SettingsScreenScaffold(title = "Accessibility service", onBack = onBack) {
        RowList {
            plainItem {
                InfoText(
                    "Android sends a keyboard keys only while a text box is open. This optional service lets PhysiBoard's " +
                        "shortcuts work everywhere else too, and selects the box you are typing into when an app forgot to.",
                )
            }
            header("")
            item {
                NavigateRow(
                    "PhysiBoard accessibility service",
                    description = if (serviceOn) {
                        "On. Tap to see it in Android's settings."
                    } else {
                        "Off. Turn it on in Android's settings. If Android says the setting is restricted, open App info, " +
                            "tap ⋮ and choose Allow restricted settings, then try again."
                    },
                    icon = Icons.Outlined.AccessibilityNew,
                    value = if (serviceOn) "On" else "Off",
                ) { openAccessibilitySettings(context) }
            }
            if (!serviceOn) {
                item {
                    ButtonRow(
                        label = "App info",
                        description = "Where Allow restricted settings is, for an app installed from outside a store.",
                        buttonText = "Open",
                        onClick = { openAppInfo(context) },
                    )
                }
                if (blocker != BrokerBlocker.NOT_PAIRED) {
                    item {
                        ButtonRow(
                            label = "Turn on with pairing",
                            description = message ?: "Uses the wireless-debugging pairing to turn the service on for you. Other apps' services are left as they are.",
                            buttonText = if (working) "Working…" else "Turn on",
                            enabled = !working,
                            onClick = {
                                working = true
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) { runCatching { privileged.accessibilitySwitch.turnOn() }.getOrDefault(AccessibilityTurnOnOutcome.FAILED) }
                                    message = turnOnMessage(outcome)
                                    serviceOn = AndroidPermissionProbe.accessibilityServiceEnabled(context)
                                    working = false
                                }
                            },
                        )
                    }
                }
            }
            header("What it does")
            item {
                SwitchRow(
                    "Fn shortcuts everywhere",
                    description = "Sym shortcuts, the quick launcher and the Fn layer also work where there is no text box: the camera, a video, Settings. Other keys go to the app untouched.",
                    note = if (!serviceOn && keys.accessibilityFnShortcuts) "Waiting for the service to be turned on." else null,
                    checked = keys.accessibilityFnShortcuts,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(accessibilityFnShortcuts = checked)) } },
                )
            }
            item {
                SwitchRow(
                    "Focus the text box when I start typing",
                    description = "In apps like Messages that open with the box not selected, the first key selects it, so the cursor shows and Backspace works.",
                    note = if (!serviceOn && keys.accessibilityFocusField) "Waiting for the service to be turned on." else null,
                    checked = keys.accessibilityFocusField,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(accessibilityFocusField = checked)) } },
                )
            }
            item {
                AboutExpander(
                    title = "What the service can see",
                    text = "An accessibility service can be powerful, so this one is kept to what its two jobs need. It reads the screen " +
                        "only when you start typing in a box that has no cursor, once per box, to find that box. It sees the keys you " +
                        "press only to run PhysiBoard's own shortcuts while no text box is open; every other key goes on to the app. " +
                        "It never stores, logs or sends what is on your screen or what you type, and it has no internet access of its " +
                        "own. Turn either switch off to stop that part; \"Reset device settings to stock\" turns the service itself off.",
                )
            }
        }
    }
}

private fun turnOnMessage(outcome: AccessibilityTurnOnOutcome): String = when (outcome) {
    AccessibilityTurnOnOutcome.TURNED_ON, AccessibilityTurnOnOutcome.ALREADY_ON -> "The service is on."
    AccessibilityTurnOnOutcome.NOT_PAIRED -> "Pair wireless debugging first (Titan tools), then try again."
    AccessibilityTurnOnOutcome.LIST_NOT_SAFE -> "Another app's accessibility entry looks unusual, so nothing was changed. Turn it on in Android's settings instead."
    AccessibilityTurnOnOutcome.FAILED -> "That did not work. Check that Wireless debugging is on, or turn it on in Android's settings."
}

/**
 * Android's page for this one service (Android 13 and later), else the list of every service. The
 * action is not in the public SDK's constants, so it is spelled out; Settings opens it for an
 * app's own service and the list otherwise.
 */
private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

internal fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, PhysiBoardAccessibilityService::class.java).flattenToString()
    val opened = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runCatching {
        context.startActivity(
            Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
