package brobata.physiboard.ime.actions

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import brobata.physiboard.core.actions.clipboard.Clip
import brobata.physiboard.core.actions.clipboard.ClipboardHistory
import brobata.physiboard.core.actions.clipboard.ClipboardPanelGeometry as G
import brobata.physiboard.core.strip.StripTheme

/**
 * The clipboard panel, Sym page 3. spec: expansion-clipboard-pickers-launcher.md SS3.5: 177 dp
 * tall, a header ("Clipboard History", "Clear All"), a 3-column grid of 64 dp cards, pinned cards
 * tinted with the accent, a close button at the bottom end, "No clipboard history" when empty. A
 * tap commits the clip as finished text and the panel stays open (`sym_auto_close` does not
 * apply); a long press offers Pin/Unpin and Delete. The list is re-read only when the count
 * changes, so a pin refreshes through the menu action itself.
 */
internal class ClipboardPanelController(service: InputMethodService) {

    interface Listener {
        fun onClipTapped(clip: Clip)
        fun onTogglePinned(clip: Clip)
        fun onDelete(clip: Clip)
        fun onClearAll()
        fun onClose()
    }

    private val panel = BottomOverlay(service, TAG)
    private var grid: GridLayout? = null
    private var scroll: ScrollView? = null
    private var clearAll: TextView? = null
    private var title: TextView? = null
    private var empty: TextView? = null
    private var renderedCount = -1
    private var theme: StripTheme = StripTheme.SLATE_DARK

    val isShown: Boolean get() = panel.isShown

    /**
     * [notSaving] is app-shell.md SS31.4's Sym-page indicator: while learning is off (private mode,
     * or a field that asks for none) the header says new copies are not being kept.
     */
    fun show(history: ClipboardHistory, theme: StripTheme, aboveBottomPx: Int, listener: Listener, notSaving: Boolean = false) {
        this.theme = theme
        if (!panel.isShown) {
            val root = build(listener)
            if (!panel.show(root, heightPx = panel.dp(G.HEIGHT_DP), bottomMarginPx = aboveBottomPx)) return
        }
        setNotSaving(notSaving)
        renderedCount = -1
        refresh(history, listener, scrollToTop = false)
    }

    /** spec SS3.5: re-read on a count change (or when [force] says a pin changed a card in place); the scroll position is kept unless a pin asked for the top. */
    fun refresh(history: ClipboardHistory, listener: Listener, scrollToTop: Boolean, force: Boolean = false) {
        val grid = grid ?: return
        if (!force && history.count == renderedCount) return
        renderedCount = history.count
        val scrollY = scroll?.scrollY ?: 0
        grid.removeAllViews()
        val ordered = history.ordered
        empty?.visibility = if (ordered.isEmpty()) View.VISIBLE else View.GONE
        clearAll?.isEnabled = ordered.isNotEmpty()
        clearAll?.alpha = if (ordered.isNotEmpty()) 1f else 0.4f
        ordered.forEachIndexed { index, clip ->
            val card = TextView(panel.overlayContext).apply {
                text = clip.text
                setTextColor(theme.textAndIcons)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, G.CARD_TEXT_SP.toFloat())
                maxLines = G.CARD_MAX_LINES
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(panel.dp(G.CARD_PADDING_DP), panel.dp(G.CARD_PADDING_DP), panel.dp(G.CARD_PADDING_DP), panel.dp(G.CARD_PADDING_DP))
                background = GradientDrawable().apply {
                    setColor(if (clip.pinned) withAlpha(theme.accent, 95) else theme.suggestion)
                    cornerRadius = panel.dp(G.CARD_CORNER_DP).toFloat()
                    setStroke(panel.dp(1), theme.divider)
                }
                setOnClickListener { listener.onClipTapped(clip) }
                setOnLongClickListener { showMenu(this, clip, listener); true }
            }
            val params = GridLayout.LayoutParams(GridLayout.spec(index / G.COLUMNS), GridLayout.spec(index % G.COLUMNS, 1f)).apply {
                width = 0
                height = panel.dp(G.CARD_HEIGHT_DP)
                val gap = panel.dp(G.CARD_GAP_DP)
                setMargins(if (index % G.COLUMNS == 0) 0 else gap, if (index < G.COLUMNS) 0 else gap, 0, 0)
            }
            grid.addView(card, params)
        }
        scroll?.post { scroll?.scrollTo(0, if (scrollToTop) 0 else scrollY) }
    }

    /** app-shell.md SS31.4: the header's private wording, changed in place while the page is open. */
    fun setNotSaving(notSaving: Boolean) {
        title?.text = if (notSaving) G.TITLE_NOT_SAVING else G.TITLE
    }

    fun hide() {
        panel.hide()
        grid = null
        scroll = null
        clearAll = null
        title = null
        empty = null
        renderedCount = -1
    }

    private fun build(listener: Listener): View {
        val context = panel.overlayContext
        val root = FrameLayout(context).apply { setBackgroundColor(theme.background) }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(panel.dp(G.HEADER_SIDE_PADDING_DP), panel.dp(G.HEADER_TOP_PADDING_DP), panel.dp(G.HEADER_SIDE_PADDING_DP), panel.dp(G.HEADER_BOTTOM_PADDING_DP))
            title = TextView(context).apply {
                text = G.TITLE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, G.HEADER_TEXT_SP.toFloat())
                setTextColor(withAlpha(theme.textAndIcons, 180))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            clearAll = TextView(context).apply {
                text = G.CLEAR_ALL
                setTextSize(TypedValue.COMPLEX_UNIT_SP, G.HEADER_TEXT_SP.toFloat())
                setTextColor(theme.accent)
                setOnClickListener { listener.onClearAll() }
            }
            addView(clearAll)
        }
        column.addView(header)
        grid = GridLayout(context).apply {
            columnCount = G.COLUMNS
            val pad = panel.dp(G.GRID_PADDING_DP)
            setPadding(pad, pad, pad, pad + panel.dp(G.GRID_EXTRA_BOTTOM_DP))
        }
        scroll = ScrollView(context).apply { addView(grid) }
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        empty = TextView(context).apply {
            text = G.EMPTY
            setTextSize(TypedValue.COMPLEX_UNIT_SP, G.CARD_TEXT_SP.toFloat())
            setTextColor(withAlpha(theme.textAndIcons, 128))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(closeButton(listener::onClose), FrameLayout.LayoutParams(panel.dp(G.CLOSE_WIDTH_DP), panel.dp(G.CLOSE_HEIGHT_DP), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, panel.dp(4), panel.dp(4)) })
        return root
    }

    /** spec SS3.5: the close button, 36 by 32 dp, 6 dp corners, the status bar button colour (fallback red 220,38,38 at alpha 95). */
    private fun closeButton(onClose: () -> Unit): View = TextView(panel.overlayContext).apply {
        text = "✕"
        gravity = Gravity.CENTER
        setTextColor(theme.textAndIcons)
        background = GradientDrawable().apply {
            setColor(theme.button)
            cornerRadius = panel.dp(6).toFloat()
        }
        setOnClickListener { onClose() }
    }

    private fun showMenu(anchor: View, clip: Clip, listener: Listener) {
        val menu = PopupMenu(anchor.context, anchor)
        menu.menu.add(if (clip.pinned) G.MENU_UNPIN else G.MENU_PIN).setOnMenuItemClickListener { listener.onTogglePinned(clip); true }
        menu.menu.add(G.MENU_DELETE).setOnMenuItemClickListener { listener.onDelete(clip); true }
        menu.show()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val TAG = "PhysiBoardClipPanel"
    }
}
