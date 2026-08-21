package it.palsoftware.pastiera.inputmethod.statusbar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

object StatusBarButtonStyles {
    val NORMAL_COLOR: Int = Color.argb(100, 17, 17, 17)  // Semi-transparent dark gray
    val PRESSED_BLUE: Int = Color.rgb(100, 150, 255)
    val RECOGNITION_RED: Int = Color.rgb(255, 80, 80)
    const val BUTTON_CORNER_RADIUS_RATIO: Float = 0.175f
    data class ThemeOverride(
        val normalColor: Int,
        val pressedColor: Int,
        val iconColor: Int,
        val cornerRadiusRatio: Float = BUTTON_CORNER_RADIUS_RATIO,
        val borderColor: Int? = null,
        val borderWidthPx: Int = 0
    )

    fun createButtonDrawable(
        heightPx: Int,
        normalColor: Int = NORMAL_COLOR,
        pressedColor: Int = PRESSED_BLUE,
        cornerRadiusRatio: Float = BUTTON_CORNER_RADIUS_RATIO,
        borderColor: Int? = null,
        borderWidthPx: Int = 0
    ): StateListDrawable {
        val radius = cornerRadiusForSize(heightPx, cornerRadiusRatio)
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

    fun cornerRadiusForSize(heightPx: Int, cornerRadiusRatio: Float = BUTTON_CORNER_RADIUS_RATIO): Float {
        return (heightPx * cornerRadiusRatio).coerceAtLeast(0f)
    }

    /**
     * Background for a bar-edge button: the outer bottom corner is rounded to [outerRadiusPx]
     * (to hug the display's rounded corner) while the other corners keep the normal radius.
     * Uses the given colors so it matches the active theme.
     */
    fun createEdgeButtonDrawable(
        heightPx: Int,
        leftEdge: Boolean,
        outerRadiusPx: Float,
        normalColor: Int = NORMAL_COLOR,
        pressedColor: Int = PRESSED_BLUE
    ): StateListDrawable {
        val r = cornerRadiusForSize(heightPx)
        val o = outerRadiusPx
        // Radii order: TL x,y, TR x,y, BR x,y, BL x,y. Round both outer corners so the whole
        // outer edge is a clean curve (capsule end) that hugs the display.
        val radii = if (leftEdge) {
            floatArrayOf(r, r, r, r, r, r, o, o) // bottom-left (outer) rounded only
        } else {
            floatArrayOf(r, r, r, r, o, o, r, r) // bottom-right (outer) rounded only
        }
        fun drawable(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadii = radii
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), drawable(pressedColor))
            addState(intArrayOf(), drawable(normalColor))
        }
    }
}
