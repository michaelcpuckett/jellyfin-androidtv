package org.jellyfin.androidtv.integration.dream.composable

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.albumPrimaryImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

@Composable
fun DreamNowPlaying(item: BaseItemDto) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages[ImageType.PRIMARY] ?: item.albumPrimaryImage ?: item.parentImages[ImageType.PRIMARY]
	val artists = item.artists.orEmpty().ifEmpty {
		item.albumArtists?.mapNotNull { it.name }.orEmpty()
	}.ifEmpty { listOfNotNull(item.albumArtist) }

	Row(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
			.padding(12.dp),
	) {
		AsyncImage(
			url = image?.getUrl(api),
			blurHash = image?.blurHash,
			placeholder = ContextCompat.getDrawable(LocalContext.current, R.drawable.ic_album),
			scaleType = ImageView.ScaleType.CENTER_CROP,
			modifier = Modifier
				.size(64.dp)
				.clip(RoundedCornerShape(4.dp)),
		)

		Column(
			verticalArrangement = Arrangement.spacedBy(4.dp),
			modifier = Modifier.widthIn(max = 240.dp),
		) {
			Text(
				text = item.name.orEmpty(),
				color = Color.White,
				fontSize = 18.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = artists.joinToString(", "),
				color = Color.White.copy(alpha = 0.8f),
				fontSize = 14.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}
