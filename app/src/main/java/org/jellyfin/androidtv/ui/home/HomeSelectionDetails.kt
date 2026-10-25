package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem

/** Reads the selected row's existing data; selecting a card never starts a metadata request. */
@Composable
internal fun HomeSelectionDetails(item: BaseRowItem?) {
	val context = LocalContext.current
	val title = remember(item, context) { item?.getFullName(context) }
	val subtitle = remember(item, context) {
		listOfNotNull(
			item?.getSubText(context)?.takeIf(String::isNotBlank),
			item?.baseItem?.productionYear?.toString(),
			item?.baseItem?.officialRating?.takeIf(String::isNotBlank),
		).distinct().joinToString("  ·  ")
	}
	val summary = remember(item, context) { item?.getSummary(context).orEmpty() }
	Column(
		modifier = Modifier.fillMaxWidth().height(128.dp).padding(start = 48.dp, end = 40.dp, top = 4.dp, bottom = 16.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = title ?: stringResource(R.string.lbl_home),
			color = Color.White,
			fontSize = 28.sp,
			fontWeight = FontWeight.Bold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		if (subtitle.isNotEmpty()) Text(subtitle, color = Color(0xFFB5B7BB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		if (summary.isNotEmpty()) Text(summary, color = Color(0xFFD6D7DA), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
	}
}
