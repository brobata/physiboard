package it.palsoftware.pastiera.inputmethod.statusbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

object StatusBarButtonStyles {
    val NORMAL_COLOR: Int = Color.argb(100, 17, 17, 17)  // Semi-transparent dark gray
    val PRESSED_BLUE: Int = Color.rgb(100, 150, 255)
    val RECOGNITION_RED: Int = Color.rgb(255, 80, 80)
    const val BUTTON_CORNER_RADIUS_RATIO: Float = 0.175f

    fun createButtonDrawable(
        heightPx: Int,
        normalColor: Int = NORMAL_COLOR,
        pressedColor: Int = PRESSED_BLUE
    ): StateListDrawable {
        val radius = cornerRadiusForSize(heightPx)
        val normalDrawable = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = radius
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(pressedColor)
            cornerRadius = radius
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    fun cornerRadiusForSize(heightPx: Int): Float {
        return (heightPx * BUTTON_CORNER_RADIUS_RATIO).coerceAtLeast(0f)
    }
}
