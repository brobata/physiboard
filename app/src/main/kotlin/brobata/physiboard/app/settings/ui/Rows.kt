package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The 48 dp floor every touch target on this screen keeps (rebuild-from-scratch.md: Titan 2
 * Elite geometry). It also gives a hardware-keyboard DPAD focus something a full row's height to
 * land on, which is what makes DPAD + Enter navigation usable on a device with no soft keyboard.
 */
val MinTouchTarget = 48.dp

/**
 * The screen chrome every hub and sub-screen shares (app-shell.md SS22.1, "the settings screens
 * share one top bar"): inset below the status bar and out of the cutout, 1 dp tonal elevation, a
 * back arrow with content description "Back", the title as a heading in headline-small
 * semi-bold, and trailing actions. Content scrolls in a [LazyColumn] so a screen with more rows
 * than the Titan's 1200 px tall panel can hold is still fully reachable by DPAD.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { trailingAction?.invoke() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/** A list of rows with the 48 dp floor and enough bottom padding to clear the last row's target. */
@Composable
fun RowList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        content = content,
    )
}

/** spec: settings-catalog.md SS9.2, the bold section labels inside a hub screen ("Capitalization", "Advanced", ...). */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun DividerLabel(text: String) = SectionHeader(text)

/** The label plus description block every row type shares, left of its control. */
@Composable
private fun RowLabel(label: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A boolean row (the catalogue's "switch" control). Tapping anywhere on the row toggles it. */
@Composable
fun SwitchRow(
    label: String,
    description: String? = null,
    // spec: per-app-behavior.md SS6.1, "an optional web-app note in the primary colour (up to two
    // lines) when the screen supplies one" (the exact-typing list's WebAPK rows, SS4.3);
    // device-backlight-ring.md SS5.7.4 wants the same slot in the error colour instead
    // ("Unavailable until the phone has been paired once..."), hence [noteIsError].
    note: String? = null,
    noteIsError: Boolean = false,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .clickableRow(enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RowLabel(label, description)
            if (note != null) {
                val color = if (noteIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(note, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
        Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
    }
}

/** A row that only navigates to another screen (the catalogue's "send to another screen" row, drawn with a ">"). */
@Composable
fun NavigateRow(label: String, description: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .clickableRow(true, onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, description, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
    }
}

/**
 * The catalogue's "chips" control: a small fixed set of values shown inline (spec:
 * settings-catalog.md SS9.2/SS9.4, "Currency Symbol chips", "Ring brightness chips", ...). Use
 * [SingleChoiceDropdownRow] instead once the option list is long or open-ended.
 */
@Composable
fun <T> SingleChoiceChipsRow(
    label: String,
    description: String? = null,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        RowLabel(label, description)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                    modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget),
                )
            }
        }
    }
}

/** The catalogue's "picker" control for a long or dynamic option list (spec: SS9.2, "Speech engine" picker, "App Language" dropdown). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceDropdownRow(
    label: String,
    description: String? = null,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        RowLabel(label, description)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = optionLabel(selected),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MinTouchTarget),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}

/** The catalogue's "multi choice" control: several checkboxes, one per option (spec: SS9.2, "Left buttons"/"Right buttons" and per-language rows). */
@Composable
fun <T> MultiChoiceRow(
    label: String,
    description: String? = null,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: Set<T>,
    onToggle: (T, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        RowLabel(label, description)
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .clickableRow(true) { onToggle(option, option !in selected) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = option in selected, onCheckedChange = { onToggle(option, it) })
                Text(optionLabel(option))
            }
        }
    }
}

/** The catalogue's "slider" control for an integer bound to a range (spec: SS9.2, "Sensitivity" 8-64, "Custom vibration" 5-80, ...). */
@Composable
fun IntRangeRow(
    label: String,
    description: String? = null,
    value: Int,
    range: IntClosedRange,
    step: Int = 1,
    valueLabel: (Int) -> String = { it.toString() },
    // device-backlight-ring.md SS5.7.3: "the value is saved when the drag ends", unlike every
    // other slider here which writes on every tick; false keeps every existing caller unchanged.
    commitOnRelease: Boolean = false,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        RowLabel(label, description)
        var dragValue by remember(value) { mutableStateOf(value) }
        val shown = if (commitOnRelease) dragValue else value
        Text(valueLabel(shown), style = MaterialTheme.typography.bodyMedium)
        val steps = if (step <= 1) 0 else ((range.last - range.first) / step) - 1
        Slider(
            value = shown.toFloat(),
            onValueChange = { raw ->
                val snapped = snapToStep(raw.toInt(), range, step)
                if (commitOnRelease) dragValue = snapped else onValueChange(snapped)
            },
            onValueChangeFinished = { if (commitOnRelease) onValueChange(dragValue) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget),
        )
    }
}

private fun snapToStep(raw: Int, range: IntClosedRange, step: Int): Int {
    val clamped = raw.coerceIn(range.first, range.last)
    if (step <= 1) return clamped
    val snapped = range.first + ((clamped - range.first) / step) * step
    return snapped.coerceIn(range.first, range.last)
}

/** A plain `first..last` pair, so callers do not need to depend on Kotlin's [IntRange] iteration cost for a UI bound. */
data class IntClosedRange(val first: Int, val last: Int)

/** The catalogue's "text" control, with an optional validator that shows an error string under the field. */
@Composable
fun TextFieldRow(
    label: String,
    description: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    validate: ((String) -> String?)? = null,
) {
    val error = validate?.invoke(value)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = {
                val text = error ?: description
                if (text != null) Text(text)
            },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
        )
    }
}

/** An ARGB colour edited as `#AARRGGBB` text (StripTheme's fields are all signed 32-bit ARGB ints; see [ColorHex]). */
@Composable
fun ColorFieldRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(ColorHex.toHex(value)) }
    TextFieldRow(
        label = label,
        value = text,
        onValueChange = { newText ->
            text = newText
            ColorHex.parse(newText)?.let(onValueChange)
        },
        validate = { ColorHex.validate(it) },
    )
}

/** A row of buttons for a confirm/cancel action (the "Reset" and dialog rows the catalogue describes throughout). */
@Composable
fun ButtonRow(label: String, description: String? = null, buttonText: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, description, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget)) { Text(buttonText) }
    }
}

/**
 * The catalogue's collapsible "Advanced" section (settings-catalog.md SS9.2, SS9.5: "opens
 * collapsed and remembers its state only while the screen is alive"), so [remember] rather than
 * `rememberSaveable` is deliberate here.
 */
@Composable
fun ExpandableSection(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouchTarget)
                .clickableRow(true) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) content()
    }
}

/** A per-app switch list with a search field at the top (rebuild-from-scratch.md: "a searchable app picker over installed packages with a switch per app"). */
@Composable
fun AppPickerBody(
    apps: List<InstalledApp>,
    selected: Set<String>,
    // spec: per-app-behavior.md SS6.1, "a description paragraph ... at the top of the list".
    description: String? = null,
    // spec: SS6.1, "an optional web-app note ... when the screen supplies one" (SS4.3's WebAPK row text).
    noteFor: ((InstalledApp) -> String?)? = null,
    onToggle: (String, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    // spec: SS6.1, "Enabled rows sort to the top (stable within each group), re-sorted after every
    // toggle". sortedBy is stable, so each group keeps [apps]'s own (alphabetical) order.
    val sorted = filtered.sortedBy { it.packageName !in selected }
    Column {
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).defaultMinSize(minHeight = MinTouchTarget),
        )
        RowList {
            items(sorted, key = { it.packageName }) { app ->
                SwitchRow(
                    label = app.label,
                    description = app.packageName,
                    note = noteFor?.invoke(app),
                    checked = app.packageName in selected,
                    onCheckedChange = { onToggle(app.packageName, it) },
                )
            }
        }
    }
}

/**
 * The catalogue's ordered multi-choice control: like [MultiChoiceRow], but [selected] is a list
 * (order matters), so a checked option also gets up/down affordances. Needed wherever the schema
 * keeps an ordered list but the settings screen previously had only [MultiChoiceRow] to bind it
 * with (status-bar.md SS6.3: "Left buttons"/"Right buttons" render every entry, in order, not just
 * which ones are on). Up/down buttons rather than drag-and-drop: nothing in this app depends on a
 * drag gesture library, and the Titan's hardware DPAD reaches an [IconButton] the same way it
 * reaches every other row, which a drag handle would not offer.
 */
@Composable
fun <T> ReorderableMultiChoiceRow(
    label: String,
    description: String? = null,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: List<T>,
    onChange: (List<T>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        RowLabel(label, description)
        selected.forEachIndexed { index, option ->
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = true, onCheckedChange = { onChange(selected - option) })
                Text(optionLabel(option), modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onChange(selected.moved(index, index - 1)) },
                    enabled = index > 0,
                    modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget),
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(
                    onClick = { onChange(selected.moved(index, index + 1)) },
                    enabled = index < selected.lastIndex,
                    modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget),
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
            }
        }
        val unselected = options.filter { it !in selected }
        unselected.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .clickableRow(true) { onChange(selected + option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = false, onCheckedChange = { onChange(selected + option) })
                Text(optionLabel(option))
            }
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (to < 0 || to >= size || from == to) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

/** Gives a row a click target with the [Role.Button] semantics DPAD/Enter navigation expects, without importing `clickable` at every call site. */
private fun Modifier.clickableRow(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick))

/**
 * The wait shown while a T2E screen asks the broker what the system currently reports. A bare
 * spinner sat here for the ten seconds that round trip takes to time out when wireless debugging
 * is off, with nothing to say what was happening or that an answer was still coming
 * (Titan, 2026-09-29).
 */
@Composable
fun CheckingSystemRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            "Checking the connection to the system…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
