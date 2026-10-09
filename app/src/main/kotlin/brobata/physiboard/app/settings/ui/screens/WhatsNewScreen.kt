package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.PhysiBoardType
import brobata.physiboard.app.settings.ui.SettingsCard
import brobata.physiboard.app.settings.ui.Spacing
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
            .padding(Spacing.l),
    ) {
        Text("physiboard:~$ whatsnew", style = TerminalPromptStyle, color = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Updated to v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.s))
        }

        if (notes.isEmpty()) {
            Text(
                "Your keyboard is up to date. Fixes and improvements are live.",
                modifier = Modifier.padding(top = 16.dp),
                style = PhysiBoardType.reading,
            )
        } else {
            Spacer(modifier = Modifier.height(Spacing.s))
            // One card, one line per change, each marked "+" the way a diff marks an added line.
            SettingsCard {
                notes.forEach { note ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                        Text("+", style = PhysiBoardType.prompt, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = Spacing.m))
                        Column(modifier = Modifier.weight(1f)) {
                            if (note.title.isNotBlank()) {
                                Text(note.title, style = if (note.body.isBlank()) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium)
                            }
                            if (note.body.isNotBlank()) {
                                Text(note.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Button(
            shape = MaterialTheme.shapes.small,
            onClick = {
                // spec: SS5.3. "Done" writes tutorial_completed and last_seen_whats_new_version,
                // then opens home. The preview/second-write branch (PREVIEW_UPDATE_TUTORIAL) is
                // dead in 2.x (SS5.3) and has no path into 3.0, so it is not reproduced here.
                controller.update {
                    it.copy(shell = it.shell.copy(tutorialCompleted = true, lastSeenWhatsNewVersion = BuildConfig.VERSION_NAME))
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.l).heightIn(min = 52.dp),
        ) { Text("Done") }
    }
}
