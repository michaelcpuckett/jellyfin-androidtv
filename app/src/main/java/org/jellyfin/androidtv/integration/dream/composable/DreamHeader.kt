package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.modifier.overscan
import org.jellyfin.androidtv.ui.composable.rememberCurrentTime
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun DreamHeader(
	showClock: Boolean,
	nowPlaying: BaseItemDto? = null,
) {
	Column(
		verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
		horizontalAlignment = Alignment.End,
		modifier = Modifier.fillMaxSize().overscan(),
	) {
		if (nowPlaying != null) DreamNowPlaying(nowPlaying)

		AnimatedVisibility(
			visible = showClock,
			enter = fadeIn(),
			exit = fadeOut(),
		) {
			val currentTime by rememberCurrentTime()
			Text(
				text = currentTime,
				fontSize = 32.sp,
				color = Color.White.copy(alpha = 0.75f),
			)
		}
	}
}
