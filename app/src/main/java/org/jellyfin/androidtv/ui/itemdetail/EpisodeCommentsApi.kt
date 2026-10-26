package org.jellyfin.androidtv.ui.itemdetail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@Serializable
internal data class EpisodeComment(
	val id: Long,
	val author: String,
	val text: String,
	val spoiler: Boolean = false,
	val likes: Int = 0,
	val replyCount: Int = 0,
	val createdAt: String? = null,
) {
	// Trakt also supports inline spoiler tags. Hide the whole comment until revealed.
	val containsSpoilers: Boolean get() = spoiler || text.contains("[spoiler]", ignoreCase = true)
}

@Serializable
internal data class EpisodeCommentPage(
	val page: Int,
	val pageCount: Int,
	val totalItems: Int,
	val comments: List<EpisodeComment>,
)

@Serializable
private data class EpisodeCommentsCapabilities(val pluginId: String, val apiVersion: Int, val configured: Boolean, val supportedItemTypes: List<String> = listOf("Episode"))

internal class EpisodeCommentsApi(private val api: ApiClient) {
	private val json = Json { ignoreUnknownKeys = true }

	suspend fun getPreview(itemId: UUID): EpisodeCommentPage? = try {
		getComments(itemId, "likes", 1)
	} catch (error: InvalidStatusException) {
		// An unmatched or ambiguous title has no discussion to display.
		if (error.status == 404 || error.status == 409) null else throw error
	}

	suspend fun isAvailable(itemType: BaseItemKind? = BaseItemKind.EPISODE): Boolean = try {
		withTimeoutOrNull(2500) {
			withContext(Dispatchers.IO) {
				val response = api.request(pathTemplate = "/EpisodeComments/v1/Capabilities")
				val capabilities = json.decodeFromString<EpisodeCommentsCapabilities>(response.body.decodeToString())
				val type = when (itemType) {
					BaseItemKind.EPISODE -> "Episode"
					BaseItemKind.MOVIE -> "Movie"
					BaseItemKind.SERIES -> "Series"
					else -> ""
				}
				capabilities.pluginId == "79672a4d-f247-4370-9e41-f508bd891631" && capabilities.apiVersion == 1 && capabilities.configured && type in capabilities.supportedItemTypes
			}
		} ?: false
	} catch (error: CancellationException) {
		throw error
	} catch (_: Exception) {
		// Optional server feature: absent, disabled, old or unreachable means hidden.
		false
	}

	suspend fun getComments(itemId: UUID, sort: String, page: Int, parentId: Long? = null): EpisodeCommentPage = withContext(Dispatchers.IO) {
		val path = if (parentId == null) {
			"/EpisodeComments/v1/Items/{itemId}/Comments"
		} else {
			"/EpisodeComments/v1/Items/{itemId}/Comments/{commentId}/Replies"
		}
		val parameters = buildMap<String, Any?> {
			put("itemId", itemId)
			if (parentId != null) put("commentId", parentId)
		}
		val query = buildMap<String, Any?> {
			put("page", page)
			put("limit", 20)
			if (parentId == null) put("sort", sort)
		}
		// The SDK preserves server base paths and sends the current Jellyfin session header.
		val response = api.request(pathTemplate = path, pathParameters = parameters, queryParameters = query)
		json.decodeFromString(response.body.decodeToString())
	}
}
