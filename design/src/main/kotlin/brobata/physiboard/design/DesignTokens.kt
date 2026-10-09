package brobata.physiboard.design

/**
 * PhysiBoard's design tokens as plain numbers: the one place the settings app and the keyboard's
 * panels read a colour, a size, a corner or a timing from. docs/design/design-system.md is the
 * written form; app-shell.md SS22.1 is the settings app's use of it.
 *
 * Nothing in this file imports Android, so every figure here (the contrast pairs above all) is
 * pinned by a JVM test. Colours are ARGB as signed 32-bit ints, the way Android and the stored
 * keyboard themes hold them; sizes are dp or sp as named.
 */
object DesignTokens {

    /** The named colours of the terminal skin. Roles are in [Scheme]; use a role, not a name. */
    object Palette {
        /** The dark page, and the ink on an amber fill. */
        const val INK: Int = 0xFF0F172A.toInt()

        /** A dark pane: one step above [INK], so the 1 dp border carries the edge. */
        const val PANE_DARK: Int = 0xFF111B2E.toInt()
        const val SLATE: Int = 0xFF1E293B.toInt()
        const val SLATE_RAISED: Int = 0xFF334155.toInt()
        const val SIGNAL_AMBER: Int = 0xFFF59E0B.toInt()

        /** Amber dark enough to be text on white and Cloud (Signal Amber is about 2:1 there). */
        const val AMBER_DEEP: Int = 0xFFB45309.toInt()
        const val SKY: Int = 0xFF38BDF8.toInt()
        const val CLOUD: Int = 0xFFF1F5F9.toInt()
        const val WHITE: Int = 0xFFFFFFFF.toInt()
        const val SLATE_400: Int = 0xFF94A3B8.toInt()
        const val SLATE_500: Int = 0xFF64748B.toInt()
        const val SLATE_550: Int = 0xFF5B6B80.toInt()
        const val PANE_BORDER_DARK: Int = 0xFF2A3A52.toInt()
        const val PANE_BORDER_LIGHT: Int = 0xFFCBD5E1.toInt()
        const val COMMENT_DARK: Int = 0xFFB8925A.toInt()
        const val COMMENT_LIGHT: Int = 0xFF7A5C2E.toInt()
        const val ERROR_DARK: Int = 0xFFEF4444.toInt()
        const val ERROR_LIGHT: Int = 0xFFDC2626.toInt()
        const val AMBER_CONTAINER_DARK: Int = 0xFF78350F.toInt()
        const val ON_AMBER_CONTAINER_DARK: Int = 0xFFFDE68A.toInt()
        const val AMBER_CONTAINER_LIGHT: Int = 0xFFFEF3C7.toInt()
    }

    /**
     * The roles a screen or a panel paints with, for one theme. The keyboard's panels take their
     * colours from the user's keyboard theme instead (status-bar.md SS9.4); this scheme is what
     * the settings app, the launcher icon, the splash and the keyboard's theme-less surfaces (the
     * trackpad hint, the expansion popup, the quick launcher) use.
     */
    data class Scheme(
        val page: Int,
        val pane: Int,
        val raised: Int,
        val border: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val onAccent: Int,
        val comment: Int,
        val error: Int,
    )

    val DARK: Scheme = Scheme(
        page = Palette.INK,
        pane = Palette.PANE_DARK,
        raised = Palette.SLATE,
        border = Palette.PANE_BORDER_DARK,
        text = Palette.CLOUD,
        muted = Palette.SLATE_400,
        accent = Palette.SIGNAL_AMBER,
        onAccent = Palette.INK,
        comment = Palette.COMMENT_DARK,
        error = Palette.ERROR_DARK,
    )

    val LIGHT: Scheme = Scheme(
        page = Palette.CLOUD,
        pane = Palette.WHITE,
        raised = Palette.WHITE,
        border = Palette.PANE_BORDER_LIGHT,
        text = Palette.INK,
        muted = Palette.SLATE_550,
        accent = Palette.AMBER_DEEP,
        onAccent = Palette.WHITE,
        comment = Palette.COMMENT_LIGHT,
        error = Palette.ERROR_LIGHT,
    )

    fun scheme(dark: Boolean): Scheme = if (dark) DARK else LIGHT

    /** Spacing steps, in dp. Every gap is one of these; the side margin is [L]. */
    object Space {
        const val XS: Int = 4
        const val S: Int = 8
        const val M: Int = 12
        const val L: Int = 16
        const val XL: Int = 24
    }

    /** Corners, in dp: barely rounded, so every surface reads as a pane in one terminal. */
    object Radius {
        /** Outlined text fields. */
        const val FIELD: Int = 2

        /** Keycaps, chips, buttons, tabs, the close button. */
        const val KEY: Int = 4

        /** Panes, cards, clips, GIF tiles, popups. */
        const val PANE: Int = 6

        /** Dialogs and sheets. */
        const val DIALOG: Int = 8
    }

    /** Every border is one hairline, in dp. */
    const val BORDER_DP: Int = 1

    /** The type sizes, in sp. Mono is JetBrains Mono, sans is Inter (design-system.md, "Type"). */
    object Type {
        /** The small letter at the top left of a Sym key, the digit-and-key line under a chooser form. */
        const val KEY_LETTER_SP: Float = 10f

        /** Badges and the caret badge's words. */
        const val BADGE_SP: Float = 11f

        /** Section labels and panel titles (`# clipboard`), tab and chip labels. */
        const val LABEL_SP: Float = 13f

        /** Field text, row labels, chooser rows. */
        const val BODY_SP: Float = 14f

        /** Longer text a person reads: clip previews, status lines (sans). */
        const val READING_SP: Float = 13f

        /** A character shown as itself (picker cells, accent tiles); sans. */
        const val GLYPH_SP: Float = 22f

        /** A one-time code: mono, wide tracking. */
        const val CODE_SP: Float = 20f
        const val CODE_TRACKING_EM: Float = 0.08f
    }

    /** Opacities, 0 to 255 or 0 to 1 as named. */
    object Alpha {
        /** The accent wash under a pressed key. */
        const val PRESSED_WASH: Int = 0x40

        /** The accent wash under a selected tab or a pinned clip. */
        const val SELECTED_WASH: Int = 0x33

        /** Secondary text drawn from a theme's one text colour. */
        const val MUTED_TEXT: Float = 0.72f

        /** A control that does nothing right now. */
        const val DISABLED: Float = 0.4f
    }

    /**
     * Motion. Panels open on a spring (stiffness and damping ratio as Jetpack's SpringForce takes
     * them: critically damped enough that nothing visibly bounces) and close on a short ease;
     * a pressed key settles back on a stiffer spring. With the system's animator duration scale
     * at 0 (Remove animations) nothing moves: every surface appears and leaves in place.
     */
    object Motion {
        const val OPEN_STIFFNESS: Float = 600f
        const val OPEN_DAMPING_RATIO: Float = 0.86f

        /** How far below its place a panel starts, in dp. */
        const val OPEN_OFFSET_DP: Int = 28
        const val OPEN_FADE_MS: Long = 140
        const val CLOSE_MS: Long = 140

        /** How far a closing panel drops, in dp. */
        const val CLOSE_OFFSET_DP: Int = 16

        /** A panel replaced in place (the Sym key stepping pages): a fade only. */
        const val SWAP_FADE_MS: Long = 110

        const val PRESS_SCALE: Float = 0.94f
        const val PRESS_IN_MS: Long = 60
        const val RELEASE_STIFFNESS: Float = 900f
        const val RELEASE_DAMPING_RATIO: Float = 0.6f

        /** Settings-app timings, kept here so the two halves agree. */
        const val SWITCH_MS: Long = 180
        const val CURSOR_BREATHE_MS: Long = 1200
    }

    /**
     * WCAG 2.x contrast ratio of two opaque colours, 1 to 21. The palette's text pairs are pinned
     * against 4.5 (AA, body text) by a test; decoration (borders) only against 1.
     */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
