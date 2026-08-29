package brobata.physiboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import brobata.physiboard.ui.SettingsTopBar

/**
 * The Extras page.
 *
 * The home screen has had an Extras button for a while, but it resolved to the same destination as
 * All settings — `DESTINATION_EXTRAS` mapped to `SettingsDestination.Main`, so the two tiles opened
 * the same page and Extras never existed as a screen. This is that screen: the things that are
 * neither device tools nor typing behaviour.
 */
@Composable
fun ExtrasScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onQuickLauncher: () -> Unit,
    onInputLanguages: () -> Unit,
    onTextExpansion: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsTopBar(
            title = stringResource(R.string.extras_title),
            onBack = onBack
        )

        ExtrasRow(
            icon = Icons.AutoMirrored.Filled.ManageSearch,
            title = stringResource(R.string.quick_launcher_title),
            description = stringResource(R.string.starter_launcher_shortcuts_description),
            onClick = onQuickLauncher
        )
        ExtrasRow(
            icon = Icons.Filled.Language,
            title = stringResource(R.string.custom_input_styles_title),
            description = stringResource(R.string.extras_languages_description),
            onClick = onInputLanguages
        )
        ExtrasRow(
            icon = Icons.AutoMirrored.Filled.ShortText,
            title = stringResource(R.string.text_expansion_title),
            description = stringResource(R.string.text_expansion_description),
            onClick = onTextExpansion
        )
    }
}

@Composable
private fun ExtrasRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
