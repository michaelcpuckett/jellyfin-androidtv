package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.Locale

/** A single native text view; no polling or text formatting while hidden or the fragment is paused. */
@OptIn(UnstableApi::class)
class RegularPlaybackInfoOverlay(
	private val textView: TextView,
	private val controller: () -> PlaybackController?,
) {
	private val handler = Handler(Looper.getMainLooper())
	private var enabled = false
	private var resumed = false
	private val refresh = object : Runnable {
		override fun run() {
			if (!enabled || !resumed) return
			val text = controller()?.let { playbackInfoText(textView.context, it) }.orEmpty()
			if (textView.text.toString() != text) textView.text = text
			handler.postDelayed(this, 1_000)
		}
	}

	fun toggle() {
		enabled = !enabled
		textView.visibility = if (enabled) View.VISIBLE else View.GONE
		update()
	}

	fun resume() {
		resumed = true
		update()
	}

	fun pause() {
		resumed = false
		handler.removeCallbacks(refresh)
	}

	private fun update() {
		handler.removeCallbacks(refresh)
		if (enabled && resumed) refresh.run()
	}
}

@OptIn(UnstableApi::class)
internal fun playbackInfoText(context: Context, controller: PlaybackController): String {
	val info = controller.currentStreamInfo ?: return context.getString(R.string.playback_info)
	val source = info.mediaSource
	val streams = source?.mediaStreams.orEmpty()
	val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
	val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO && it.index == controller.audioStreamIndex }
		?: streams.firstOrNull { it.type == MediaStreamType.AUDIO }
	val manager = controller.mVideoManager
	val player = manager?.mExoPlayer
	val videoFormat = player?.videoFormat
	val audioFormat = player?.audioFormat
	val method = when (info.playMethod) {
		PlayMethod.DIRECT_PLAY -> R.string.playback_info_direct_play
		PlayMethod.DIRECT_STREAM -> R.string.playback_info_direct_stream
		PlayMethod.TRANSCODE -> R.string.playback_info_transcoding
		else -> null
	}
	return buildList {
		add(context.getString(R.string.playback_info))
		method?.let { add(context.getString(R.string.playback_info_play_method, context.getString(it))) }
		(info.container ?: source?.container)?.let { add(context.getString(R.string.playback_info_container, it.uppercase(Locale.ROOT))) }
		video?.let {
			add(context.getString(R.string.playback_info_source_video, it.codec.orEmpty().uppercase(Locale.ROOT), it.width ?: 0, it.height ?: 0, it.videoRangeType.name))
			it.bitRate?.takeIf { rate -> rate > 0 }?.let { rate -> add(context.getString(R.string.playback_info_bitrate, formatPlaybackBitrate(rate))) }
		}
		videoFormat?.takeIf { it.width > 0 && it.height > 0 }?.let {
			add(context.getString(R.string.playback_info_output_video, it.sampleMimeType.orEmpty(), it.width, it.height))
		}
		(audioFormat?.sampleMimeType ?: audio?.codec)?.let { codec ->
			val channels = audioFormat?.channelCount?.takeIf { it > 0 } ?: audio?.channels ?: 0
			add(context.getString(R.string.playback_info_output_audio, codec, channels))
		}
		audio?.bitRate?.takeIf { it > 0 }?.let {
			add(context.getString(R.string.playback_info_source_audio_bitrate, formatPlaybackBitrate(it)))
		}
		manager?.videoFrameStats?.let {
			add(context.getString(R.string.playback_info_dropped_frames, it.droppedFrames))
			add(context.getString(R.string.playback_info_rendered_frames, it.renderedFrames))
		}
	}.joinToString("\n")
}

internal fun formatPlaybackBitrate(bitrate: Int): String = String.format(Locale.getDefault(), "%.1f Mbit/s", bitrate / 1_000_000.0)
