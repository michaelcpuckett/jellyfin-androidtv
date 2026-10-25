package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.View
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter

class PlaybackInfoAction(
	context: Context,
	glue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, glue) {
	init {
		initializeWithIcon(R.drawable.ic_info)
		setLabels(arrayOf(context.getString(R.string.playback_info)))
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		videoPlayerAdapter.masterOverlayFragment.togglePlaybackInfo()
	}
}
