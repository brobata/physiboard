package brobata.physiboard.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import brobata.physiboard.ui.theme.Ink
import brobata.physiboard.ui.theme.SignalAmber

/**
 * PhysiBoard Terminal header.
 *
 * Ink background, a thin amber top hairline, and a monospace shell prompt
 * (`physiboard:~$`) in Signal Amber with a blinking block cursor. No Canvas,
 * no infinite dessert lattice — the blink is a single cheap alpha animation
 * that is held static when the system has animations disabled
 * (prefers-reduced-motion).
 */
@Composable
fun CustomTopBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }

    val cursorAlpha = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "terminalCursor")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorBlink"
        )
        alpha
    } else {
        1f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Ink)
            .drawBehind {
                // Thin amber top hairline.
                val strokeHeight = 2.dp.toPx()
                drawRect(
                    color = SignalAmber,
                    size = size.copy(height = strokeHeight)
                )
            }
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "physiboard:~$",
                color = SignalAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            // Blinking block cursor.
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(width = 10.dp, height = 20.dp)
                    .background(SignalAmber.copy(alpha = cursorAlpha))
            )
        }
    }
}
