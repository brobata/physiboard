package brobata.physiboard.app.settings.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import brobata.physiboard.app.R

/**
 * The shell's one visual identity (app-shell.md SS22.1): Material 3 with dynamic colour disabled,
 * dark or light following the system, the terminal palette, and JetBrains Mono throughout
 * (regular, medium and bold, vendored as `res/font/jetbrains_mono_*.ttf` under the SIL Open Font
 * License; see LICENSING.md).
 */
object PhysiBoardColors {
    val Ink = Color(0xFF0F172A)
    val Slate = Color(0xFF1E293B)
    val SignalAmber = Color(0xFFF59E0B)
    val Sky = Color(0xFF38BDF8)
    val Cloud = Color(0xFFF1F5F9)
    val Slate500 = Color(0xFF64748B)
    val ErrorDark = Color(0xFFEF4444)
    val ErrorLight = Color(0xFFDC2626)
}

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
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
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
    surfaceContainerLow = Color(0xFF131C2E),
    surfaceContainer = PhysiBoardColors.Slate,
    surfaceContainerHigh = Color(0xFF263449),
    surfaceContainerHighest = Color(0xFF2E3D54),
    surfaceVariant = PhysiBoardColors.Slate,
    onSurfaceVariant = PhysiBoardColors.Slate500,
    surfaceTint = PhysiBoardColors.SignalAmber,
    inverseSurface = PhysiBoardColors.Cloud,
    inverseOnSurface = PhysiBoardColors.Ink,
    outline = PhysiBoardColors.Slate500,
    outlineVariant = Color(0xFF334155),
    error = PhysiBoardColors.ErrorDark,
    onError = PhysiBoardColors.Ink,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
)

private val PhysiBoardLightColors = lightColorScheme(
    primary = PhysiBoardColors.SignalAmber,
    onPrimary = PhysiBoardColors.Ink,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    inversePrimary = Color(0xFFFBBF24),
    secondary = PhysiBoardColors.Sky,
    onSecondary = PhysiBoardColors.Ink,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = PhysiBoardColors.SignalAmber,
    onTertiary = PhysiBoardColors.Ink,
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
    onSurfaceVariant = PhysiBoardColors.Slate500,
    surfaceTint = PhysiBoardColors.SignalAmber,
    inverseSurface = PhysiBoardColors.Ink,
    inverseOnSurface = PhysiBoardColors.Cloud,
    outline = PhysiBoardColors.Slate500,
    outlineVariant = Color(0xFFCBD5E1),
    error = PhysiBoardColors.ErrorLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

/**
 * JetBrains Mono, vendored under `res/font` (SIL OFL 1.1). Only the three weights app-shell.md
 * SS22.1 names are shipped; Compose's own weight matching picks Bold for a style that asks for
 * SemiBold (the settings top bar's heading), which is the closest of the three.
 */
private val jetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val PhysiBoardTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = jetBrainsMono),
        displayMedium = base.displayMedium.copy(fontFamily = jetBrainsMono),
        displaySmall = base.displaySmall.copy(fontFamily = jetBrainsMono),
        headlineLarge = base.headlineLarge.copy(fontFamily = jetBrainsMono),
        headlineMedium = base.headlineMedium.copy(fontFamily = jetBrainsMono),
        headlineSmall = base.headlineSmall.copy(fontFamily = jetBrainsMono, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = jetBrainsMono),
        titleMedium = base.titleMedium.copy(fontFamily = jetBrainsMono, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = jetBrainsMono, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = jetBrainsMono),
        bodyMedium = base.bodyMedium.copy(fontFamily = jetBrainsMono),
        bodySmall = base.bodySmall.copy(fontFamily = jetBrainsMono),
        labelLarge = base.labelLarge.copy(fontFamily = jetBrainsMono, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = jetBrainsMono),
        labelSmall = base.labelSmall.copy(fontFamily = jetBrainsMono),
    )
}

/** The terminal-header monospace style shared by Home and the setup/what's-new pages (SS22.1). */
val TerminalPromptStyle: TextStyle
    @Composable get() = TextStyle(fontFamily = jetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 18.sp)

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
    MaterialTheme(colorScheme = colorScheme, typography = PhysiBoardTypography, content = content)
}
