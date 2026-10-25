package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

class HomePreviewMetadataTests : FunSpec({
	val id = UUID.randomUUID()
	val seriesId = UUID.randomUUID()
	test("own backdrop wins over parent art") {
		val image = homePreviewBackdrop(BaseItemDto(id = id, type = BaseItemKind.MOVIE, backdropImageTags = listOf("own"),
			parentBackdropItemId = seriesId, parentBackdropImageTags = listOf("series")))
		image?.item shouldBe id
		image?.tag shouldBe "own"
	}
	test("episode uses series backdrop before its screenshot") {
		val image = homePreviewBackdrop(BaseItemDto(id = id, type = BaseItemKind.EPISODE,
			parentBackdropItemId = seriesId, parentBackdropImageTags = listOf("series"),
			imageTags = mapOf(ImageType.PRIMARY to "screenshot"), primaryImageAspectRatio = 16.0 / 9.0))
		image?.item shouldBe seriesId
	}
	test("landscape episode screenshot is a last resort") {
		homePreviewBackdrop(BaseItemDto(id = id, type = BaseItemKind.EPISODE,
			imageTags = mapOf(ImageType.PRIMARY to "screenshot"), primaryImageAspectRatio = 16.0 / 9.0))?.tag shouldBe "screenshot"
	}
	test("portrait posters and missing artwork do not become stretched backdrops") {
		homePreviewBackdrop(BaseItemDto(id = id, type = BaseItemKind.MOVIE,
			imageTags = mapOf(ImageType.PRIMARY to "poster"), primaryImageAspectRatio = 2.0 / 3.0)) shouldBe null
		homePreviewBackdrop(null) shouldBe null
	}
})
