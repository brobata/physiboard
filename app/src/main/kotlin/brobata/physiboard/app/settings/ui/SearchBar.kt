package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * The one search style for settings, app lists and pickers, as a shell prompt (app-shell.md
 * SS22.1): a `$` in the accent where the magnifier was, the placeholder in mono ending in the
 * underscore of an idle cursor (`$ search settings_`), on a pane with the 1 dp border, which turns
 * amber while the field has focus. A clear button appears once something is typed.
 */
@Composable
fun SearchPill(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val prompt = placeholder.trimEnd('…', '.', ' ').lowercase(Locale.getDefault()) + "_"
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(prompt, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Text("$", style = PhysiBoardType.prompt, color = MaterialTheme.colorScheme.primary) },
        trailingIcon = if (value.isEmpty()) null else {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = paneBorderColor(),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
    )
}
