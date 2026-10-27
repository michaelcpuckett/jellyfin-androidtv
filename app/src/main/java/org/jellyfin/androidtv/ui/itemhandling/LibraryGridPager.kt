package org.jellyfin.androidtv.ui.itemhandling

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.ui.browsing.LibraryGridWindow
import org.jellyfin.androidtv.ui.browsing.GridRange
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber

/** Cancellable, view-scoped category paging, using the server's first-letter filter. */
class LibraryGridPager internal constructor(
	private val adapter: ItemRowAdapter,
	private val scope: CoroutineScope,
	pageSize: Int,
	initialPosition: Int,
	private val alphabet: String,
	initialLetter: Char?,
	private val selection: () -> Int,
	private val onPosition: (Int) -> Unit,
	private val onFailure: () -> Unit,
	private val fetchPage: suspend (LibraryGridSource, Int, Int) -> BaseItemDtoQueryResult,
) : ItemRowAdapter.GridPaging {
	constructor(
		adapter: ItemRowAdapter, api: ApiClient, owner: LifecycleOwner, pageSize: Int,
		initialPosition: Int, alphabet: String, initialLetter: Char?, selection: () -> Int,
		onPosition: (Int) -> Unit, onFailure: () -> Unit,
	) : this(adapter, owner.lifecycleScope, pageSize, initialPosition, alphabet, initialLetter,
		selection, onPosition, onFailure, { source, start, count -> source.fetch(api, start, count) })

	private val window = LibraryGridWindow<BaseRowItem>(pageSize)
	private var job: Job? = null
	private var pendingLoad = false
	private var source: LibraryGridSource? = null
	private var letter = initialLetter
	private var restorePosition = initialPosition.coerceAtLeast(0)
	var isLoading = false
		private set
	override fun getStartIndex() = window.start

	override fun retrieve() {
		job?.cancel()
		pendingLoad = false
		isLoading = true
		val request = LibraryGridSource(adapter.itemsQuery, adapter.artistsQuery, adapter.albumArtistsQuery, letter, alphabet)
		source = request
		val target = restorePosition
		restorePosition = 0
		startRequest {
			try {
				val range = if (target > 0) window.around(target) else GridRange(0, window.pageSize)
				val page = fetchPage(request, range.start, range.count)
				currentCoroutineContext().ensureActive()
				publish(range.start, page, replace = true, target = target)
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				pendingLoad = false
				isLoading = false
				Timber.w(error, "Could not load library grid")
				onFailure()
			}
		}
	}

	override fun loadMore(position: Int) {
		if (job?.isActive == true) {
			pendingLoad = true
			return
		}
		pendingLoad = false
		val range = window.next(position) ?: return
		val request = source ?: return
		isLoading = true
		startRequest {
			try {
				val page = fetchPage(request, range.start, range.count)
				currentCoroutineContext().ensureActive()
				// Read selection after the request: the viewer can keep moving while it loads.
				val absoluteSelection = window.start + selection().coerceAtLeast(0)
				if (page.items.isNotEmpty()) publish(range.start, page, replace = false, target = absoluteSelection)
				else {
					isLoading = false
					onPosition(selection())
					pendingLoad = false
				}
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				pendingLoad = false
				isLoading = false
				Timber.w(error, "Could not page library grid")
				onFailure()
			}
		}
	}

	private fun startRequest(block: suspend () -> Unit) {
		// Set the active job before running callbacks, even with an immediate dispatcher/cache hit.
		val request = scope.launch(start = CoroutineStart.LAZY) { block() }
		job = request
		request.invokeOnCompletion { error ->
			if (error == null && job === request && pendingLoad) {
				pendingLoad = false
				scope.launch {
					if (job === request) loadMore(selection())
				}
			}
		}
		request.start()
	}

	fun filter(letter: Char?) {
		job?.cancel()
		this.letter = letter
		restorePosition = 0
		window.replace(0, emptyList(), 0)
		adapter.totalItems = 0
		adapter.itemsLoaded = 0
		adapter.clear()
		retrieve()
	}

	private fun publish(offset: Int, page: BaseItemDtoQueryResult, replace: Boolean, target: Int) {
		val rows = page.items.map { BaseItemDtoBaseRowItem(it, adapter.preferParentThumb, adapter.isStaticHeight) }
		if (replace) window.replace(offset, rows, page.totalRecordCount)
		else {
			// Keep badges/metadata refreshed while a page request was in flight.
			window.replace(window.start, adapter.filterIsInstance<BaseRowItem>(), adapter.totalItems)
			window.merge(offset, rows, page.totalRecordCount)
		}
		adapter.totalItems = window.total
		adapter.replaceAll(window.items, areContentsTheSame = { old, new -> old === new })
		adapter.itemsLoaded = window.items.size
		isLoading = false
		adapter.notifyRetrieveFinished()
		onPosition((target - window.start).coerceIn(0, (window.items.size - 1).coerceAtLeast(0)))
	}
}

/** Immutable query snapshot keeps filters, library, sort direction and user consistent across pages. */
internal class LibraryGridSource(
	val items: GetItemsRequest?,
	val artists: GetArtistsRequest?,
	val albumArtists: GetAlbumArtistsRequest?,
	val letter: Char? = null,
	val alphabet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
) {
	private val prefix = letter?.takeUnless { it == '#' }?.toString()
	private val before = alphabet.first().takeIf { letter == '#' }?.toString()
	fun itemsPage(start: Int, limit: Int) = items?.copy(
		startIndex = start, limit = limit, enableTotalRecordCount = true,
		nameStartsWith = prefix, nameLessThan = before, nameStartsWithOrGreater = null,
	)
	fun artistsPage(start: Int, limit: Int) = artists?.copy(
		startIndex = start, limit = limit, enableTotalRecordCount = true,
		nameStartsWith = prefix, nameLessThan = before, nameStartsWithOrGreater = null,
	)
	fun albumArtistsPage(start: Int, limit: Int) = albumArtists?.copy(
		startIndex = start, limit = limit, enableTotalRecordCount = true,
		nameStartsWith = prefix, nameLessThan = before, nameStartsWithOrGreater = null,
	)
	suspend fun fetch(api: ApiClient, start: Int, limit: Int): BaseItemDtoQueryResult = withContext(Dispatchers.IO) {
		when {
			items != null -> api.itemsApi.getItems(requireNotNull(itemsPage(start, limit))).content
			artists != null -> api.artistsApi.getArtists(requireNotNull(artistsPage(start, limit))).content
			else -> api.artistsApi.getAlbumArtists(requireNotNull(albumArtistsPage(start, limit))).content
		}
	}
}
