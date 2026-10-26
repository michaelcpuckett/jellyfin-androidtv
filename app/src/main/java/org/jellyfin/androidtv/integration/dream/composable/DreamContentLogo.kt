package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.composable.modifier.overscan

@Composable
fun DreamContentLogo() = Box(
	modifier = Modifier
		.fillMaxSize()
		.background(Color.Black),
) {
	Box(Modifier.align(Alignment.BottomStart).overscan()) {
		Image(
			painter = painterResource(R.drawable.app_logo),
			contentDescription = stringResource(R.string.app_name),
			modifier = Modifier.width(240.dp).height(100.dp)
		)
	}
}
