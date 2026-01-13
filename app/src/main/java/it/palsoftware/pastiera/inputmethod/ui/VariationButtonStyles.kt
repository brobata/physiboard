package it.palsoftware.pastiera.inputmethod.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

object VariationButtonStyles {
    val NORMAL_COLOR: Int = Color.rgb(17, 17, 17)
    val PRESSED_BLUE: Int = Color.rgb(100, 150, 255)
    const val BUTTON_CORNER_RADIUS_PX: Float = 0f

    fun createButtonDrawable(
        normalColor: Int = NORMAL_COLOR,
        pressedColor: Int = PRESSED_BLUE
    ): StateListDrawable {
        val normalDrawable = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = BUTTON_CORNER_RADIUS_PX
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(pressedColor)
            cornerRadius = BUTTON_CORNER_RADIUS_PX
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }
}
