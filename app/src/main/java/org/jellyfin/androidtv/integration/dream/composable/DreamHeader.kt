package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.modifier.overscan
import org.jellyfin.androidtv.ui.composable.rememberCurrentTime

@Composable
fun DreamHeader(
	showClock: Boolean,
) {
	// Clock
	AnimatedVisibility(
		visible = showClock,
		enter = fadeIn(),
		exit = fadeOut(),
		modifier = Modifier.fillMaxSize(),
	) {
		Box(Modifier.fillMaxSize().overscan()) {
			val currentTime by rememberCurrentTime()
			Text(
				text = currentTime,
				fontSize = 32.sp,
				color = Color.White.copy(alpha = 0.75f),
				modifier = Modifier.align(Alignment.BottomEnd),
			)
		}
	}
}
