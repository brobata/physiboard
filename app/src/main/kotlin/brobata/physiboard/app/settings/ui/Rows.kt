package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * The 48 dp floor every touch target on this screen keeps (rebuild-from-scratch.md: Titan 2
 * Elite geometry). It also gives a hardware-keyboard DPAD focus something a full row's height to
 * land on, which is what makes DPAD + Enter navigation usable on a device with no soft keyboard.
 */
val MinTouchTarget = 48.dp

/** The height a settings row reaches at the least: a 48 dp target plus room to breathe. */
val RowMinHeight = 56.dp

/**
 * The screen chrome every hub and sub-screen shares (app-shell.md SS22.1, "the settings screens
 * share one top bar"): inset below the status bar and out of the cutout, the page's own
 * background so the cards below are the only raised surfaces, a back arrow with content
 * description "Back", the title in the mono headline style, and trailing actions in the same
 * full-contrast colour as the arrow. Content scrolls in a [LazyColumn] so a screen with more rows
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    containerColor = MaterialTheme.colorScheme.background,
                    // The default muted tint made "Add", "Import" and "Reset" look disabled next
                    // to the full-contrast back arrow.
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) { content() }
    }
}

/**
 * For a dialog whose body is a list or a grid (an app list, the speech engines, the character
 * picker): the platform's default dialog width is about 320 dp on the Titan, which wrapped every
 * row and clipped chip rows. Pass as `properties` together with [wideDialog] as the `modifier`.
 */
val WideDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

/** The width [WideDialogProperties] dialogs take: the screen less a 16 dp margin each side. */
fun Modifier.wideDialog(): Modifier = this.fillMaxWidth().padding(horizontal = 16.dp)

/**
 * A list of settings, drawn as rounded cards on the page background (app-shell.md SS22.1). Every
 * [LazyListScope.item] is a row inside a card; [SettingsListScope.header] ends the card above,
 * prints a section label and starts the next; [SettingsListScope.plainItem] is drawn full width
 * between cards (a preview, a horizontal carousel, a card of its own). Screens keep writing plain
 * `item {}` / `items()` calls: the scope records them and lays the cards out afterwards, once it
 * knows which row is the first and last of each group.
 */
@Composable
fun RowList(content: SettingsListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xl),
    ) {
        GroupingListScope().apply(content).emitInto(this)
    }
}

/** The receiver of [RowList]: a [LazyListScope] that also knows about section headers and full-width items. */
interface SettingsListScope : LazyListScope {
    /** A section label between two cards (settings-catalog.md SS9.2's bold labels: "Capitalization", "Advanced", ...). */
    fun header(text: String)

    /** An item drawn full width, outside any card. */
    fun plainItem(key: Any? = null, content: @Composable LazyItemScope.() -> Unit)
}

private class GroupingListScope : SettingsListScope {
    private sealed interface Entry

    private class Row(val key: Any?, val contentType: Any?, val content: @Composable LazyItemScope.() -> Unit) : Entry

    private class Rows(
        val count: Int,
        val key: ((Int) -> Any)?,
        val contentType: (Int) -> Any?,
        val content: @Composable LazyItemScope.(Int) -> Unit,
    ) : Entry

    private class Header(val text: String) : Entry

    private class Plain(val key: Any?, val content: @Composable LazyItemScope.() -> Unit) : Entry

    private val entries = mutableListOf<Entry>()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        entries += Row(key, contentType, content)
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        if (count > 0) entries += Rows(count, key, contentType, itemContent)
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) {
        entries += Plain(key) { content(0) }
    }

    override fun header(text: String) {
        entries += Header(text)
    }

    override fun plainItem(key: Any?, content: @Composable LazyItemScope.() -> Unit) {
        entries += Plain(key, content)
    }

    private fun isRow(entry: Entry?) = entry is Row || entry is Rows

    fun emitInto(scope: LazyListScope) {
        entries.forEachIndexed { index, entry ->
            val prev = entries.getOrNull(index - 1)
            val next = entries.getOrNull(index + 1)
            // A run of rows is one card: rounded where the run starts and ends. A header or a
            // full-width item on either side is what starts or ends the run.
            val startsCard = !isRow(prev)
            val endsCard = !isRow(next)
            // Below a header the label's own padding is the gap; elsewhere the card keeps its distance.
            val marginTop = if (prev is Header || prev == null) 0.dp else Spacing.m
            when (entry) {
                is Row -> scope.item(entry.key, entry.contentType) {
                    CardSegment(top = startsCard, bottom = endsCard, marginTop = marginTop) { entry.content(this@item) }
                }
                is Rows -> scope.items(entry.count, entry.key, entry.contentType) { i ->
                    CardSegment(top = startsCard && i == 0, bottom = endsCard && i == entry.count - 1, marginTop = marginTop) { entry.content(this@items, i) }
                }
                is Header -> scope.item(contentType = "header") { CardBreak(text = entry.text, first = prev == null) }
                is Plain -> scope.item(entry.key) { entry.content(this@item) }
            }
        }
    }
}

private val CardRadius = 16.dp

/** One row's slice of a card: square where it joins its neighbours, rounded (and padded) where the card starts or ends. */
@Composable
private fun CardSegment(top: Boolean, bottom: Boolean, marginTop: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = if (top) CardRadius else 0.dp,
        topEnd = if (top) CardRadius else 0.dp,
        bottomStart = if (bottom) CardRadius else 0.dp,
        bottomEnd = if (bottom) CardRadius else 0.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l)
            .padding(top = if (top) marginTop else 0.dp, bottom = if (bottom) Spacing.xs else 0.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(top = if (top) Spacing.xs else 0.dp, bottom = if (bottom) Spacing.xs else 0.dp),
    ) {
        // A header inside an "About" expander in this row is not a card break.
        CompositionLocalProvider(LocalInsideCard provides true) { content() }
    }
}

/** A section label between two cards. */
@Composable
private fun CardBreak(text: String, first: Boolean) {
    SectionLabel(text, modifier = Modifier.padding(start = Spacing.l + Spacing.xs, end = Spacing.l, top = if (first) Spacing.s else Spacing.xl, bottom = Spacing.s))
}

private val LocalInsideCard = staticCompositionLocalOf { false }

/**
 * A standalone rounded card for screens that lay out a plain [Column] rather than a [RowList]
 * (About, What's new, the setup pages). Same surface and corners as a [RowList] card; the caller's
 * column supplies the side margin.
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s)
            .clip(RoundedCornerShape(CardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = Spacing.xs),
    ) {
        CompositionLocalProvider(LocalInsideCard provides true) { content() }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = PhysiBoardType.sectionLabel,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

/**
 * spec: settings-catalog.md SS9.2, the section labels inside a hub screen. In a [RowList] use
 * [SettingsListScope.header] instead, which also splits the cards; this one is for a plain column
 * and for a sub-heading inside a card.
 */
@Composable
fun SectionHeader(text: String, inset: Boolean = true) {
    if (LocalInsideCard.current) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, top = Spacing.m, end = Spacing.l, bottom = Spacing.xs).semantics { heading() },
        )
    } else {
        // [inset] false: the caller's column already keeps the 16 dp side margin.
        val side = if (inset) Spacing.l else 0.dp
        SectionLabel(text, modifier = Modifier.padding(start = side + Spacing.xs, top = Spacing.l, end = side, bottom = Spacing.xs))
    }
}

@Composable
fun DividerLabel(text: String) = SectionHeader(text)

/** A short explanatory paragraph inside a card, in the description colour. */
@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}

/**
 * What a list shows when it has nothing in it yet: an icon and one line that says how to fill it
 * (app-shell.md SS22.1).
 */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KeycapIcon(icon, size = 56.dp, iconSize = 28.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

/**
 * The leading icon of a navigable row: a small rounded tile in the raised surface tone with the
 * glyph in the accent colour, so a column of them reads like a row of keycaps.
 */
@Composable
fun KeycapIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 36.dp, iconSize: Dp = 20.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** The label plus description block every row type shares, left of its control. */
@Composable
private fun RowLabel(label: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
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
            .defaultMinSize(minHeight = RowMinHeight)
            .clickableRow(enabled) { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The end gap keeps a long description from running up against the switch.
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.l)) {
            RowLabel(label, description)
            if (note != null) {
                val color = if (noteIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(note, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
        Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
    }
}

/**
 * A row that only navigates to another screen (the catalogue's "send to another screen" row):
 * a keycap icon, the label and description, and a chevron. [icon] is required in spirit: every
 * navigable row carries one (app-shell.md SS22.1); it is nullable only for rows whose label is
 * itself a glyph (a letter in Customize Variations).
 */
@Composable
fun NavigateRow(label: String, description: String? = null, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .clickableRow(enabled, onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            KeycapIcon(icon)
            Spacer(modifier = Modifier.width(Spacing.l))
        }
        RowLabel(label, description, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.s),
        )
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        RowLabel(label, description)
        // Wraps onto a second line rather than running off a narrow screen.
        FlowRow(
            modifier = Modifier.padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        RowLabel(label, description)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = Spacing.s)) {
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
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
                Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge)
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        RowLabel(label, description)
        var dragValue by remember(value) { mutableStateOf(value) }
        val shown = if (commitOnRelease) dragValue else value
        Text(valueLabel(shown), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = Spacing.xs))
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
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val error = validate?.invoke(value)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
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
            leadingIcon = leadingIcon,
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
        // A swatch of the stored colour, so the row shows the colour and not only its hex code.
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        },
    )
}

/** A row of buttons for a confirm/cancel action (the "Reset" and dialog rows the catalogue describes throughout). */
@Composable
fun ButtonRow(label: String, description: String? = null, buttonText: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = RowMinHeight).padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, description, modifier = Modifier.weight(1f).padding(end = Spacing.m))
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget)) { Text(buttonText) }
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
                .defaultMinSize(minHeight = RowMinHeight)
                .clickableRow(true) { expanded = !expanded }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) content()
    }
}

/**
 * The "About ..." pattern (Terminal mode's, commit 3940db42): a short line stays on screen and
 * the longer explanation waits behind a collapsed row, so the controls are near the top.
 */
@Composable
fun AboutExpander(title: String, text: String) {
    ExpandableSection(title = title) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m),
        )
    }
}

/**
 * A per-app switch list with a search field (rebuild-from-scratch.md: "a searchable app picker
 * over installed packages with a switch per app").
 *
 * The summary, the search field and the rows are one [RowList], so they scroll together and
 * the apps get the whole screen once the user scrolls: a long explanation pinned above the list
 * used to leave room for a single row on the Titan's 1200 px panel. The full explanation, when
 * the screen has one, sits collapsed behind [detailsTitle] instead of above the list.
 */
@Composable
fun AppPickerBody(
    apps: List<InstalledApp>,
    selected: Set<String>,
    // spec: per-app-behavior.md SS6.1, a one-line summary at the top of the list.
    summary: String? = null,
    // spec: SS6.1, the full explanation, collapsed under [detailsTitle] below the summary.
    details: String? = null,
    detailsTitle: String = "About this",
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
    RowList {
        if (summary != null || details != null) {
            item(key = "summary") {
                if (summary != null) InfoText(summary)
                if (details != null) AboutExpander(title = detailsTitle, text = details)
            }
        }
        plainItem(key = "search") {
            SearchPill(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search apps",
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
        }
        if (sorted.isEmpty()) {
            plainItem(key = "empty") {
                EmptyState(Icons.Filled.SearchOff, if (query.isBlank()) "No apps to show." else "No apps match “$query”.")
            }
        }
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
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
