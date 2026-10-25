package org.jellyfin.androidtv.ui.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.playback.core.model.VideoFrameStats
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

private fun statsContext() = mockk<Context> {
	every { getString(any()) } returns "Playback info"
	every { getString(any(), *anyVararg()) } answers {
		"${firstArg<Int>()}:" + secondArg<Array<Any>>().joinToString("|")
	}
}

@OptIn(UnstableApi::class)
class RegularPlaybackInfoTests : FunSpec({
	test("can open the overlay before stream information is ready") {
		val controller = mockk<PlaybackController> { every { currentStreamInfo } returns null }
		playbackInfoText(statsContext(), controller) shouldBe "Playback info"
	}

	test("shows actual local playback formats and frame counters without source metadata") {
		val player = mockk<ExoPlayer> {
			every { videoFormat } returns Format.Builder().setSampleMimeType("video/avc").setWidth(1280).setHeight(720).build()
			every { audioFormat } returns Format.Builder().setSampleMimeType("audio/mp4a-latm").setChannelCount(2).build()
		}
		val manager = mockk<VideoManager> { every { videoFrameStats } returns VideoFrameStats(7, 600) }
		manager.mExoPlayer = player
		val controller = mockk<PlaybackController> { every { currentStreamInfo } returns StreamInfo() }
		controller.mVideoManager = manager
		val text = playbackInfoText(statsContext(), controller)
		text shouldContain "${R.string.playback_info_output_video}:video/avc|1280|720"
		text shouldContain "${R.string.playback_info_output_audio}:audio/mp4a-latm|2"
		text shouldContain "${R.string.playback_info_dropped_frames}:7"
		text shouldContain "${R.string.playback_info_rendered_frames}:600"
	}

	test("audio metadata follows the selected audio track when local format is unavailable") {
		fun audioTrack(streamIndex: Int, streamCodec: String, streamChannels: Int) = mockk<MediaStream> {
			every { type } returns MediaStreamType.AUDIO
			every { index } returns streamIndex
			every { codec } returns streamCodec
			every { channels } returns streamChannels
			every { bitRate } returns null
		}
		val source = mockk<MediaSourceInfo> {
			every { mediaStreams } returns listOf(audioTrack(1, "ac3", 6), audioTrack(2, "truehd", 8))
			every { container } returns "mkv"
		}
		val info = StreamInfo().apply { mediaSource = source }
		val controller = mockk<PlaybackController> {
			every { currentStreamInfo } returns info
			every { audioStreamIndex } returns 2
		}
		controller.mVideoManager = null
		playbackInfoText(statsContext(), controller) shouldContain "${R.string.playback_info_output_audio}:truehd|8"
	}
})
