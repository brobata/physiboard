package brobata.physiboard.app.settings.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.shell.AppDebugCaptureStore
import brobata.physiboard.app.shell.ImeComponent
import brobata.physiboard.app.shell.ImeProbeAndroid
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.shell.DebugExportPolicy
import brobata.physiboard.core.shell.DebugShareMethod
import brobata.physiboard.core.shell.DiagnosticsReport
import brobata.physiboard.core.shell.ImeContextSnapshot
import brobata.physiboard.core.shell.KeyboardEventExport
import brobata.physiboard.core.shell.KeyboardEventRecord
import brobata.physiboard.core.shell.KeyboardEventRecording
import brobata.physiboard.core.shell.RecordedKeyboardEvent
import brobata.physiboard.core.shell.ReportSection
import brobata.physiboard.core.shell.SettingsSnapshotExport
import brobata.physiboard.core.shell.SuggestionExport
import brobata.physiboard.device.privileged.PrivilegedExport
import brobata.physiboard.device.privileged.PrivilegedExportFacts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Diagnostics screen (app-shell.md SS10): the bug-report pipeline for a build with stripped
 * release logging. The text field exists only to give the keyboard a place to attach so key
 * events flow; nothing here reads what is typed into it.
 *
 * Registers as the store's one [brobata.physiboard.core.shell.KeyboardEventListener] while this
 * screen is on screen (SS10.2: "nothing is recorded while the screen is not open; leaving it
 * unregisters"), drives the "Last Keyboard Event" panel from every event it sees, and appends to
 * the recorded list only while "Record" is on (SS10.4). The recorded list is this screen's own
 * state, not the capture store's: section 11's buffer table has no keyboard-event buffer, unlike
 * autocorrections, suggestions and raw trackpad.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    val store = AppDebugCaptureStore.instance

    var fieldText by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var lastRecordedAtMs by remember { mutableStateOf<Long?>(null) }
    val recordedEvents = remember { mutableStateListOf<RecordedKeyboardEvent>() }
    var displayedEvent by remember { mutableStateOf<KeyboardEventRecord?>(null) }
    var ignoreBack by remember { mutableStateOf(true) } // spec SS10.3: "on by default"
    var includeSuggestions by remember { mutableStateOf(false) }
    var includeRawTrackpad by remember { mutableStateOf(false) }
    var includeAutocorrections by remember { mutableStateOf(false) }
    var viewerText by remember { mutableStateOf<String?>(null) }

    // spec SS10.2: "it registers as the ONE listener for key events reported by the keyboard
    // service... leaving it unregisters." "Leaving" means the screen is no longer the thing on
    // screen, not merely still alive in composition: a composable stays composed while the whole
    // Settings app is backgrounded (Home pressed without navigating away), and a capture left
    // registered through that would keep recording whatever the user types into other apps,
    // passwords included, until Settings is foregrounded again. Registration is therefore tied to
    // the lifecycle's foreground state (ON_RESUME/ON_PAUSE), not just to composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val listener: (KeyboardEventRecord) -> Unit = { event ->
            displayedEvent = KeyboardEventRecording.displayedEvent(displayedEvent, event, ignoreBack)
            if (recording) {
                val atMs = KeyboardEventRecording.wallClockAtMs(System.currentTimeMillis(), SystemClock.uptimeMillis(), event.eventUptimeMs)
                val delta = KeyboardEventRecording.deltaMs(lastRecordedAtMs, atMs)
                recordedEvents.add(RecordedKeyboardEvent(atMs, delta, event))
                lastRecordedAtMs = atMs
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> store.setKeyboardEventListener(listener)
                Lifecycle.Event.ON_PAUSE -> store.setKeyboardEventListener(null)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            store.setKeyboardEventListener(null)
        }
    }

    fun buildReport(): String {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        val now = Date()
        val sections = mutableListOf<ReportSection>()
        sections += ReportSection(
            "system",
            listOf(
                "android_release=${Build.VERSION.RELEASE}",
                "android_sdk=${Build.VERSION.SDK_INT}",
                "android_incremental=${Build.VERSION.INCREMENTAL}",
                "android_security_patch=${Build.VERSION.SECURITY_PATCH}",
            ),
        )
        sections += ReportSection(
            "app",
            listOf(
                "package=${context.packageName}",
                "version_name=${BuildConfig.VERSION_NAME}",
                "version_code=${BuildConfig.VERSION_CODE}",
                "build_type=${BuildConfig.BUILD_TYPE}",
            ),
        )
        sections += ReportSection("device", deviceLines())
        sections += ReportSection("input_devices", inputDeviceLines(context))
        sections += ReportSection(
            "recording",
            listOf(
                "started_at=${startedAt?.let { timeFormat.format(Date(it)) } ?: "n/a"}",
                "event_count=${recordedEvents.size}",
                "include_suggestions=$includeSuggestions",
                "include_raw_trackpad=$includeRawTrackpad",
                "include_autocorrections=$includeAutocorrections",
                "suggestions_filter=${if (includeSuggestions) "empty_hidden,dedupe_consecutive" else "disabled"}",
                "attempt_logging_supported=true",
            ),
        )
        sections += ReportSection("ime_context", imeContextLines(store.lastField(), store.lastFieldFromAnotherApp(), timeFormat))
        sections += ReportSection("settings_snapshot", SettingsSnapshotExport.lines(SettingsCodec.toMap(controller.current.value)))
        // spec: broker-privileged-toolbox.md SS8 ("The debug export's `[privileged]` section").
        run {
            val application = context.applicationContext as PhysiBoardApplication
            val privileged = application.privileged
            val settings = controller.current.value
            val probe = ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)
            val facts = PrivilegedExportFacts(
                brokerPaired = privileged.broker.isPaired(),
                wirelessDebuggingEnabled = privileged.broker.isWirelessDebuggingOn(),
                brokerBlocker = privileged.broker.blocker()?.reason,
                backlightEnabled = settings.device.smartBacklightEnabled,
                backlightAppliedFlag = settings.captures.smartBacklightApplied,
                overlayPermissionGranted = privileged.permissions.canDrawOverlays(),
                notificationListenerGranted = privileged.permissions.isNotificationListenerGranted(),
                notificationRingEnabled = settings.device.ringEnabled,
                screenTrackpadEnabled = settings.trackpad.enabled,
                trackpadProvider = if (settings.trackpad.enabled) "screen_trackpad" else "none",
                imeEnabled = probe.enabled,
                imeSelected = probe.selected,
            )
            val timestamped: (Long) -> String = { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(it)) }
            sections += ReportSection("privileged", PrivilegedExport.lines(facts, privileged.diagnostics, timestamped))
        }
        if (includeAutocorrections) {
            val rows = store.autocorrections()
            sections += ReportSection(
                "autocorrections",
                if (rows.isEmpty()) listOf("(no autocorrections recorded)") else rows.map { "${timeFormat.format(Date(it.atMs))} | type=${it.type} trigger=${it.trigger} source=${it.source} outcome=${it.outcome} before='${it.before}' after='${it.after}' reason='${it.reason}'" },
            )
        } else {
            sections += ReportSection("autocorrections", listOf("(excluded - contains typed words; enable \"Include autocorrections\" to share)"))
        }
        if (includeSuggestions) {
            val collapsed = SuggestionExport.collapse(store.suggestionSnapshots())
            sections += ReportSection(
                "suggestions",
                if (collapsed.isEmpty()) listOf("(no suggestion snapshots recorded)") else collapsed.map { row ->
                    val candidates = row.candidates.joinToString(",") { "cand{$it}" }
                    if (row.repeatCount > 1) "${timeFormat.format(Date(row.atMs))} | $candidates x${row.repeatCount}" else "${timeFormat.format(Date(row.atMs))} | $candidates"
                },
            )
        }
        if (includeRawTrackpad) {
            val rows = store.rawTrackpadEvents()
            sections += ReportSection("raw_trackpad", if (rows.isEmpty()) listOf("(no raw trackpad events recorded)") else rows.map { "${timeFormat.format(Date(it.atMs))} | ${it.line}" })
        }
        sections += ReportSection(
            "events",
            if (recordedEvents.isEmpty()) listOf("(no recorded events)") else recordedEvents.mapIndexed { index, recorded ->
                KeyboardEventExport.eventLine(recorded, timeFormat.format(Date(recorded.atMs)), isFirst = index == 0)
            },
        )

        return DiagnosticsReport.assemble(
            exportedAt = timeFormat.format(now),
            timezoneId = TimeZone.getDefault().id,
            timezoneOffsetSeconds = TimeZone.getDefault().getOffset(now.time) / 1000L,
            sections = sections,
        )
    }

    SettingsScreenScaffold(title = "Diagnostics", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(
                value = fieldText,
                onValueChange = { fieldText = it },
                placeholder = { Text("Type here with the physical keyboard…") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 2,
            )

            Row(modifier = Modifier.padding(top = 12.dp)) {
                Button(onClick = {
                    if (recording) {
                        recording = false
                    } else {
                        store.clear()
                        recordedEvents.clear()
                        lastRecordedAtMs = null
                        startedAt = System.currentTimeMillis()
                        recording = true
                    }
                }) { Text(if (recording) "Stop" else "Record") }
                TextButton(onClick = {
                    store.clear()
                    recordedEvents.clear()
                    lastRecordedAtMs = null
                    startedAt = null
                    recording = false
                    displayedEvent = null
                    viewerText = null
                }) { Text("Clear") }
                TextButton(onClick = { viewerText = buildReport() }) { Text("View") }
                TextButton(onClick = {
                    val report = buildReport()
                    val method = DebugExportPolicy.shareMethod(recordedEvents.size, includeRawTrackpad, report.toByteArray(Charsets.UTF_8).size)
                    shareReport(context, report, method)
                }) { Text("Share") }
            }

            Text(
                "Recorder: ${if (recording) "recording" else "stopped"} · Events: ${recordedEvents.size}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Started at: ${startedAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it)) } ?: "n/a"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            // A plain Row squeezed the third chip into the width that was left, which on the
            // Titan's 1080-wide screen wrapped "incl. autocorrections" to two characters a line
            // down a column the height of the screen (2026-09-29). These wrap onto a second row.
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = includeSuggestions, onClick = { includeSuggestions = !includeSuggestions }, label = { Text("incl. suggestions") })
                FilterChip(selected = includeRawTrackpad, onClick = { includeRawTrackpad = !includeRawTrackpad }, label = { Text("incl. raw trackpad") })
                FilterChip(selected = includeAutocorrections, onClick = { includeAutocorrections = !includeAutocorrections }, label = { Text("incl. autocorrections") })
            }

            LastKeyboardEventPanel(
                event = displayedEvent,
                ignoreBack = ignoreBack,
                onIgnoreBackChanged = { ignoreBack = it },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    val report = viewerText
    if (report != null) {
        AlertDialog(
            onDismissRequest = { viewerText = null },
            title = { Text("Debug Report Viewer") },
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("physiboard-keyboard-debug", report))
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { viewerText = null }) { Text("Close") } },
        )
    }
}

/** spec SS10.1 "5.", SS10.3: the two-column panel plus its modifier chips and the "Ignore BACK" control. */
@Composable
private fun LastKeyboardEventPanel(
    event: KeyboardEventRecord?,
    ignoreBack: Boolean,
    onIgnoreBackChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text("Last Keyboard Event", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                    val left = event?.let { KeyboardEventExport.leftColumn(it) } ?: listOf("n/a")
                    for (line in left) Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    val right = event?.let { KeyboardEventExport.rightColumn(it) } ?: listOf("n/a")
                    for (line in right) Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
            val chips = event?.let { KeyboardEventExport.modifierChips(it) } ?: emptyList()
            if (chips.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    chips.forEachIndexed { index, chip ->
                        FilterChip(selected = true, onClick = {}, label = { Text(chip) }, modifier = if (index == 0) Modifier else Modifier.padding(start = 8.dp))
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                // spec SS10.3: "on by default"; a BACK event does not replace the panel while this is on.
                FilterChip(selected = ignoreBack, onClick = { onIgnoreBackChanged(!ignoreBack) }, label = { Text("Ignore BACK") })
            }
        }
    }
}

/** spec SS10.6 `[device]`: every field the report lists, in order. */
private fun deviceLines(): List<String> {
    val soc = if (Build.VERSION.SDK_INT >= 31) runCatching { Build.SOC_MODEL }.getOrDefault("n/a") else "n/a"
    val socManufacturer = if (Build.VERSION.SDK_INT >= 31) runCatching { Build.SOC_MANUFACTURER }.getOrDefault("n/a") else "n/a"
    val sku = runCatching { Build.SKU }.getOrDefault("n/a")
    val odmSku = runCatching { Build.ODM_SKU }.getOrDefault("n/a")
    val isUnihertz = Build.BRAND.lowercase().contains("unihertz") || Build.MANUFACTURER.lowercase().contains("unihertz")
    return listOf(
        "brand=${Build.BRAND}",
        "manufacturer=${Build.MANUFACTURER}",
        "model=${Build.MODEL}",
        "device=${Build.DEVICE}",
        "product=${Build.PRODUCT}",
        "fingerprint=${Build.FINGERPRINT}",
        "hardware=${Build.HARDWARE}",
        "board=${Build.BOARD}",
        "bootloader=${Build.BOOTLOADER}",
        "build_display=${Build.DISPLAY}",
        "build_id=${Build.ID}",
        "build_tags=${Build.TAGS}",
        "build_type=${Build.TYPE}",
        "supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}",
        "sku=$sku",
        "odm_sku=$odmSku",
        "soc_manufacturer=$socManufacturer",
        "soc_model=$soc",
        "physical_keyboard_name=${physicalKeyboardName() ?: "n/a"}",
        "keyboard_family=${if (isUnihertz) "Unihertz" else "unknown"}",
        // spec SS27 D3, keys-and-modifiers.md: 3.0 ships one physical profile, so there is no
        // override to read (settings-catalog.md SS2.3's row is dropped, see core/settings/Settings.kt).
        "profile_override=auto",
        "resolved_physical_profile=titan2elite_qwerty",
    )
}

/** spec SS10.6 `[input_devices]`: one line per input device carrying the keyboard source or a keyboard type. */
private fun inputDeviceLines(context: Context): List<String> {
    val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    val ids = (manager?.inputDeviceIds ?: InputDevice.getDeviceIds()).toList()
    val lines = ids.mapNotNull { id ->
        val device = InputDevice.getDevice(id) ?: return@mapNotNull null
        val isKeyboardLike = (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
            device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
        if (!isKeyboardLike) return@mapNotNull null
        "id=${device.id} name='${device.name}' descriptor=${device.descriptor} vendor_id=${device.vendorId} " +
            "product_id=${device.productId} keyboard_type=${device.keyboardType} sources=${device.sources} " +
            "sources_hex=0x${device.sources.toString(16)} external=${device.isExternal} virtual=${device.isVirtual}"
    }
    return lines.ifEmpty { listOf("(no keyboard-like input devices found)") }
}

/** spec SS10.7: the physical keyboard's own reported name, for the `[device]` section's `physical_keyboard_name`. */
private fun physicalKeyboardName(): String? = InputDevice.getDeviceIds().toList()
    .mapNotNull { InputDevice.getDevice(it) }
    .firstOrNull { (it.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD && !it.isVirtual }
    ?.name

/** spec SS10.6 `[ime_context]`, SS10.7: own field snapshot, then the six `external_` fields from the last non-PhysiBoard field. */
private fun imeContextLines(own: ImeContextSnapshot?, external: ImeContextSnapshot?, timeFormat: SimpleDateFormat): List<String> {
    fun field(snapshot: ImeContextSnapshot?, key: String) = snapshot?.fields?.get(key) ?: "n/a"
    return listOf(
        "captured_at=${own?.let { timeFormat.format(Date(it.atMs)) } ?: "n/a"}",
        "target_package=${own?.packageName ?: "n/a"}",
        "input_type=${field(own, "input_type")}",
        "ime_options=${field(own, "ime_options")}",
        "ime_no_enter_action=${field(own, "ime_no_enter_action")}",
        "resolved_editor_action=${field(own, "resolved_editor_action")}",
        "subtype_locale=${field(own, "subtype_locale")}",
        "resolved_layout=${field(own, "resolved_layout")}",
        "external_target_package=${external?.packageName ?: "n/a"}",
        "external_input_type=${field(external, "input_type")}",
        "external_ime_options=${field(external, "ime_options")}",
        "external_ime_no_enter_action=${field(external, "ime_no_enter_action")}",
        "external_resolved_editor_action=${field(external, "resolved_editor_action")}",
        "external_subtype_locale=${field(external, "subtype_locale")}",
        "profile_override_snapshot=${field(own, "profile_override_snapshot")}",
        "resolved_physical_profile_snapshot=${field(own, "resolved_physical_profile_snapshot")}",
    )
}

/** spec: SS10.5. Text goes straight to the chooser; a file goes through the app's file provider with a read grant. */
private fun shareReport(context: Context, report: String, method: DebugShareMethod) {
    val intent = if (method == DebugShareMethod.TEXT) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PhysiBoard Keyboard Debug Export")
            putExtra(Intent.EXTRA_TEXT, report)
        }
    } else {
        val dir = File(context.cacheDir, "debug-reports")
        dir.deleteRecursively()
        dir.mkdirs()
        val name = DebugExportPolicy.fileName(SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()))
        val file = File(dir, name)
        file.writeText(report, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PhysiBoard Keyboard Debug Export")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Share debug report"))
}
