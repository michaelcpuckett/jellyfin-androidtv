package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.sdk.model.api.BaseItemDto

internal class VideoQueueItem(item: BaseItemDto, index: Int, current: Boolean) :
	BaseItemDtoBaseRowItem(item, staticHeight = true) {
	val index = index
	val current = current

	override fun getCardName(context: Context) = "${index + 1}. ${super.getCardName(context).orEmpty()}"
	override fun getSubText(context: Context) = if (current) context.getString(R.string.lbl_now_playing) else super.getSubText(context)

	// The same video can appear more than once; changing its current marker must also rebind the card.
	override fun equals(other: Any?) = other is VideoQueueItem && baseItem == other.baseItem && index == other.index && current == other.current
	override fun hashCode() = 31 * (31 * baseItem.hashCode() + index) + current.hashCode()
}
