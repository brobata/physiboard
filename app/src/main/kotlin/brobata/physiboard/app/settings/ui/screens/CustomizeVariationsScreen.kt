package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SingleChoiceDropdownRow
import brobata.physiboard.app.settings.ui.UnicodeCharacterDialog
import brobata.physiboard.core.keys.VariationChooser
import brobata.physiboard.core.keys.Variations
import java.util.Locale

/**
 * "Customize Variations" (layers-sym-alt.md SS8.3): every letter a to z with the accents a long
 * press in Accent mode offers, and for one letter its small and capital lists, each entry movable
 * and removable, up to ten, any text added, or the letter put back to the built-in lists.
 *
 * A letter the user never touched follows the keyboard language's order; the "Order shown for"
 * row only chooses which language this screen previews it in. Once a list is edited, it is
 * stored as shown and used in every language.
 */
@Composable
fun CustomizeVariationsScreen(onBack: () -> Unit) {
    val controller = LocalSettingsController.current
    val stored = controller.current.value.keys.customVariations
    var previewLanguage by remember { mutableStateOf(defaultPreviewLanguage()) }
    var editing by remember { mutableStateOf<Char?>(null) }
    var adding by remember { mutableStateOf<Char?>(null) }
    val table = Variations.effective(previewLanguage, Variations.overridesFromStored(stored))

    fun save(character: Char, list: List<String>?) {
        controller.update { settings ->
            val key = character.toString()
            val updated = if (list == null) settings.keys.customVariations - key else settings.keys.customVariations + (key to Variations.clean(list))
            settings.copy(keys = settings.keys.copy(customVariations = updated))
        }
    }

    val letter = editing
    if (letter == null) {
        SettingsScreenScaffold(title = "Customize Variations", onBack = onBack) {
            RowList {
                item {
                    Text(
                        "With Long press set to Accent / variation, holding a letter types the first accent in its list. Tap a letter to choose, order or add its accents. Your changes apply in every language.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item {
                    SingleChoiceDropdownRow(
                        label = "Order shown for",
                        description = "Letters you have not changed put the keyboard language's own accents first. This only picks the language previewed here.",
                        options = listOf("") + Variations.languagesWithOwnOrder,
                        optionLabel = ::languageName,
                        selected = previewLanguage,
                        onSelect = { previewLanguage = it },
                    )
                }
                ('a'..'z').forEach { base ->
                    item {
                        val customised = stored.containsKey(base.toString()) || stored.containsKey(base.uppercaseChar().toString())
                        val list = table.listFor(base)
                        NavigateRow(
                            label = if (customised) "$base  ·  changed" else base.toString(),
                            description = list.joinToString("  ").ifEmpty { "No accents" },
                        ) { editing = base }
                    }
                }
                item {
                    TextButton(
                        onClick = { controller.update { it.copy(keys = it.keys.copy(customVariations = emptyMap())) } },
                        enabled = stored.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text("Reset every letter to default", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    } else {
        SettingsScreenScaffold(title = "Accents for $letter", onBack = { editing = null }) {
            RowList {
                for (character in listOf(letter, letter.uppercaseChar())) {
                    val list = table.listFor(character)
                    val customised = stored.containsKey(character.toString())
                    item { SectionHeader(if (customised) "$character (changed)" else character.toString()) }
                    if (list.isEmpty()) {
                        item {
                            Text("No accents: holding $character types $character.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                    list.forEachIndexed { index, entry ->
                        item {
                            EntryRow(
                                position = index,
                                entry = entry,
                                canMoveUp = index > 0,
                                canMoveDown = index < list.lastIndex,
                                onMoveUp = { save(character, list.moved(index, index - 1)) },
                                onMoveDown = { save(character, list.moved(index, index + 1)) },
                                onRemove = { save(character, list.filterIndexed { i, _ -> i != index }) },
                            )
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { adding = character }, enabled = list.size < Variations.MAX_PER_CHARACTER) {
                                Text(if (list.size < Variations.MAX_PER_CHARACTER) "Add" else "Full (${Variations.MAX_PER_CHARACTER})")
                            }
                            TextButton(onClick = { save(character, null) }, enabled = customised, modifier = Modifier.padding(start = 8.dp)) {
                                Text("Reset to default")
                            }
                        }
                    }
                }
                item {
                    Text(
                        "The first entry is what a long press types. With \"Show every accent\" on, the bar numbers them 1 to 9, then 0, the digits printed on the keys.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    adding?.let { character ->
        UnicodeCharacterDialog(
            letter = character,
            resetLabel = "Cancel",
            onDismiss = { adding = null },
            onChoose = { chosen ->
                adding = null
                if (chosen.isNotEmpty()) save(character, table.listFor(character) + chosen)
            },
        )
    }
}

@Composable
private fun EntryRow(
    position: Int,
    entry: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            VariationChooser.digitForIndex(position).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry, fontSize = 24.sp)
            if (position == 0) Text("Typed by a long press", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
            Icon(Icons.Filled.Close, contentDescription = "Remove")
        }
    }
}

/** The phone's language when it has accents of its own, else the neutral order. */
private fun defaultPreviewLanguage(): String {
    val language = Variations.languageOf(Locale.getDefault().language)
    return if (language in Variations.languagesWithOwnOrder) language else ""
}

private fun languageName(code: String): String =
    if (code.isEmpty()) "No particular language" else Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (to < 0 || to >= size || from == to) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}
