package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import brobata.physiboard.core.actions.picker.UnicodeCharacterCatalog

/**
 * "Select unicode character" (expansion-clipboard-pickers-launcher.md SS5.2): opened from the
 * "Customize SYM Keyboard" screen's Symbols-page editor when a letter is tapped. [onReset] is
 * called with the empty string, "which the caller treats as 'use the shipped character'" (the
 * caller removes the key from the custom map rather than storing "").
 */
@Composable
fun UnicodeCharacterDialog(letter: Char? = null, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val categories = UnicodeCharacterCatalog.CATEGORIES
    var selected by remember { mutableStateOf(categories.first().label) }
    var custom by remember { mutableStateOf("") }
    val title = if (letter != null) "Select character for $letter" else "Select unicode character"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        placeholder = { Text("Custom character") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { if (custom.isNotBlank()) onChoose(custom) }, enabled = custom.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
                }
                TextButton(onClick = { onChoose("") }, modifier = Modifier.fillMaxWidth()) { Text("Reset to Default") }
                LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = category.label == selected,
                            onClick = { selected = category.label },
                            label = { Text(category.label) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                val glyphs = categories.firstOrNull { it.label == selected }?.glyphs.orEmpty()
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 44.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(glyphs) { glyph ->
                        Card(modifier = Modifier.padding(1.dp).size(44.dp).clickable { onChoose(glyph) }) {
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(glyph, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
