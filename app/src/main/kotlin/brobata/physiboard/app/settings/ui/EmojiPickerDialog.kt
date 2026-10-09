package brobata.physiboard.app.settings.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import brobata.physiboard.core.actions.emoji.EmojiAvailability
import brobata.physiboard.core.actions.emoji.EmojiCategories
import brobata.physiboard.core.actions.emoji.EmojiCategory
import brobata.physiboard.core.actions.emoji.EmojiEntry
import brobata.physiboard.core.actions.emoji.EmojiSearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Select Emoji" (expansion-clipboard-pickers-launcher.md SS5.1): opened from the "Customize SYM
 * Keyboard" screen's Emoji-page editor when a letter is tapped. Shares its data and scoring with
 * the Sym page 4 picker (`:core:actions`' [EmojiCategories]/[EmojiSearchIndex], SS4.1/SS4.2); this
 * file only owns the dialog chrome and the asset read, which `:ime`'s `EmojiAssets` already shows
 * is the only Android-specific part.
 *
 * SPEC GAP: only the `en` search-term file ships (`EmojiAssets`' own note), so search elsewhere
 * still ranks by literal base/variant match. The variant popup is a [Popup] anchored under the
 * long-pressed cell rather than SS4.4's precise "above the cell, clamped to the screen" geometry.
 */
@Composable
fun EmojiPickerDialog(letter: Char? = null, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var categories by remember { mutableStateOf<List<EmojiCategory>?>(null) }
    var index by remember { mutableStateOf<EmojiSearchIndex?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var variantsFor by remember { mutableStateOf<EmojiEntry?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { loadEmojiCategories(context) }
        if (loaded.isEmpty()) {
            loadFailed = true
        } else {
            categories = loaded
            selectedCategoryId = loaded.first().id
            index = EmojiSearchIndex.build(loaded, emptyList())
        }
    }

    val title = if (letter != null) "Select emoji for letter $letter" else "Select Emoji"
    val trimmedQuery = query.trim()
    val entries: List<EmojiEntry> = when {
        loadFailed -> emptyList()
        categories == null -> emptyList()
        trimmedQuery.isNotEmpty() -> index?.search(trimmedQuery, limit = Int.MAX_VALUE)?.map { it.entry }.orEmpty()
        else -> categories?.firstOrNull { it.id == selectedCategoryId }?.entries.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search emoji...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                when {
                    loadFailed -> Text("Unable to load emoji", style = MaterialTheme.typography.bodyMedium)
                    categories == null -> Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> {
                        if (trimmedQuery.isEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                items(categories.orEmpty()) { category ->
                                    FilterChip(
                                        colors = terminalChipColors(),
                                        selected = category.id == selectedCategoryId,
                                        onClick = { selectedCategoryId = category.id },
                                        label = { Text(category.label) },
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }
                        }
                        if (entries.isEmpty()) {
                            Text(if (trimmedQuery.isNotEmpty()) "No emoji found" else "", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 44.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                                items(entries, key = { it.base }) { entry ->
                                    Card(
                                        modifier = Modifier
                                            .padding(1.dp)
                                            .size(44.dp)
                                            .combinedClickable(
                                                onClick = { onChoose(entry.base) },
                                                onLongClick = { if (entry.hasVariants) variantsFor = entry },
                                            ),
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                                            Text(entry.base, style = PhysiBoardType.glyph)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    variantsFor?.let { entry ->
        Popup(onDismissRequest = { variantsFor = null }, properties = PopupProperties(focusable = true)) {
            Card {
                Row(modifier = Modifier.padding(8.dp)) {
                    (listOf(entry.base) + entry.variants).forEach { glyph ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(40.dp)
                                .clickable { onChoose(glyph); variantsFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(glyph, style = PhysiBoardType.glyph) }
                    }
                }
            }
        }
    }
}

/** spec SS4.1/SS4.2: the nine shipped category files plus availability filtering; no search-term index is loaded here beyond the base/variant literal match (this dialog's own SPEC GAP note). */
private fun loadEmojiCategories(context: Context): List<EmojiCategory> {
    val assets = context.assets
    fun read(path: String): String? = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
    val minApi = read("emoji/${EmojiCategories.MIN_API_FILE}")?.let(EmojiCategories::parseMinApi).orEmpty()
    val shipped = EmojiCategories.SHIPPED.mapNotNull { s ->
        read("emoji/${s.fileName}")?.let { EmojiCategory(s.id, s.label, s.icon, EmojiCategories.parseCategoryFile(it)) }
    }
    val paint = Paint()
    val apiLevel = android.os.Build.VERSION.SDK_INT
    return runCatching { EmojiAvailability.filterCategories(shipped, minApi, apiLevel) { paint.hasGlyph(it) } }.getOrDefault(shipped)
}
