package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.StatusBarController

class ModifierIndicatorView(
    private val context: Context
) {
    private var container: LinearLayout? = null
    private val indicatorViews = mutableListOf<View>()

    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            field = value
            styleIndicators()
        }

    fun ensureView(): LinearLayout {
        container?.let { return it }
        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, 0, 0, 0)
        }
        return container!!
    }

    fun update(snapshot: StatusBarController.StatusSnapshot): Boolean {
        val specs = buildIndicatorSpecs(snapshot)
        val root = ensureView()
        root.removeAllViews()
        indicatorViews.clear()

        specs.forEachIndexed { index, spec ->
            val view = createIndicatorView(spec)
            root.addView(
                view,
                LinearLayout.LayoutParams(dpToPx(26f), dpToPx(26f)).apply {
                    if (index != specs.lastIndex) marginEnd = dpToPx(2f)
                }
            )
            indicatorViews.add(view)
        }

        val visible = specs.isNotEmpty()
        root.visibility = if (visible) View.VISIBLE else View.GONE
        return visible
    }

    fun isVisible(): Boolean = container?.visibility == View.VISIBLE

    private fun buildIndicatorSpecs(snapshot: StatusBarController.StatusSnapshot): List<IndicatorSpec> {
        val specs = mutableListOf<IndicatorSpec>()
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        if (shiftLocked || shiftActive) {
            specs.add(
                IndicatorSpec.Icon(
                    resId = if (shiftLocked) R.drawable.shift_filled_24 else R.drawable.shift_24,
                    locked = shiftLocked,
                    description = "Shift"
                )
            )
        }

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        if (ctrlLocked || ctrlActive) {
            specs.add(
                IndicatorSpec.Icon(
                    resId = R.drawable.keyboard_control_key_24,
                    locked = ctrlLocked,
                    description = "Ctrl"
                )
            )
        }

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        if (altLocked || altActive) {
            specs.add(
                IndicatorSpec.Icon(
                    resId = R.drawable.keyboard_option_key_24,
                    locked = altLocked,
                    description = "Alt"
                )
            )
        }

        if (snapshot.symPage > 0) {
            specs.add(
                IndicatorSpec.Text(
                    label = if (snapshot.symPage == 2) "SYM" else "SYM",
                    locked = snapshot.symPage == 2,
                    description = "SYM"
                )
            )
        }
        return specs
    }

    private fun createIndicatorView(spec: IndicatorSpec): View {
        return when (spec) {
            is IndicatorSpec.Icon -> ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, spec.resId))
                setColorFilter(indicatorColor(spec.locked))
                background = indicatorBackground()
                contentDescription = spec.description
                scaleType = ImageView.ScaleType.CENTER
            }
            is IndicatorSpec.Text -> TextView(context).apply {
                text = spec.label
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(indicatorColor(spec.locked))
                background = indicatorBackground()
                contentDescription = spec.description
            }
        }
    }

    private fun styleIndicators() {
        indicatorViews.forEach { view ->
            view.background = indicatorBackground()
        }
    }

    private fun indicatorColor(locked: Boolean): Int {
        val theme = themeOverride
        return when {
            locked -> theme?.ledLocked ?: DEFAULT_LOCKED_COLOR
            else -> theme?.ledActive ?: DEFAULT_ACTIVE_COLOR
        }
    }

    private fun indicatorBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(7f).toFloat()
            setColor(theme?.statusBarButton ?: DEFAULT_BACKGROUND_COLOR)
            setStroke(dpToPx(1f), theme?.divider ?: DEFAULT_BORDER_COLOR)
        }
    }

    private fun dpToPx(dp: Float): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    private sealed class IndicatorSpec(open val locked: Boolean, open val description: String) {
        data class Icon(
            val resId: Int,
            override val locked: Boolean,
            override val description: String
        ) : IndicatorSpec(locked, description)

        data class Text(
            val label: String,
            override val locked: Boolean,
            override val description: String
        ) : IndicatorSpec(locked, description)
    }

    private companion object {
        private val DEFAULT_ACTIVE_COLOR = 0xFF6496FF.toInt()
        private val DEFAULT_LOCKED_COLOR = 0xFFF76300.toInt()
        private val DEFAULT_BACKGROUND_COLOR = 0xFF2B3138.toInt()
        private val DEFAULT_BORDER_COLOR = 0xFF2C3136.toInt()
    }
}
