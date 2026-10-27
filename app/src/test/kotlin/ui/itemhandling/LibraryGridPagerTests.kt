package org.jellyfin.androidtv.ui.itemhandling

import androidx.leanback.widget.Presenter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import java.io.IOException
import java.util.UUID

private class ArtistGridFixture(
	val fetch: suspend (GetArtistsRequest) -> BaseItemDtoQueryResult,
) {
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
	val adapter = spyk(ItemRowAdapter(mockk(), GetArtistsRequest(parentId = UUID.randomUUID()), 30, mockk<Presenter>(), null), recordPrivateCalls = true)
	var selected = 0
	var failures = 0
	var onPublished: (() -> Unit)? = null
	val pager = LibraryGridPager(adapter, scope, 30, 0, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", null,
		{ selected }, { selected = it; onPublished?.invoke() }, { failures++ },
		{ source, start, count -> fetch(requireNotNull(source.artistsPage(start, count))) })

	init {
		// Keep the real adapter data/diff/paging behavior; Android's JVM Observable stub has no observer list.
		every { adapter["notifyItemRangeInserted"](any<Int>(), any<Int>()) } returns Unit
		every { adapter["notifyItemRangeRemoved"](any<Int>(), any<Int>()) } returns Unit
		every { adapter["notifyItemMoved"](any<Int>(), any<Int>()) } returns Unit
		every { adapter.notifyItemRangeChanged(any(), any(), any()) } returns Unit
		adapter.setGridPaging(pager)
	}
	fun ids() = adapter.filterIsInstance<BaseRowItem>().map { requireNotNull(it.baseItem).id }
}

class LibraryGridPagerTests : FunSpec({
	val artists = (0 until 569).map { index ->
		val first = if (index < 7) '0' else 'A' + ((index - 7) / 22)
		BaseItemDto(id = UUID(0, index.toLong()), type = BaseItemKind.MUSIC_ARTIST, name = "$first Artist $index")
	}
	fun response(query: GetArtistsRequest, cap: Int = 12): BaseItemDtoQueryResult {
		val matches = artists.filter { item ->
			(query.nameStartsWith == null || item.name.orEmpty().startsWith(query.nameStartsWith!!)) &&
				(query.nameLessThan == null || item.name.orEmpty() < query.nameLessThan!!)
		}
		val start = query.startIndex ?: 0
		return BaseItemDtoQueryResult(matches.drop(start).take(minOf(query.limit ?: cap, cap)), matches.size, start)
	}

	test("clearing a music letter filter can browse all 569 artists beyond the initial numbers and A rows") {
		val calls = mutableListOf<GetArtistsRequest>()
		val grid = ArtistGridFixture { query -> calls.add(query); response(query) }
		try {
			// Leanback emits selection callbacks while publishing a page, before its coroutine completes.
			grid.onPublished = {
				if (grid.adapter.size() == 12) grid.selected = 7
				grid.adapter.loadMoreItemsIfNeeded(grid.selected)
			}
			grid.pager.filter('M')
			grid.adapter.totalItems shouldBe 22
			grid.adapter.size() shouldBe 22
			calls.clear()
			grid.pager.filter(null)
			grid.adapter.totalItems shouldBe 569
			(grid.adapter.size() > 12) shouldBe true
			var attempts = 0
			while (grid.adapter.size() < 569 && attempts++ < 100) {
				grid.selected = grid.adapter.size() - 1
				grid.adapter.loadMoreItemsIfNeeded(grid.selected)
			}
			grid.adapter.size() shouldBe 569
			grid.ids() shouldBe artists.map { it.id }
			grid.failures shouldBe 0
			calls.forEach { query ->
				query.nameStartsWith shouldBe null
				query.nameLessThan shouldBe null
				query.enableTotalRecordCount shouldBe true
			}
		} finally { grid.scope.cancel() }
	}
	test("a next-page request made while the first page loads runs after completion exactly once") {
		val first = CompletableDeferred<BaseItemDtoQueryResult>()
		val calls = mutableListOf<Int>()
		val grid = ArtistGridFixture { query ->
			calls.add(query.startIndex!!)
			if (query.startIndex == 0) first.await() else response(query)
		}
		try {
			grid.adapter.Retrieve()
			grid.selected = 7
			repeat(5) { grid.adapter.loadMoreItemsIfNeeded(7) }
			calls shouldBe listOf(0)
			first.complete(response(GetArtistsRequest(startIndex = 0, limit = 30)))
			calls shouldBe listOf(0, 12)
			grid.ids() shouldBe artists.take(24).map { it.id }
			grid.pager.isLoading shouldBe false
		} finally { grid.scope.cancel() }
	}
	test("a cancelled old letter response cannot replace All even if the transport finishes late") {
		val old = CompletableDeferred<BaseItemDtoQueryResult>()
		val grid = ArtistGridFixture { query ->
			if (query.nameStartsWith == "M") withContext(NonCancellable) { old.await() } else response(query)
		}
		try {
			grid.pager.filter('M')
			grid.pager.filter(null)
			grid.adapter.totalItems shouldBe 569
			val allIds = grid.ids()
			old.complete(response(GetArtistsRequest(nameStartsWith = "M", startIndex = 0, limit = 30)))
			grid.adapter.totalItems shouldBe 569
			grid.ids() shouldBe allIds
			grid.failures shouldBe 0
		} finally { grid.scope.cancel() }
	}
	test("a failed page does not turn a deferred request into an automatic retry loop") {
		var calls = 0
		lateinit var grid: ArtistGridFixture
		grid = ArtistGridFixture {
			calls++
			grid.adapter.loadMoreItemsIfNeeded(7)
			throw IOException("offline")
		}
		try {
			grid.adapter.Retrieve()
			calls shouldBe 1
			grid.failures shouldBe 1
			grid.pager.isLoading shouldBe false
		} finally { grid.scope.cancel() }
	}
})
