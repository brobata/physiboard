package brobata.physiboard.ime.actions

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
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.ime.skin.PanelSkin

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
    private var skin: PanelSkin? = null

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
        val skin = skin ?: PanelSkin(panel.overlayContext, theme)
        ordered.forEachIndexed { index, clip ->
            // A clip is text a person reads (Inter); a pinned one carries the accent's wash and outline.
            val card = skin.reading(clip.text, G.CARD_TEXT_SP.toFloat()).apply {
                maxLines = G.CARD_MAX_LINES
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(panel.dp(G.CARD_PADDING_DP), panel.dp(G.CARD_PADDING_DP), panel.dp(G.CARD_PADDING_DP), panel.dp(G.CARD_PADDING_DP))
                background = if (clip.pinned) {
                    skin.keyDrawable(fill = PanelSkin.blend(theme.suggestion, theme.accent, DesignTokens.Alpha.SELECTED_WASH), stroke = theme.accent, radiusDp = G.CARD_CORNER_DP)
                } else {
                    skin.keyDrawable(radiusDp = G.CARD_CORNER_DP)
                }
                if (clip.pinned) contentDescription = "Pinned: ${clip.text}"
                setOnClickListener { listener.onClipTapped(clip) }
                setOnLongClickListener { showMenu(this, clip, listener); true }
                DesignMotion.pressable(this)
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
        val title = title ?: return
        skin?.setComment(title, if (notSaving) G.TITLE_NOT_SAVING else G.TITLE)
    }

    fun hide() {
        panel.hide()
        grid = null
        scroll = null
        clearAll = null
        title = null
        empty = null
        skin = null
        renderedCount = -1
    }

    private fun build(listener: Listener): View {
        val context = panel.overlayContext
        val skin = PanelSkin(context, theme).also { skin = it }
        val root = FrameLayout(context).apply { background = skin.panelBackground() }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(panel.dp(G.HEADER_SIDE_PADDING_DP), panel.dp(G.HEADER_TOP_PADDING_DP), panel.dp(G.HEADER_SIDE_PADDING_DP), panel.dp(G.HEADER_BOTTOM_PADDING_DP))
            title = skin.comment(G.TITLE)
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            clearAll = skin.textButton(G.CLEAR_ALL) { listener.onClearAll() }
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
        empty = skin.label(G.EMPTY, DesignTokens.Type.BODY_SP, color = skin.mutedText).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(skin.closeButton(listener::onClose), FrameLayout.LayoutParams(panel.dp(G.CLOSE_WIDTH_DP), panel.dp(G.CLOSE_HEIGHT_DP), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, panel.dp(4), panel.dp(4)) })
        return root
    }

    private fun showMenu(anchor: View, clip: Clip, listener: Listener) {
        val menu = PopupMenu(anchor.context, anchor)
        menu.menu.add(if (clip.pinned) G.MENU_UNPIN else G.MENU_PIN).setOnMenuItemClickListener { listener.onTogglePinned(clip); true }
        menu.menu.add(G.MENU_DELETE).setOnMenuItemClickListener { listener.onDelete(clip); true }
        menu.show()
    }

    private companion object {
        const val TAG = "PhysiBoardClipPanel"
    }
}
