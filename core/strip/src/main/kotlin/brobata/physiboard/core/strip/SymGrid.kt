package brobata.physiboard.core.strip

/**
 * The on-screen surface for a key-layer Sym page (Emoji or Symbols): the three-row grid of
 * letter keys layers-sym-alt.md SS5.7 describes, drawn "inside the same chrome as the status bar
 * strip". `:core:strip` has no dependency on `:core:keys` on purpose (see this module's
 * `build.gradle.kts`), so every type here is this module's own: a caller (`:ime`) translates
 * `:core:keys`' letter keys and Sym page maps into the small alphabet [SymGridLetter] enumerates
 * and the character map [SymGridModel.rows] takes, then draws exactly what comes back and
 * forwards taps. Nothing here reads a preference, measures a view, or touches `android.*`.
 */

/** Which key-layer Sym page is open. spec: layers-sym-alt.md SS1: pages 3 and 4 are panels, not grids, and have no [SymGridPage]. */
enum class SymGridPage(val pageNumber: Int) {
    EMOJI(SYM_PAGE_EMOJI),
    SYMBOLS(SYM_PAGE_SYMBOLS),
    ;

    companion object {
        /** spec SS4.2, SS5.7: only pages 1 and 2 have a grid; every other page number (0 closed, 3, 4 panels) has none. */
        fun forPageNumber(pageNumber: Int): SymGridPage? = entries.firstOrNull { it.pageNumber == pageNumber }
    }
}

/** One of the 26 physical letter keys, named for the QWERTY letter printed on it. spec SS5.7's row order. */
enum class SymGridLetter(val letter: Char) {
    Q('Q'), W('W'), E('E'), R('R'), T('T'), Y('Y'), U('U'), I('I'), O('O'), P('P'),
    A('A'), S('S'), D('D'), F('F'), G('G'), H('H'), J('J'), K('K'), L('L'),
    Z('Z'), X('X'), C('C'), V('V'), B('B'), N('N'), M('M'),
}

/**
 * One slot of the grid before any character is looked up: a letter key, an inert filler, or one
 * of the two chrome buttons a Titan grid always carries. spec SS5.7: "With `titan2_layout_enabled`
 * (default: on for the Titan family, D6) the rows are left-aligned... row 2 gets one blank cell
 * at the end; row 3 is Z X C V, a pencil button..., a globe button..., B N M, and a blank cell."
 * `titan2_layout_enabled`'s own off-state (the centred layout for a non-Titan legend) is not
 * carried into 3.0 (`:core:settings`' `LanguagePrefs` KDoc: "the profile override,
 * `titan2_layout_enabled`... are not carried"), and 3.0 ships to the Titan 2 Elite only
 * (`rebuild-from-scratch.md`), so [SymGridLayout] only ever builds the on-state rows below.
 */
sealed class SymGridSlot {
    data class LetterKey(val letter: SymGridLetter) : SymGridSlot()
    data object Blank : SymGridSlot()

    /** spec SS5.7, SS5.8: opens the customisation screen for this page (a key long press does the same, for that key's picker). */
    data object Pencil : SymGridSlot()

    /** spec SS5.7: opens the system input-method picker. */
    data object Globe : SymGridSlot()
}

/** The grid's row layout. spec SS5.7. */
object SymGridLayout {
    /** spec SS5.7: "key width = ... divided by 10", the widest row's width. */
    const val COLUMNS: Int = 10
    const val ROWS: Int = 3

    private val TOP_ROW: List<SymGridSlot> =
        listOf(SymGridLetter.Q, SymGridLetter.W, SymGridLetter.E, SymGridLetter.R, SymGridLetter.T, SymGridLetter.Y, SymGridLetter.U, SymGridLetter.I, SymGridLetter.O, SymGridLetter.P)
            .map { SymGridSlot.LetterKey(it) }

    private val HOME_ROW_LETTERS: List<SymGridSlot> =
        listOf(SymGridLetter.A, SymGridLetter.S, SymGridLetter.D, SymGridLetter.F, SymGridLetter.G, SymGridLetter.H, SymGridLetter.J, SymGridLetter.K, SymGridLetter.L)
            .map { SymGridSlot.LetterKey(it) }

    private val BOTTOM_LEFT: List<SymGridSlot> = listOf(SymGridLetter.Z, SymGridLetter.X, SymGridLetter.C, SymGridLetter.V).map { SymGridSlot.LetterKey(it) }
    private val BOTTOM_RIGHT: List<SymGridSlot> = listOf(SymGridLetter.B, SymGridLetter.N, SymGridLetter.M).map { SymGridSlot.LetterKey(it) }

    /** spec SS5.7: "Rows: Q W E R T Y U I O P; A S D F G H J K L; Z X C V B N M", left-aligned with the Titan's own blanks and chrome buttons. */
    val rows: List<List<SymGridSlot>> = listOf(
        TOP_ROW,
        HOME_ROW_LETTERS + SymGridSlot.Blank,
        BOTTOM_LEFT + listOf(SymGridSlot.Pencil, SymGridSlot.Globe) + BOTTOM_RIGHT + listOf(SymGridSlot.Blank),
    )
}

/** The grid's pixel numbers for one screen width. spec SS5.7. */
data class SymGridGeometry(
    val keyHeightPx: Int,
    val keyWidthPx: Int,
    val spacingPx: Int,
    val cornerPx: Int,
    val borderPx: Int,
    /** spec SS5.7: "emoji at 0.75 of the key height". */
    val emojiCharacterPx: Int,
    /** spec SS5.7: "page 2 characters at 0.5 of the key height". */
    val symbolsCharacterPx: Int,
    /** spec SS5.7: "the measured grid height", computed directly here from the same row/key numbers rather than an on-screen measurement pass. */
    val contentHeightPx: Int,
    /** spec SS5.7: half of the "16 dp" the key-width formula reserves, so the row sits centred within that margin rather than flush against one edge. */
    val sideInsetPx: Int,
) {
    /** spec SS5.7: the character size for [page], "emoji at 0.75 of the key height" or "page 2 characters at 0.5". */
    fun characterPx(page: SymGridPage): Int = when (page) {
        SymGridPage.EMOJI -> emojiCharacterPx
        SymGridPage.SYMBOLS -> symbolsCharacterPx
    }

    companion object {
        const val KEY_HEIGHT_DP: Int = 56
        const val SPACING_DP: Int = 4
        const val CORNER_DP: Int = 6
        const val BORDER_DP: Int = 1

        /** spec SS5.7: "key width = (screen width in px minus 16 dp) minus 9 gaps of 4 dp, divided by 10". */
        const val SIDE_INSET_DP: Int = 16
        const val EMOJI_HEIGHT_RATIO: Double = 0.75
        const val SYMBOLS_HEIGHT_RATIO: Double = 0.5

        /** spec SS5.7: "Surface height: the measured grid height, or 600 dp until measured." A caller that must give a window manager a height before this geometry can be computed (no screen width known yet) uses this as that placeholder. */
        const val SURFACE_HEIGHT_FALLBACK_DP: Int = 600

        fun forScreenWidth(screenWidthPx: Int, pxPerDp: Float): SymGridGeometry {
            fun dp(value: Double): Int = (value * pxPerDp).toInt()
            val keyHeightPx = dp(KEY_HEIGHT_DP.toDouble())
            val spacingPx = dp(SPACING_DP.toDouble())
            val keyWidthPx = (screenWidthPx - dp(SIDE_INSET_DP.toDouble()) - (SymGridLayout.COLUMNS - 1) * spacingPx) / SymGridLayout.COLUMNS
            return SymGridGeometry(
                keyHeightPx = keyHeightPx,
                keyWidthPx = keyWidthPx,
                spacingPx = spacingPx,
                cornerPx = dp(CORNER_DP.toDouble()),
                borderPx = dp(BORDER_DP.toDouble()),
                emojiCharacterPx = (keyHeightPx * EMOJI_HEIGHT_RATIO).toInt(),
                symbolsCharacterPx = (keyHeightPx * SYMBOLS_HEIGHT_RATIO).toInt(),
                contentHeightPx = SymGridLayout.ROWS * keyHeightPx + (SymGridLayout.ROWS - 1) * spacingPx,
                sideInsetPx = dp(SIDE_INSET_DP / 2.0),
            )
        }
    }
}

/** One drawn cell: a letter key with its label and (when the page maps it) its big character, or one of the inert/chrome slots. spec SS5.7. */
sealed class SymGridCell {
    data class Key(val letter: SymGridLetter, val label: Char, val character: String?) : SymGridCell() {
        /** spec SS5.7: "Keys with no character are not tappable." */
        val isTappable: Boolean get() = character != null
    }

    data object Blank : SymGridCell()
    data object Pencil : SymGridCell()
    data object Globe : SymGridCell()
}

/** Builds what the grid draws for one open page. spec SS5.7. */
object SymGridModel {
    /**
     * [characters] is already resolved for [page] and any custom mapping (SS4.4) and for the
     * current Shift state (`:core:keys`' `CharacterResolution.symPageCharacters`, a caller's
     * concern since this module does not know `:core:keys`' types). A letter missing from it
     * draws with no big character and [SymGridCell.Key.isTappable] false.
     */
    fun rows(characters: Map<SymGridLetter, String>): List<List<SymGridCell>> = SymGridLayout.rows.map { row ->
        row.map { slot ->
            when (slot) {
                is SymGridSlot.LetterKey -> SymGridCell.Key(slot.letter, slot.letter.letter, characters[slot.letter])
                SymGridSlot.Blank -> SymGridCell.Blank
                SymGridSlot.Pencil -> SymGridCell.Pencil
                SymGridSlot.Globe -> SymGridCell.Globe
            }
        }
    }
}
