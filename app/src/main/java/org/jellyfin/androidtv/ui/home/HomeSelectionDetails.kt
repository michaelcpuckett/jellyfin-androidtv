package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject

/** A text-only preview using the selected card's existing metadata. */
@Composable
internal fun HomeSelectionDetails(item: BaseRowItem?, previewMaxHeight: Dp) {
	val context = LocalContext.current
	val preferences = koinInject<UserPreferences>()
	val detailed by rememberPreference(preferences, UserPreferences.homeCinematicPreview)
	val previewHeight = if (detailed) previewMaxHeight else minOf(previewMaxHeight, 128.dp)
	val tallPreview = detailed && previewHeight >= 144.dp
	val baseItem = item?.baseItem
	val title = remember(item, context) { item?.getFullName(context) }
	val summary = remember(item, context) { item?.getSummary(context).orEmpty() }
	val background = Color(0xFF10151E)
	Box(Modifier.fillMaxWidth().height(previewHeight).clipToBounds().background(background)) {
		Column(
			modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart)
				.padding(start = 48.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
			verticalArrangement = Arrangement.spacedBy(7.dp),
		) {
			if (tallPreview) {
				val type = when (baseItem?.type) {
					BaseItemKind.MOVIE -> stringResource(R.string.home_preview_movie)
					BaseItemKind.SERIES -> stringResource(R.string.lbl_series)
					BaseItemKind.EPISODE -> stringResource(R.string.home_preview_episode)
					else -> null
				}
				val eyebrow = listOfNotNull(type, baseItem?.genres?.take(2)?.joinToString(" · ")?.takeIf(String::isNotBlank)).joinToString("  ·  ")
				if (eyebrow.isNotEmpty()) Text(eyebrow, color = Color(0xFF62C8F1), fontSize = 11.sp,
					fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			Text(title ?: stringResource(R.string.lbl_home), color = Color.White,
				fontSize = if (tallPreview) 32.sp else 28.sp, fontWeight = FontWeight.Bold,
				maxLines = 1, overflow = TextOverflow.Ellipsis)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				baseItem?.productionYear?.let { PreviewMetadata(it.toString()) }
				baseItem?.runTimeTicks?.takeIf { it >= 600_000_000L }?.let { ticks ->
					val minutes = ticks / 600_000_000L
					PreviewMetadata(if (minutes >= 60) stringResource(R.string.runtime_hours_minutes, minutes / 60, minutes % 60)
						else stringResource(R.string.runtime_minutes, minutes))
				}
				baseItem?.officialRating?.takeIf(String::isNotBlank)?.let { PreviewMetadata(it, badge = true) }
				baseItem?.communityRating?.takeIf { it > 0 && detailed }?.let {
					PreviewMetadata("★ ${String.format(java.util.Locale.getDefault(), "%.1f", it)}")
				}
			}
			if (summary.isNotEmpty() && previewHeight >= 112.dp) Text(summary, color = Color(0xFFD6DDE7), fontSize = 13.sp,
				lineHeight = 18.sp, maxLines = if (tallPreview) 2 else 1, overflow = TextOverflow.Ellipsis)
		}
	}
}

@Composable
private fun PreviewMetadata(text: String, badge: Boolean = false) {
	val shape = RoundedCornerShape(3.dp)
	Text(text, color = Color(0xFFCDD5E0), fontSize = 11.sp, maxLines = 1,
		overflow = TextOverflow.Ellipsis,
		modifier = if (badge) Modifier.background(Color.White.copy(alpha = 0.07f), shape)
			.border(1.dp, Color.White.copy(alpha = 0.2f), shape).padding(horizontal = 5.dp, vertical = 2.dp) else Modifier)
}
