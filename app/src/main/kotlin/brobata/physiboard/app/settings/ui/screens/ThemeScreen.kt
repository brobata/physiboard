package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.settings.ui.KeyboardUiPreview
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.RowList
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.core.settings.StatusBarPrefs
import brobata.physiboard.core.settings.StripTheme
import brobata.physiboard.core.settings.StripThemePresets

/**
 * "Theme" (settings-catalog.md SS9.2, status-bar.md SS9.4 in its 3.1 form): the colours the Sym
 * pages and the panels beside them (emoji picker, clipboard, GIFs, the Sym page chooser) draw
 * with. A row of presets and saved themes, a live preview of the Symbols page, then the colour
 * editor, the saved themes and the per-language overrides. The suggestion strip's own parts (its
 * buttons, height and LEDs) are gone with the strip (c61c240); their stored keys stay so a backup
 * still restores them. The modifier badge at the caret lives on Look & feel.
 */
@Composable
fun ThemeScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val controller = LocalSettingsController.current
    val statusBar = controller.current.value.statusBar
    val symPages = controller.current.value.symPages
    fun set(transform: (StatusBarPrefs) -> StatusBarPrefs) = controller.update { it.copy(statusBar = transform(it.statusBar)) }

    SettingsScreenScaffold(title = "Theme", onBack = onBack) {
        RowList {
            header("Presets")
            plainItem {
                ThemePresetRow(
                    savedThemeNames = statusBar.savedThemes.map { it.name },
                    activeTheme = statusBar.theme,
                    onApplyPreset = { theme -> set { p -> p.copy(theme = theme) } },
                    onApplySaved = { name ->
                        statusBar.savedThemes.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { named ->
                            set { p -> p.copy(theme = named.theme) }
                        }
                    },
                )
            }
            header("Preview")
            plainItem {
                KeyboardUiPreview(
                    theme = statusBar.theme,
                    characters = effectiveCharacters(isEmoji = false, customEmoji = symPages.customEmojiPage, customSymbols = symPages.customSymbolsPage),
                )
            }
            header("")
            item { NavigateRow("Colours", "Keys, buttons, text and accent", icon = Icons.Outlined.ColorLens) { onNavigate(Routes.CUSTOMIZE_COLORS) } }
            item {
                NavigateRow(
                    "Saved themes",
                    icon = Icons.Outlined.Bookmarks,
                    value = if (statusBar.savedThemes.isEmpty()) "None yet" else "${statusBar.savedThemes.size}",
                ) { onNavigate(Routes.SAVED_THEMES) }
            }
            item {
                NavigateRow(
                    "Per-language themes",
                    "A different theme for a language or layout",
                    icon = Icons.Outlined.Translate,
                    value = if (statusBar.layoutOverrides.isEmpty()) "None" else "${statusBar.layoutOverrides.size}",
                ) { onNavigate(Routes.THEME_LAYOUT_OVERRIDES) }
            }
        }
    }
}

/**
 * status-bar.md SS9.4 item 1: "a horizontal... row of cards, one per preset, then one per
 * user-saved theme... the active one is marked 'Active'." Drafts are not modelled in 3.0 (there is
 * no [brobata.physiboard.core.settings.StatusBarPrefs] field for one), so this row stops at
 * presets and saved themes.
 */
@Composable
private fun ThemePresetRow(
    savedThemeNames: List<String>,
    activeTheme: StripTheme,
    onApplyPreset: (StripTheme) -> Unit,
    onApplySaved: (String) -> Unit,
) {
    // contentPadding rather than a padding modifier, so the row scrolls out to the screen edge
    // instead of clipping the last visible card 16 dp short of it.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        contentPadding = PaddingValues(horizontal = Spacing.l),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(StripThemePresets.ALL, key = { "preset:${it.name}" }) { preset ->
            PresetCard(name = preset.name, theme = preset.theme, active = preset.theme == activeTheme, onClick = { onApplyPreset(preset.theme) })
        }
        items(savedThemeNames, key = { "saved:$it" }) { name ->
            PresetCard(name = name, theme = null, active = false, onClick = { onApplySaved(name) })
        }
    }
}

@Composable
private fun PresetCard(name: String, theme: StripTheme?, active: Boolean, onClick: () -> Unit) {
    // The active preset gets an accent outline and its own "Active" tag, so it never reads as a
    // third line of the name; a strip of the preset's own colours shows what it looks like.
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, brobata.physiboard.app.settings.ui.paneBorderColor()),
        modifier = Modifier.padding(vertical = Spacing.xs).width(112.dp).height(112.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.m)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            if (active) Text("Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.weight(1f))
            if (theme != null) {
                Row(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp))) {
                    listOf(theme.background, theme.suggestion, theme.statusBarButton, theme.accent).forEach { argb ->
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(argb)))
                    }
                }
            }
        }
    }
}
