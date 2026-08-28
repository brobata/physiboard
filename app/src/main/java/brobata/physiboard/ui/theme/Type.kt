package brobata.physiboard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import brobata.physiboard.R

// PhysiBoard Terminal typeface: JetBrains Mono (SIL OFL 1.1), bundled under res/font.
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

// Monospace everywhere — the whole point of the terminal brand.
private val baseline = Typography()

val Typography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = JetBrainsMono),
    displayMedium = baseline.displayMedium.copy(fontFamily = JetBrainsMono),
    displaySmall = baseline.displaySmall.copy(fontFamily = JetBrainsMono),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = JetBrainsMono),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = JetBrainsMono),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = JetBrainsMono),
    titleLarge = baseline.titleLarge.copy(fontFamily = JetBrainsMono),
    titleMedium = baseline.titleMedium.copy(fontFamily = JetBrainsMono),
    titleSmall = baseline.titleSmall.copy(fontFamily = JetBrainsMono),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = JetBrainsMono),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = JetBrainsMono),
    bodySmall = baseline.bodySmall.copy(fontFamily = JetBrainsMono),
    labelLarge = baseline.labelLarge.copy(fontFamily = JetBrainsMono),
    labelMedium = baseline.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall = baseline.labelSmall.copy(fontFamily = JetBrainsMono)
)
