package brobata.physiboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * The window's size in dp. Sized from the window rather than the display configuration, so
 * multi-window and the near-square Titan screen get the bounds the content is actually in.
 */
@Composable
fun windowWidthDp(): Dp {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { size.width.toDp() }
}

@Composable
fun windowHeightDp(): Dp {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { size.height.toDp() }
}
