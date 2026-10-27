package org.jellyfin.playback.core.queue.order

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ShuffleOrderIndexProviderTests : FunSpec({
	test("a full preview contains each remaining valid index once and excludes the current song") {
		repeat(30) {
			val provider = ShuffleOrderIndexProvider()
			val result = provider.provideIndices(100, 10, listOf(1, 1, 3), 4).toList()
			result.size shouldBe 7
			result.toSet() shouldBe setOf(0, 2, 5, 6, 7, 8, 9)
		}
	}
	test("repeated and expanded previews preserve the already displayed order") {
		val provider = ShuffleOrderIndexProvider()
		val first = provider.provideIndices(4, 20, emptyList(), 0).toList()
		provider.provideIndices(4, 20, emptyList(), 0).toList() shouldBe first
		val expanded = provider.provideIndices(100, 20, emptyList(), 0).toList()
		expanded.take(4) shouldBe first
		expanded.size shouldBe 19
		expanded.distinct().size shouldBe 19
	}
	test("jumping consumes the selected entry and its predecessors while preserving the suffix") {
		val provider = ShuffleOrderIndexProvider()
		val preview = provider.provideIndices(100, 12, emptyList(), 0).toList()
		val selected = preview[4]
		val skipped = provider.consumeThrough(selected)
		skipped.toList() shouldBe preview.take(4)
		provider.provideIndices(100, 12, listOf(0) + skipped, selected).toList() shouldBe preview.drop(5)
	}
	test("using a preview entry does not repeat it on the next preview") {
		val provider = ShuffleOrderIndexProvider()
		val preview = provider.provideIndices(100, 6, emptyList(), 0).toList()
		provider.useNextIndex()
		provider.provideIndices(100, 6, listOf(0), preview.first()).toList() shouldBe preview.drop(1)
	}
	test("empty and exhausted queues have no out-of-range fallback or repeated current item") {
		val provider = ShuffleOrderIndexProvider()
		provider.provideIndices(100, 0, emptyList(), -1).toList() shouldBe emptyList()
		provider.provideIndices(100, 1, emptyList(), 0).toList() shouldBe emptyList()
		provider.provideIndices(100, 3, listOf(0, 1), 2).toList() shouldBe emptyList()
		provider.provideIndices(0, 5, emptyList(), 0).toList() shouldBe emptyList()
	}
})
