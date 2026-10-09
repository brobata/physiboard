package brobata.physiboard.app.settings.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import brobata.physiboard.app.R

/**
 * The shell's one visual identity (app-shell.md SS22.1): Material 3 with dynamic colour disabled,
 * dark or light following the system, the terminal palette, JetBrains Mono for the chrome and
 * Inter for descriptions (both vendored under `res/font`, SIL Open Font License; see
 * LICENSING.md).
 */
object PhysiBoardColors {
    val Ink = Color(0xFF0F172A)
    val Slate = Color(0xFF1E293B)
    val SignalAmber = Color(0xFFF59E0B)
    val Sky = Color(0xFF38BDF8)
    val Cloud = Color(0xFFF1F5F9)
    val Slate500 = Color(0xFF64748B)

    /**
     * Muted text on the dark theme. Slate500 measured about 3.7:1 on Ink and 2.6:1 on a dialog's
     * surface, below the 4.5:1 small text needs, so descriptions and dialog bodies read as
     * disabled. Slate400 is about 7:1 on Ink and 5:1 on a dialog.
     */
    val Slate400 = Color(0xFF94A3B8)
    val ErrorDark = Color(0xFFEF4444)

    /**
     * Amber dark enough to be text on the light theme. Signal Amber is about 2:1 on white, so
     * section labels and text buttons in amber were unreadable there; this is about 5:1 on
     * Cloud and white, and stays the primary colour of the light theme only.
     */
    val AmberDeep = Color(0xFFB45309)
    val ErrorLight = Color(0xFFDC2626)

    /**
     * The `# comment` colour of the section labels: a muted amber, quieter than the accent so the
     * rows stay the loudest thing on a screen. 6.2:1 on Ink, 5.9:1 on a dark pane.
     */
    val CommentDark = Color(0xFFB8925A)

    /** The same comment on the light theme: 5.6:1 on Cloud, 6.2:1 on a white pane. */
    val CommentLight = Color(0xFF7A5C2E)

    /**
     * Muted text on the light theme. Slate500 measured 4.3:1 on Cloud, under AA for the intro
     * paragraphs that sit on the page rather than on a pane; this is 5.0:1 there and 5.4:1 on white.
     */
    val Slate550 = Color(0xFF5B6B80)

    /** A terminal pane's fill on the dark theme: a step above Ink, so the 1 dp border carries the edge. */
    val PaneDark = Color(0xFF111B2E)

    /** A pane's 1 dp border: slate on dark, a light slate on light. Decoration only; never the only cue. */
    val PaneBorderDark = Color(0xFF2A3A52)
    val PaneBorderLight = Color(0xFFCBD5E1)
}

/** The comment colour of the section labels, for the current theme. */
val MaterialThemeCommentColor: Color
    @Composable get() = if (isSystemInDarkTheme()) PhysiBoardColors.CommentDark else PhysiBoardColors.CommentLight

// Two extra roles [darkColorScheme]/[lightColorScheme] do not name in app-shell.md SS22.1: a
// "container" tone for primary/secondary/tertiary/error (chips, tonal buttons, dialog fills use
// these) and the surface-container ladder Material 3 now uses for card and sheet elevation
// instead of plain `surface`. Leaving either at the generator's own dynamic-colour-off default
// (a muted purple) is what actually produced "pale grey cards on near-white": those tokens were
// never touched, so most elevated surfaces kept Material's stock tone instead of this palette's.
// Every value below is derived from the five named colours, not invented from scratch.
private val PhysiBoardDarkColors = darkColorScheme(
    primary = PhysiBoardColors.SignalAmber,
    onPrimary = PhysiBoardColors.Ink,
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFDE68A),
    inversePrimary = Color(0xFFB45309),
    secondary = PhysiBoardColors.Sky,
    onSecondary = PhysiBoardColors.Ink,
    // Tonal buttons, selected chips and slider tracks fill with secondaryContainer. Sky's deep
    // blue there put a second accent beside the amber one on every screen; the amber container
    // keeps one accent family.
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = PhysiBoardColors.SignalAmber,
    onTertiary = PhysiBoardColors.Ink,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = PhysiBoardColors.Ink,
    onBackground = PhysiBoardColors.Cloud,
    surface = PhysiBoardColors.Slate,
    onSurface = PhysiBoardColors.Cloud,
    surfaceDim = PhysiBoardColors.Ink,
    surfaceBright = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = PhysiBoardColors.PaneDark,
    // A settings pane: one step above the page, the border does the rest (SS22.1, the terminal skin).
    surfaceContainer = PhysiBoardColors.PaneDark,
    surfaceContainerHigh = Color(0xFF263449),
    surfaceContainerHighest = Color(0xFF2E3D54),
    surfaceVariant = PhysiBoardColors.Slate,
    onSurfaceVariant = PhysiBoardColors.Slate400,
    surfaceTint = PhysiBoardColors.SignalAmber,
    inverseSurface = PhysiBoardColors.Cloud,
    inverseOnSurface = PhysiBoardColors.Ink,
    // Slate500 is only about 3:1 on a card (Slate), the floor for a switch's or field's border;
    // Slate400 keeps the off switch and the text fields clearly outlined.
    outline = PhysiBoardColors.Slate400,
    outlineVariant = PhysiBoardColors.PaneBorderDark,
    error = PhysiBoardColors.ErrorDark,
    onError = PhysiBoardColors.Ink,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
)

private val PhysiBoardLightColors = lightColorScheme(
    primary = PhysiBoardColors.AmberDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    inversePrimary = Color(0xFFFBBF24),
    secondary = PhysiBoardColors.Sky,
    onSecondary = PhysiBoardColors.Ink,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = PhysiBoardColors.AmberDeep,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = PhysiBoardColors.Cloud,
    // spec: SS22.1 only names Cloud as the light background; a card sharing that exact colour is
    // what read as "pale grey on near-white". White for `surface` (and the container ladder below)
    // is the smallest change that gives every card, sheet and dialog real separation from the page.
    surface = Color(0xFFFFFFFF),
    onBackground = PhysiBoardColors.Ink,
    onSurface = PhysiBoardColors.Ink,
    surfaceDim = Color(0xFFE2E8F0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE9EEF3),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = PhysiBoardColors.Slate550,
    surfaceTint = PhysiBoardColors.AmberDeep,
    inverseSurface = PhysiBoardColors.Ink,
    inverseOnSurface = PhysiBoardColors.Cloud,
    outline = PhysiBoardColors.Slate550,
    outlineVariant = PhysiBoardColors.PaneBorderLight,
    error = PhysiBoardColors.ErrorLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

/**
 * JetBrains Mono, vendored under `res/font` (SIL OFL 1.1). The brand face (app-shell.md SS22.1):
 * screen and dialog titles, section labels and the `physiboard:~$` prompts. Compose's weight
 * matching picks Bold for a style that asks for SemiBold, the closest of the three shipped.
 */
private val jetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Inter, vendored under `res/font` (SIL OFL 1.1, `third_party/licenses/OFL-1.1-Inter.txt`). The
 * reading face (app-shell.md SS22.1): row titles, descriptions, dialog bodies and buttons, where a
 * monospace line ran a third wider and wrapped where it did not need to.
 */
private val inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun mono(size: Int, line: Int, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = jetBrainsMono, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(fontFamily = inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

/**
 * The one type scale (app-shell.md SS22.1, the terminal skin). JetBrains Mono is the voice of the
 * chrome: titles, row labels, values, buttons, chips, fields and dialog titles. Inter is kept only
 * for what a person reads through, the descriptions and explanations (body medium and small),
 * where a monospace paragraph runs a third wider and wraps where it need not. Screens use these
 * roles and [PhysiBoardType], never a font size of their own.
 */
private val PhysiBoardTypography = Typography(
    displayLarge = mono(48, 56),
    displayMedium = mono(40, 48),
    displaySmall = mono(32, 40),
    headlineLarge = mono(28, 36),
    headlineMedium = mono(24, 32),
    headlineSmall = mono(19, 26),
    titleLarge = mono(18, 24),
    titleMedium = mono(15, 21),
    titleSmall = mono(13, 18),
    bodyLarge = mono(14, 20, FontWeight.Medium),
    bodyMedium = sans(14, 20),
    bodySmall = sans(13, 18),
    labelLarge = mono(14, 20, FontWeight.Medium),
    labelMedium = mono(12, 16, FontWeight.Medium),
    labelSmall = mono(11, 16, FontWeight.Medium),
)

/** Brand roles Material's scale has no slot for. */
object PhysiBoardType {
    /** The `physiboard:~$` prompt on Home, Setup and What's new (SS22.1: bold 18 sp amber). */
    val prompt: TextStyle = mono(18, 24)

    /** The `# comment` above each group of panes: mono, in the comment colour, lower case. */
    val sectionLabel: TextStyle = mono(13, 18, FontWeight.Medium)

    /** A row's current value at its right, styled as command output: mono, in the accent. */
    val value: TextStyle = mono(13, 18, FontWeight.Medium)

    /** Log lines, key-event dumps and report text on Diagnostics: mono so the columns line up. */
    val code: TextStyle = mono(12, 17, FontWeight.Normal)

    /** A single character or emoji shown as itself (picker cells, key tiles). */
    val glyph: TextStyle = sans(22, 28)

    /** A long paragraph read through (What's new, setup): Inter at the size of body large. */
    val reading: TextStyle = sans(16, 23)
}

/** Spacing steps every screen picks from, so gaps line up from screen to screen. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

/**
 * Terminal corners: barely rounded. Chips and fields 4 dp, panes and cards 6 dp, dialogs 8 dp, so
 * every surface reads as a pane in one terminal rather than a soft card.
 */
private val PhysiBoardShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/** The terminal-header monospace style shared by Home and the setup/what's-new pages (SS22.1). */
val TerminalPromptStyle: TextStyle
    @Composable get() = PhysiBoardType.prompt

/**
 * True when the system's animator duration scale is 0 ("reduced motion" / developer setting
 * "Remove animations"). app-shell.md SS22.1: the home header's cursor fade "is held static" then,
 * rather than reading a preference this app owns.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
}

@Composable
fun PhysiBoardTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) PhysiBoardDarkColors else PhysiBoardLightColors
    MaterialTheme(colorScheme = colorScheme, typography = PhysiBoardTypography, shapes = PhysiBoardShapes, content = content)
}
