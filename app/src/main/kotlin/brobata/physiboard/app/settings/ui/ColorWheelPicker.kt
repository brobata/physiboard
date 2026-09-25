package brobata.physiboard.app.settings.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import brobata.physiboard.device.titan.NotificationRingColor
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * The colour picker shared by the notification ring and (later) the cursor-modifier colours: nine
 * quick swatches, a hue/saturation disc with its own brightness slider (floored so it can never
 * go black), and the "too dark to see" caution. spec: device-backlight-ring.md SS5.4.
 */
@Composable
fun ColorWheelPicker(colorArgb: Int, onColorChange: (Int) -> Unit) {
    // spec SS5.4: "the initial brightness of the current colour is raised to at least 0.35 when the wheel opens".
    var brightness by remember(colorArgb) { mutableFloatStateOf(initialBrightness(colorArgb)) }
    val hsv = remember(colorArgb) { FloatArray(3).also { AndroidColor.colorToHSV(colorArgb, it) } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            SWATCHES.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .clickable { onColorChange(swatch) },
                )
            }
        }
        HueSaturationDisc(hue = hsv[0], saturation = hsv[1]) { hue, saturation ->
            onColorChange(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        }
        Text("Brightness", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = brightness,
            onValueChange = { value ->
                brightness = value.coerceAtLeast(BRIGHTNESS_FLOOR)
                onColorChange(AndroidColor.HSVToColor(floatArrayOf(hsv[0], hsv[1], brightness)))
            },
            valueRange = BRIGHTNESS_FLOOR..1f,
        )
        if (NotificationRingColor.relativeLuminance(colorArgb) < NotificationRingColor.DARK_LUMINANCE_THRESHOLD) {
            Text(
                "This is too dark to see on the screen while it is off. Slide the brightness up.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HueSaturationDisc(hue: Float, saturation: Float, onPick: (Float, Float) -> Unit) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val (pickedHue, pickedSaturation) = pickHueSaturation(offset, size.width.toFloat(), size.height.toFloat())
                    onPick(pickedHue, pickedSaturation)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val (pickedHue, pickedSaturation) = pickHueSaturation(change.position, size.width.toFloat(), size.height.toFloat())
                    onPick(pickedHue, pickedSaturation)
                }
            },
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(brush = Brush.sweepGradient(HUE_STOPS, center), radius = radius, center = center)
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius = radius, center = center)
        val angle = Math.toRadians(hue.toDouble())
        val markerRadius = (saturation.coerceIn(0f, 1f)) * radius
        val marker = Offset(center.x + (markerRadius * kotlin.math.cos(angle)).toFloat(), center.y + (markerRadius * kotlin.math.sin(angle)).toFloat())
        drawCircle(color = Color.Black, radius = 6.dp.toPx(), center = marker, style = Stroke(width = 2.dp.toPx()))
    }
}

/** [offset]'s angle is the hue (0..360); its distance from the centre, clamped to the radius, is the saturation. */
private fun pickHueSaturation(offset: Offset, width: Float, height: Float): Pair<Float, Float> {
    val center = Offset(width / 2f, height / 2f)
    val radius = min(width, height) / 2f
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    var hue = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    if (hue < 0f) hue += 360f
    val saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
    return hue to saturation
}

private fun initialBrightness(colorArgb: Int): Float {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(colorArgb, hsv)
    return hsv[2].coerceAtLeast(0.35f)
}

/** spec SS5.4: the nine quick swatches. */
private val SWATCHES = listOf(
    0xFF34C759.toInt(), 0xFF2F80ED.toInt(), 0xFF22D3EE.toInt(), 0xFFA855F7.toInt(), 0xFFF472B6.toInt(),
    0xFFEF4444.toInt(), 0xFFF97316.toInt(), 0xFFFACC15.toInt(), 0xFFF5F5F5.toInt(),
)

private const val BRIGHTNESS_FLOOR = 0.15f

private val HUE_STOPS: List<Color> = (0..360 step 30).map { Color.hsv(it.toFloat() % 360f, 1f, 1f) }
