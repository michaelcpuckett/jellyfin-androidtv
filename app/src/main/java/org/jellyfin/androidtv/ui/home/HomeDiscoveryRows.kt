package org.jellyfin.androidtv.ui.home

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal enum class HomeDiscoveryFeed { UNWATCHED, RECENT, GENRE }

internal fun homeDiscoveryLibraries(views: Collection<BaseItemDto>, excluded: Collection<UUID>): List<BaseItemDto> =
	views.filter { it.collectionType in setOf(CollectionType.MOVIES, CollectionType.TVSHOWS) && it.id !in excluded }
		.distinctBy { it.id }

internal fun homeDiscoveryRequest(library: BaseItemDto, feed: HomeDiscoveryFeed, genre: String? = null): GetItemsRequest {
	require(library.collectionType == CollectionType.MOVIES || library.collectionType == CollectionType.TVSHOWS)
	require(feed != HomeDiscoveryFeed.GENRE || !genre.isNullOrBlank())
	return GetItemsRequest(
		parentId = library.id,
		includeItemTypes = setOf(if (library.collectionType == CollectionType.MOVIES) BaseItemKind.MOVIE else BaseItemKind.SERIES),
		recursive = true,
		fields = ItemRepository.browseFields,
		imageTypeLimit = 1,
		startIndex = 0,
		limit = 24,
		enableTotalRecordCount = false,
		sortBy = listOf(if (feed == HomeDiscoveryFeed.RECENT) ItemSortBy.PREMIERE_DATE else ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME),
		sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
		isPlayed = if (feed == HomeDiscoveryFeed.UNWATCHED) false else null,
		minCommunityRating = if (feed == HomeDiscoveryFeed.UNWATCHED) 7.0 else null,
		genres = if (feed == HomeDiscoveryFeed.GENRE) setOf(requireNotNull(genre)) else null,
	)
}

/** Rotate daily through genres actually returned by the selected library, never a hard-coded list. */
internal fun homeSpotlightGenre(names: List<String?>, seed: Int): String? {
	val genres = names.filterNotNull().filter(String::isNotBlank).distinct().sorted()
	return genres.takeIf { it.isNotEmpty() }?.get(Math.floorMod(seed, genres.size))
}

/** Optional work runs after normal Home rows are attached; genre failures cannot block Home. */
internal suspend fun loadHomeDiscoveryRows(
	context: Context,
	api: ApiClient,
	views: Collection<BaseItemDto>,
	excluded: Collection<UUID>,
): List<BrowseRowDef> = buildList {
	for (library in homeDiscoveryLibraries(views, excluded)) {
		fun addFeed(title: String, feed: HomeDiscoveryFeed, genre: String? = null) {
			add(BrowseRowDef(title, homeDiscoveryRequest(library, feed, genre), 0, false, true,
				arrayOf(ChangeTriggerType.LibraryUpdated, ChangeTriggerType.MoviePlayback, ChangeTriggerType.TvPlayback)))
		}
		addFeed(context.getString(R.string.home_discovery_unwatched, library.name), HomeDiscoveryFeed.UNWATCHED)
		addFeed(context.getString(R.string.home_discovery_recent, library.name), HomeDiscoveryFeed.RECENT)
		val genres = try {
			withTimeoutOrNull(3.seconds) {
				api.genresApi.getGenres(parentId = library.id, limit = 100, sortBy = setOf(ItemSortBy.SORT_NAME),
					enableTotalRecordCount = false).content.items.map { it.name }
			}.orEmpty()
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (error: Exception) {
			Timber.w(error, "Could not load Home genre spotlight")
			emptyList()
		}
		homeSpotlightGenre(genres, library.id.hashCode() + LocalDate.now().toEpochDay().toInt())?.let { genre ->
			addFeed(context.getString(R.string.home_discovery_genre, genre, library.name), HomeDiscoveryFeed.GENRE, genre)
		}
	}
}
