package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import brobata.physiboard.app.settings.CustomLayoutStore
import brobata.physiboard.app.settings.ui.EmptyState
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.LetterEntry
import brobata.physiboard.core.keys.PunctuationKey
import brobata.physiboard.core.subtype.BundledLayoutIds
import brobata.physiboard.device.titan.TitanLayouts

/** [KeyId] plus its entry, in the order the viewer lists them. */
private data class ViewerRow(val keyId: KeyId, val entry: LetterEntry)

private sealed interface ViewerState {
    object Loading : ViewerState
    object Failed : ViewerState
    object Empty : ViewerState
    data class Loaded(val rows: List<ViewerRow>) : ViewerState
}

/** The Android key name a row shows, e.g. `KEYCODE_Q` (layers-sym-alt.md SS9.7). */
private fun keyName(keyId: KeyId): String = when (keyId) {
    is KeyId.Letter -> "KEYCODE_${keyId.qwertyLetter}"
    is KeyId.Digit -> "KEYCODE_${keyId.digit}"
    is KeyId.Punctuation -> "KEYCODE_${keyId.key.name}"
    is KeyId.Modifier -> "KEYCODE_${keyId.key.name}"
    is KeyId.Control -> "KEYCODE_${keyId.key.name}"
}

/** SS9.7: "sorted by keycode". This module has no android import, so keycode order is approximated with real Android `KeyEvent` values: digits, then letters, then the punctuation keys in their own keycode order. */
private fun keySortOrder(keyId: KeyId): Int = when (keyId) {
    is KeyId.Digit -> keyId.digit - '0'
    is KeyId.Letter -> 20 + (keyId.qwertyLetter - 'A')
    is KeyId.Punctuation -> 60 + when (keyId.key) {
        PunctuationKey.COMMA -> 0
        PunctuationKey.PERIOD -> 1
        PunctuationKey.GRAVE -> 2
        PunctuationKey.MINUS -> 3
        PunctuationKey.EQUALS -> 4
        PunctuationKey.LEFT_BRACKET -> 5
        PunctuationKey.RIGHT_BRACKET -> 6
        PunctuationKey.BACKSLASH -> 7
        PunctuationKey.SEMICOLON -> 8
        PunctuationKey.APOSTROPHE -> 9
        PunctuationKey.SLASH -> 10
    }
    else -> 100
}

private suspend fun loadState(context: android.content.Context, layoutId: String): ViewerState {
    val entries = if (layoutId in BundledLayoutIds.ALL) {
        TitanLayouts.bundled().firstOrNull { it.first == layoutId }?.third?.baseLayout?.entries
    } else {
        CustomLayoutStore(context).list().firstOrNull { it.name == layoutId }?.parsed?.layout?.entries
            ?: return ViewerState.Failed
    }
    if (entries == null) return ViewerState.Failed
    if (entries.isEmpty()) return ViewerState.Empty
    return ViewerState.Loaded(entries.map { (keyId, entry) -> ViewerRow(keyId, entry) }.sortedBy { keySortOrder(it.keyId) })
}

/**
 * "Keyboard map" (layers-sym-alt.md SS9.7): one row per key, sorted by keycode, each a key-name
 * chip, its lower/upper pair, a "multitap"/"single" badge and one chip per tap level.
 */
@Composable
fun LayoutViewerScreen(layoutId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember(layoutId) { mutableStateOf<ViewerState>(ViewerState.Loading) }
    LaunchedEffect(layoutId) { state = loadState(context, layoutId) }

    SettingsScreenScaffold(title = "Keyboard map", onBack = onBack) {
        RowList {
            item {
                Text(
                    "Layout: $layoutId",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when (val s = state) {
                ViewerState.Loading -> {}
                ViewerState.Failed -> plainItem { EmptyState(Icons.Outlined.ErrorOutline, "Unable to load this layout mapping.") }
                ViewerState.Empty -> plainItem { EmptyState(Icons.Outlined.Keyboard, "No key mappings found for this layout.") }
                is ViewerState.Loaded -> items(s.rows, key = { keyName(it.keyId) }) { row -> ViewerKeyRow(row) }
            }
        }
    }
}

@Composable
private fun ViewerKeyRow(row: ViewerRow) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(keyName(row.keyId)) })
            Text(
                "  ${row.entry.lowercase}/${row.entry.uppercase}",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (row.entry.isMultiTap) "  multitap" else "  single",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (row.entry.isMultiTap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.entry.isMultiTap) {
            // Wraps, so a key with many taps does not run off the right edge.
            FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                row.entry.taps.forEach { tap ->
                    val label = if (tap.uppercase.isNotEmpty()) "${tap.lowercase}/${tap.uppercase}" else tap.lowercase
                    AssistChip(onClick = {}, label = { Text(label) }, modifier = Modifier.padding(end = 4.dp))
                }
            }
        }
    }
}
