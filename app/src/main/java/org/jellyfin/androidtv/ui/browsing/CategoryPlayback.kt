package org.jellyfin.androidtv.ui.browsing

import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

internal const val CATEGORY_SHUFFLE_LIMIT = 100

internal fun supportsCategoryPlayback(folder: BaseItemDto): Boolean = when {
	folder.collectionType in setOf(CollectionType.MUSIC, CollectionType.MUSICVIDEOS, CollectionType.FOLDERS) -> true
	folder.type == BaseItemKind.FOLDER -> true
	folder.type in setOf(BaseItemKind.USER_VIEW, BaseItemKind.COLLECTION_FOLDER) &&
		(folder.collectionType == null || folder.collectionType == CollectionType.UNKNOWN) -> true
	else -> false
}

internal fun categoryPlaybackMediaTypes(folder: BaseItemDto): Set<MediaType> = when (folder.collectionType) {
	CollectionType.MUSIC -> setOf(MediaType.AUDIO)
	CollectionType.MUSICVIDEOS -> setOf(MediaType.VIDEO)
	else -> setOf(MediaType.AUDIO, MediaType.VIDEO)
}

internal fun categoryPlaybackRequest(folderId: UUID, userId: UUID, mediaType: MediaType, shuffle: Boolean = false) = GetItemsRequest(
	parentId = folderId,
	userId = userId,
	recursive = true,
	isMissing = false,
	filters = setOf(ItemFilter.IS_NOT_FOLDER),
	mediaTypes = setOf(mediaType),
	sortBy = if (shuffle) listOf(ItemSortBy.RANDOM) else if (mediaType == MediaType.AUDIO) listOf(
		ItemSortBy.ALBUM_ARTIST, ItemSortBy.ALBUM, ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME,
	) else listOf(ItemSortBy.SORT_NAME),
	sortOrder = listOf(SortOrder.ASCENDING),
	fields = if (mediaType == MediaType.VIDEO) ItemRepository.browseFields + setOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS)
		else ItemRepository.browseFields,
	imageTypeLimit = 1,
	enableTotalRecordCount = false,
	startIndex = 0,
	limit = if (shuffle) CATEGORY_SHUFFLE_LIMIT else 200,
)

/** Ordered playback fetches every page; shuffle fetches one server-randomized batch. */
internal suspend fun loadCategoryPlaybackItems(
	request: GetItemsRequest,
	fetch: suspend (GetItemsRequest) -> List<BaseItemDto>,
): List<BaseItemDto> {
	if (request.sortBy?.contains(ItemSortBy.RANDOM) == true) {
		val limit = minOf(request.limit ?: CATEGORY_SHUFFLE_LIMIT, CATEGORY_SHUFFLE_LIMIT)
		return fetch(request.copy(startIndex = 0, limit = limit))
			.asSequence().distinctBy { it.id }.take(limit).toList()
	}
	val items = mutableListOf<BaseItemDto>()
	val seen = mutableSetOf<UUID>()
	var offset = 0
	while (true) {
		val page = fetch(request.copy(startIndex = offset))
		val newItems = page.filter { seen.add(it.id) }
		items.addAll(newItems)
		// Also stop safely if a server ignores paging or the library changes during retrieval.
		if (newItems.isEmpty() || page.size < requireNotNull(request.limit)) break
		offset += page.size
	}
	return items
}
