package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.IntClosedRange
import brobata.physiboard.app.settings.ui.IntRangeRow
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.SwitchRow
import brobata.physiboard.core.keys.LongPressMode

/**
 * "Long press" (keys-and-modifiers.md SS8.2, SS8.3, SS18; layers-sym-alt.md SS7, SS8): what holding
 * a letter types, how long to hold, whether the accent chooser shows, and the way to the user's
 * own accent lists.
 */
@Composable
fun LongPressScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val keys = controller.current.value.keys
    SettingsScreenScaffold(title = "Long press & accents", onBack = onBack) {
        RowList {
            header("Long press types")
            item {
                Text(
                    "Hold a letter key to type something else in place of the letter.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LONG_PRESS_CHOICES.forEach { choice ->
                item {
                    ModeRow(
                        choice = choice,
                        selected = keys.longPressMode == choice.mode,
                        onSelect = { controller.update { it.copy(keys = it.keys.copy(longPressMode = choice.mode)) } },
                    )
                }
            }
            item {
                // keys-and-modifiers.md SS8.3: clamped to 50 to 1000 ms, moved in 50 ms steps.
                IntRangeRow(
                    label = "Hold time",
                    description = "How long to hold a key before it counts as a long press.",
                    value = keys.longPressThresholdMs.toInt().coerceIn(MIN_HOLD_MS, MAX_HOLD_MS),
                    range = IntClosedRange(MIN_HOLD_MS, MAX_HOLD_MS),
                    step = HOLD_STEP_MS,
                    valueLabel = { "$it ms" },
                    onValueChange = { ms -> controller.update { it.copy(keys = it.keys.copy(longPressThresholdMs = ms.toLong())) } },
                )
            }
            header("Accents")
            item {
                // layers-sym-alt.md SS8.4.
                SwitchRow(
                    label = "Show every accent",
                    description = "When a letter has several accents, a bar shows them all, numbered. Still holding the letter, press the key with that number (1 is W, 2 is E, 3 is R...); after letting go, use Alt with it, or tap the bar. Other keys type as usual.",
                    checked = keys.variationChooser,
                    enabled = keys.longPressMode == LongPressMode.VARIATIONS,
                    onCheckedChange = { checked -> controller.update { it.copy(keys = it.keys.copy(variationChooser = checked)) } },
                )
            }
            item {
                NavigateRow("Customize Variations", "Choose, order or add the accents each letter offers", icon = Icons.Outlined.Abc) { onNavigate(Routes.CUSTOMIZE_VARIATIONS) }
            }
        }
    }
}

private data class ModeChoice(val mode: LongPressMode, val label: String, val description: String)

/** keys-and-modifiers.md SS8.2's six modes, in plain words; the stored values are unchanged. */
private val LONG_PRESS_CHOICES: List<ModeChoice> = listOf(
    ModeChoice(LongPressMode.ALT, "Alt symbol", "The symbol printed on the key, as with Alt. The default."),
    ModeChoice(LongPressMode.SHIFT, "Capital letter", "Hold a for A."),
    ModeChoice(LongPressMode.VARIATIONS, "Accent / variation", "Hold a for its first accent, ą in Polish or ä in German, in your keyboard language's order."),
    ModeChoice(LongPressMode.SYM_SYMBOLS, "Sym symbol", "The key's character on the Symbols page."),
    ModeChoice(LongPressMode.SYM_EMOJI, "Sym emoji", "The key's emoji on the Emoji page."),
    ModeChoice(LongPressMode.SYM, "First Sym page", "The key's character on the Emoji or Symbols page, whichever comes first in your Sym page order."),
)

private const val MIN_HOLD_MS = 50
private const val MAX_HOLD_MS = 1000
private const val HOLD_STEP_MS = 50

@Composable
private fun ModeRow(choice: ModeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
            Text(choice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
