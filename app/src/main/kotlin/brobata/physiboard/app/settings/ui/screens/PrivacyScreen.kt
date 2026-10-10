package brobata.physiboard.app.settings.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.settings.ui.InfoText
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.AboutExpander
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow

/**
 * "Privacy" (settings-catalog.md SS9.2): private mode (app-shell.md SS31), clean links
 * (expansion-clipboard-pickers-launcher.md SS3.7), and the one permission that lets PhysiBoard
 * read something outside the keyboard (notification access, for the Fill page's one-time codes,
 * layers-sym-alt.md SS4.7; the same row is on Sym pages). Reached from the home index and search.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val privacy = controller.current.value.privacy
    // Re-read on every return: the choice is made in Android's own settings.
    val context = LocalContext.current
    var notificationAccess by remember { mutableStateOf(oneTimeCodeAccessGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationAccess = oneTimeCodeAccessGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreenScaffold(title = "Privacy", onBack = onBack) {
        RowList {
            item {
                SwitchRow(
                    "Private mode",
                    description = "PhysiBoard remembers nothing you type and makes no network requests.",
                    note = if (privacy.privateMode) "On. The caret badge shows PRIVATE while you type." else null,
                    checked = privacy.privateMode,
                    onCheckedChange = { checked -> controller.update { it.copy(privacy = it.privacy.copy(privateMode = checked)) } },
                )
                AboutExpander(
                    title = "About private mode",
                    text = "While this is on, PhysiBoard remembers nothing you type: no new words, no word predictions learned, " +
                        "no clipboard history, no recent emoji. Autocorrect still uses what it already knows. PhysiBoard also makes no " +
                        "network requests: no update checks, no update downloads and no dictionary downloads. Dictation is done by your phone's speech " +
                        "service, which may still go online; it is not part of PhysiBoard. To switch it from the keyboard, give \"Private mode\" a key " +
                        "under Assigned launcher keys (Sym + that key), or put the command physiboard.toggle_private_mode on the Fn layer.",
                )
            }
            plainItem {
                // Not a setting: a disabled switch that could never be turned off read as broken.
                InfoText(
                    "Apps can also ask the keyboard not to learn from a text box, as incognito browser tabs do. PhysiBoard always " +
                        "honours that: nothing typed there is remembered, whether or not private mode is on.",
                )
            }
            header("Links")
            item {
                SwitchRow(
                    "Clean links",
                    description = "Remove tracking from links you paste from the clipboard panel.",
                    checked = privacy.cleanLinks,
                    onCheckedChange = { checked -> controller.update { it.copy(privacy = it.privacy.copy(cleanLinks = checked)) } },
                )
                AboutExpander(
                    title = "About clean links",
                    text = "Remove tracking from links you copy and paste from the clipboard panel, such as utm_source, fbclid " +
                        "or a YouTube share code, and open up Google and Facebook redirect links to the real address. The rest of " +
                        "the link is kept exactly. Pasting with Ctrl+V is done by the app, so it pastes the link as it was copied.",
                )
            }
            header("What PhysiBoard can read")
            item {
                NavigateRow(
                    "Notification access for codes",
                    description = "Lets the Fill page offer a sign-in code from a text or e-mail. Codes stay in memory for 10 minutes and are never saved or sent.",
                    icon = Icons.Outlined.NotificationsActive,
                    value = if (notificationAccess) "Allowed" else "Not allowed",
                ) { openNotificationAccess(context) }
            }
        }
    }
}
