package org.jellyfin.androidtv.ui.itemdetail

import kotlinx.serialization.Serializable
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale

@Serializable
internal data class FileInfoField(val label: Int, val value: String?)

@Serializable
internal data class FileInfoSource(val title: String?, val fields: List<FileInfoField>)

internal fun canShowFileInfo(item: BaseItemDto): Boolean = item.isFolder != true && (
	!item.mediaSources.isNullOrEmpty() || !item.path.isNullOrBlank() || item.type in setOf(
		BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO, BaseItemKind.AUDIO,
		BaseItemKind.RECORDING, BaseItemKind.PHOTO,
	)
)

// Serialize just this summary; a complete item DTO can contain huge chapter/trickplay lists.
internal fun mediaFileInfo(item: BaseItemDto): List<FileInfoSource> {
	val sources: List<MediaSourceInfo?> = item.mediaSources.orEmpty().ifEmpty { listOf(null) }
	return sources.map { source ->
		val single = sources.size == 1
		val streams = source?.mediaStreams?.takeIf { it.isNotEmpty() }
			?: if (single) item.mediaStreams.orEmpty() else emptyList()
		FileInfoSource(source?.name?.takeIf { it.isNotBlank() }, buildList {
			add(FileInfoField(R.string.file_info_path, source?.path?.takeIf { it.isNotBlank() } ?: item.path?.takeIf { single && it.isNotBlank() }))
			add(FileInfoField(R.string.file_info_container, source?.container?.takeIf { it.isNotBlank() }))
			add(FileInfoField(R.string.file_info_size, formatFileSize(source?.size)))
			add(FileInfoField(R.string.file_info_bitrate, formatFileBitrate(source?.bitrate)))
			for (stream in streams) {
				val label = when (stream.type) {
					MediaStreamType.VIDEO -> R.string.file_info_video
					MediaStreamType.AUDIO -> R.string.file_info_audio
					MediaStreamType.SUBTITLE -> R.string.file_info_subtitle
					else -> continue
				}
				add(FileInfoField(label, fileStreamDescription(stream)))
			}
		})
	}
}

internal fun formatFileBitrate(value: Int?): String? = value?.takeIf { it > 0 }?.let {
	if (it >= 1_000_000) String.format(Locale.ROOT, "%.2f Mbps", it / 1_000_000.0)
	else String.format(Locale.ROOT, "%.0f kbps", it / 1_000.0)
}

internal fun formatFileSize(value: Long?): String? = value?.takeIf { it > 0 }?.let {
	if (it >= 1_073_741_824L) String.format(Locale.ROOT, "%.2f GiB", it / 1_073_741_824.0)
	else String.format(Locale.ROOT, "%.2f MiB", it / 1_048_576.0)
}

private fun fileStreamDescription(stream: MediaStream): String? = buildList {
	add(stream.displayTitle?.takeIf { it.isNotBlank() } ?: stream.codec)
	if (stream.type == MediaStreamType.VIDEO) {
		if ((stream.width ?: 0) > 0 && (stream.height ?: 0) > 0) add("${stream.width} × ${stream.height}")
		stream.averageFrameRate?.takeIf { it > 0 }?.let { add(String.format(Locale.ROOT, "%.3f fps", it)) }
	}
	if (stream.type == MediaStreamType.AUDIO) {
		stream.channelLayout?.let { add(it) }
		stream.sampleRate?.takeIf { it > 0 }?.let { add("$it Hz") }
	}
	add(stream.language)
	add(formatFileBitrate(stream.bitRate))
}.filterNotNull().filter { it.isNotBlank() }.distinct().joinToString(" · ").takeIf { it.isNotBlank() }
