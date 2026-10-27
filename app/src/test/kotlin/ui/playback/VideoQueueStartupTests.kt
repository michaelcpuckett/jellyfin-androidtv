package org.jellyfin.androidtv.ui.playback

import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.androidtv.ui.playback.overlay.LeanbackOverlayFragment
import org.jellyfin.androidtv.ui.playback.overlay.VideoQueueRow

class VideoQueueStartupTests : FunSpec({
	test("queue construction before the player view exists does not access its null adapter") {
		val fragment = mockk<LeanbackOverlayFragment>(relaxed = true)
		every { fragment.adapter } returns null
		VideoQueueRow(fragment, mockk(relaxed = true))
		verify(exactly = 0) { fragment.adapter }
	}
	test("an early media update with no adapter is safe and can be retried after view creation") {
		val fragment = mockk<LeanbackOverlayFragment>(relaxed = true)
		val controller = mockk<PlaybackController>(relaxed = true)
		every { fragment.adapter } returns null
		val row = VideoQueueRow(fragment, controller)
		row.update()
		verify(exactly = 0) { controller.queueItems }

		val selector = ClassPresenterSelector()
		val adapter = ArrayObjectAdapter(selector)
		every { fragment.adapter } returns adapter
		every { controller.queueItems } returns emptyList()
		row.update()
		verify(exactly = 1) { controller.queueItems }
		adapter.size() shouldBe 0
		selector.presenters?.size shouldBe 1
	}
})
