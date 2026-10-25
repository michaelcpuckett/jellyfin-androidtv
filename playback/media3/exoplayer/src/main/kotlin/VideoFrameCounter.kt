package org.jellyfin.playback.media3.exoplayer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import org.jellyfin.playback.core.model.VideoFrameStats
import java.util.IdentityHashMap

/** Read on the player application thread. Decoder counters themselves are updated by the playback thread. */
@OptIn(UnstableApi::class)
class VideoFrameCounter {
	private val snapshots = IdentityHashMap<DecoderCounters, VideoFrameStats>()
	private var total = VideoFrameStats()

	fun reset(counters: DecoderCounters? = null) {
		snapshots.clear()
		total = VideoFrameStats()
		// A renderer may be reused for the next item. Exclude frames from the preceding item.
		if (counters != null) snapshots[counters] = counters.readStats()
	}

	fun snapshot(counters: DecoderCounters?): VideoFrameStats {
		if (counters == null) return total
		val current = counters.readStats()
		val previous = snapshots.put(counters, current) ?: VideoFrameStats()
		total = VideoFrameStats(
			droppedFrames = total.droppedFrames + (current.droppedFrames - previous.droppedFrames).coerceAtLeast(0),
			renderedFrames = total.renderedFrames + (current.renderedFrames - previous.renderedFrames).coerceAtLeast(0),
		)
		return total
	}

	private fun DecoderCounters.readStats(): VideoFrameStats {
		ensureUpdated()
		return VideoFrameStats(droppedBufferCount.toLong(), renderedOutputBufferCount.toLong())
	}
}
