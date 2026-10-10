package brobata.physiboard.app.settings.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.CategoryTint
import brobata.physiboard.app.settings.ui.KeycapIcon
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.PhysiBoardType
import brobata.physiboard.app.settings.ui.SettingsController
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.settings.ui.SpellCheckerSettings
import brobata.physiboard.app.settings.ui.terminalPane
import brobata.physiboard.app.shell.ImeComponent
import brobata.physiboard.app.shell.ImeProbeAndroid
import brobata.physiboard.core.pointer.OverlayAvailability
import brobata.physiboard.core.shell.FirstRunSetup
import brobata.physiboard.core.shell.FirstRunSteps
import brobata.physiboard.device.privileged.setup.AndroidPermissionProbe
import brobata.physiboard.ime.pointer.OverlayPermission
import kotlinx.coroutines.delay

/**
 * The first-run pages (app-shell.md SS4): three short pages, one thing each. Turn PhysiBoard on,
 * make it the keyboard, then the optional extras. Every page reads the phone again on return and
 * every second while it shows, so it changes by itself when the user comes back from Android's
 * settings or picks PhysiBoard in the picker; there is nothing to confirm. Every page has Skip,
 * Back steps to the page before, and the buttons take focus so a hardware keyboard's Enter works.
 *
 * Who sees these pages is [brobata.physiboard.core.shell.FirstRun]'s rule; "Show the tutorial"
 * in Help opens them again on purpose.
 */
@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalSettingsController.current

    var state by remember { mutableStateOf(readSetupState(context)) }
    // Live: every second while the app is in front (the input-method picker is a dialog over this
    // screen, so no resume follows it), and at once on every return from Android's settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(POLL_MS)
                state = readSetupState(context)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readSetupState(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Start at the first step still to do: a phone with PhysiBoard on but not chosen opens on page 2.
    var page by rememberSaveable { mutableIntStateOf(firstPage(state.steps)) }
    BackHandler(enabled = page > 0) { page-- }
    val finish = { completeSetup(controller, overlayGranted = readSetupState(context).overlayOn, onComplete) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TerminalHeader(command = "setup", cursorPeriodMillis = 650) {
            Text("${page + 1}/$PAGE_COUNT", style = PhysiBoardType.value, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
        // The page scrolls on its own; the Skip / Next bar below it never leaves the short square screen.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.l),
        ) {
            when (page) {
                0 -> EnablePage(state.steps)
                1 -> SelectPage(state.steps, onBackToEnable = { page = 0 })
                else -> ExtrasPage(state)
            }
        }
        val pageDone = when (page) {
            0 -> state.steps.enableDone
            1 -> state.steps.selectDone
            else -> true
        }
        BottomBar(
            last = page == PAGE_COUNT - 1,
            pageDone = pageDone,
            focusKey = page,
            onSkip = finish,
            onNext = { if (page < PAGE_COUNT - 1) page++ else finish() },
        )
    }
}

/** Page 1: turn PhysiBoard on in Android's list of keyboards. */
@Composable
private fun EnablePage(steps: FirstRunSteps) {
    val context = LocalContext.current
    PageTitle("Turn on PhysiBoard")
    PageText("Android keeps a new keyboard switched off until you turn it on. Open the list and switch PhysiBoard on.")
    PageText("Android shows the same warning for every keyboard. That's expected.")
    StepPane(
        done = steps.enableDone,
        icon = Icons.Outlined.ToggleOn,
        todo = "PhysiBoard is off",
        doneText = "PhysiBoard is on",
        buttonText = "Open keyboard list",
        focusKey = "enable",
    ) { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) } }
}

/** Page 2: make it the keyboard in use, from Android's picker. */
@Composable
private fun SelectPage(steps: FirstRunSteps, onBackToEnable: () -> Unit) {
    val context = LocalContext.current
    PageTitle("Make it your keyboard")
    PageText("Pick PhysiBoard in the list Android shows. You can change keyboards again any time.")
    if (!steps.enableDone) {
        StepPane(
            done = false,
            icon = Icons.Outlined.Keyboard,
            todo = "Turn PhysiBoard on first",
            doneText = "",
            buttonText = "Back to step 1",
            focusKey = "select-blocked",
            onClick = onBackToEnable,
        )
    } else {
        StepPane(
            done = steps.selectDone,
            icon = Icons.Outlined.Keyboard,
            todo = "PhysiBoard is not your keyboard yet",
            doneText = "PhysiBoard is your keyboard",
            buttonText = "Choose keyboard",
            focusKey = "select",
        ) { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker() }
    }
}

/** Page 3: the optional extras, each with its own way in and its own live state. */
@Composable
private fun ExtrasPage(state: SetupState) {
    val context = LocalContext.current
    PageTitle("A few extras")
    PageText("All optional. Turn on what you like now, or later in Settings.")
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).terminalPane()) {
        // The same words as Home's warning line for the service (SS6.3), and the same opener as
        // the Accessibility service screen.
        ExtraRow(
            icon = Icons.Outlined.AccessibilityNew,
            title = "Accessibility service",
            description = "Needed for Fn shortcuts everywhere and focusing the text box",
            hint = "If Android says it is restricted: App info, ⋮, Allow restricted settings.",
            on = state.accessibilityOn,
        ) { openAccessibilitySettings(context) }
        RowDivider()
        // The permission every keyboard panel and the screen trackpad check (trackpad-caret-nav.md SS4.6).
        ExtraRow(
            icon = Icons.Outlined.Layers,
            title = "Display over other apps",
            description = "Needed for the emoji and clipboard panels and the screen trackpad (hold Space to move the cursor)",
            hint = null,
            on = state.overlayOn,
        ) { OverlayPermission.explainAndOpenSettings(context, "PhysiBoard") }
        RowDivider()
        // Home's "Turn on spell checking" line (SS6.3), with Android's spell checker picker.
        ExtraRow(
            icon = Icons.Outlined.Spellcheck,
            title = "Spell checking",
            description = "Pick PhysiBoard so apps underline misspellings",
            hint = null,
            on = state.spellCheckerOurs,
        ) { SpellCheckerSettings.open(context) }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.l).semantics { heading() },
    )
}

@Composable
private fun PageText(text: String) {
    Text(text, style = PhysiBoardType.reading, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.s))
}

/**
 * The one thing a page asks for, as a pane: amber-bordered with the button while it is to do, a
 * calm pane with a green check once it is done. The button takes focus when the page opens so
 * Enter on a hardware keyboard presses it.
 */
@Composable
private fun StepPane(done: Boolean, icon: ImageVector, todo: String, doneText: String, buttonText: String, focusKey: String, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.l)
            .terminalPane(featured = !done)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = CategoryTint.EMERALD.glyph(dark), modifier = Modifier.size(28.dp))
            } else {
                KeycapIcon(icon, size = 40.dp, iconSize = 22.dp, category = CategoryTint.AMBER)
            }
            Text(
                if (done) doneText else todo,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
        if (!done) {
            val focus = remember { FocusRequester() }
            Button(
                onClick = onClick,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).defaultMinSize(minHeight = MinTouchTarget).focusRequester(focus),
            ) { Text(buttonText) }
            LaunchedEffect(focusKey) { runCatching { focus.requestFocus() } }
        }
    }
}

/** One extra: what it is and what it is for, then "Turn on", or "On" with a check once it is. */
@Composable
private fun ExtraRow(icon: ImageVector, title: String, description: String, hint: String?, on: Boolean, onTurnOn: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!on && hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
        if (on) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = CategoryTint.EMERALD.glyph(dark), modifier = Modifier.size(20.dp))
                Text("On", style = PhysiBoardType.value, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Spacing.xs))
            }
        } else {
            // The terminal button of ButtonRow: the label in the accent inside a 1 dp accent box.
            OutlinedButton(
                onClick = onTurnOn,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget),
            ) { Text("Turn on") }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * Skip at the start, Next (Done on the last page) at the end. Next is the filled button once the
 * page's step is done, and takes focus then, so Enter moves on; before that it stays outlined, a
 * way past the step rather than the next thing to press.
 */
@Composable
private fun BottomBar(last: Boolean, pageDone: Boolean, focusKey: Int, onSkip: () -> Unit, onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip, shape = MaterialTheme.shapes.small, modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget)) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
            val label = if (last) "Done" else "Next"
            val buttonModifier = Modifier.defaultMinSize(minWidth = 112.dp, minHeight = MinTouchTarget).focusRequester(focus)
            if (pageDone) {
                Button(onClick = onNext, shape = MaterialTheme.shapes.small, modifier = buttonModifier) { Text(label) }
            } else {
                OutlinedButton(
                    onClick = onNext,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = buttonModifier,
                ) { Text(label) }
            }
        }
    }
    // A finished step hands focus to Next; an unfinished one leaves it on the step's own button.
    LaunchedEffect(focusKey, pageDone) {
        if (pageDone) runCatching { focus.requestFocus() }
    }
}

/** What the pages show, read fresh from the phone on every poll and every return. */
private data class SetupState(
    val steps: FirstRunSteps,
    val accessibilityOn: Boolean,
    val overlayOn: Boolean,
    val spellCheckerOurs: Boolean,
)

private fun readSetupState(context: Context): SetupState {
    val probe = ImeProbeAndroid.evaluate(context, ImeComponent.SERVICE_CLASS_NAME)
    return SetupState(
        steps = FirstRunSetup.steps(probe.enabled, probe.selected),
        accessibilityOn = AndroidPermissionProbe.accessibilityServiceEnabled(context),
        overlayOn = OverlayPermission.availability(context) == OverlayAvailability.AVAILABLE,
        spellCheckerOurs = SpellCheckerSettings.state(context) == SpellCheckerSettings.State.OURS,
    )
}

private fun firstPage(steps: FirstRunSteps): Int = when {
    !steps.enableDone -> 0
    !steps.selectDone -> 1
    else -> 2
}

private fun completeSetup(controller: SettingsController, overlayGranted: Boolean, onComplete: () -> Unit) {
    // spec: SS4.6. One commit: tutorial_completed and last_seen_whats_new_version together, so a
    // fresh install never sees the what's-new note for the version it was installed with. The
    // trackpad joins it when this first run granted what it needs (FirstRunSetup.trackpadAfterSetup).
    controller.update {
        val trackpadOn = FirstRunSetup.trackpadAfterSetup(firstRun = !it.shell.tutorialCompleted, overlayGranted = overlayGranted, trackpadOn = it.trackpad.enabled)
        it.copy(
            shell = it.shell.copy(tutorialCompleted = true, lastSeenWhatsNewVersion = BuildConfig.VERSION_NAME),
            trackpad = it.trackpad.copy(enabled = trackpadOn),
        )
    }
    onComplete()
}

private const val PAGE_COUNT = 3
private const val POLL_MS = 1000L
