package brobata.physiboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * Apps whose text box stays hidden under the suggestion strip until the strip visibly moves.
 * See [brobata.physiboard.inputmethod.KeyboardInsetsNudge] for what the keyboard does about it.
 */
@Composable
fun AppKeyboardNudgeScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    AppToggleListScreen(
        modifier = modifier,
        title = stringResource(R.string.app_keyboard_nudge_title),
        description = stringResource(R.string.app_keyboard_nudge_description),
        readEnabledPackages = { SettingsManager.getKeyboardNudgePackages(context) },
        setEnabled = { packageName, enabled -> SettingsManager.setKeyboardNudgeApp(context, packageName, enabled) },
        onBack = onBack
    )
}
