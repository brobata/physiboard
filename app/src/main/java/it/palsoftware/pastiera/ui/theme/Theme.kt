package it.palsoftware.pastiera.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark: Ink/Slate backgrounds, Cloud text, Amber accent.
private val DarkColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = Ink,
    secondary = Sky,
    onSecondary = Ink,
    tertiary = SignalAmber,
    onTertiary = Ink,
    background = Ink,
    onBackground = Cloud,
    surface = Slate,
    onSurface = Cloud,
    surfaceVariant = Slate,
    onSurfaceVariant = Slate500,
    outline = Slate500,
    error = Color(0xFFEF4444),
    onError = Ink
)

// Light: Cloud/white backgrounds, Ink/Slate text, Amber accent.
private val LightColorScheme = lightColorScheme(
    primary = SignalAmber,
    onPrimary = Ink,
    secondary = Sky,
    onSecondary = Ink,
    tertiary = SignalAmber,
    onTertiary = Ink,
    background = Cloud,
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    surfaceVariant = Cloud,
    onSurfaceVariant = Slate,
    outline = Slate500,
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun PastieraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material-You dynamic color is intentionally disabled so the Terminal brand stays consistent.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
