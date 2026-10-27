package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.androidtv.ui.playback.overlay.VideoQueueItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

class VideoQueueItemTests : FunSpec({
	val item = BaseItemDto(id = UUID.randomUUID(), name = "Queued video", type = BaseItemKind.VIDEO)
	test("duplicate videos retain separate selectable queue positions") {
		VideoQueueItem(item, 0, false) shouldNotBe VideoQueueItem(item, 2, false)
		VideoQueueItem(item, 2, false).index shouldBe 2
	}
	test("changing the playing item invalidates cards so the now-playing marker updates") {
		VideoQueueItem(item, 0, false) shouldNotBe VideoQueueItem(item, 0, true)
		VideoQueueItem(item, 0, true) shouldBe VideoQueueItem(item, 0, true)
	}
})
