package brobata.physiboard.app.settings.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.PhysiBoardColors
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.settings.ui.TerminalPromptStyle
import brobata.physiboard.app.shell.ImeComponent
import brobata.physiboard.app.shell.ImeProbeAndroid
import brobata.physiboard.core.shell.FirstRunSetup
import kotlinx.coroutines.delay

/**
 * The two-step first-run setup screen (app-shell.md SS4). [isWhatsNew] selects the what's-new
 * variant of the same activity in 2.x (`UPDATE_TUTORIAL` extra); 3.0 routes to
 * [WhatsNewScreen] as its own destination instead, so this screen is always the setup flow.
 */
@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current

    var steps by remember { mutableStateOf(currentSteps(context)) }
    LaunchedEffectPoll { steps = currentSteps(context) }

    var essentialsExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // spec: SS4.2. "360 ms after both steps become done the page scrolls to its bottom... the
    // same scroll happens when the essentials expand."
    LaunchedEffect(steps.bothDone) {
        if (steps.bothDone) {
            delay(360)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    LaunchedEffect(essentialsExpanded) {
        if (essentialsExpanded) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Vertical + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
            .padding(16.dp),
    ) {
        TerminalHeader()
        Text("Two quick steps to start typing.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp))

        StepCard(
            number = 1,
            title = "Enable PhysiBoard",
            done = steps.enableDone,
            enabled = true,
            buttonLabel = "Open settings",
        ) {
            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
        }

        StepCard(
            number = 2,
            title = "Set as keyboard",
            done = steps.selectDone,
            enabled = steps.enableDone && !steps.selectDone,
            buttonLabel = "Switch",
            dimmed = !steps.enableDone,
        ) {
            (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
        }

        // spec: SS4.2. The section appears the moment both steps are done; the delayed scroll to
        // it is driven by the LaunchedEffect above.
        if (steps.bothDone) {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = PhysiBoardColors.SignalAmber)
                    Text("You're set.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                if (!essentialsExpanded) {
                    Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { essentialsExpanded = true }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Show me the essentials") }
                        OutlinedButton(onClick = { completeSetup(controller, onComplete) }, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Skip") }
                    }
                } else {
                    Card(
                        modifier = Modifier.padding(top = Spacing.l).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Hold Fn to talk (dictation)", modifier = Modifier.padding(vertical = 4.dp))
                            Text("Backlight can light the dark (one-time setup)", modifier = Modifier.padding(vertical = 4.dp))
                            Text("Everything else is on the home screen, by category", modifier = Modifier.padding(vertical = 4.dp))
                            Button(onClick = { completeSetup(controller, onComplete) }, modifier = Modifier.padding(top = 12.dp)) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
}

private fun completeSetup(controller: brobata.physiboard.app.settings.ui.SettingsController, onComplete: () -> Unit) {
    // spec: SS4.3. One commit: tutorial_completed and last_seen_whats_new_version together, so a
    // fresh install never sees the what's-new note for the version it was installed with.
    controller.update {
        it.copy(
            shell = it.shell.copy(
                tutorialCompleted = true,
                lastSeenWhatsNewVersion = brobata.physiboard.app.BuildConfig.VERSION_NAME,
            ),
        )
    }
    onComplete()
}

private fun currentSteps(context: android.content.Context): brobata.physiboard.core.shell.FirstRunSteps {
    val probe = ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)
    return FirstRunSetup.steps(probe.enabled, probe.selected)
}

/** spec: SS4.1, "polls ... every 1800 ms for as long as it is showing". */
@Composable
private fun LaunchedEffectPoll(action: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            action()
        }
    }
}

/** spec: SS4, "a cursor that fades between opaque and transparent every 650 ms". */
@Composable
private fun TerminalHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("physiboard:~$ setup", style = TerminalPromptStyle, color = PhysiBoardColors.SignalAmber)
        TerminalCursor(modifier = Modifier.padding(start = 4.dp), periodMillis = 650)
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    done: Boolean,
    enabled: Boolean,
    buttonLabel: String,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.m)
            .alpha(if (dimmed) 0.45f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (done) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$number. $title", style = MaterialTheme.typography.titleMedium)
                if (done) Text("done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text(buttonLabel) }
            }
        }
    }
}

