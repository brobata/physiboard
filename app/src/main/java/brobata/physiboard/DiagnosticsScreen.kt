package brobata.physiboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import brobata.physiboard.inputmethod.DebugCaptureStore
import brobata.physiboard.inputmethod.DeviceSpecific
import brobata.physiboard.inputmethod.KeyboardEventTracker
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class RecordedKeyboardEvent(
    val wallTimestampMs: Long,
    val uptimeTimestampMs: Long?,
    val deltaMs: Long?,
    val event: KeyboardEventTracker.KeyEventInfo
)

private const val DEBUG_REPORT_TEXT_SHARE_MAX_EVENTS = 250
private const val DEBUG_REPORT_TEXT_SHARE_MAX_BYTES = 500 * 1024

/**
 * Diagnostics screen — the physical key-event logger and debug exporter.
 *
 * Relocated here from the home screen: recording, the ScanCode/KeyCode/Unicode/
 * Origin/Layout "Last Keyboard Event" panel, and Record/Clear/View/Share. It owns
 * the [KeyboardEventTracker] registration so live capture works while it is open.
 */
@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var testText by remember { mutableStateOf("") }
    val rawLastKeyEventState = remember { mutableStateOf<KeyboardEventTracker.KeyEventInfo?>(null) }
    val rawLastKeyEvent by rawLastKeyEventState
    var displayedLastKeyEvent by remember { mutableStateOf<KeyboardEventTracker.KeyEventInfo?>(null) }
    var lastNonBackKeyEvent by remember { mutableStateOf<KeyboardEventTracker.KeyEventInfo?>(null) }
    var ignoreKeyboardCloseBackEvent by rememberSaveable { mutableStateOf(true) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var recordStartedAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastRecordedAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var includeSuggestionsInExport by rememberSaveable { mutableStateOf(false) }
    var includeRawTrackpadInExport by rememberSaveable { mutableStateOf(false) }
    var showDebugReportViewer by rememberSaveable { mutableStateOf(false) }
    var latestDebugReport by rememberSaveable { mutableStateOf("") }
    val recordedEvents = remember { mutableStateListOf<RecordedKeyboardEvent>() }

    BackHandler { onBack() }

    // Connect state to the global tracker
    LaunchedEffect(Unit) {
        KeyboardEventTracker.registerState(rawLastKeyEventState)
    }

    // Recording follows the raw stream; display filtering is handled separately below.
    LaunchedEffect(rawLastKeyEvent) {
        val event = rawLastKeyEvent ?: return@LaunchedEffect
        if (event.keyCode != KeyEvent.KEYCODE_BACK) {
            lastNonBackKeyEvent = event
        }
        if (isRecording) {
            val nowWall = System.currentTimeMillis()
            val nowUptime = SystemClock.uptimeMillis()
            val eventUptime = event.eventTimeUptimeMs.takeIf { it > 0L }
            val wallTimestampMs = eventUptime?.let { nowWall - (nowUptime - it) } ?: nowWall
            val deltaMs = lastRecordedAtMs?.let { previous -> (wallTimestampMs - previous).coerceAtLeast(0L) }
            recordedEvents.add(
                RecordedKeyboardEvent(
                    wallTimestampMs = wallTimestampMs,
                    uptimeTimestampMs = eventUptime,
                    deltaMs = deltaMs,
                    event = event
                )
            )
            lastRecordedAtMs = wallTimestampMs
        }
    }

    // Keep the latest useful debug event visible when keyboard close/navigation emits BACK.
    LaunchedEffect(rawLastKeyEvent, ignoreKeyboardCloseBackEvent, lastNonBackKeyEvent) {
        val event = rawLastKeyEvent
        displayedLastKeyEvent = when {
            event == null -> null
            ignoreKeyboardCloseBackEvent && event.keyCode == KeyEvent.KEYCODE_BACK -> lastNonBackKeyEvent
            else -> event
        }
    }

    // Clear state when the composable is removed
    DisposableEffect(Unit) {
        onDispose {
            KeyboardEventTracker.unregisterState()
        }
    }

    val buildCurrentReport: () -> String = {
        buildKeyboardDebugReport(
            context = context,
            recordStartedAtMs = recordStartedAtMs,
            events = recordedEvents,
            includeSuggestions = includeSuggestionsInExport,
            includeRawTrackpad = includeRawTrackpadInExport
        )
    }
    val copyReportToClipboard: (String) -> Unit = { report ->
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("pastiera-keyboard-debug", report)
        )
        Toast.makeText(
            context,
            context.getString(R.string.debug_recorder_copied, recordedEvents.size),
            Toast.LENGTH_SHORT
        ).show()
    }
    val shareReport: (String) -> Unit = { report ->
        val shouldShareAsFile = includeRawTrackpadInExport ||
            recordedEvents.size > DEBUG_REPORT_TEXT_SHARE_MAX_EVENTS ||
            report.toByteArray(Charsets.UTF_8).size > DEBUG_REPORT_TEXT_SHARE_MAX_BYTES
        val shareIntent = if (shouldShareAsFile) {
            createDebugReportFileShareIntent(context, report)
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Pastiera Keyboard Debug Export")
                putExtra(Intent.EXTRA_TEXT, report)
            }
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.debug_recorder_share_chooser))
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Test field
            OutlinedTextField(
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.test_field_placeholder)) },
                minLines = 1,
                maxLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            // Recorder controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isRecording) {
                                        isRecording = false
                                    } else {
                                        recordedEvents.clear()
                                        val startTime = System.currentTimeMillis()
                                        recordStartedAtMs = startTime
                                        lastRecordedAtMs = null
                                        isRecording = true
                                    }
                                }
                            ) {
                                Text(
                                    if (isRecording) {
                                        stringResource(R.string.debug_recorder_stop)
                                    } else {
                                        stringResource(R.string.debug_recorder_record)
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    recordedEvents.clear()
                                    DebugCaptureStore.clearAll()
                                    recordStartedAtMs = null
                                    lastRecordedAtMs = null
                                    isRecording = false
                                    showDebugReportViewer = false
                                    latestDebugReport = ""
                                    displayedLastKeyEvent = null
                                }
                            ) {
                                Text(stringResource(R.string.debug_recorder_clear))
                            }
                            OutlinedButton(
                                onClick = {
                                    latestDebugReport = buildCurrentReport()
                                    showDebugReportViewer = true
                                }
                            ) {
                                Text(stringResource(R.string.debug_recorder_view))
                            }
                            OutlinedButton(
                                onClick = {
                                    val report = buildCurrentReport()
                                    shareReport(report)
                                }
                            ) {
                                Text(stringResource(R.string.debug_recorder_share))
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = context.getString(
                                    R.string.debug_recorder_status,
                                    if (isRecording) context.getString(R.string.debug_recorder_status_recording)
                                    else context.getString(R.string.debug_recorder_status_stopped),
                                    recordedEvents.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${stringResource(R.string.debug_recorder_started_at)}${
                                    recordStartedAtMs?.let { formatDebugTimestamp(it) } ?: "n/a"
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.width(140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = includeSuggestionsInExport,
                            onClick = {
                                includeSuggestionsInExport = !includeSuggestionsInExport
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.debug_recorder_include_suggestions),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = includeRawTrackpadInExport,
                            onClick = {
                                includeRawTrackpadInExport = !includeRawTrackpadInExport
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.debug_recorder_include_raw_trackpad),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Last keyboard event
            val event = displayedLastKeyEvent
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = event?.keyCodeName ?: "n/a",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${stringResource(R.string.event_action_label)}${event?.action ?: "n/a"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${stringResource(R.string.event_keycode_label)}${event?.keyCode?.toString() ?: "n/a"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Origin: ${event?.origin ?: "n/a"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Layout: ${event?.resolvedLayout ?: "n/a"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${stringResource(R.string.event_scancode_label)}${event?.scanCode?.toString() ?: "n/a"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${stringResource(R.string.event_unicode_label)}${
                                    event?.let {
                                        "raw=${formatUnicodeForDebug(it.rawUnicodeChar)} effective=${formatUnicodeForDebug(it.effectiveUnicodeChar)}"
                                    } ?: "n/a"
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${stringResource(R.string.event_output_label)}${
                                    event?.outputKeyCodeName?.let {
                                        "$it${event.outputKeyCode?.let { code -> " ($code)" } ?: ""}"
                                    } ?: "n/a"
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (event?.outputKeyCodeName != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.last_keyboard_event_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = ignoreKeyboardCloseBackEvent,
                            onClick = { ignoreKeyboardCloseBackEvent = !ignoreKeyboardCloseBackEvent },
                            label = {
                                Text(
                                    text = stringResource(R.string.ignore_back_short),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }

                    if (event?.let { it.isShiftPressed || it.isCtrlPressed || it.isAltPressed } == true) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (event.isShiftPressed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.modifier_shift),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (event.isCtrlPressed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.modifier_ctrl),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (event.isAltPressed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.modifier_alt),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showDebugReportViewer) {
                DebugReportViewerDialog(
                    report = latestDebugReport,
                    onDismiss = { showDebugReportViewer = false },
                    onCopy = { copyReportToClipboard(latestDebugReport) },
                    onShare = { shareReport(latestDebugReport) }
                )
            }
        }
    }
}

@Composable
private fun DebugReportViewerDialog(
    report: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val lines = remember(report) { report.lines() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.debug_report_viewer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onCopy) {
                        Text(stringResource(R.string.debug_recorder_copy))
                    }
                    OutlinedButton(onClick = onShare) {
                        Text(stringResource(R.string.debug_recorder_share))
                    }
                }
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scroll)
                    ) {
                        lines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = debugReportLineColor(line, colorScheme)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun debugReportLineColor(line: String, colors: ColorScheme): Color {
    val trimmed = line.trim()
    return when {
        trimmed.startsWith("===") -> colors.primary
        trimmed.startsWith("[") && trimmed.endsWith("]") -> colors.tertiary
        trimmed.startsWith("sha256=") -> colors.primary
        trimmed.startsWith("(no ") || trimmed == "n/a" || trimmed.isEmpty() -> colors.onSurfaceVariant
        " | " in line -> colors.onSurface
        "=" in line -> colors.secondary
        else -> colors.onSurfaceVariant
    }
}

private fun formatDebugTimestamp(timestampMs: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    formatter.timeZone = TimeZone.getDefault()
    return formatter.format(Date(timestampMs))
}

private fun createDebugReportFileShareIntent(context: Context, report: String): Intent {
    val reportDir = File(context.cacheDir, "debug-reports").apply {
        deleteRecursively()
        mkdirs()
    }
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val reportFile = File(reportDir, "pastiera-keyboard-debug-$timestamp.txt")
    reportFile.writeText(report, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        reportFile
    )
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Pastiera Keyboard Debug Export")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Pastiera Keyboard Debug Export", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun formatUnicodeForDebug(unicode: Int): String {
    if (unicode == 0) {
        return "0(n/a)"
    }
    val char = unicode.toChar()
    val printable = if (char.isISOControl()) "\\u%04X".format(unicode) else char.toString()
    return "$unicode('$printable')"
}

private fun flattenPreferenceValue(value: Any?): String {
    return when (value) {
        null -> "null"
        is Set<*> -> value.map { it.toString() }.sorted().joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }
}

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { b -> "%02x".format(b) }
}

private fun formatHexInt(value: Int): String = "0x${value.toUInt().toString(16)}"

private data class KeyboardInputDeviceDebugInfo(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val keyboardType: Int,
    val sources: Int,
    val isExternal: Boolean,
    val isVirtual: Boolean
)

private fun keyboardInputDevicesSnapshot(): List<KeyboardInputDeviceDebugInfo> {
    val rows = mutableListOf<KeyboardInputDeviceDebugInfo>()
    val deviceIds: IntArray = InputDevice.getDeviceIds()
    for (id in deviceIds) {
        val device: InputDevice = InputDevice.getDevice(id) ?: continue
        val hasKeyboardSource =
            (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
        val hasKeyboardType = device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
        if (!hasKeyboardSource && !hasKeyboardType) {
            continue
        }
        rows.add(
            KeyboardInputDeviceDebugInfo(
                id = device.id,
                name = device.name ?: "unknown",
                descriptor = device.descriptor ?: "unknown",
                vendorId = device.vendorId,
                productId = device.productId,
                keyboardType = device.keyboardType,
                sources = device.sources,
                isExternal = device.isExternal,
                isVirtual = device.isVirtual
            )
        )
    }
    return rows
}

private data class SuggestionsExportRow(
    val timestampMs: Long,
    val entries: List<DebugCaptureStore.SuggestionEntry>,
    val repeatCount: Int
)

private fun buildFilteredSuggestionRows(
    snapshots: List<DebugCaptureStore.SuggestionsSnapshot>
): List<SuggestionsExportRow> {
    val nonEmpty = snapshots.filter { it.entries.isNotEmpty() }
    if (nonEmpty.isEmpty()) return emptyList()
    val rows = mutableListOf<SuggestionsExportRow>()
    nonEmpty.forEach { snapshot ->
        val last = rows.lastOrNull()
        if (last != null && last.entries == snapshot.entries) {
            rows[rows.lastIndex] = last.copy(
                timestampMs = snapshot.timestampMs,
                repeatCount = last.repeatCount + 1
            )
        } else {
            rows.add(
                SuggestionsExportRow(
                    timestampMs = snapshot.timestampMs,
                    entries = snapshot.entries,
                    repeatCount = 1
                )
            )
        }
    }
    return rows
}

private fun formatNullable(value: Boolean?): String = value?.toString() ?: "n/a"

private fun buildKeyboardDebugReport(
    context: Context,
    recordStartedAtMs: Long?,
    events: List<RecordedKeyboardEvent>,
    includeSuggestions: Boolean,
    includeRawTrackpad: Boolean
): String {
    val nowMs = System.currentTimeMillis()
    val tz = TimeZone.getDefault()
    val prefs = SettingsManager.getPreferences(context)
    val imeContext = DebugCaptureStore.imeContextSnapshot()
    val autoCorrections = DebugCaptureStore.autoCorrectionsSnapshot()
    val suggestionsSnapshots = if (includeSuggestions) DebugCaptureStore.suggestionsSnapshot() else emptyList()
    val rawTrackpadEvents = if (includeRawTrackpad) DebugCaptureStore.rawTrackpadEventsSnapshot() else emptyList()
    val filteredSuggestionsRows = if (includeSuggestions) buildFilteredSuggestionRows(suggestionsSnapshots) else emptyList()
    val suggestionFilterMode = "empty_hidden,dedupe_consecutive"
    val inputDevices = keyboardInputDevicesSnapshot()
    val resolvedPhysicalProfile = DeviceSpecific.physicalKeyboardName()
    val settingsDump = prefs.all.entries
        .sortedBy { it.key }
        .joinToString("\n") { (key, value) -> "$key=${flattenPreferenceValue(value)}" }

    val header = buildString {
        appendLine("=== Pastiera Keyboard Debug Export ===")
        appendLine("exported_at=${formatDebugTimestamp(nowMs)}")
        appendLine("timezone_id=${tz.id}")
        appendLine("timezone_offset=${tz.getOffset(nowMs) / 1000}s")
        appendLine()
        appendLine("[system]")
        appendLine("android_release=${Build.VERSION.RELEASE ?: "unknown"}")
        appendLine("android_sdk=${Build.VERSION.SDK_INT}")
        appendLine("android_incremental=${Build.VERSION.INCREMENTAL ?: "unknown"}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            appendLine("android_security_patch=${Build.VERSION.SECURITY_PATCH}")
        }
        appendLine()
        appendLine("[app]")
        appendLine("package=${context.packageName}")
        appendLine("version_name=${BuildConfig.VERSION_NAME}")
        appendLine("version_code=${BuildConfig.VERSION_CODE}")
        appendLine("build_type=${BuildConfig.BUILD_TYPE}")
        appendLine("release_channel=${BuildConfig.RELEASE_CHANNEL}")
        appendLine()
        appendLine("[device]")
        appendLine("brand=${Build.BRAND}")
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("model=${Build.MODEL}")
        appendLine("device=${Build.DEVICE}")
        appendLine("product=${Build.PRODUCT}")
        appendLine("fingerprint=${Build.FINGERPRINT}")
        appendLine("hardware=${Build.HARDWARE}")
        appendLine("board=${Build.BOARD}")
        appendLine("bootloader=${Build.BOOTLOADER}")
        appendLine("build_display=${Build.DISPLAY}")
        appendLine("build_id=${Build.ID}")
        appendLine("build_tags=${Build.TAGS}")
        appendLine("build_type=${Build.TYPE}")
        appendLine("supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendLine("sku=${Build.SKU}")
            appendLine("odm_sku=${Build.ODM_SKU}")
            appendLine("soc_manufacturer=${Build.SOC_MANUFACTURER}")
        appendLine("soc_model=${Build.SOC_MODEL}")
        } else {
            appendLine("sku=n/a")
            appendLine("odm_sku=n/a")
            appendLine("soc_manufacturer=n/a")
            appendLine("soc_model=n/a")
        }
        appendLine("physical_keyboard_name=${DeviceSpecific.physicalKeyboardName()}")
        appendLine("keyboard_family=${DeviceSpecific.keyboardName()}")
        appendLine("profile_override=${SettingsManager.getPhysicalKeyboardProfileOverride(context)}")
        appendLine("resolved_physical_profile=$resolvedPhysicalProfile")
        appendLine()
        appendLine("[input_devices]")
        if (inputDevices.isEmpty()) {
            appendLine("(no keyboard-like input devices found)")
        } else {
            inputDevices.forEach { device ->
                appendLine(
                    "id=${device.id} name='${device.name}' descriptor='${device.descriptor}' " +
                        "vendor_id=${device.vendorId} product_id=${device.productId} " +
                        "keyboard_type=${device.keyboardType} sources=${device.sources} " +
                        "sources_hex=${formatHexInt(device.sources)} " +
                        "external=${device.isExternal} virtual=${device.isVirtual}"
                )
            }
        }
        appendLine()
        appendLine("[recording]")
        appendLine("started_at=${recordStartedAtMs?.let { formatDebugTimestamp(it) } ?: "n/a"}")
        appendLine("event_count=${events.size}")
        appendLine("include_suggestions=$includeSuggestions")
        appendLine("include_raw_trackpad=$includeRawTrackpad")
        appendLine("suggestions_filter=${if (includeSuggestions) suggestionFilterMode else "disabled"}")
        appendLine("attempt_logging_supported=true")
        appendLine()
        appendLine("[ime_context]")
        appendLine("captured_at=${imeContext?.timestampMs?.let { formatDebugTimestamp(it) } ?: "n/a"}")
        appendLine("target_package=${imeContext?.packageName ?: "n/a"}")
        appendLine("input_type=${imeContext?.inputType?.toString() ?: "n/a"}")
        appendLine("subtype_locale=${imeContext?.subtypeLocale ?: "n/a"}")
        appendLine("resolved_layout=${imeContext?.resolvedLayout ?: "n/a"}")
        appendLine("profile_override_snapshot=${imeContext?.physicalProfileOverride ?: "n/a"}")
        appendLine("resolved_physical_profile_snapshot=$resolvedPhysicalProfile")
        appendLine()
        appendLine("[settings_snapshot]")
        appendLine(settingsDump.ifBlank { "(empty)" })
        appendLine("resolved_mid_word_quote_to_apostrophe=${SettingsManager.getMidWordQuoteToApostrophe(context)}")
        appendLine("resolved_french_punctuation_spacing=${SettingsManager.getFrenchPunctuationSpacing(context)}")
        appendLine("resolved_comma_space=${SettingsManager.getCommaSpace(context)}")
        appendLine("resolved_auto_space_punctuation=${SettingsManager.getAutoSpacePunctuation(context)}")
        appendLine("resolved_space_after_punctuation=${SettingsManager.getSpaceAfterPunctuation(context)}")
        appendLine()
        appendLine("[autocorrections]")
        if (autoCorrections.isEmpty()) {
            appendLine("(no autocorrections recorded)")
        } else {
            autoCorrections.forEach { entry ->
                val details = buildString {
                    entry.distance?.let { append(" distance=$it") }
                    entry.kind?.let { append(" kind=$it") }
                }
                appendLine(
                    "${formatDebugTimestamp(entry.timestampMs)} | type=${entry.type} trigger=${entry.trigger} " +
                        "source=${entry.source} outcome=${entry.outcome} before='${entry.before}' " +
                        "after='${entry.after ?: "n/a"}' reason='${entry.reason ?: "n/a"}'$details"
                )
            }
        }
        if (includeSuggestions) {
            appendLine()
            appendLine("[suggestions]")
            if (filteredSuggestionsRows.isEmpty()) {
                appendLine("(no suggestion snapshots recorded)")
            } else {
                filteredSuggestionsRows.forEach { snapshot ->
                    val repeatSuffix = if (snapshot.repeatCount > 1) " x${snapshot.repeatCount}" else ""
                    appendLine(
                        "${formatDebugTimestamp(snapshot.timestampMs)} | " +
                            snapshot.entries.joinToString(", ") { "${it.candidate}{${it.source}/${it.kind}}" } +
                            repeatSuffix
                    )
                }
            }
        }
        if (includeRawTrackpad) {
            appendLine()
            appendLine("[raw_trackpad]")
            if (rawTrackpadEvents.isEmpty()) {
                appendLine("(no raw trackpad events recorded)")
            } else {
                rawTrackpadEvents.forEach { event ->
                    appendLine(
                        "${formatDebugTimestamp(event.timestampMs)} | provider=${event.provider} " +
                            "origin=${event.origin} phase=${event.phase} action=${event.action} " +
                            "outcome=${event.outcome} start=(${event.startX ?: "n/a"},${event.startY ?: "n/a"}) " +
                            "xy=(${event.x ?: "n/a"},${event.y ?: "n/a"}) " +
                            "delta=(${event.deltaX ?: "n/a"},${event.deltaY ?: "n/a"}) " +
                            "threshold=${event.threshold ?: "n/a"} deviceId=${event.deviceId} " +
                            "source=${event.source} sourceHex=${formatHexInt(event.source)} " +
                            "eventUptimeMs=${event.eventTimeUptimeMs}"
                    )
                }
            }
        }
        appendLine()
        appendLine("[events]")
    }

    val eventLines = if (events.isEmpty()) {
        listOf("(no recorded events)")
    } else {
        events.map { entry ->
            val event = entry.event
            val deltaPart = entry.deltaMs?.let { "+${it}ms" } ?: "start"
            val outputPart = if (event.outputKeyCodeName != null) {
                " output=${event.outputKeyCodeName}${event.outputKeyCode?.let { "($it)" } ?: ""}"
            } else {
                ""
            }
            "${formatDebugTimestamp(entry.wallTimestampMs)} | $deltaPart | ${event.action} | " +
                "origin=${event.origin} " +
                "key=${event.keyCodeName}(${event.keyCode}) scan=${event.scanCode} " +
                "deviceId=${event.deviceId} source=${event.source} " +
                "flags=${event.flags} repeat=${event.repeatCount} " +
                "meta=${event.metaState}(${formatHexInt(event.metaState)}) " +
                "unicode_raw=${formatUnicodeForDebug(event.rawUnicodeChar)} " +
                "unicode_effective=${formatUnicodeForDebug(event.effectiveUnicodeChar)} " +
                "alt=${event.isAltPressed} " +
                "shift=${event.isShiftPressed} ctrl=${event.isCtrlPressed} " +
                "altLatch=${formatNullable(event.altLatchActive)} altOneShot=${formatNullable(event.altOneShot)} " +
                "shiftLatch=${formatNullable(event.shiftLatchActive)} ctrlLatch=${formatNullable(event.ctrlLatchActive)} " +
                "symPage=${event.symPage?.toString() ?: "n/a"} layout=${event.resolvedLayout ?: "n/a"} " +
                "eventUptimeMs=${entry.uptimeTimestampMs?.toString() ?: "n/a"} " +
                "sourceHex=${formatHexInt(event.source)} flagsHex=${formatHexInt(event.flags)}$outputPart"
        }
    }.joinToString("\n")

    val payload = "$header$eventLines\n"
    val hash = sha256Hex(payload)
    return payload + "\nsha256=$hash\n"
}
