package org.jellyfin.playback.core.model

/** Local video renderer totals for the current playback item. Deliberately skipped frames are excluded. */
data class VideoFrameStats(
	val droppedFrames: Long = 0,
	val renderedFrames: Long = 0,
)
