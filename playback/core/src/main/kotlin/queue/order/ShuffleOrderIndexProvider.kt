package org.jellyfin.playback.core.queue.order

internal class ShuffleOrderIndexProvider : OrderIndexProvider {
	private val nextIndices = mutableListOf<Int>()

	override fun reset() = nextIndices.clear()

	override fun provideIndices(
		amount: Int,
		size: Int,
		playedIndices: Collection<Int>,
		currentIndex: Int,
	): Collection<Int> {
		if (amount <= 0 || size <= 0) return emptyList()
		val excluded = playedIndices.toSet() + currentIndex
		nextIndices.removeAll { it !in 0 until size || it in excluded }
		val needed = (amount - nextIndices.size).coerceAtLeast(0)
		if (needed > 0) {
			val planned = nextIndices.toSet()
			nextIndices.addAll((0 until size).filterNot { it in excluded || it in planned }.shuffled().take(needed))
		}
		return nextIndices.take(amount)
	}

	override fun notifyRemoved(index: Int) {
		nextIndices.removeAll { it == index }
		nextIndices.replaceAll { if (it > index) it - 1 else it }
	}

	override fun useNextIndex() {
		nextIndices.removeFirstOrNull()
	}

	override fun consumeThrough(index: Int): Collection<Int> {
		val position = nextIndices.indexOf(index)
		if (position < 0) {
			nextIndices.clear()
			return emptyList()
		}
		val skipped = nextIndices.take(position)
		nextIndices.subList(0, position + 1).clear()
		return skipped
	}
}
