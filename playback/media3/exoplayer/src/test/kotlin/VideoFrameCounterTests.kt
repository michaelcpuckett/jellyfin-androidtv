package org.jellyfin.playback.media3.exoplayer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.playback.core.model.VideoFrameStats

@OptIn(UnstableApi::class)
class VideoFrameCounterTests : FunSpec({
	test("polling and analytics callbacks do not count the same frames twice") {
		val counter = VideoFrameCounter()
		val decoder = DecoderCounters().apply {
			droppedBufferCount = 3
			renderedOutputBufferCount = 120
		}
		counter.snapshot(decoder) shouldBe VideoFrameStats(3, 120)
		counter.snapshot(decoder) shouldBe VideoFrameStats(3, 120)
		decoder.droppedBufferCount = 4
		decoder.renderedOutputBufferCount = 180
		counter.snapshot(decoder) shouldBe VideoFrameStats(4, 180)
	}

	test("counts frames accumulated while the overlay was hidden and excludes deliberate skips") {
		val counter = VideoFrameCounter()
		val decoder = DecoderCounters()
		counter.snapshot(decoder)
		decoder.droppedBufferCount = 2
		decoder.renderedOutputBufferCount = 600
		decoder.skippedInputBufferCount = 20
		decoder.skippedOutputBufferCount = 30
		counter.snapshot(decoder) shouldBe VideoFrameStats(2, 600)
	}

	test("preserves totals across decoder replacement and late final snapshots") {
		val counter = VideoFrameCounter()
		val first = DecoderCounters().apply { droppedBufferCount = 4; renderedOutputBufferCount = 100 }
		val second = DecoderCounters().apply { droppedBufferCount = 1; renderedOutputBufferCount = 50 }
		counter.snapshot(first)
		counter.snapshot(second) shouldBe VideoFrameStats(5, 150)
		first.droppedBufferCount = 6
		counter.snapshot(first) shouldBe VideoFrameStats(7, 150)
		counter.snapshot(second) shouldBe VideoFrameStats(7, 150)
		counter.snapshot(null) shouldBe VideoFrameStats(7, 150)
	}

	test("new item resets totals and baselines a reused decoder") {
		val counter = VideoFrameCounter()
		val decoder = DecoderCounters().apply { droppedBufferCount = 5; renderedOutputBufferCount = 200 }
		counter.snapshot(decoder)
		counter.reset(decoder)
		counter.snapshot(decoder) shouldBe VideoFrameStats()
		decoder.droppedBufferCount = 7
		decoder.renderedOutputBufferCount = 250
		counter.snapshot(decoder) shouldBe VideoFrameStats(2, 50)
		counter.reset()
		counter.snapshot(null) shouldBe VideoFrameStats()
	}
})
