package org.jellyfin.playback.media3.session

import org.jellyfin.playback.core.queue.Queue
import org.jellyfin.playback.core.queue.QueueEntry

internal data class SessionQueueWindow(
	val entries: List<QueueEntry>,
	val currentIndex: Int,
	val hasPrevious: Boolean,
	val hasNext: Boolean,
)

/** Actual neighbours follow shuffle and playback history; numeric queue indices do not. */
internal suspend fun Queue.sessionQueueWindow(): SessionQueueWindow {
	val current = entry.value ?: return SessionQueueWindow(emptyList(), -1, false, false)
	val previous = peekPrevious()
	val next = peekNext()
	// Keep separate occurrences of the same song. QueueEntry identity is also the timeline UID.
	val entries = listOfNotNull(previous, current, next).distinct()
	return SessionQueueWindow(entries, entries.indexOf(current), previous != null, next != null)
}
