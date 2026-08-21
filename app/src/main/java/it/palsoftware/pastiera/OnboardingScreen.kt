package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Lean, brand-styled first-run onboarding for PhysiBoard Terminal.
 *
 * Two live steps (Enable / Set as keyboard) that auto-advance by polling
 * [checkOnboardingImeStatus], followed by a "You're set" state that offers a
 * compact 3-point highlights card or an immediate skip. Either exit calls
 * [onComplete]. When [updateTutorial] is true it renders a tiny "What's new"
 * note instead so the version-bump path never spins up the old 8-page tour.
 *
 * Strong defaults are applied silently in MainActivity via
 * SettingsManager.applyImpactDefaultsIfNeeded — this screen only reports them.
 */
@Composable
fun OnboardingScreen(
    updateTutorial: Boolean = false,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    if (updateTutorial) {
        WhatsNewNote(onDone = onComplete)
        return
    }

    var isEnabled by remember { mutableStateOf(false) }
    var isSelected by remember { mutableStateOf(false) }
    var showEssentials by remember { mutableStateOf(false) }

    // Poll IME status so both steps flip to done without a user tap.
    LaunchedEffect(Unit) {
        while (true) {
            checkOnboardingImeStatus(context) { enabled, selected ->
                isEnabled = enabled
                isSelected = selected
            }
            delay(1800)
        }
    }

    val bothDone = isEnabled && isSelected
    val scrollState = rememberScrollState()

    // When setup completes / the essentials expand, scroll down so the action buttons
    // (Show me the essentials / Skip / Done) are never left stranded below the fold on the
    // short square screen.
    LaunchedEffect(bothDone, showEssentials) {
        if (bothDone) {
            delay(360)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        TerminalPrompt(command = "setup")

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Two quick steps to start typing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        StepCard(
            index = 1,
            title = "Enable PhysiBoard",
            done = isEnabled,
            actionEnabled = !isEnabled,
            actionLabel = "Open settings",
            dimmed = false,
            onAction = {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            index = 2,
            title = "Set as keyboard",
            done = isSelected,
            actionEnabled = isEnabled && !isSelected,
            actionLabel = "Switch",
            dimmed = !isEnabled,
            onAction = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        )

        AnimatedVisibility(visible = bothDone) {
            Column {
                Spacer(Modifier.height(16.dp))
                YoureSetSection(
                    showEssentials = showEssentials,
                    onShowEssentials = { showEssentials = true },
                    onSkip = onComplete,
                    onDone = onComplete
                )
            }
        }
    }
}

/** `physiboard:~$ <command>` with an amber prompt and a blinking cursor. */
@Composable
private fun TerminalPrompt(command: String) {
    val amber = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "physiboard:~",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "$ ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amber
        )
        Text(
            text = command,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "_",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amber,
            modifier = Modifier.alpha(cursorAlpha)
        )
    }
}

@Composable
private fun StepCard(
    index: Int,
    title: String,
    done: Boolean,
    actionEnabled: Boolean,
    actionLabel: String,
    dimmed: Boolean,
    onAction: () -> Unit
) {
    val amber = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (done) amber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) amber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$index. $title",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (done) {
                    Text(
                        text = "done",
                        style = MaterialTheme.typography.bodySmall,
                        color = amber
                    )
                }
            }
            if (!done) {
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun YoureSetSection(
    showEssentials: Boolean,
    onShowEssentials: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit
) {
    val amber = MaterialTheme.colorScheme.primary
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = amber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "You're set.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!showEssentials) {
            Button(
                onClick = onShowEssentials,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Show me the essentials", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Skip", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            EssentialsCard()
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EssentialsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EssentialRow(
                icon = Icons.Filled.RecordVoiceOver,
                text = "Hold Fn to talk (dictation)"
            )
            EssentialRow(
                icon = Icons.Filled.WbSunny,
                text = "Backlight can light the dark (one-time setup)"
            )
            EssentialRow(
                icon = Icons.Filled.Settings,
                text = "Everything else lives in the Settings tile"
            )
        }
    }
}

@Composable
private fun EssentialRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Minimal "What's new" note for the version-bump path. */
@Composable
private fun WhatsNewNote(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 22.dp, vertical = 28.dp)
    ) {
        TerminalPrompt(command = "whatsnew")
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Updated to v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your keyboard is up to date. Fixes and improvements are live.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Self-contained IME status probe (enabled + selected) for onboarding. */
private fun checkOnboardingImeStatus(
    context: Context,
    callback: (enabled: Boolean, selected: Boolean) -> Unit
) {
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pastieraPackageName = ImeIdentity.packageName

        val enabledInputMethods = imm.enabledInputMethodList
        val isEnabled = enabledInputMethods.any { info ->
            info.packageName == pastieraPackageName || ImeIdentity.matchesImeId(info.id)
        }

        var isSelected = false
        if (isEnabled) {
            try {
                val defaultInputMethod = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD
                ) ?: ""
                isSelected = ImeIdentity.matchesImeId(defaultInputMethod)
            } catch (_: SecurityException) {
                // Android 14+ may block reading the secure setting; fall back.
                try {
                    val currentSubtype = imm.currentInputMethodSubtype
                    val pastieraInputMethod = imm.inputMethodList.find {
                        it.packageName == pastieraPackageName || ImeIdentity.matchesImeId(it.id)
                    }
                    isSelected = currentSubtype != null &&
                        pastieraInputMethod != null &&
                        enabledInputMethods.size == 1
                } catch (_: Exception) {
                    isSelected = false
                }
            } catch (_: Exception) {
                isSelected = false
            }
        }

        callback(isEnabled, isSelected)
    } catch (e: Exception) {
        android.util.Log.e("OnboardingScreen", "Error checking IME status", e)
        callback(false, false)
    }
}
