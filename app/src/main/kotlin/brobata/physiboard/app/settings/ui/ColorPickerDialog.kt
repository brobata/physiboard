package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * The colour dialog shared by "everything that picks a color". spec: status-bar.md SS9.5: a
 * title, an optional subtitle, quick-pick swatches in rows of five 40 dp circles, the
 * [ColorWheelPicker] hue/saturation wheel and brightness slider, an optional warning line, OK and
 * Cancel; "bounded to the window height minus 220 dp (never under 200 dp) and scrolls" (D8). The
 * draft editor's own extra hex field (SS9.5's last sentence) is always shown here rather than
 * conditioned on a draft, since 3.0's theme screen has no separate non-hex picker to fall back to.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    swatches: List<Int>,
    subtitle: String = "Pick a colour, or dial one in on the wheel.",
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var current by remember(initial) { mutableStateOf(initial) }
    var hexText by remember(initial) { mutableStateOf(ColorHex.toHex(initial)) }
    fun pick(color: Int) {
        current = color
        hexText = ColorHex.toHex(color)
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    // spec SS9.5, D8: "bounded to the window height minus 220 dp (never under 200 dp)".
    val contentMaxHeight = (screenHeightDp - 220).coerceAtLeast(200)
    // spec SS9.5: "the wheel is capped at the smaller of 220 dp and 30 percent of the window height".
    val wheelMaxWidth = minOf(220, (screenHeightDp * 0.3).toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = contentMaxHeight.dp).verticalScroll(rememberScrollState())) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                if (swatches.isNotEmpty()) {
                    // spec SS9.5: "quick-pick swatches in rows of five 40 dp circles".
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().heightIn(max = ((swatches.size / 5 + 1) * 48).dp),
                    ) {
                        items(swatches) { swatch ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch))
                                    .clickable { pick(swatch) },
                            )
                        }
                    }
                }
                Box(modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = wheelMaxWidth.dp)) {
                    ColorWheelPicker(colorArgb = current, onColorChange = ::pick, swatches = emptyList())
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        ColorHex.parse(text)?.let { current = it }
                    },
                    label = { Text("Hex colour") },
                    isError = ColorHex.validate(hexText) != null,
                    supportingText = { ColorHex.validate(hexText)?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }, enabled = ColorHex.validate(hexText) == null) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
