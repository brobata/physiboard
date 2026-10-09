package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import brobata.physiboard.core.settings.StripTheme
import brobata.physiboard.core.strip.SymGridLayout
import brobata.physiboard.core.strip.SymGridSlot

/**
 * "Keyboard UI Preview" (status-bar.md SS9.4 item 2, 3.0 form): the Symbols Sym page drawn in the
 * chosen theme, because that page and the panels beside it are what the theme colours now that the
 * suggestion row is gone. It follows the live grid (layers-sym-alt.md SS5.7, `SymGridPanelController`):
 * the Titan's three left-aligned rows with their blanks, keys in [StripTheme.suggestion] with a
 * [StripTheme.divider] outline and 6 dp corners, the pencil and globe and the close button in
 * [StripTheme.statusBarButton], text in [StripTheme.textAndIcons], all on [StripTheme.background].
 * [characters] maps each letter to what the page types on it, the user's own page if they edited it.
 */
@Composable
fun KeyboardUiPreview(theme: StripTheme, characters: Map<Char, String>) {
    val keyFill = Color(theme.suggestion)
    val buttonFill = Color(theme.statusBarButton)
    val outline = Color(theme.divider)
    val ink = Color(theme.textAndIcons)
    val keyShape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(theme.background))
            // A theme whose background is close to the page's would otherwise have no edge.
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(Spacing.s)
            .semantics { contentDescription = "Preview of the Symbols page in this theme" },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SymGridLayout.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { slot ->
                    val cell = Modifier.weight(1f).height(40.dp)
                    when (slot) {
                        is SymGridSlot.LetterKey -> Box(
                            modifier = cell.clip(keyShape).background(keyFill).border(1.dp, outline, keyShape),
                        ) {
                            Text(
                                slot.letter.letter.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ink.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 3.dp, top = 1.dp),
                            )
                            Text(
                                characters[slot.letter.letter].orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = ink,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        SymGridSlot.Blank -> Spacer(modifier = cell)
                        SymGridSlot.Pencil -> ChromeKey("✏", buttonFill, outline, ink, cell)
                        SymGridSlot.Globe -> ChromeKey("🌐", buttonFill, outline, ink, cell)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier.size(width = 36.dp, height = 28.dp).clip(keyShape).background(buttonFill),
                contentAlignment = Alignment.Center,
            ) { Text("✕", style = MaterialTheme.typography.labelLarge, color = ink) }
        }
    }
}

@Composable
private fun ChromeKey(glyph: String, fill: Color, outline: Color, ink: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.clip(shape).background(fill).border(1.dp, outline, shape), contentAlignment = Alignment.Center) {
        Text(glyph, style = MaterialTheme.typography.titleSmall, color = ink)
    }
}
