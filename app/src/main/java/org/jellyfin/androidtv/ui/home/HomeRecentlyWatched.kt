package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/** Playback history belongs to the signed-in user; keep unfinished video in Continue Watching. */
internal fun homeRecentlyWatchedRequest(userId: UUID) = GetItemsRequest(
	userId = userId,
	includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
	recursive = true,
	isPlayed = true,
	sortBy = listOf(ItemSortBy.DATE_PLAYED, ItemSortBy.SORT_NAME),
	sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
	fields = ItemRepository.browseFields,
	enableUserData = true,
	imageTypeLimit = 1,
	startIndex = 0,
	limit = 24,
	enableTotalRecordCount = false,
)
