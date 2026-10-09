package brobata.physiboard.app.settings.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.app.settings.ui.CategoryTint
import brobata.physiboard.app.settings.ui.KeycapIcon
import brobata.physiboard.app.settings.ui.MinTouchTarget
import brobata.physiboard.app.settings.ui.PhysiBoardType
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.settings.ui.paneBorderColor
import brobata.physiboard.app.settings.ui.terminalPane
import brobata.physiboard.core.settings.DevicePrefs
import brobata.physiboard.device.privileged.broker.BrokerVerdict
import brobata.physiboard.device.privileged.toolbox.DensityReadOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Titan toolbox, featured on Home under the status card (app-shell.md SS6.4a): the part of
 * PhysiBoard no other keyboard has, so it gets the one amber-bordered pane on the page. It reads
 * as a short status listing, one `key  value` line per tool (the pairing, the backlight, the
 * notification ring, the screen density), with chips that go straight to each tool's screen.
 * Unpaired, the listing shrinks to the pairing line and the pane's primary action is the pairing
 * flow, with one line on why it is worth doing. The whole pane opens Titan tools; it is the
 * index's entry for that category.
 */
@Composable
fun TitanToolboxCard(
    device: DevicePrefs,
    verdict: BrokerVerdict?,
    keyStored: Boolean,
    density: String?,
    onNavigate: (String) -> Unit,
) {
    val ok = keyStored && verdict == BrokerVerdict.OK

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .terminalPane(featured = true)
            .clickable(role = Role.Button) { onNavigate(Routes.T2E_TOOLS) }
            .padding(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeycapIcon(Icons.Outlined.Handyman, size = 40.dp, iconSize = 22.dp, category = CategoryTint.AMBER)
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text("Titan toolbox", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "The phone itself, tuned for a keyboard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(modifier = Modifier.padding(top = Spacing.m)) {
            StatusLine("adb", pairingText(keyStored, verdict), pairingTone(keyStored, verdict))
            if (keyStored) {
                StatusLine("backlight", if (device.smartBacklightEnabled) "lit in the dark" else "stock · 30 s", if (device.smartBacklightEnabled) Tone.ON else Tone.OFF)
                StatusLine("ring", if (device.ringEnabled) "on · ${device.ringMinutes} min" else "off", if (device.ringEnabled) Tone.ON else Tone.OFF)
                StatusLine("density", density ?: if (ok) "reading…" else "needs adb", if (density != null && ok) Tone.ON else Tone.OFF)
            }
        }

        if (!keyStored) {
            Text(
                "Pair once to keep the keys lit in the dark, glow a ring for notifications and fit more on screen. It survives reboots.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.m),
            )
            Button(
                onClick = { onNavigate(Routes.T2E_TOOLS) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(top = Spacing.m).defaultMinSize(minHeight = MinTouchTarget),
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text("Pair Titan tools")
            }
        } else {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                ToolChip("backlight", Icons.Outlined.Lightbulb) { onNavigate(Routes.SMART_BACKLIGHT) }
                ToolChip("ring", Icons.Outlined.NotificationsActive) { onNavigate(Routes.NOTIFICATION_RING) }
                ToolChip("density", Icons.Outlined.AspectRatio) { onNavigate(Routes.SCREEN_DENSITY) }
            }
        }
    }
}

private enum class Tone { ON, OFF, ATTENTION }

/** One `key  value` line of the listing: the key muted and padded to a column, the value as output. */
@Composable
private fun StatusLine(key: String, value: String, tone: Tone) {
    val color = when (tone) {
        Tone.ON -> MaterialTheme.colorScheme.primary
        Tone.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
        Tone.ATTENTION -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clearAndSetSemantics { contentDescription = key; stateDescription = value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = PhysiBoardType.value, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 96.dp))
        Text(value, style = PhysiBoardType.value, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.padding(0.dp)) },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = paneBorderColor()),
        modifier = Modifier.defaultMinSize(minHeight = MinTouchTarget),
    )
}

/** The pairing line in words, the same names the Titan tools screen uses for each verdict. */
private fun pairingText(keyStored: Boolean, verdict: BrokerVerdict?): String = when {
    !keyStored -> "not paired"
    verdict == null -> "checking…"
    verdict == BrokerVerdict.OK -> "paired ✓"
    verdict == BrokerVerdict.WIRELESS_DEBUGGING_OFF -> "paired · debugging off"
    verdict == BrokerVerdict.NO_SERVICE -> "paired · unreachable"
    verdict == BrokerVerdict.REJECTED -> "pairing refused"
    else -> "not paired"
}

private fun pairingTone(keyStored: Boolean, verdict: BrokerVerdict?): Tone = when {
    keyStored && verdict == BrokerVerdict.OK -> Tone.ON
    keyStored && verdict == null -> Tone.OFF
    else -> Tone.ATTENTION
}

/**
 * The toolbox's `density` line, read once through the broker each time it is verified (SS6.4a).
 * Held by Home rather than the card, so scrolling the card away or typing a search does not ask
 * the broker again. Never asked for an unverified broker, so an unreachable one cannot hold the
 * page on a ten second timeout.
 */
@Composable
fun rememberToolboxDensity(ok: Boolean): String? {
    val application = LocalContext.current.applicationContext as PhysiBoardApplication
    var density by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ok) {
        density = null
        if (ok) {
            val outcome = withContext(Dispatchers.IO) { runCatching { application.privileged.density.read() }.getOrNull() }
            density = (outcome as? DensityReadOutcome.Loaded)?.reading?.let { r ->
                if (r.overridden) "${r.currentDpi} dpi · custom" else "${r.currentDpi} dpi · stock"
            } ?: "unreadable"
        }
    }
    return density
}
