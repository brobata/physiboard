package brobata.physiboard.app.settings.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
 * SPEC GAP: nothing in `:ime` reports key events into [AppDebugCaptureStore] yet (that wiring is
 * the keys/keyboard-service feature's job, not the app shell's), so "Record" here always yields
 * an empty `[events]` section until that lands. Every other section (system, app, device,
 * settings_snapshot) is real.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current
    val store = AppDebugCaptureStore.instance

    var fieldText by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var eventCount by remember { mutableStateOf(0) }
    var includeSuggestions by remember { mutableStateOf(false) }
    var includeRawTrackpad by remember { mutableStateOf(false) }
    var includeAutocorrections by remember { mutableStateOf(false) }
    var viewerText by remember { mutableStateOf<String?>(null) }

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
        sections += ReportSection(
            "device",
            listOf(
                "brand=${Build.BRAND}",
                "manufacturer=${Build.MANUFACTURER}",
                "model=${Build.MODEL}",
                "device=${Build.DEVICE}",
                "product=${Build.PRODUCT}",
                "fingerprint=${Build.FINGERPRINT}",
                "hardware=${Build.HARDWARE}",
                "board=${Build.BOARD}",
            ),
        )
        sections += ReportSection(
            "recording",
            listOf(
                "started_at=${startedAt?.let { timeFormat.format(Date(it)) } ?: "n/a"}",
                "event_count=$eventCount",
                "include_suggestions=$includeSuggestions",
                "include_raw_trackpad=$includeRawTrackpad",
                "include_autocorrections=$includeAutocorrections",
            ),
        )
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
        sections += ReportSection("events", listOf("(no recorded events)")) // SPEC GAP: see the file header.

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
                        eventCount = 0
                        startedAt = System.currentTimeMillis()
                        recording = true
                    }
                }) { Text(if (recording) "Stop" else "Record") }
                TextButton(onClick = {
                    store.clear()
                    eventCount = 0
                    startedAt = null
                    recording = false
                    viewerText = null
                }) { Text("Clear") }
                TextButton(onClick = { viewerText = buildReport() }) { Text("View") }
                TextButton(onClick = {
                    val report = buildReport()
                    val method = DebugExportPolicy.shareMethod(eventCount, includeRawTrackpad, report.toByteArray(Charsets.UTF_8).size)
                    shareReport(context, report, method)
                }) { Text("Share") }
            }

            Text(
                "Recorder: ${if (recording) "recording" else "stopped"} · Events: $eventCount",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text(
                "Started at: ${startedAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it)) } ?: "n/a"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )

            Row(modifier = Modifier.padding(top = 12.dp)) {
                FilterChip(selected = includeSuggestions, onClick = { includeSuggestions = !includeSuggestions }, label = { Text("incl. suggestions") })
                FilterChip(selected = includeRawTrackpad, onClick = { includeRawTrackpad = !includeRawTrackpad }, label = { Text("incl. raw trackpad") }, modifier = Modifier.padding(start = 8.dp))
                FilterChip(selected = includeAutocorrections, onClick = { includeAutocorrections = !includeAutocorrections }, label = { Text("incl. autocorrections") }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    val report = viewerText
    if (report != null) {
        AlertDialog(
            onDismissRequest = { viewerText = null },
            title = { Text("Debug Report Viewer") },
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(report, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } },
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
