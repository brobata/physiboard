package brobata.physiboard

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Preference keys the long-press rows watch; must match SettingsManager. */
private const val KEY_LONG_PRESS_THRESHOLD = "long_press_threshold"
private const val KEY_LONG_PRESS_MODIFIER = "long_press_modifier"

/** Ordered long-press actions; values match SettingsManager.getLongPressModifier(). */
internal val LONG_PRESS_MODIFIER_OPTIONS: List<Pair<String, Int>> = listOf(
    "alt" to R.string.long_press_modifier_option_alt,
    "shift" to R.string.long_press_modifier_option_shift,
    "variations" to R.string.long_press_modifier_option_variations,
    "sym" to R.string.long_press_modifier_option_sym
)

/**
 * The long-press threshold slider and modifier dropdown, backed by SettingsManager.
 * Owns its state and re-reads when the preference changes elsewhere, so it can sit on
 * any screen as a self-contained block.
 */
@Composable
internal fun LongPressSettingsRows() {
    val context = LocalContext.current
    var longPressThreshold by remember {
        mutableStateOf(SettingsManager.getLongPressThreshold(context))
    }
    var longPressModifier by remember {
        mutableStateOf(SettingsManager.getLongPressModifier(context))
    }
    DisposableEffect(context) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_LONG_PRESS_THRESHOLD ->
                    longPressThreshold = SettingsManager.getLongPressThreshold(context)
                KEY_LONG_PRESS_MODIFIER ->
                    longPressModifier = SettingsManager.getLongPressModifier(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LongPressThresholdRow(
        threshold = longPressThreshold,
        onThresholdChange = { value ->
            longPressThreshold = value
            SettingsManager.setLongPressThreshold(context, value)
        }
    )
    LongPressModifierRow(
        selected = longPressModifier,
        onSelected = { value ->
            longPressModifier = value
            SettingsManager.setLongPressModifier(context, value)
        }
    )
}

/** Slider row for the long-press threshold; the caller persists the clamped value. */
@Composable
internal fun LongPressThresholdRow(
    threshold: Long,
    onThresholdChange: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.long_press_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(
                        R.string.keyboard_timing_long_press_value,
                        threshold
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Slider(
                value = threshold.toFloat(),
                onValueChange = { newValue ->
                    onThresholdChange(
                        newValue.toLong().coerceIn(
                            SettingsManager.getMinLongPressThreshold(),
                            SettingsManager.getMaxLongPressThreshold()
                        )
                    )
                },
                valueRange = SettingsManager.getMinLongPressThreshold().toFloat()..SettingsManager.getMaxLongPressThreshold().toFloat(),
                steps = 18,
                modifier = Modifier
                    .weight(1.5f)
                    .height(24.dp)
            )
        }
    }
}

/** Dropdown row for the long-press modifier; legacy "sym_*" values display as Sym. */
@Composable
internal fun LongPressModifierRow(
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = if (selected.startsWith("sym")) "sym" else selected
    @StringRes val selectedLabelRes: Int =
        LONG_PRESS_MODIFIER_OPTIONS.firstOrNull { it.first == current }?.second
            ?: LONG_PRESS_MODIFIER_OPTIONS.first().second
    // Surface(onClick) paints the ripple above its background and adds button
    // semantics; Row and DropdownMenu sit directly in it so the 64dp minimum
    // propagates and the content centres like the threshold row above.
    Surface(
        onClick = { expanded = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.long_press_modifier_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                // Current value below the title, like the threshold row, so a long
                // label never squeezes the title on the Titan's narrow screen.
                Text(
                    text = stringResource(selectedLabelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LONG_PRESS_MODIFIER_OPTIONS.forEach { (value, labelRes) ->
                val isSelected = value == current
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    }
                )
            }
        }
    }
}
