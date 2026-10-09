package brobata.physiboard.app.settings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import brobata.physiboard.core.actions.feedback.HapticEvent
import brobata.physiboard.ime.feedback.HapticPlayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The settings app's feel (app-shell.md SS22.2, keys-and-modifiers.md SS13.5): the haptic
 * language's player, and the springs every transition shares.
 */
val LocalHaptics = staticCompositionLocalOf<HapticPlayer?> { null }

/** Plays [event] through the app's player, if there is one (previews and tests have none). */
@Composable
fun rememberHaptic(): (HapticEvent) -> Unit {
    val player = LocalHaptics.current
    return remember(player) { { event: HapticEvent -> player?.play(event) } }
}

/**
 * The springs. Critically damped for anything that moves across the screen (a page never
 * wobbles), a touch of bounce only on the switch thumb, the one thing a finger flicks.
 * Compose scales every one of them by the system's animator duration scale, so with animations
 * off (scale 0) each lands at its end at once.
 */
object SettingsMotion {
    /** A page sliding in or out: settles in about 350 ms without overshoot. */
    val page: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)
    val pageFade: FiniteAnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** An expander opening or closing, and anything else that changes size inside a pane. */
    val expandSize = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold)
    val expandFade: FiniteAnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val chevron: FiniteAnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** A row moving to its new place in a list (reorder, delete, undo). */
    val placement: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** The switch thumb: a short, slightly lively throw. */
    val thumb = spring(dampingRatio = 0.72f, stiffness = 700f, visibilityThreshold = 0.1.dp)

    fun push(reduced: Boolean): ContentTransform =
        if (reduced) EnterTransition.None togetherWith ExitTransition.None
        else slideInHorizontally(page) { it } togetherWith (slideOutHorizontally(page) { -it / 5 } + fadeOut(pageFade, targetAlpha = 0.6f))

    fun pop(reduced: Boolean): ContentTransform =
        if (reduced) EnterTransition.None togetherWith ExitTransition.None
        else slideInHorizontally(page) { -it / 5 } togetherWith slideOutHorizontally(page) { it }
}

/**
 * A screen with an inner page (Sym pages and the page being edited, Customize Variations and
 * one letter): the same push and pop as the app's own screens, each side keeping its scroll and
 * open expanders while the other shows, and predictive back on the inner page. While the back
 * gesture is held the inner page shrinks a little toward the far edge (Material's in-app
 * predictive back); letting go goes back, cancelling springs it home.
 */
@Composable
fun <K : Any> InnerPages(
    detail: K?,
    onCloseDetail: () -> Unit,
    detailKey: (K) -> String = { it.toString() },
    list: @Composable () -> Unit,
    detailContent: @Composable (K) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    val edge = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = detail != null) { events ->
        try {
            events.collect { event ->
                edge.snapTo(if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f)
                progress.snapTo(event.progress)
            }
            onCloseDetail()
        } catch (e: CancellationException) {
            // The gesture's job is already cancelled here, so the spring home runs outside it.
            withContext(NonCancellable) { progress.animateTo(0f, SettingsMotion.expandFade) }
            throw e
        }
    }
    AnimatedContent(
        targetState = detail,
        transitionSpec = { if (targetState != null) SettingsMotion.push(reduced) else SettingsMotion.pop(reduced) },
        contentKey = { it?.let(detailKey) ?: LIST_KEY },
        label = "inner_pages",
    ) { shown ->
        // The page shrunk by a completed back gesture keeps that size while it slides out, and
        // the gesture's progress is cleared once it has gone.
        if (shown == null && transition.currentState == transition.targetState) {
            LaunchedEffect(Unit) { progress.snapTo(0f) }
        }
        if (shown == null) {
            holder.SaveableStateProvider(LIST_KEY) { list() }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val p = progress.value
                    val s = 1f - 0.1f * p
                    scaleX = s
                    scaleY = s
                    translationX = edge.value * p * 24.dp.toPx()
                    transformOrigin = TransformOrigin(if (edge.value >= 0f) 1f else 0f, 0.5f)
                },
            ) {
                holder.SaveableStateProvider(detailKey(shown)) { detailContent(shown) }
            }
        }
    }
}

private const val LIST_KEY = "__list__"
