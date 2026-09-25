package brobata.physiboard.ime.actions

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import brobata.physiboard.core.actions.snippets.SnippetMatch

/**
 * The floating popup presentation of text expansion. spec: expansion-clipboard-pickers-launcher.md
 * SS2.5: a dark rounded panel 300 dp wide, up to 10 rows of 44 dp, at most 5 visible (the rest
 * scroll), the highlighted row in rgb 65,83,125, a row tap commits with no trailing space; it does
 * not take focus, is not dismissed by outside touches, and disappears when the matches clear.
 * 2.x anchored it to the keyboard window; 3.0 has no input view, so it sits just above the strip
 * as a [BottomOverlay] (SS11, "Popup while the keyboard window is not up: nothing is drawn").
 */
internal class ExpansionPopupController(service: InputMethodService) {

    private val panel = BottomOverlay(service, TAG)
    private var rowViews: List<TextView> = emptyList()
    private var lastRows: List<String> = emptyList()
    private var lastHighlight = -1

    fun render(rows: List<SnippetMatch>, highlight: Int, aboveBottomPx: Int, onRowTapped: (Int) -> Unit) {
        if (rows.isEmpty()) {
            hide()
            return
        }
        val labels = rows.map { it.label }
        if (labels == lastRows && highlight == lastHighlight && panel.isShown) return
        hide()
        lastRows = labels
        lastHighlight = highlight
        val context = panel.overlayContext
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rowViews = labels.mapIndexed { index, label ->
            TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(panel.dp(PADDING_H_DP), panel.dp(PADDING_V_DP), panel.dp(PADDING_H_DP), panel.dp(PADDING_V_DP))
                minHeight = panel.dp(ROW_DP)
                if (index == highlight) setBackgroundColor(HIGHLIGHT)
                setOnClickListener { onRowTapped(index) }
                column.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panel.dp(ROW_DP)))
            }
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            scrollBarDefaultDelayBeforeFade = Int.MAX_VALUE
            background = GradientDrawable().apply {
                setColor(BACKGROUND)
                cornerRadius = panel.dp(CORNER_DP).toFloat()
            }
            elevation = panel.dp(ELEVATION_DP).toFloat()
            clipToOutline = true
            addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val visibleRows = minOf(rows.size, MAX_VISIBLE_ROWS)
        panel.show(scroll, heightPx = panel.dp(ROW_DP * visibleRows), bottomMarginPx = aboveBottomPx, focusable = false, widthPx = panel.dp(WIDTH_DP))
        rowViews.getOrNull(highlight)?.let { row -> scroll.post { scroll.smoothScrollTo(0, row.top) } }
    }

    fun hide() {
        panel.hide()
        rowViews = emptyList()
        lastRows = emptyList()
        lastHighlight = -1
    }

    val isShown: Boolean get() = panel.isShown

    private companion object {
        const val TAG = "PhysiBoardExpansion"
        const val WIDTH_DP = 300
        const val ROW_DP = 44
        const val MAX_VISIBLE_ROWS = 5
        const val TEXT_SP = 14f
        const val PADDING_H_DP = 14
        const val PADDING_V_DP = 10
        const val CORNER_DP = 12
        const val ELEVATION_DP = 8
        val BACKGROUND: Int = Color.rgb(35, 35, 38)
        val HIGHLIGHT: Int = Color.rgb(65, 83, 125)
    }
}
