package org.jellyfin.androidtv.ui.home

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID

class HomeDiscoveryRowsTests : FunSpec({
	val movies = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.USER_VIEW, collectionType = CollectionType.MOVIES)
	val shows = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.USER_VIEW, collectionType = CollectionType.TVSHOWS)
	test("discovery respects excluded libraries and never queries music or duplicates") {
		val music = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.USER_VIEW, collectionType = CollectionType.MUSIC)
		homeDiscoveryLibraries(listOf(movies, music, shows, movies), listOf(shows.id)) shouldBe listOf(movies)
	}
	test("unwatched picks are bounded and scoped to movies without heavy playback metadata") {
		val request = homeDiscoveryRequest(movies, HomeDiscoveryFeed.UNWATCHED)
		request.parentId shouldBe movies.id
		request.includeItemTypes shouldBe setOf(BaseItemKind.MOVIE)
		request.isPlayed shouldBe false
		request.minCommunityRating shouldBe 7.0
		request.startIndex shouldBe 0
		request.limit shouldBe 24
		request.enableTotalRecordCount shouldBe false
		request.fields.orEmpty().any { it in setOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS, ItemFields.CHAPTERS, ItemFields.TRICKPLAY) } shouldBe false
	}
	test("recent series use release date rather than play count or date added") {
		val request = homeDiscoveryRequest(shows, HomeDiscoveryFeed.RECENT)
		request.includeItemTypes shouldBe setOf(BaseItemKind.SERIES)
		request.sortBy?.first() shouldBe ItemSortBy.PREMIERE_DATE
		request.isPlayed shouldBe null
	}
	test("genre rows query only the chosen actual genre in the chosen library") {
		val request = homeDiscoveryRequest(shows, HomeDiscoveryFeed.GENRE, "Science Fiction")
		request.parentId shouldBe shows.id
		request.genres shouldBe setOf("Science Fiction")
		shouldThrow<IllegalArgumentException> { homeDiscoveryRequest(shows, HomeDiscoveryFeed.GENRE, " ") }
	}
	test("spotlight ignores blank genres and remains stable for a daily seed") {
		homeSpotlightGenre(listOf(null, "", "  "), 1) shouldBe null
		homeSpotlightGenre(listOf("Drama", "Action", "Drama", null), -1) shouldBe "Drama"
		homeSpotlightGenre(listOf("Action", "Drama"), -1) shouldBe "Drama"
	}
})
