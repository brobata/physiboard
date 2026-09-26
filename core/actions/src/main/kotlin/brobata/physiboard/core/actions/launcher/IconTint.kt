package brobata.physiboard.core.actions.launcher

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * spec: expansion-clipboard-pickers-launcher.md SS7.5: a row's color when neither a custom
 * per-command color nor the static top-highlight override applies. "The icon-derived color is
 * the alpha- and saturation-weighted average of a 32 by 32 rendering of the icon, pushed to
 * saturation 0.34..0.72 and value 0.58..0.92; icons that yield nothing use a hue per source"
 * (the per-source hues are [brobata.physiboard.core.actions.commands.CommandSource.hue]). Pure
 * math only: this module carries no `android.graphics.Color` import, so RGB/HSV conversion is
 * reimplemented below rather than borrowed from it.
 */
object IconTint {
    private const val MIN_SATURATION = 0.34f
    private const val MAX_SATURATION = 0.72f
    private const val MIN_VALUE = 0.58f
    private const val MAX_VALUE = 0.92f

    /**
     * [argbPixels] is a 32 by 32 icon rendering's raw pixels (alpha in the top byte, the shape
     * `android.graphics.Bitmap.getPixels` returns; `:ime` is the caller that renders one).
     * Weighting by alpha keeps a mostly transparent pixel from counting as much as an opaque one;
     * weighting by saturation keeps a vivid logo pixel from being washed out by a white or grey
     * background, while still leaving every pixel some say so a flat monochrome icon still
     * yields *a* hue rather than an arbitrary one. Returns null when every pixel is fully
     * transparent, per spec's "icons that yield nothing".
     */
    fun averageColor(argbPixels: IntArray, alpha: Int): Int? {
        var sumX = 0.0
        var sumY = 0.0
        var sumSaturation = 0.0
        var sumValue = 0.0
        var totalWeight = 0.0
        for (pixel in argbPixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a == 0) continue
            val (hue, saturation, value) = rgbToHsv((pixel ushr 16) and 0xFF, (pixel ushr 8) and 0xFF, pixel and 0xFF)
            val weight = (a / 255.0) * (0.15 + saturation)
            val radians = Math.toRadians(hue.toDouble())
            sumX += cos(radians) * weight
            sumY += sin(radians) * weight
            sumSaturation += saturation * weight
            sumValue += value * weight
            totalWeight += weight
        }
        if (totalWeight <= 0.0) return null
        val hue = ((Math.toDegrees(atan2(sumY, sumX)) + 360.0) % 360.0).toFloat()
        val saturation = (sumSaturation / totalWeight).toFloat().coerceIn(MIN_SATURATION, MAX_SATURATION)
        val value = (sumValue / totalWeight).toFloat().coerceIn(MIN_VALUE, MAX_VALUE)
        return hsvToArgb(hue, saturation, value, alpha)
    }

    /** spec's fallback: "icons that yield nothing use a hue per source", at the midpoint of the same saturation/value ranges. */
    fun colorForHue(hue: Int, alpha: Int): Int =
        hsvToArgb(hue.toFloat(), (MIN_SATURATION + MAX_SATURATION) / 2f, (MIN_VALUE + MAX_VALUE) / 2f, alpha)

    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val rawHue = when {
            delta == 0f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta).mod(6f))
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val saturation = if (maxC == 0f) 0f else delta / maxC
        return Triple(rawHue, saturation, maxC)
    }

    private fun hsvToArgb(hue: Float, saturation: Float, value: Float, alpha: Int): Int {
        val c = value * saturation
        val x = c * (1 - abs((hue / 60f).mod(2f) - 1f))
        val m = value - c
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255).toInt().coerceIn(0, 255)
        return ((alpha and 0xFF) shl 24) or (r shl 16) or (g shl 8) or b
    }
}
