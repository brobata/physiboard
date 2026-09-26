package brobata.physiboard.app.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import brobata.physiboard.core.settings.StripTheme
import kotlin.math.roundToInt

/**
 * "Keyboard UI Preview" (status-bar.md SS9.4 item 2), the hardware page only: 3.0 has no
 * on-screen keyboard, so SS19's Keep/Drop keeps only "the hardware preview, drop[ping] the
 * virtual page and the viewport slider". Shows the suggestion row with "Werk", "Team", "Park",
 * the Shift chip (SS8.3, snapshot: "Shift physically held"), and the LED row when the theme shows
 * LEDs, with the Sym LED locked (the snapshot's "Sym page 2 open") and every other LED inactive
 * (SS7: "a plain physical hold does not light it").
 */
@Composable
fun KeyboardUiPreview(theme: StripTheme) {
    // spec SS9.4 item 2: "36 dp times the theme's suggestions scale (clamped 0.65..2.2) plus 12 dp,
    // minus 6.5 dp when LEDs are off".
    val scale = theme.suggestionsHeightScale.coerceIn(0.65, 2.2)
    val heightDp = (36.0 * scale + 12.0 - if (theme.showLeds) 0.0 else 6.5).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(Color(theme.background)),
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Werk", "Team", "Park").forEachIndexed { index, word ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape((heightDp * theme.chromeCornerRadiusRatio).dp))
                        .background(Color(theme.suggestion)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(word, color = Color(theme.textAndIcons), textAlign = TextAlign.Center)
                }
                if (index < 2) Unit
            }
        }
        // spec SS8.3: the Shift chip, 26 dp square, inset 20 dp from the left edge, active colour
        // (a physical hold, not a one-shot, which SS7 keeps off the LED but SS8.3 keeps on the chip).
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(theme.statusBarButton)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(10.dp).background(Color(theme.ledActive)))
        }
        if (theme.showLeds) {
            Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).padding(horizontal = 1.dp)) {
                // Shift, Sym, blank, blank, Ctrl, Alt (status-bar.md SS7's six positions).
                val ledColors = listOf(theme.ledInactive, theme.ledLocked, theme.background, theme.background, theme.ledInactive, theme.ledInactive)
                ledColors.forEach { color ->
                    Box(modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = 1.dp).background(Color(color)))
                }
            }
        }
    }
}
