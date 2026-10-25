package org.jellyfin.playback.media3.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.playback.core.queue.Queue
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.queue.QueueEntryMetadata
import org.jellyfin.playback.core.queue.metadata

private fun queue(current: QueueEntry?, previous: QueueEntry? = null, next: QueueEntry? = null, index: Int = 0, size: Int = 1) = mockk<Queue> {
	every { entry } returns MutableStateFlow(current)
	every { entryIndex } returns MutableStateFlow(index)
	every { estimatedSize } returns size
	coEvery { peekPrevious() } returns previous
	coEvery { peekNext() } returns next
}

class SessionQueueWindowTests : FunSpec({
	test("idle sessions expose no skip targets or current timeline index") {
		queue(null).sessionQueueWindow() shouldBe SessionQueueWindow(emptyList(), -1, false, false)
	}
	test("ordinary middle tracks preserve previous, current and next order") {
		val previous = QueueEntry()
		val current = QueueEntry()
		val next = QueueEntry()
		queue(current, previous, next, 1, 3).sessionQueueWindow() shouldBe SessionQueueWindow(listOf(previous, current, next), 1, true, true)
	}
	test("shuffle can have a next track even at the numerically last queue index") {
		val current = QueueEntry()
		val next = QueueEntry()
		val result = queue(current, next = next, index = 19, size = 20).sessionQueueWindow()
		result.hasNext shouldBe true
		result.entries shouldBe listOf(current, next)
		result.currentIndex shouldBe 0
	}
	test("shuffle history permits Previous at queue index zero and disables Next when exhausted") {
		val previous = QueueEntry()
		val current = QueueEntry()
		queue(current, previous, index = 0, size = 20).sessionQueueWindow() shouldBe SessionQueueWindow(listOf(previous, current), 1, true, false)
	}
	test("repeated songs keep independent timeline entries instead of collapsing by media id") {
		fun song() = QueueEntry().apply { metadata = QueueEntryMetadata(mediaId = "same-song") }
		val previous = song()
		val current = song()
		val next = song()
		queue(current, previous, next).sessionQueueWindow() shouldBe SessionQueueWindow(listOf(previous, current, next), 1, true, true)
	}
	test("reusing a queue entry never leaves the current timeline index outside the playlist") {
		val current = QueueEntry()
		queue(current, current, current).sessionQueueWindow() shouldBe SessionQueueWindow(listOf(current), 0, true, true)
	}
})
