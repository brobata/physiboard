package brobata.physiboard.app.settings.ui.screens

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.core.settings.RingFit
import brobata.physiboard.device.titan.CutoutRect
import brobata.physiboard.device.titan.NotificationRingGeometry
import brobata.physiboard.device.titan.RingOverride

/**
 * "Fit the ring to the lens" (device-backlight-ring.md SS5.7.5). A white canvas with a red ring
 * the same shape the real ring draws, moved and resized until it sits on the lens.
 *
 * Like [brobata.physiboard.device.privileged.ring.NotificationRingActivity]'s own window, this
 * lays out into the display cutout and hides the system bars for as long as it is on screen (spec:
 * "a fullscreen white canvas laid out into the cutout with system bars hidden"), and seeds from
 * [NotificationRingGeometry.resolve] against the phone's own reported cutout rather than the
 * hard-coded Titan fit, so a stored override still wins but an unfitted screen starts from the
 * same geometry the real ring would draw.
 */
@Composable
fun RingFitScreen(onDone: () -> Unit) {
    val controller = LocalSettingsController.current
    val densityDpi = LocalDensity.current.density * 160f
    val stored = controller.current.value.device.ringFit
    val override = stored?.let { RingOverride(it.cx, it.cy, it.radius, it.stroke) }

    val view = LocalView.current
    val activity = LocalActivity.current

    val initialGeometry = remember(override) { NotificationRingGeometry.resolve(override, null, densityDpi) }
    var cx by remember { mutableFloatStateOf(initialGeometry.centerX) }
    var cy by remember { mutableFloatStateOf(initialGeometry.centerY) }
    var radius by remember { mutableFloatStateOf(initialGeometry.radius) }
    var stroke by remember { mutableFloatStateOf(initialGeometry.strokeWidth) }
    var lastCutout by remember { mutableStateOf<CutoutRect?>(null) }
    var seededFromCutout by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // spec SS5.7.5: hides the system bars and lays out into the cutout while this screen is up,
    // and reads the phone's own cutout the first time insets arrive (with no stored override, the
    // fix seeds from it instead of always starting from the Titan hand-fit).
    DisposableEffect(Unit) {
        val window = activity?.window
        // This screen is a composable inside the one shared `MainActivity`, not its own Activity,
        // so a `layoutInDisplayCutoutMode` this sets outlives the screen unless it is put back:
        // every other screen in Settings would otherwise render laid out into the cutout too, from
        // the moment this one is left, until the Activity happens to recreate.
        val originalCutoutMode = window?.attributes?.layoutInDisplayCutoutMode
        if (window != null) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val rect = insets.displayCutout?.boundingRects?.firstOrNull()
            val cutout = rect?.let { CutoutRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            lastCutout = cutout
            if (override == null && !seededFromCutout) {
                val geometry = NotificationRingGeometry.resolve(null, cutout, densityDpi)
                cx = geometry.centerX
                cy = geometry.centerY
                radius = geometry.radius
                stroke = geometry.strokeWidth
                seededFromCutout = true
            }
            insets
        }
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = originalCutoutMode ?: WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    fun save() {
        controller.update { it.copy(device = it.device.copy(ringFit = RingFit(cx, cy, radius, stroke))) }
    }

    fun auto() {
        val fitted = NotificationRingGeometry.resolve(null, lastCutout, densityDpi)
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
