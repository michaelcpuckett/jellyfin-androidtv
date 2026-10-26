package org.jellyfin.androidtv.ui.browsing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

class CategoryPlaybackTests : FunSpec({
	val folderId = UUID.randomUUID()
	val userId = UUID.randomUUID()
	val request = categoryPlaybackRequest(folderId, userId, MediaType.AUDIO)
	val tracks = (0..424).map { BaseItemDto(id = UUID(0, it.toLong()), type = BaseItemKind.AUDIO, mediaType = MediaType.AUDIO) }

	test("shuffle asks the server for 100 random songs once even when the response fills the batch") {
		val randomRequest = categoryPlaybackRequest(folderId, userId, MediaType.AUDIO, shuffle = true)
		var calls = 0
		val randomSongs = tracks.drop(300).take(100).reversed()
		val result = loadCategoryPlaybackItems(randomRequest) { page ->
			calls++
			page.sortBy shouldBe listOf(ItemSortBy.RANDOM)
			page.limit shouldBe 100
			page.startIndex shouldBe 0
			page.parentId shouldBe folderId
			page.userId shouldBe userId
			page.recursive shouldBe true
			page.mediaTypes shouldBe setOf(MediaType.AUDIO)
			randomSongs
		}
		calls shouldBe 1
		result shouldBe randomSongs
	}
	test("shuffle accepts small libraries and never queues over 100 items from an oversized response") {
		val randomRequest = categoryPlaybackRequest(folderId, userId, MediaType.AUDIO, shuffle = true)
		loadCategoryPlaybackItems(randomRequest) { tracks.take(12) } shouldBe tracks.take(12)
		loadCategoryPlaybackItems(randomRequest) { emptyList() } shouldBe emptyList()
		loadCategoryPlaybackItems(randomRequest) { tracks.take(50) + tracks } shouldBe tracks.take(100)
		val videoRequest = categoryPlaybackRequest(folderId, userId, MediaType.VIDEO, shuffle = true)
		videoRequest.sortBy shouldBe listOf(ItemSortBy.RANDOM)
		videoRequest.limit shouldBe 100
	}
	test("Play All includes nested tracks beyond the old 150 item limit and the visible page") {
		val offsets = mutableListOf<Int>()
		val result = loadCategoryPlaybackItems(request) { page ->
			offsets.add(requireNotNull(page.startIndex))
			page.parentId shouldBe folderId
			page.userId shouldBe userId
			page.recursive shouldBe true
			page.filters shouldBe setOf(ItemFilter.IS_NOT_FOLDER)
			page.isMissing shouldBe false
			page.mediaTypes shouldBe setOf(MediaType.AUDIO)
			tracks.drop(page.startIndex!!).take(page.limit!!)
		}
		result shouldBe tracks
		offsets shouldBe listOf(0, 200, 400)
	}
	test("album ordering keeps artist album disc and track sequence ahead of title") {
		request.sortBy shouldBe listOf(ItemSortBy.ALBUM_ARTIST, ItemSortBy.ALBUM,
			ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME)
		val video = categoryPlaybackRequest(folderId, userId, MediaType.VIDEO)
		video.sortBy shouldBe listOf(ItemSortBy.SORT_NAME)
		video.mediaTypes shouldBe setOf(MediaType.VIDEO)
		video.fields.orEmpty().contains(ItemFields.MEDIA_SOURCES) shouldBe true
		video.fields.orEmpty().contains(ItemFields.TRICKPLAY) shouldBe false
	}
	test("a server repeating a full page cannot hang Play All or duplicate the queue") {
		var calls = 0
		loadCategoryPlaybackItems(request) { calls++; tracks.take(200) } shouldBe tracks.take(200)
		calls shouldBe 2
	}
	test("an empty folder returns no queue and a later page failure does not return a partial queue") {
		loadCategoryPlaybackItems(request) { emptyList() } shouldBe emptyList()
		shouldThrow<IllegalStateException> {
			loadCategoryPlaybackItems(request) { page ->
				if (page.startIndex == 0) tracks.take(200) else error("page failed")
			}
		}
	}
	test("music videos load video only while music and generic folders retain their media scopes") {
		val library = BaseItemDto(id = folderId, type = BaseItemKind.USER_VIEW)
		categoryPlaybackMediaTypes(library.copy(collectionType = CollectionType.MUSICVIDEOS)) shouldBe setOf(MediaType.VIDEO)
		categoryPlaybackMediaTypes(library.copy(collectionType = CollectionType.MUSIC)) shouldBe setOf(MediaType.AUDIO)
		categoryPlaybackMediaTypes(library.copy(collectionType = CollectionType.FOLDERS)) shouldBe setOf(MediaType.AUDIO, MediaType.VIDEO)
		categoryPlaybackMediaTypes(BaseItemDto(id = folderId, type = BaseItemKind.FOLDER)) shouldBe setOf(MediaType.AUDIO, MediaType.VIDEO)
	}
	test("actions appear on music music videos and folders without adding buttons to series movie or live TV screens") {
		for (collection in listOf(CollectionType.MUSIC, CollectionType.MUSICVIDEOS, CollectionType.FOLDERS)) {
			supportsCategoryPlayback(BaseItemDto(id = folderId, type = BaseItemKind.USER_VIEW, collectionType = collection)) shouldBe true
		}
		supportsCategoryPlayback(BaseItemDto(id = folderId, type = BaseItemKind.FOLDER)) shouldBe true
		for (kind in listOf(BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.MOVIE, BaseItemKind.EPISODE)) {
			supportsCategoryPlayback(BaseItemDto(id = folderId, type = kind)) shouldBe false
		}
		supportsCategoryPlayback(BaseItemDto(id = folderId, type = BaseItemKind.USER_VIEW, collectionType = CollectionType.LIVETV)) shouldBe false
	}
})
