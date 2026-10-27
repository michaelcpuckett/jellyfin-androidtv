package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.androidtv.ui.playback.rewrite.RewriteMediaManager
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.PlayerState
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.queue.QueueService

class AudioPlayFromTests : FunSpec({
	test("Play from here advances the existing queue and starts playback without inserting a supplier") {
		val queue = mockk<QueueService>(relaxed = true)
		val state = mockk<PlayerState>(relaxed = true)
		val player = mockk<PlaybackManager>()
		every { player.getService(QueueService::class) } returns queue
		every { player.state } returns state
		val selected = QueueEntry()
		coEvery { queue.playFrom(selected) } returns selected
		RewriteMediaManager(mockk(), player).playFrom(selected) shouldBe true
		coVerify(exactly = 1) { queue.playFrom(selected) }
		verify(exactly = 1) { state.play() }
		verify(exactly = 0) { queue.addSupplier(any()); queue.clear() }
	}
	test("a stale selection does not start playback or replace the queue") {
		val queue = mockk<QueueService>(relaxed = true)
		val state = mockk<PlayerState>(relaxed = true)
		val player = mockk<PlaybackManager>()
		every { player.getService(QueueService::class) } returns queue
		every { player.state } returns state
		val selected = QueueEntry()
		coEvery { queue.playFrom(selected) } returns null
		RewriteMediaManager(mockk(), player).playFrom(selected) shouldBe false
		verify(exactly = 0) { state.play(); queue.addSupplier(any()); queue.clear() }
	}
})
