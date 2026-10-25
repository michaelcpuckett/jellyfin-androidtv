package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.UUID

private fun source(
	name: String? = null, path: String? = null, bitrate: Int? = null, size: Long? = null,
	container: String? = null, streams: List<MediaStream>? = null,
) = mockk<MediaSourceInfo>().also {
	every { it.name } returns name
	every { it.path } returns path
	every { it.bitrate } returns bitrate
	every { it.size } returns size
	every { it.container } returns container
	every { it.mediaStreams } returns streams
}

private fun stream(
	index: Int, type: MediaStreamType, codec: String, width: Int? = null, height: Int? = null,
	averageFrameRate: Float? = null, language: String? = null, bitRate: Int? = null,
) = MediaStream(
	index = index, type = type, codec = codec, width = width, height = height,
	averageFrameRate = averageFrameRate, language = language, bitRate = bitRate,
	isInterlaced = false, isDefault = false, isForced = false, isHearingImpaired = false,
	isExternal = false, isTextSubtitleStream = false, supportsExternalStream = false,
)

class MediaFileInfoTests : FunSpec({
	fun item(sources: List<MediaSourceInfo>? = null, streams: List<MediaStream>? = null) = BaseItemDto(
		id = UUID.randomUUID(), type = BaseItemKind.MOVIE, path = "/movies/original.mkv",
		mediaSources = sources, mediaStreams = streams,
	)
	fun FileInfoSource.field(label: Int) = fields.first { it.label == label }.value

	test("source versions retain their own file paths and bitrate") {
		val result = mediaFileInfo(item(listOf(
			source(name = "Original", path = "/movies/4k.mkv", bitrate = 20_000_000, size = 2_147_483_648L),
			source(name = "HD", path = "/movies/hd.mp4", bitrate = 4_500_000),
		)))
		result[0].field(R.string.file_info_path) shouldBe "/movies/4k.mkv"
		result[1].field(R.string.file_info_path) shouldBe "/movies/hd.mp4"
		result[0].field(R.string.file_info_bitrate) shouldBe "20.00 Mbps"
		result[1].field(R.string.file_info_bitrate) shouldBe "4.50 Mbps"
		result[0].field(R.string.file_info_size) shouldBe "2.00 GiB"
	}
	test("missing metadata in another version does not borrow the first file's path or tracks") {
		val stream = stream(index = 0, type = MediaStreamType.VIDEO, codec = "hevc")
		val result = mediaFileInfo(item(listOf(source(path = "/first.mkv"), source()), listOf(stream)))
		result[1].field(R.string.file_info_path) shouldBe null
		result[1].fields.any { it.label == R.string.file_info_video } shouldBe false
		result[1].field(R.string.file_info_bitrate) shouldBe null
	}
	test("single files fall back to item path and streams without inventing total bitrate") {
		val streams = listOf(
			stream(index = 0, type = MediaStreamType.VIDEO, codec = "hevc", width = 3840, height = 2160, averageFrameRate = 23.976f),
			stream(index = 1, type = MediaStreamType.AUDIO, codec = "aac", bitRate = 192_000, language = "eng"),
			stream(index = 2, type = MediaStreamType.SUBTITLE, codec = "srt", language = "spa"),
		)
		val source = mediaFileInfo(item(streams = streams)).single()
		source.field(R.string.file_info_path) shouldBe "/movies/original.mkv"
		source.field(R.string.file_info_bitrate) shouldBe null
		source.field(R.string.file_info_video)!! shouldContain "3840 × 2160"
		source.field(R.string.file_info_video)!! shouldContain "23.976 fps"
		source.field(R.string.file_info_audio)!! shouldContain "192 kbps"
		source.field(R.string.file_info_subtitle)!! shouldContain "spa"
	}
	test("unknown or invalid size and bitrate stay unavailable") {
		formatFileBitrate(null) shouldBe null
		formatFileBitrate(0) shouldBe null
		formatFileBitrate(-1) shouldBe null
		formatFileSize(null) shouldBe null
		formatFileSize(-1) shouldBe null
		formatFileSize(1_048_576L) shouldBe "1.00 MiB"
	}
	test("folders have no file action while incomplete movie metadata still does") {
		canShowFileInfo(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE)) shouldBe true
		canShowFileInfo(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.FOLDER, isFolder = true, path = "/movies")) shouldBe false
	}
	test("dialog summaries survive saved-state serialization") {
		val original = mediaFileInfo(item(listOf(source(name = "4K", container = "mkv"))))
		Json.decodeFromString<List<FileInfoSource>>(Json.encodeToString(original)) shouldBe original
	}
})
