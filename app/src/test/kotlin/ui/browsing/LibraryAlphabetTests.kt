package org.jellyfin.androidtv.ui.browsing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.itemhandling.LibraryGridSource
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

class LibraryAlphabetTests : FunSpec({
	test("returning from details restores a distant filtered position with aligned columns") {
		val window = LibraryGridWindow<Int>(30)
		val range = window.around(4001)
		range shouldBe GridRange(3960, 90)
		window.replace(range.start, (3960 until 4050).toList(), 4500)
		window.items[4001 - window.start] shouldBe 4001
		window.start % LibraryPosterGrid.COLUMNS shouldBe 0
		window.next(41) shouldBe null
	}
	test("paging backward preserves the selected item's absolute position and all columns") {
		val window = LibraryGridWindow<Int>(30)
		window.replace(3960, (3960 until 4050).toList(), 4500)
		val selected = window.start + 7
		window.next(7) shouldBe GridRange(3930, 30)
		window.merge(3930, (3930 until 3960).toList(), 4500)
		window.items[selected - window.start] shouldBe 3967
		(selected - window.start) % 6 shouldBe 7 % 6
		window.items shouldBe (3930 until 4050).toList()
	}
	test("forward paging keeps the window start and doesn't repeat or omit entries") {
		val window = LibraryGridWindow<Int>(30)
		window.replace(60, (60 until 90).toList(), 100)
		window.next(29) shouldBe GridRange(90, 30)
		window.merge(90, (90 until 100).toList(), 100)
		window.start shouldBe 60
		window.items shouldBe (60 until 100).toList()
		window.next(39) shouldBe null
		window.next(-1) shouldBe null
	}
	test("the first and last library pages do not request outside the result") {
		val window = LibraryGridWindow<Int>(30)
		window.around(0) shouldBe GridRange(0, 90)
		window.replace(0, (0 until 12).toList(), 12)
		window.next(0) shouldBe null
		window.next(11) shouldBe null
		window.replace(0, emptyList(), 0)
		window.next(0) shouldBe null
	}
	test("Right enters the rail only at the last column or the incomplete final row") {
		(0..16).filter { isAlphabetEdge(it, 17, 6) } shouldBe listOf(5, 11, 16)
		isAlphabetEdge(-1, 17, 6) shouldBe false
		isAlphabetEdge(17, 17, 6) shouldBe false
	}
	test("Down stays in the grid and selects the next off-screen row past artist seven") {
		verticalLibraryPosition(7, 30, 6, false) shouldBe 13
		verticalLibraryPosition(13, 30, 6, false) shouldBe 19
		verticalLibraryPosition(19, 30, 6, false) shouldBe 25
		verticalLibraryPosition(25, 30, 6, false) shouldBe null
		verticalLibraryPosition(13, 30, 6, true) shouldBe 7
		verticalLibraryPosition(7, 30, 6, true) shouldBe 1
		verticalLibraryPosition(1, 30, 6, true) shouldBe null
	}
	test("vertical navigation clamps a partial last row and waits at an unloaded boundary") {
		verticalLibraryPosition(11, 17, 6, false) shouldBe 16
		verticalLibraryPosition(16, 17, 6, false) shouldBe null
		verticalLibraryPosition(7, 12, 6, false) shouldBe null
		verticalLibraryPosition(-1, 0, 6, false) shouldBe null
	}
	test("letter filtering keeps parent user favorites fields and ordering and starts at zero") {
		val request = GetItemsRequest(parentId = UUID.randomUUID(), userId = UUID.randomUUID(), recursive = true,
			filters = setOf(ItemFilter.IS_FAVORITE), sortBy = setOf(ItemSortBy.SORT_NAME), sortOrder = setOf(SortOrder.DESCENDING),
			fields = setOf(ItemFields.OVERVIEW), enableImages = true, enableUserData = true)
		val source = LibraryGridSource(request, null, null, 'M')
		source.itemsPage(0, 30) shouldBe request.copy(startIndex = 0, limit = 30, enableTotalRecordCount = true, nameStartsWith = "M")
		source.itemsPage(30, 30) shouldBe request.copy(startIndex = 30, limit = 30, enableTotalRecordCount = true, nameStartsWith = "M")
	}
	test("artist and album artist filtering uses the same server prefix on subsequent pages") {
		val artists = GetArtistsRequest(parentId = UUID.randomUUID(), filters = setOf(ItemFilter.IS_FAVORITE), fields = setOf(ItemFields.OVERVIEW))
		val albums = GetAlbumArtistsRequest(parentId = artists.parentId, filters = artists.filters, fields = artists.fields)
		val artistSource = LibraryGridSource(null, artists, null, 'Z')
		val albumSource = LibraryGridSource(null, null, albums, 'Z')
		artistSource.artistsPage(0, 30) shouldBe artists.copy(startIndex = 0, limit = 30, enableTotalRecordCount = true, nameStartsWith = "Z")
		albumSource.albumArtistsPage(0, 30) shouldBe albums.copy(startIndex = 0, limit = 30, enableTotalRecordCount = true, nameStartsWith = "Z")
		artistSource.artistsPage(30, 30) shouldBe artists.copy(startIndex = 30, limit = 30, enableTotalRecordCount = true, nameStartsWith = "Z")
		albumSource.albumArtistsPage(30, 30) shouldBe albums.copy(startIndex = 30, limit = 30, enableTotalRecordCount = true, nameStartsWith = "Z")
	}
	test("# requests names before the first alphabet letter for each supported endpoint") {
		LibraryGridSource(GetItemsRequest(), null, null, '#').itemsPage(0, 30)?.let {
			it.nameLessThan shouldBe "A"
			it.nameStartsWith shouldBe null
		}
		LibraryGridSource(null, GetArtistsRequest(), null, '#').artistsPage(0, 30)?.nameLessThan shouldBe "A"
		LibraryGridSource(null, null, GetAlbumArtistsRequest(), '#').albumArtistsPage(0, 30)?.nameLessThan shouldBe "A"
	}
	test("All clears every name boundary and preserves the category and other filters") {
		val items = GetItemsRequest(parentId = UUID.randomUUID(), filters = setOf(ItemFilter.IS_UNPLAYED), nameStartsWith = "M", nameLessThan = "A", nameStartsWithOrGreater = "B")
		val artists = GetArtistsRequest(parentId = items.parentId, nameStartsWith = "M", nameLessThan = "A", nameStartsWithOrGreater = "B")
		val albums = GetAlbumArtistsRequest(parentId = items.parentId, nameStartsWith = "M", nameLessThan = "A", nameStartsWithOrGreater = "B")
		LibraryGridSource(items, null, null).itemsPage(0, 30) shouldBe items.copy(startIndex = 0, limit = 30, enableTotalRecordCount = true,
			nameStartsWith = null, nameLessThan = null, nameStartsWithOrGreater = null)
		LibraryGridSource(null, artists, null).artistsPage(0, 30) shouldBe artists.copy(startIndex = 0, limit = 30, enableTotalRecordCount = true,
			nameStartsWith = null, nameLessThan = null, nameStartsWithOrGreater = null)
		LibraryGridSource(null, null, albums).albumArtistsPage(0, 30) shouldBe albums.copy(startIndex = 0, limit = 30, enableTotalRecordCount = true,
			nameStartsWith = null, nameLessThan = null, nameStartsWithOrGreater = null)
	}
})
