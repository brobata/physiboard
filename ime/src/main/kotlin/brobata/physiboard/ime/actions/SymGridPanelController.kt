package brobata.physiboard.ime.actions

import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.core.strip.SymGridCell
import brobata.physiboard.core.strip.SymGridGeometry
import brobata.physiboard.core.strip.SymGridLetter
import brobata.physiboard.core.strip.SymGridModel
import brobata.physiboard.core.strip.SYM_PAGE_CUSTOM_1
import brobata.physiboard.core.strip.SymGridPage
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.design.R as DesignR
import brobata.physiboard.ime.skin.PanelSkin

/**
 * The on-screen surface for a key-layer Sym page (Emoji, page 1, or Symbols, page 2): the grid
 * `:core:strip`'s [SymGridModel] describes, drawn as a [BottomOverlay] like the clipboard panel
 * and emoji picker (`ClipboardPanelController`, `EmojiPickerController`). This class decides
 * nothing: which page is open, the grid's rows, which keys carry a character and how big it
 * draws are all `:core:strip`'s pure answers; this class only lays them out at the geometry's
 * pixel numbers and turns a touch into a call on [Listener].
 *
 * spec: layers-sym-alt.md SS5.7 ("What the strip shows during a session"). A tap's character is
 * not committed here: [Listener.onKeyTapped] hands the letter back to the caller, which types it
 * through the same `KeyboardPipeline.onKeyStroke` path a physical key uses (SS5.4), so auto-close
 * (`sym_auto_close`) and every other side effect of "a key on the open page was pressed" run
 * exactly once, in one place.
 */
internal class SymGridPanelController(service: InputMethodService) {

    interface Listener {
        /** spec SS5.7: "Tapping a key commits its character"; not sent for a key with no character (SS5.7: "not tappable"). */
        fun onKeyTapped(letter: Char)

        /** spec SS5.7, SS5.8: "opens the customisation screen directly on that letter's picker", or, for an emoji that takes skin tones, the skin-tone chooser (expansion-clipboard-pickers-launcher.md SS4.7). */
        fun onKeyLongPressed(letter: Char)

        /** spec SS5.7: the pencil button, "opens the customisation screen for this page". Not built in this milestone. */
        fun onPencil()

        /** spec SS5.7: the globe button, "opens the system input-method picker." */
        fun onGlobe()

        /** layers-sym-alt.md SS5.7: the Symbols page's search, into the picker's Unicode symbols. */
        fun onSearch()

        fun onClose()
    }

    private val panel = BottomOverlay(service, TAG)
    private var theme: StripTheme = StripTheme.SLATE_DARK
    private var shownPage: SymGridPage? = null

    val isShown: Boolean get() = panel.isShown

    /**
     * Shows the grid for [page], rebuilding it when the page changed while already open (the Sym
     * key can cycle straight from the Emoji page to the Symbols page without a close in between,
     * SS4.2). [characters] is `:core:keys`' already-resolved letter-to-character map for [page].
     */
    fun show(page: SymGridPage, characters: Map<Char, String>, theme: StripTheme, aboveBottomPx: Int, listener: Listener) {
        this.theme = theme
        if (panel.isShown && shownPage == page) return
        // The Sym key stepping straight to the next page swaps the panel in place, with a fade.
        val replacing = panel.isShown
        if (replacing) panel.hide(animate = false)
        shownPage = page
        val metrics = panel.overlayContext.resources.displayMetrics
        val geometry = SymGridGeometry.forScreenWidth(metrics.widthPixels, metrics.density, cornerSideInsetPx = panel.cornerInsets(aboveBottomPx).sidePx)
        val root = build(page, geometry, characters, listener)
        panel.show(
            root,
            heightPx = geometry.contentHeightPx + 2 * panel.dp(EDGE_DP),
            bottomMarginPx = aboveBottomPx,
            enter = if (replacing) DesignMotion.Enter.FADE else DesignMotion.Enter.SPRING,
        )
    }

    fun hide() {
        if (!panel.isShown) return
        panel.hide()
        shownPage = null
    }

    /** `:core:keys`' `CharacterResolution.symPageCharacters` answers by QWERTY letter; `:core:strip` has no dependency on `:core:keys` (SymGrid.kt's own KDoc), so this is the one place that bridges the two alphabets. */
    private fun toGridLetters(characters: Map<Char, String>): Map<SymGridLetter, String> =
        SymGridLetter.entries.mapNotNull { letter -> characters[letter.letter]?.let { letter to it } }.toMap()

    private fun build(page: SymGridPage, geometry: SymGridGeometry, characters: Map<Char, String>, listener: Listener): View {
        val context = panel.overlayContext
        val skin = PanelSkin(context, theme)
        val root = FrameLayout(context).apply {
            background = skin.panelBackground()
            setPadding(0, panel.dp(EDGE_DP), 0, panel.dp(EDGE_DP))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(geometry.sideInsetPx, 0, geometry.sideInsetPx, 0)
        }
        SymGridModel.rows(toGridLetters(characters), withSearch = page == SymGridPage.SYMBOLS)
            .forEachIndexed { rowIndex, row ->
                val rowView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                }
                row.forEachIndexed { cellIndex, cell ->
                    val cellView = buildCell(skin, page, geometry, cell, listener)
                    val params = LinearLayout.LayoutParams(geometry.keyWidthPx, geometry.keyHeightPx)
                    if (cellIndex > 0) params.marginStart = geometry.spacingPx
                    rowView.addView(cellView, params)
                }
                val rowParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, geometry.keyHeightPx)
                if (rowIndex > 0) rowParams.topMargin = geometry.spacingPx
                column.addView(rowView, rowParams)
            }
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(
            skin.closeButton(listener::onClose),
            FrameLayout.LayoutParams(panel.dp(CLOSE_WIDTH_DP), panel.dp(CLOSE_HEIGHT_DP), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, panel.dp(4), panel.dp(4))
            },
        )
        return root
    }

    /**
     * spec SS5.7: keycaps in Keys ([StripTheme.suggestion]) with a 1 dp Key-outline stroke and the
     * design system's 4 dp corners; the chrome keys (pencil, globe, search) in Buttons, drawn with
     * the shared icon family (docs/design/design-system.md, "Panels").
     */
    private fun buildCell(skin: PanelSkin, page: SymGridPage, geometry: SymGridGeometry, cell: SymGridCell, listener: Listener): View = when (cell) {
        is SymGridCell.Key -> buildKeyCell(skin, page, geometry, cell, listener)
        SymGridCell.Blank -> View(skin.context)
        SymGridCell.Pencil -> skin.iconButton(DesignR.drawable.pb_ic_edit, "Edit this page") { listener.onPencil() }
        SymGridCell.Globe -> skin.iconButton(DesignR.drawable.pb_ic_globe, "Switch keyboard") { listener.onGlobe() }
        SymGridCell.Search -> skin.iconButton(DesignR.drawable.pb_ic_search, "Search symbols") { listener.onSearch() }
    }

    private fun buildKeyCell(skin: PanelSkin, page: SymGridPage, geometry: SymGridGeometry, cell: SymGridCell.Key, listener: Listener): View {
        val context = skin.context
        val frame = FrameLayout(context).apply {
            background = skin.keyDrawable(radiusDp = SymGridGeometry.CORNER_DP)
        }
        frame.addView(
            skin.label(cell.label.toString(), DesignTokens.Type.KEY_LETTER_SP, PhysiFonts.Face.MONO_MEDIUM, skin.mutedText).apply {
                gravity = Gravity.TOP or Gravity.START
                setPadding(panel.dp(4), panel.dp(3), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        if (cell.character != null) {
            frame.addView(
                TextView(context).apply {
                    text = cell.character
                    gravity = Gravity.CENTER
                    setTextColor(theme.textAndIcons)
                    // A character shown as itself: the glyph face (Inter); an emoji draws in the system's emoji font either way.
                    if (page != SymGridPage.EMOJI) typeface = skin.face(PhysiFonts.Face.SANS_MEDIUM)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, geometry.characterPx(page).toFloat())
                    if (page.pageNumber >= SYM_PAGE_CUSTOM_1) {
                        // layers-sym-alt.md SS4.6: a key of the user's own may hold a short word;
                        // it shrinks to fit its key rather than spilling out of it.
                        maxLines = 1
                        val maxPx = geometry.characterPx(page).coerceAtLeast(MIN_CUSTOM_TEXT_PX + 1)
                        setAutoSizeTextTypeUniformWithConfiguration(MIN_CUSTOM_TEXT_PX, maxPx, 1, TypedValue.COMPLEX_UNIT_PX)
                    }
                },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            frame.contentDescription = "${cell.character}, ${cell.label}"
            frame.isClickable = true
            frame.setOnClickListener { listener.onKeyTapped(cell.letter.letter) }
        }
        // A key with nothing on it still says which key it is (a long press assigns it).
        if (cell.character == null) frame.contentDescription = cell.label.toString()
        frame.isLongClickable = true
        frame.setOnLongClickListener { listener.onKeyLongPressed(cell.letter.letter); true }
        DesignMotion.pressable(frame)
        return frame
    }

    private companion object {
        const val TAG = "PhysiBoardSymGrid"
        const val CLOSE_WIDTH_DP = 36
        const val CLOSE_HEIGHT_DP = 32

        /** The panel's own breathing room above and below the grid, in dp (one design-system step). */
        const val EDGE_DP = 4

        /** The smallest a word on a page of the user's own shrinks to, in pixels. */
        const val MIN_CUSTOM_TEXT_PX = 14
    }
}
