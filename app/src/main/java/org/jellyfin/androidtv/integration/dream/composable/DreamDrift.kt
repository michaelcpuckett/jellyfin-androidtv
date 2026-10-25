package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Keep persistent screensaver foreground elements moving inside the available, overscan-safe area. */
@Composable
fun DreamDrift(
	modifier: Modifier = Modifier,
	reverse: Boolean = false,
	content: @Composable () -> Unit,
) {
	val transition = rememberInfiniteTransition(label = "ScreensaverDrift")
	val horizontal by transition.animateFloat(
		initialValue = if (reverse) 1f else 0f,
		targetValue = if (reverse) 0f else 1f,
		animationSpec = infiniteRepeatable(tween(if (reverse) 112_000 else 90_000, easing = LinearEasing), RepeatMode.Reverse),
		label = "ScreensaverHorizontalPosition",
	)
	val vertical by transition.animateFloat(
		initialValue = if (reverse) 0f else 1f,
		targetValue = if (reverse) 1f else 0f,
		animationSpec = infiniteRepeatable(tween(if (reverse) 83_000 else 67_000, easing = LinearEasing), RepeatMode.Reverse),
		label = "ScreensaverVerticalPosition",
	)
	Box(modifier.fillMaxSize()) {
		Box(Modifier.layout { measurable, constraints ->
			val child = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
			layout(constraints.maxWidth, constraints.maxHeight) {
				// Animation is read during placement, without recomposing the screen every frame.
				val offset = dreamDriftOffset(
					IntSize(constraints.maxWidth, constraints.maxHeight),
					IntSize(child.width, child.height),
					horizontal,
					vertical,
				)
				child.placeRelative(offset.x, offset.y)
			}
		}) { content() }
	}
}

internal fun dreamDriftOffset(viewport: IntSize, content: IntSize, horizontal: Float, vertical: Float): IntOffset = IntOffset(
	x = ((viewport.width - content.width).coerceAtLeast(0) * horizontal.coerceIn(0f, 1f)).roundToInt(),
	y = ((viewport.height - content.height).coerceAtLeast(0) * vertical.coerceIn(0f, 1f)).roundToInt(),
)
