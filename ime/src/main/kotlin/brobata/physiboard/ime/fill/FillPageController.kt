package brobata.physiboard.ime.fill

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.actions.fill.FillLabels
import brobata.physiboard.core.actions.fill.OneTimeCode
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.ime.skin.PanelSkin
import brobata.physiboard.ime.actions.BottomOverlay

/**
 * The Fill page, Sym page 10. spec: layers-sym-alt.md SS4.7. A panel at the bottom of the screen
 * like the other Sym panels: a password manager's suggestions for this field as chips (its pinned
 * chips first, never scrolled away), then the one-time codes newest first, each with the key
 * that types it (the key printed with 1, 2, 3...), the code, and "Code from Messages · 2 min ago".
 * A tap on a code types it; a tap on a chip lets the password manager fill the field. The ages are
 * redrawn every [AGE_REFRESH_MS] while the page is up.
 */
internal class FillPageController(service: InputMethodService, private val handler: Handler) {

    interface Listener {
        fun onCode(code: OneTimeCode)
        fun onClose()
    }

    /** What the page shows; read again on every redraw. */
    interface Content {
        val codes: List<OneTimeCode>
        val inline: InlineFill
        val nowMs: Long

        /** The key label for pick key [digit] (1 to 9), the letter printed with that digit; null when no key has it. */
        fun keyLabel(digit: Int): String?

        /** The page's text when it has nothing. */
        val emptyNote: String
    }

    private val panel = BottomOverlay(service, TAG)
    private var codesBox: LinearLayout? = null
    private var listener: Listener? = null
    private var content: Content? = null
    private var theme: StripTheme = StripTheme.SLATE_DARK
    private var skin: PanelSkin? = null

    /** The suggestions the open page drew chips for; a new response redraws the page. */
    private var shownSuggestions: List<Any> = emptyList()
    private val ageRefresh = object : Runnable {
        override fun run() {
            renderCodes()
            if (panel.isShown) handler.postDelayed(this, AGE_REFRESH_MS)
        }
    }

    val isShown: Boolean get() = panel.isShown

    /** The codes as the page last drew them, for the pick keys: a code arriving between the drawing and a press never shifts what a key types. */
    val shownCodes: List<OneTimeCode> get() = if (panel.isShown) renderedCodes else emptyList()
    private var renderedCodes: List<OneTimeCode> = emptyList()

    fun show(theme: StripTheme, aboveBottomPx: Int, content: Content, listener: Listener) {
        this.listener = listener
        this.content = content
        if (panel.isShown && theme == this.theme && content.inline.suggestions === shownSuggestions) {
            renderCodes()
            return
        }
        // A new response (or theme) while the page is up redraws it in place, with a fade.
        val replacing = panel.isShown
        hide(animate = false)
        this.listener = listener
        this.content = content
        this.theme = theme
        shownSuggestions = content.inline.suggestions
        val root = build(content)
        if (!panel.show(root, heightPx = null, bottomMarginPx = aboveBottomPx, enter = if (replacing) DesignMotion.Enter.FADE else DesignMotion.Enter.SPRING)) {
            codesBox = null
            return
        }
        renderCodes()
        handler.removeCallbacks(ageRefresh)
        handler.postDelayed(ageRefresh, AGE_REFRESH_MS)
    }

    /** Redraws the codes (one was typed, one arrived, or one expired). */
    fun refresh() {
        if (panel.isShown) renderCodes()
    }

    fun hide(animate: Boolean = true) {
        handler.removeCallbacks(ageRefresh)
        panel.hide(animate)
        codesBox = null
        content = null
        listener = null
        shownSuggestions = emptyList()
        renderedCodes = emptyList()
    }

    private fun build(content: Content): View {
        val context = panel.overlayContext
        val skin = PanelSkin(context, theme).also { skin = it }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = skin.panelBackground()
            setPadding(panel.dp(8), panel.dp(6), panel.dp(6), panel.dp(8))
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(skin.comment(TITLE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(skin.closeButton { listener?.onClose() }, LinearLayout.LayoutParams(panel.dp(PanelSkin.CLOSE_WIDTH_DP), panel.dp(PanelSkin.CLOSE_HEIGHT_DP)))
        column.addView(header)
        if (content.inline.hasSuggestions) column.addView(buildChips(content.inline), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        codesBox = box
        column.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return column
    }

    /** The manager's pinned chips stay put at the start; the rest scroll beside them. */
    private fun buildChips(inline: InlineFill): View {
        val context = panel.overlayContext
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, panel.dp(4), 0, panel.dp(4))
        }
        val pinned = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val scrolling = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(scrolling)
        }
        row.addView(pinned)
        row.addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val chipHeight = panel.dp(InlineFill.CHIP_HEIGHT_DP)
        // Slots in order, so chips the manager draws out of order still land in its order.
        inline.suggestions.forEach { suggestion ->
            val target = if (suggestion.info.isPinned) pinned else scrolling
            val slot = LinearLayout(context)
            target.addView(slot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight).apply { marginEnd = panel.dp(6) })
            inline.inflate(context, suggestion) { view ->
                if (codesBox == null) return@inflate
                slot.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight))
            }
        }
        return row
    }

    private fun renderCodes() {
        val box = codesBox ?: return
        val content = content ?: return
        val skin = skin ?: return
        box.removeAllViews()
        val codes = content.codes
        renderedCodes = codes
        if (codes.isEmpty()) {
            if (content.inline.hasSuggestions) return
            box.addView(
                skin.reading(content.emptyNote, DesignTokens.Type.BODY_SP, skin.mutedText).apply {
                    setPadding(panel.dp(2), panel.dp(10), panel.dp(8), panel.dp(8))
                },
            )
            return
        }
        codes.forEachIndexed { index, code ->
            val row = LinearLayout(skin.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(panel.dp(8), panel.dp(8), panel.dp(10), panel.dp(8))
                background = skin.keyDrawable(radiusDp = DesignTokens.Radius.PANE)
                setOnClickListener { listener?.onCode(code) }
                contentDescription = "Type code ${code.code.toCharArray().joinToString(" ")}, ${FillLabels.codeLine(code, content.nowMs)}"
                DesignMotion.pressable(this)
            }
            val key = content.keyLabel(index + 1)
            // The key that types it, as a keycap: the same 4 dp cap and mono letter as a Sym key.
            row.addView(
                skin.label(key ?: "${index + 1}", DesignTokens.Type.LABEL_SP, PhysiFonts.Face.MONO_BOLD).apply {
                    gravity = Gravity.CENTER
                    background = skin.rounded(theme.button, theme.divider, DesignTokens.Radius.KEY)
                },
                LinearLayout.LayoutParams(panel.dp(28), panel.dp(28)).apply { marginEnd = panel.dp(12) },
            )
            row.addView(
                skin.label(code.code, DesignTokens.Type.CODE_SP, PhysiFonts.Face.MONO_BOLD).apply {
                    letterSpacing = DesignTokens.Type.CODE_TRACKING_EM
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = panel.dp(12) },
            )
            row.addView(
                skin.reading(FillLabels.codeLine(code, content.nowMs), 12f, skin.mutedText).apply { maxLines = 2 },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            box.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = panel.dp(6) })
        }
    }

    private companion object {
        const val TAG = "PhysiBoardFill"
        const val TITLE = "Fill: press a code's key, or tap"
        const val AGE_REFRESH_MS = 30_000L
    }
}
