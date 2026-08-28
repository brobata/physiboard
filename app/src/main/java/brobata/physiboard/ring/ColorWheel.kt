package brobata.physiboard.ring

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A hue/saturation disc with a brightness slider under it.
 *
 * The ring is drawn around the camera hole on a black AMOLED panel, where a dark colour is simply
 * not visible - so brightness is a separate, deliberate control rather than something you fall into
 * by dragging toward the middle of a wheel, and it is floored short of black. The caller warns via
 * NotificationRingPolicy.isTooDarkForRing when a choice still lands too dark to make out.
 */
@Composable
fun ColorWheel(
    selected: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = FloatArray(3).also { android.graphics.Color.colorToHSV(selected, it) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2].coerceAtLeast(0.35f)) }

    fun emit() = onColorChange(hsv(hue, saturation, value))

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    fun pick(position: Offset, area: Size) {
                        val cx = area.width / 2f
                        val cy = area.height / 2f
                        val dx = position.x - cx
                        val dy = position.y - cy
                        val radius = min(cx, cy)
                        val distance = sqrt(dx * dx + dy * dy)
                        // Outside the disc still counts, clamped: dragging off the edge should
                        // hold full saturation rather than snap back to grey.
                        saturation = (distance / radius).coerceIn(0f, 1f)
                        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                        emit()
                    }
                    detectTapGestures { pick(it, Size(size.width.toFloat(), size.height.toFloat())) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = change.position.x - cx
                        val dy = change.position.y - cy
                        val radius = min(cx, cy)
                        saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
                        hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                        emit()
                    }
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                val radius = min(size.width, size.height) / 2f
                val centre = Offset(size.width / 2f, size.height / 2f)
                // Hue around, saturation outward, brightness applied on top so the disc previews
                // the colour actually being chosen rather than a permanently bright one.
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = (0..360 step 10).map { Color(hsv(it.toFloat(), 1f, value)) },
                        center = centre
                    ),
                    radius = radius,
                    center = centre
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(hsv(0f, 0f, value)), Color(hsv(0f, 0f, value)).copy(alpha = 0f)),
                        center = centre,
                        radius = radius
                    ),
                    radius = radius,
                    center = centre
                )
                val angle = Math.toRadians(hue.toDouble())
                val marker = Offset(
                    centre.x + (cos(angle) * radius * saturation).toFloat(),
                    centre.y + (sin(angle) * radius * saturation).toFloat()
                )
                drawCircle(color = Color.White, radius = 10f, center = marker)
                drawCircle(color = Color.Black, radius = 7f, center = marker)
                drawCircle(color = Color(hsv(hue, saturation, value)), radius = 6f, center = marker)
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(hsv(hue, saturation, 0.15f)), Color(hsv(hue, saturation, 1f)))
                    )
                )
                .pointerInput(Unit) {
                    fun pickValue(x: Float) {
                        // Floored, not zeroed: black is not a colour the ring can show.
                        value = (x / size.width).coerceIn(0.15f, 1f)
                        emit()
                    }
                    detectTapGestures { pickValue(it.x) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        value = (change.position.x / size.width).coerceIn(0.15f, 1f)
                        emit()
                    }
                }
        )
    }
}

private fun hsv(h: Float, s: Float, v: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
