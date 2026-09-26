package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.R
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.shell.AppLocaleApplier
import brobata.physiboard.core.shell.AppLocale
import java.util.Locale

/**
 * "App Language" (dictionaries-languages.md SS11; app-shell.md SS15 card 3, SS22.3;
 * settings-catalog.md SS9.2): "System default" plus the ten shipped locales in the spec's fixed
 * order, each row labelled by [AppLocale.optionLabel]'s dual-name rule ("<native>" alone, or
 * "<native> - <ui name>" when they differ). Tapping a row writes `app_language_tag`, applies it
 * immediately through [AppLocaleApplier] and recreates this activity, matching "Changing it
 * recreates the current screen" (SS22.3) rather than waiting for the user to back out.
 */
@Composable
fun AppLanguageScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val context = LocalContext.current
    val storedTag = controller.current.value.languages.appLanguageTag
    val selectedTag = AppLocale.resolve(storedTag)
    // The language the interface is shown in right now (the resolved override, or the system's,
    // when there is none): the "current UI language" SS11's dual-name rule compares each option
    // against.
    val uiTag = selectedTag ?: Locale.getDefault().toLanguageTag()

    fun choose(tag: String) {
        controller.update { it.copy(languages = it.languages.copy(appLanguageTag = tag)) }
        AppLocaleApplier.applyAndRecreate(context, tag)
    }

    SettingsScreenScaffold(title = stringResource(R.string.app_language_title), onBack = onBack) {
        RowList {
            item {
                Text(
                    stringResource(R.string.app_language_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                AppLanguageOptionRow(
                    label = stringResource(R.string.app_language_system_default),
                    selected = selectedTag == null,
                    onClick = { choose("") },
                )
            }
            items(AppLocale.SUPPORTED_TAGS) { tag ->
                AppLanguageOptionRow(
                    label = AppLocale.optionLabel(tag, uiTag),
                    selected = selectedTag == tag,
                    onClick = { choose(tag) },
                )
            }
        }
    }
}

@Composable
private fun AppLanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
