package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.core.settings.RingFit
import brobata.physiboard.device.titan.NotificationRingGeometry

/**
 * "Fit the ring to the lens" (device-backlight-ring.md SS5.7.5). A white canvas with a red ring
 * the same shape the real ring draws, moved and resized until it sits on the lens.
 */
@Composable
fun RingFitScreen(onDone: () -> Unit) {
    val controller = LocalSettingsController.current
    val densityDpi = LocalDensity.current.density * 160f
    val stored = controller.current.value.device.ringFit

    var cx by remember { mutableFloatStateOf(stored?.cx ?: NotificationRingGeometry.fittedRing(densityDpi).centerX) }
    var cy by remember { mutableFloatStateOf(stored?.cy ?: NotificationRingGeometry.fittedRing(densityDpi).centerY) }
    var radius by remember { mutableFloatStateOf(stored?.radius ?: NotificationRingGeometry.fittedRing(densityDpi).radius) }
    var stroke by remember { mutableFloatStateOf(stored?.stroke ?: NotificationRingGeometry.fittedRing(densityDpi).strokeWidth) }
    val focusRequester = remember { FocusRequester() }

    fun save() {
        controller.update { it.copy(device = it.device.copy(ringFit = RingFit(cx, cy, radius, stroke))) }
    }

    fun auto() {
        val fitted = NotificationRingGeometry.fittedRing(densityDpi)
        cx = fitted.centerX; cy = fitted.centerY; radius = fitted.radius; stroke = fitted.strokeWidth
        controller.update { it.copy(device = it.device.copy(ringFit = null)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionLeft -> { cx -= 1f; true }
                        Key.DirectionRight -> { cx += 1f; true }
                        Key.DirectionUp -> { cy -= 1f; true }
                        Key.DirectionDown -> { cy += 1f; true }
                        Key.Plus, Key.Equals, Key.NumPadAdd -> { radius = (radius + 1f).coerceAtLeast(stroke); true }
                        Key.Minus, Key.NumPadSubtract -> { radius = (radius - 1f).coerceAtLeast(stroke); true }
                        Key.LeftBracket -> { stroke = (stroke - 1f).coerceIn(1f, 40f); true }
                        Key.RightBracket -> { stroke = (stroke + 1f).coerceIn(1f, 40f); true }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { save(); onDone(); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        cx += dragAmount.x
                        cy += dragAmount.y
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(cx, cy)
                drawCircle(color = Color(0xFFD32F2F), radius = radius + stroke, center = center, alpha = 0.25f)
                drawCircle(color = Color(0xFFD32F2F), radius = radius, center = center, style = Stroke(width = stroke))
            }
            Text(
                "Drag to move · arrows nudge · + and − resize · [ and ] change thickness · Enter saves",
                color = Color.Black,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { radius = (radius - 1f).coerceAtLeast(stroke) }) { Text("−") }
            TextButton(onClick = { radius += 1f }) { Text("+") }
            TextButton(onClick = { stroke = (stroke - 1f).coerceIn(1f, 40f) }) { Text("Thinner") }
            TextButton(onClick = { stroke = (stroke + 1f).coerceIn(1f, 40f) }) { Text("Thicker") }
            TextButton(onClick = { auto() }) { Text("Auto") }
            TextButton(onClick = { save(); onDone() }) { Text("Done") }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
