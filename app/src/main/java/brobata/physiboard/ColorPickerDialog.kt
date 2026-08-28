package brobata.physiboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import brobata.physiboard.ring.ColorWheel

/** A single colour, tappable when [onClick] is given. */
@Composable
fun ColorSwatch(argb: Int, selected: Boolean, size: Dp, onClick: (() -> Unit)? = null) {
    val outline = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .border(if (selected) 3.dp else 1.dp, outline, CircleShape)
            .padding(3.dp)
            .background(Color(argb), CircleShape)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
    )
}

/**
 * Quick swatches over a full hue/saturation wheel.
 *
 * Shared by everything in the app that picks a colour, so the notification ring and the cursor
 * modifiers offer the same control rather than each growing its own.
 *
 * @param swatches the quick picks offered above the wheel.
 * @param warning called with the live colour; a non-null result is shown as a caution. It only warns
 * - a caller that knows a colour is a poor choice should say so and still let it be chosen.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    current: Int?,
    swatches: List<Int>,
    subtitle: String? = null,
    warning: (Int) -> String? = { null },
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var live by remember(current) { mutableStateOf(current ?: swatches.firstOrNull() ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                swatches.chunked(5).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        row.forEach { argb ->
                            ColorSwatch(
                                argb = argb,
                                selected = argb == live,
                                size = 40.dp,
                                onClick = { live = argb }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ColorWheel(
                    selected = live,
                    onColorChange = { live = it },
                    modifier = Modifier.fillMaxWidth()
                )

                warning(live)?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(live) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
