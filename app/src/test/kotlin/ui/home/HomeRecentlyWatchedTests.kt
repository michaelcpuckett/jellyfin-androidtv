package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

class HomeRecentlyWatchedTests : FunSpec({
	test("history is scoped to the current user and completed movies and episodes") {
		val userId = UUID.randomUUID()
		val request = homeRecentlyWatchedRequest(userId)
		request.userId shouldBe userId
		request.isPlayed shouldBe true
		request.includeItemTypes shouldBe setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE)
		request.recursive shouldBe true
		request.enableUserData shouldBe true
	}
	test("history uses last playback rather than library date or release date") {
		val request = homeRecentlyWatchedRequest(UUID.randomUUID())
		request.sortBy shouldBe listOf(ItemSortBy.DATE_PLAYED, ItemSortBy.SORT_NAME)
		request.sortOrder shouldBe listOf(SortOrder.DESCENDING, SortOrder.ASCENDING)
	}
	test("history is bounded and avoids fetching heavy playback details") {
		val request = homeRecentlyWatchedRequest(UUID.randomUUID())
		request.startIndex shouldBe 0
		request.limit shouldBe 24
		request.enableTotalRecordCount shouldBe false
		request.imageTypeLimit shouldBe 1
		request.fields.orEmpty().any { it in setOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS, ItemFields.CHAPTERS, ItemFields.TRICKPLAY) } shouldBe false
	}
})
