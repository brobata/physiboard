package brobata.physiboard.app.settings.ui

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import brobata.physiboard.core.actions.feedback.HapticEvent
import brobata.physiboard.core.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's one undo snackbar (app-shell.md SS22.4). It lives above the navigation stack, so an
 * offer made on one screen can still be taken for its 8 seconds after going back. [offer] is
 * called right after the action already happened.
 */
class UndoController(private val scope: CoroutineScope, private val haptic: (HapticEvent) -> Unit) {
    val snackbar = SnackbarHostState()
    private val slot = UndoSlot<suspend () -> Unit>()
    private var showing: Job? = null

    /**
     * Shows "[message] · Undo". [key] names the action: a second one with the same key while
     * the snackbar is up extends it and keeps the first action's [restore] (see [UndoSlot.offer]).
     * [feel] is the haptic for the action itself.
     */
    fun offer(key: String, message: String, feel: HapticEvent? = HapticEvent.CONFIRM_DESTRUCTIVE, restore: suspend () -> Unit) {
        feel?.let(haptic)
        val pending = slot.offer(key, message, restore, SystemClock.uptimeMillis())
        showing?.cancel()
        showing = scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(UndoSlot.WINDOW_MS) {
                snackbar.showSnackbar(message = pending.message, actionLabel = "Undo", withDismissAction = false, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) {
                val putBack = slot.take(SystemClock.uptimeMillis()) ?: return@launch
                haptic(HapticEvent.UNDO)
                putBack()
            } else {
                slot.dismiss(pending.offeredAtMs)
            }
        }
    }

    /** A [Settings] change that can be undone: [section] as it is now is what Undo puts back. */
    fun <T> updateSettings(
        controller: SettingsController,
        key: String,
        message: String,
        section: SettingsSection<T>,
        feel: HapticEvent? = HapticEvent.CONFIRM_DESTRUCTIVE,
        transform: (Settings) -> Settings,
    ) {
        val restore = section.restoreFrom(controller.current.value)
        controller.update(transform)
        offer(key, message, feel) { controller.update(restore) }
    }
}

val LocalUndo = staticCompositionLocalOf<UndoController?> { null }

/** The snackbar in the terminal skin: a raised pane with a 1 dp border, the message in mono, Undo in the accent. */
@Composable
fun UndoSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    Snackbar(
        modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).border(1.dp, paneBorderColor(), MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        containerColor = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        action = {
            TextButton(onClick = { data.performAction() }) {
                Text(data.visuals.actionLabel ?: "Undo", color = colors.primary, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyLarge)
    }
}
