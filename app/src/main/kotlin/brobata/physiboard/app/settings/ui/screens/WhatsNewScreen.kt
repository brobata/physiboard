package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.PhysiBoardColors
import brobata.physiboard.app.settings.ui.TerminalPromptStyle
import brobata.physiboard.core.shell.WhatsNewNotes

/**
 * The what's-new note (app-shell.md SS5.3): one scrolling page, no skip, no pager, no network.
 * Reads the build-time asset `common/whats_new.md`; when the asset is missing or empty (this
 * milestone has no Gradle task wiring [brobata.physiboard.core.shell.ChangeRecordSection] into the
 * build yet, a SPEC GAP noted in this module's report), the empty-notes copy is shown, which is
 * indistinguishable from a real release with nothing to say.
 */
@Composable
fun WhatsNewScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current

    val notes = remember {
        val markdown = runCatching { context.assets.open("common/whats_new.md").bufferedReader().use { it.readText() } }.getOrDefault("")
        WhatsNewNotes.parse(markdown)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal))
            .padding(16.dp),
    ) {
        Text("physiboard:~$ whatsnew", style = TerminalPromptStyle, color = PhysiBoardColors.SignalAmber)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = PhysiBoardColors.SignalAmber)
            Text("Updated to v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }

        if (notes.isEmpty()) {
            Text(
                "Your keyboard is up to date. Fixes and improvements are live.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            notes.forEach { note ->
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (note.title.isNotBlank()) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (note.body.isNotBlank()) {
                        Text(note.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Button(
            onClick = {
                // spec: SS5.3. "Done" writes tutorial_completed and last_seen_whats_new_version,
                // then opens home. The preview/second-write branch (PREVIEW_UPDATE_TUTORIAL) is
                // dead in 2.x (SS5.3) and has no path into 3.0, so it is not reproduced here.
                controller.update {
                    it.copy(shell = it.shell.copy(tutorialCompleted = true, lastSeenWhatsNewVersion = BuildConfig.VERSION_NAME))
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) { Text("Done") }
    }
}
