package it.palsoftware.pastiera.inputmethod.expansion

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

class TextExpansionPopup(
    private val context: Context,
    private val onSelected: (ExpansionMatch) -> Unit,
    private val onDismissed: () -> Unit
) {
    companion object {
        private const val MAX_VISIBLE_ROWS = 5
        private const val ROW_HEIGHT_DP = 44

        internal fun popupOffsetY(popupHeight: Int): Int = -popupHeight
    }

    private val entries = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val content = ScrollView(context).apply {
        addView(entries)
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(35, 35, 38))
        }
    }
    private val window = PopupWindow(content, dp(300), dp(ROW_HEIGHT_DP), false).apply {
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
        val popupHeight = popupHeight()
        window.height = popupHeight
        val top = popupOffsetY(popupHeight)
        if (!window.isShowing) {
            window.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, top)
        } else {
            window.update(0, top, dp(300), popupHeight)
        }
    }

    fun update(anchor: View, values: List<ExpansionMatch>) {
        matches = values.take(10)
        selectedIndex = selectedIndex.coerceAtMost(matches.lastIndex.coerceAtLeast(0))
        render()
        val popupHeight = popupHeight()
        window.height = popupHeight
        val top = popupOffsetY(popupHeight)
        window.update(0, top, dp(300), popupHeight)
    }

    fun moveSelection(delta: Int): Boolean {
        if (matches.isEmpty()) return false
        selectedIndex = (selectedIndex + delta).coerceIn(0, matches.lastIndex)
        updateSelection()
        ensureSelectedVisible()
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
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(ROW_HEIGHT_DP)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(ROW_HEIGHT_DP)
                )
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener { onSelected(match) }
            }
            rows.add(row)
            entries.addView(row)
        }
        updateSelection()
        ensureSelectedVisible()
    }

    private fun updateSelection() {
        rows.forEachIndexed { index, row ->
            row.setBackgroundColor(if (index == selectedIndex) Color.rgb(65, 83, 125) else Color.TRANSPARENT)
        }
    }

    private fun popupHeight(): Int =
        dp(ROW_HEIGHT_DP) * matches.size.coerceIn(1, MAX_VISIBLE_ROWS)

    private fun ensureSelectedVisible() {
        val selected = rows.getOrNull(selectedIndex) ?: return
        content.post {
            val viewportTop = content.scrollY
            val viewportBottom = viewportTop + content.height
            when {
                selected.top < viewportTop -> content.scrollTo(0, selected.top)
                selected.bottom > viewportBottom -> content.scrollTo(0, selected.bottom - content.height)
            }
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
