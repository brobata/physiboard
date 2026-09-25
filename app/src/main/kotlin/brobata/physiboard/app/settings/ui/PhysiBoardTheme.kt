package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The shell's one visual identity (app-shell.md SS22.1): Material 3 with dynamic colour disabled,
 * dark or light following the system, the terminal palette, and a monospace type scale. No
 * `JetBrains Mono` font file is vendored into `:app` yet (a licensing/asset task for whichever
 * agent brings the font and sound assets in), so [FontFamily.Monospace] stands in for it; every
 * text style still routes through this one [Typography] so swapping the family later is a
 * one-line change.
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

private val PhysiBoardDarkColors = darkColorScheme(
    primary = PhysiBoardColors.SignalAmber,
    onPrimary = PhysiBoardColors.Ink,
    secondary = PhysiBoardColors.Sky,
    tertiary = PhysiBoardColors.SignalAmber,
    background = PhysiBoardColors.Ink,
    surface = PhysiBoardColors.Slate,
    outline = PhysiBoardColors.Slate500,
    error = PhysiBoardColors.ErrorDark,
)

private val PhysiBoardLightColors = lightColorScheme(
    primary = PhysiBoardColors.SignalAmber,
    onPrimary = PhysiBoardColors.Ink,
    secondary = PhysiBoardColors.Sky,
    tertiary = PhysiBoardColors.SignalAmber,
    background = PhysiBoardColors.Cloud,
    surface = PhysiBoardColors.Cloud,
    onBackground = PhysiBoardColors.Ink,
    onSurface = PhysiBoardColors.Ink,
    outline = PhysiBoardColors.Slate500,
    error = PhysiBoardColors.ErrorLight,
)

private val monospace = FontFamily.Monospace

private val PhysiBoardTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = monospace),
        displayMedium = base.displayMedium.copy(fontFamily = monospace),
        displaySmall = base.displaySmall.copy(fontFamily = monospace),
        headlineLarge = base.headlineLarge.copy(fontFamily = monospace),
        headlineMedium = base.headlineMedium.copy(fontFamily = monospace),
        headlineSmall = base.headlineSmall.copy(fontFamily = monospace, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = monospace),
        titleMedium = base.titleMedium.copy(fontFamily = monospace),
        titleSmall = base.titleSmall.copy(fontFamily = monospace),
        bodyLarge = base.bodyLarge.copy(fontFamily = monospace),
        bodyMedium = base.bodyMedium.copy(fontFamily = monospace),
        bodySmall = base.bodySmall.copy(fontFamily = monospace),
        labelLarge = base.labelLarge.copy(fontFamily = monospace),
        labelMedium = base.labelMedium.copy(fontFamily = monospace),
        labelSmall = base.labelSmall.copy(fontFamily = monospace),
    )
}

/** The terminal-header monospace style shared by Home and the setup/what's-new pages (SS22.1). */
val TerminalPromptStyle: TextStyle
    @Composable get() = TextStyle(fontFamily = monospace, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleLarge.fontSize)

@Composable
fun PhysiBoardTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) PhysiBoardDarkColors else PhysiBoardLightColors
    MaterialTheme(colorScheme = colorScheme, typography = PhysiBoardTypography, content = content)
}
