package it.palsoftware.pastiera.inputmethod.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

object VariationButtonStyles {
    val NORMAL_COLOR: Int = Color.argb(100, 17, 17, 17)  // Semi-transparent dark gray
    val PRESSED_BLUE: Int = Color.rgb(100, 150, 255)
    const val BUTTON_CORNER_RADIUS_RATIO: Float = 0.175f

    fun createButtonDrawable(
        heightPx: Int,
        normalColor: Int = NORMAL_COLOR,
        pressedColor: Int = PRESSED_BLUE,
        cornerRadiusRatio: Float = BUTTON_CORNER_RADIUS_RATIO,
        borderColor: Int? = null,
        borderWidthPx: Int = 0
    ): StateListDrawable {
        val radius = (heightPx * cornerRadiusRatio).coerceAtLeast(0f)
        val normalDrawable = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = radius
            if (borderColor != null && borderWidthPx > 0) {
                setStroke(borderWidthPx, borderColor)
            }
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(pressedColor)
            cornerRadius = radius
            if (borderColor != null && borderWidthPx > 0) {
                setStroke(borderWidthPx, borderColor)
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    fun cornerRadiusForSize(heightPx: Int, cornerRadiusRatio: Float? = null): Float {
        return (heightPx * (cornerRadiusRatio ?: BUTTON_CORNER_RADIUS_RATIO)).coerceAtLeast(0f)
    }
}
