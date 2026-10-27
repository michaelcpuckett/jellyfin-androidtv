package org.jellyfin.playback.core.queue

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.PlayerState
import org.jellyfin.playback.core.model.PlaybackOrder
import org.jellyfin.playback.core.model.RepeatMode
import org.jellyfin.playback.core.queue.supplier.QueueSupplier

class QueuePlayFromTests : FunSpec({
	val scopes = mutableListOf<CoroutineScope>()
	afterTest { scopes.forEach { it.cancel() }; scopes.clear() }

	suspend fun queue(order: PlaybackOrder, items: List<QueueEntry>): QueueService {
		val scope = CoroutineScope(Dispatchers.Unconfined + Job()).also(scopes::add)
		val manager = mockk<PlaybackManager>(relaxed = true)
		val playerState = mockk<PlayerState>(relaxed = true) {
			every { playbackOrder } returns MutableStateFlow(order)
			every { repeatMode } returns MutableStateFlow(RepeatMode.NONE)
		}
		val queue = spyk(QueueService())
		every { queue.coroutineScope } returns scope
		every { queue.manager } returns manager
		every { queue.state } returns playerState
		queue.onInitialize()
		queue.addSupplier(object : QueueSupplier {
			override val size = items.size
			override suspend fun getItem(index: Int) = items.getOrNull(index)
		})
		return queue
	}

	test("ordered Play from here selects the existing entry and continues with its successors") {
		val items = List(8) { QueueEntry() }
		val queue = queue(PlaybackOrder.DEFAULT, items)
		queue.peekNext(100)
		queue.playFrom(items[4]) shouldBe items[4]
		queue.entryIndex.value shouldBe 4
		queue.entries.value shouldBe items
		queue.estimatedSize shouldBe 8
		queue.peekNext(100).toList() shouldBe items.drop(5)
		queue.peekPrevious() shouldBe items[0]
		queue.previous() shouldBe items[0]
	}
	test("shuffle Play from here skips the displayed prefix without inserting or replaying the selection") {
		val items = List(12) { QueueEntry() }
		val queue = queue(PlaybackOrder.SHUFFLE, items)
		val preview = queue.peekNext(100).toList()
		val selected = preview[4]
		queue.playFrom(selected) shouldBe selected
		queue.peekNext(100).toList() shouldBe preview.drop(5)
		val remainder = buildList { while (true) add(queue.next() ?: break) }
		remainder shouldBe preview.drop(5)
		queue.estimatedSize shouldBe 12
		queue.entries.value shouldBe items
	}
	test("selecting the current entry leaves the upcoming plan and history intact") {
		val items = List(8) { QueueEntry() }
		val queue = queue(PlaybackOrder.SHUFFLE, items)
		val preview = queue.peekNext(100).toList()
		queue.playFrom(items[0]) shouldBe items[0]
		queue.peekNext(100).toList() shouldBe preview
		queue.peekPrevious() shouldBe null
	}
	test("a stale entry cannot modify the current queue or its plan") {
		val items = List(8) { QueueEntry() }
		val queue = queue(PlaybackOrder.SHUFFLE, items)
		val preview = queue.peekNext(100).toList()
		queue.playFrom(QueueEntry()) shouldBe null
		queue.entry.value shouldBe items[0]
		queue.peekNext(100).toList() shouldBe preview
	}
	test("duplicate songs retain independent queue entries when choosing a later occurrence") {
		val items = List(5) { QueueEntry().apply { metadata = QueueEntryMetadata(mediaId = "same-song") } }
		val queue = queue(PlaybackOrder.DEFAULT, items)
		queue.peekNext(100)
		queue.playFrom(items[3]) shouldBe items[3]
		queue.entryIndex.value shouldBe 3
		queue.peekNext() shouldBe items[4]
		queue.estimatedSize shouldBe 5
	}
	test("removing a skipped entry preserves the planned remaining order") {
		val items = List(12) { QueueEntry() }
		val queue = queue(PlaybackOrder.SHUFFLE, items)
		val preview = queue.peekNext(100).toList()
		queue.playFrom(preview[4])
		queue.removeEntry(preview[0])
		queue.peekNext(100).toList() shouldBe preview.drop(5)
		queue.entry.value shouldBe preview[4]
		queue.estimatedSize shouldBe 11
	}
	test("a new queue clears skipped entries and cached shuffle indices from the old queue") {
		val oldItems = List(10) { QueueEntry() }
		val queue = queue(PlaybackOrder.SHUFFLE, oldItems)
		queue.playFrom(queue.peekNext(100).toList()[5])
		queue.clear()
		val newItems = List(10) { QueueEntry() }
		queue.addSupplier(object : QueueSupplier {
			override val size = newItems.size
			override suspend fun getItem(index: Int) = newItems.getOrNull(index)
		})
		queue.entry.value shouldBe newItems[0]
		val preview = queue.peekNext(100).toList()
		preview.size shouldBe 9
		preview.toSet() shouldBe newItems.drop(1).toSet()
	}
})
