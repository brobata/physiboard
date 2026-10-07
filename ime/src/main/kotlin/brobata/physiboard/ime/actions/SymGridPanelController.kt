package brobata.physiboard.ime.actions

import android.graphics.drawable.GradientDrawable
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
        if (panel.isShown) panel.hide()
        shownPage = page
        val metrics = panel.overlayContext.resources.displayMetrics
        val geometry = SymGridGeometry.forScreenWidth(metrics.widthPixels, metrics.density)
        val root = build(page, geometry, characters, listener)
        panel.show(root, heightPx = geometry.contentHeightPx, bottomMarginPx = aboveBottomPx)
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
        val root = FrameLayout(context).apply { setBackgroundColor(theme.background) }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(geometry.sideInsetPx, 0, geometry.sideInsetPx, 0)
        }
        SymGridModel.rows(toGridLetters(characters))
            .forEachIndexed { rowIndex, row ->
                val rowView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START
                }
                row.forEachIndexed { cellIndex, cell ->
                    val cellView = buildCell(context, page, geometry, cell, listener)
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
            closeButton(listener::onClose),
            FrameLayout.LayoutParams(panel.dp(CLOSE_WIDTH_DP), panel.dp(CLOSE_HEIGHT_DP), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, panel.dp(4), panel.dp(4))
            },
        )
        return root
    }

    /**
     * spec SS5.7: "Key corners 6 dp, 1 dp divider stroke". The reduced [StripTheme] (status-bar.md
     * SS9.1's Keep/Drop) has no `normal_key` field of its own; the Slate Dark default sets
     * `normal_key` to the same value as `suggestion` (status-bar.md SS9.2: both 0xFF15191D), so
     * [StripTheme.suggestion] stands in for it here.
     */
    private fun buildCell(context: android.content.Context, page: SymGridPage, geometry: SymGridGeometry, cell: SymGridCell, listener: Listener): View = when (cell) {
        is SymGridCell.Key -> buildKeyCell(context, page, geometry, cell, listener)
        SymGridCell.Blank -> View(context)
        SymGridCell.Pencil -> chromeCell(context, geometry, "✏") { listener.onPencil() }
        SymGridCell.Globe -> chromeCell(context, geometry, "🌐") { listener.onGlobe() }
    }

    private fun buildKeyCell(context: android.content.Context, page: SymGridPage, geometry: SymGridGeometry, cell: SymGridCell.Key, listener: Listener): View {
        val frame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(theme.suggestion)
                setStroke(geometry.borderPx, theme.divider)
                cornerRadius = geometry.cornerPx.toFloat()
            }
        }
        frame.addView(
            TextView(context).apply {
                text = cell.label.toString()
                gravity = Gravity.TOP or Gravity.START
                setTextColor(theme.textAndIcons)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP)
                setPadding(panel.dp(3), panel.dp(1), 0, 0)
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        if (cell.character != null) {
            frame.addView(
                TextView(context).apply {
                    text = cell.character
                    gravity = Gravity.CENTER
                    setTextColor(theme.textAndIcons)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
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
            frame.isClickable = true
            frame.setOnClickListener { listener.onKeyTapped(cell.letter.letter) }
        }
        frame.isLongClickable = true
        frame.setOnLongClickListener { listener.onKeyLongPressed(cell.letter.letter); true }
        return frame
    }

    private fun chromeCell(context: android.content.Context, geometry: SymGridGeometry, glyph: String, onTap: () -> Unit): View = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextColor(theme.textAndIcons)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, GLYPH_TEXT_SP)
        background = GradientDrawable().apply {
            setColor(theme.button)
            setStroke(geometry.borderPx, theme.divider)
            cornerRadius = geometry.cornerPx.toFloat()
        }
        setOnClickListener { onTap() }
    }

    /** spec SS5.7: the close button, 36 by 32 dp, matching the clipboard and emoji picker panels' own (`ClipboardPanelController.closeButton`). */
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

    private companion object {
        const val TAG = "PhysiBoardSymGrid"
        const val CLOSE_WIDTH_DP = 36
        const val CLOSE_HEIGHT_DP = 32

        /** SPEC GAP: SS5.7 sizes only the big character (0.75 / 0.5 of the key height); the small letter label's size is unstated. 10 sp reads as "small" beside a 52-78 px character without crowding the 56 dp key. */
        const val LABEL_TEXT_SP = 10f
        const val GLYPH_TEXT_SP = 20f

        /** The smallest a word on a page of the user's own shrinks to, in pixels. */
        const val MIN_CUSTOM_TEXT_PX = 14
    }
}
