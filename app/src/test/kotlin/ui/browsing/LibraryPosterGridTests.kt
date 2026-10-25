package org.jellyfin.androidtv.ui.browsing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

class LibraryPosterGridTests : FunSpec({
	test("six full posters fit the measured viewport including gutters and the focus margin") {
		for (viewport in listOf(640, 720, 880, 960, 1280, 1840)) {
			val width = LibraryPosterGrid.cardWidth(viewport)
			val occupied = width * 6 + LibraryPosterGrid.GAP * 5 + LibraryPosterGrid.PADDING * 2
			(occupied <= viewport) shouldBe true
			(viewport - occupied < 6) shouldBe true
		}
		// A 960dp TV viewport leaves 880dp after the 80dp sidebar.
		LibraryPosterGrid.cardWidth(880) shouldBe 122
	}
	test("video artwork has a uniform poster ratio while music artwork remains square") {
		LibraryPosterGrid.cardHeight(880, false) shouldBe 183
		LibraryPosterGrid.cardHeight(880, true) shouldBe 122
	}
	test("paging includes the next row and uses complete rows") {
		LibraryPosterGrid.pageSize(12) shouldBe 30
		LibraryPosterGrid.pageSize(36) shouldBe 42
		LibraryPosterGrid.pageSize(37) shouldBe 48
	}
	test("movie and show grids query their library recursively with lightweight card metadata") {
		for ((collection, kind) in listOf(
			CollectionType.MOVIES to BaseItemKind.MOVIE,
			CollectionType.TVSHOWS to BaseItemKind.SERIES,
		)) {
			val library = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.COLLECTION_FOLDER, collectionType = collection)
			val query = BrowsingUtils.createPosterGridItemsRequest(library)
			query.parentId shouldBe library.id
			query.recursive shouldBe true
			query.includeItemTypes shouldBe setOf(kind)
			query.fields shouldBe ItemRepository.browseFields
			query.fields!!.contains(ItemFields.MEDIA_SOURCES) shouldBe false
			query.fields!!.contains(ItemFields.CHAPTERS) shouldBe false
		}
	}
	test("the music grid requests artists within its library using lightweight card metadata") {
		val libraryId = UUID.randomUUID()
		val query = BrowsingUtils.createPosterGridArtistsRequest(libraryId)
		query.parentId shouldBe libraryId
		query.fields shouldBe ItemRepository.browseFields
		query.fields!!.contains(ItemFields.MEDIA_SOURCES) shouldBe false
		query.fields!!.contains(ItemFields.CHAPTERS) shouldBe false
	}
})
