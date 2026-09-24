package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold

/**
 * The stand-in for a screen another agent is building under `:ime` and `:device:privileged`
 * (the broker pairing, the backlight and the ring). This module only wires the hub entry that
 * gets a user to it.
 */
@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    SettingsScreenScaffold(title = title, onBack = onBack) {
        Text(
            "This feature is being built.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
        )
    }
}
