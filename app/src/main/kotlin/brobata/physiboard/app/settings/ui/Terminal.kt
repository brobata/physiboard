package brobata.physiboard.app.settings.ui

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * The terminal skin's own pieces (app-shell.md SS22.1): the prompt-path titles, the pane border,
 * the square switch. Everything else is the Material component in the theme's mono type and
 * near-square shapes.
 */
object TerminalPath {
    /**
     * A screen title as the directory in its prompt: "Sym pages" is `sym-pages`, "Look & feel"
     * is `look-feel`. Letters and digits are kept (any script), everything else is a separator.
     */
    fun slug(title: String): String =
        title.lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifEmpty { "settings" }

    /** The full prompt, `physiboard:~/sym-pages$`. */
    fun prompt(slug: String): String = "physiboard:~/$slug$"

    /** The collapsed bar's short form, `~/sym-pages`. */
    fun short(slug: String): String = "~/$slug"
}

/**
 * A selected chip is highlighted the way a terminal highlights a selection: the accent as the
 * fill and the page's ink as the text (Signal Amber with Ink is 8.3:1, Amber Deep with white
 * 5.0:1). Material's tonal container was a brown blob next to the panes.
 */
@Composable
fun terminalChipColors(): SelectableChipColors {
    val colors = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.primary,
        selectedLabelColor = colors.onPrimary,
        selectedLeadingIconColor = colors.onPrimary,
        selectedTrailingIconColor = colors.onPrimary,
    )
}

/** The 1 dp border of a pane: slate on both themes, amber when the pane is the featured one. */
@Composable
fun paneBorderColor(featured: Boolean = false): Color = when {
    featured -> MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.7f else 0.85f)
    else -> MaterialTheme.colorScheme.outlineVariant
}

/** A terminal pane: the surface fill, a 1 dp border and the small corner of the theme's medium shape. */
@Composable
fun Modifier.terminalPane(featured: Boolean = false, shape: Shape = MaterialTheme.shapes.medium, fill: Color = MaterialTheme.colorScheme.surfaceContainer): Modifier =
    this.clip(shape).background(fill).border(1.dp, paneBorderColor(featured), shape)

/**
 * A screen's title as a shell prompt, `physiboard:~/voice$`: the host in the accent, the path in
 * full contrast, the separators muted. Read aloud as the plain [title] and marked a heading, so a
 * screen reader never spells out the punctuation. [announce] false hands the heading to the
 * collapsed bar's copy once the prompt has folded away. The size steps down for a long path so the
 * prompt always fits on one line.
 */
@Composable
fun PromptTitle(title: String, slug: String, modifier: Modifier = Modifier, maxSize: Int = 20, announce: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val path = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = accent)) { append("physiboard") }
        withStyle(SpanStyle(color = muted)) { append(":") }
        withStyle(SpanStyle(color = path)) { append("~/$slug") }
        withStyle(SpanStyle(color = accent)) { append("$") }
    }
    BoxWithConstraints(modifier = modifier.clearAndSetSemantics { if (announce) { heading(); contentDescription = title } }) {
        // JetBrains Mono's advance is 0.6 em: fit the whole prompt on the line, between 14 and maxSize sp.
        val chars = text.length.coerceAtLeast(1)
        val fit = (maxWidth.value / (chars * 0.6f)).coerceIn(14f, maxSize.toFloat())
        Text(
            text,
            style = PhysiBoardType.prompt.copy(fontSize = fit.sp, lineHeight = (fit * 1.3f).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The switch: a rounded pill in the skin's colours (amber track and a light thumb when on, a
 * filled slate track when off), sliding with a short ease. The square terminal version read as
 * clunky and was hard to tell on from off at a glance (maintainer, 2026-10-09). Same contract as
 * Material's Switch: [onCheckedChange] null makes it display-only (the row around it takes the
 * tap), and it is at least 48 dp wide and tall as a target when it takes one.
 */
@Composable
fun TerminalSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val motion = tween<Color>(180, easing = FastOutSlowInEasing)
    val track by animateColorAsState(if (checked) colors.primary else colors.outline, motion, label = "switch_track")
    val thumb by animateColorAsState(if (checked) colors.onPrimary else colors.surface, motion, label = "switch_thumb")
    val offset by animateDpAsState(if (checked) TrackWidth - ThumbSize - ThumbInset * 2 else 0.dp, tween(180, easing = FastOutSlowInEasing), label = "switch_offset")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(toggle)
            .size(width = if (onCheckedChange != null) 56.dp else TrackWidth, height = if (onCheckedChange != null) MinTouchTarget else TrackHeight)
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = TrackWidth, height = TrackHeight)
                .clip(TrackShape)
                .background(track)
                .padding(ThumbInset),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .size(ThumbSize)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(thumb),
            )
        }
    }
}

private val TrackWidth: Dp = 46.dp
private val TrackHeight: Dp = 26.dp
private val ThumbSize: Dp = 20.dp
private val ThumbInset: Dp = 3.dp
private val TrackShape = RoundedCornerShape(50)
