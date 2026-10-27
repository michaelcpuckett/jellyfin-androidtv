package org.jellyfin.androidtv.ui.playback

import org.jellyfin.playback.media3.session.MediaSkipKeyHandler

/** A stopped music queue must not steal skips from the visible video player. */
internal class PlaybackMediaKeyHandler(
	videoActive: () -> Boolean,
	videoNext: () -> Unit,
	videoPrevious: () -> Unit,
	audioPresent: () -> Boolean,
	audioNext: () -> Unit,
	audioPrevious: () -> Unit,
) {
	private val video = MediaSkipKeyHandler(videoActive, videoNext, videoPrevious)
	private val audio = MediaSkipKeyHandler({ !videoActive() && audioPresent() }, audioNext, audioPrevious)

	fun onKey(key: Int, action: Int, repeatCount: Int, canceled: Boolean = false): Boolean =
		video.onKey(key, action, repeatCount, canceled) || audio.onKey(key, action, repeatCount, canceled)
}
