package it.palsoftware.pastiera.inputmethod.expansion

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

class TextExpansionPopup(
    private val context: Context,
    private val onSelected: (ExpansionMatch) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val entries = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val content = ScrollView(context).apply {
        addView(entries)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(35, 35, 38))
        }
    }
    private val window = PopupWindow(content, dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = false
        isTouchable = true
        elevation = dp(8).toFloat()
        setOnDismissListener { onDismissed() }
    }
    private var matches: List<ExpansionMatch> = emptyList()
    private var selectedIndex = 0
    private val rows = mutableListOf<TextView>()

    fun show(anchor: View, values: List<ExpansionMatch>) {
        matches = values.take(10)
        selectedIndex = 0
        render()
        content.layoutParams = ViewGroup.LayoutParams(dp(300), dp(280))
        if (!window.isShowing) {
            window.showAtLocation(anchor, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(44))
        } else {
            window.update()
        }
    }

    fun update(values: List<ExpansionMatch>) {
        matches = values.take(10)
        selectedIndex = selectedIndex.coerceAtMost(matches.lastIndex.coerceAtLeast(0))
        render()
    }

    fun moveSelection(delta: Int): Boolean {
        if (matches.isEmpty()) return false
        selectedIndex = (selectedIndex + delta).coerceIn(0, matches.lastIndex)
        updateSelection()
        return true
    }

    fun selected(): ExpansionMatch? = matches.getOrNull(selectedIndex)
    fun isShowing(): Boolean = window.isShowing
    fun dismiss() = window.dismiss()

    private fun render() {
        entries.removeAllViews()
        rows.clear()
        matches.forEachIndexed { index, match ->
            val row = TextView(context).apply {
                text = match.displayText
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 2
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { onSelected(match) }
            }
            rows.add(row)
            entries.addView(row)
        }
        updateSelection()
    }

    private fun updateSelection() {
        rows.forEachIndexed { index, row ->
            row.setBackgroundColor(if (index == selectedIndex) Color.rgb(65, 83, 125) else Color.TRANSPARENT)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
