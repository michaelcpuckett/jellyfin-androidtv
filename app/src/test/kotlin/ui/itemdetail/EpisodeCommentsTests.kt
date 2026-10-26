package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

class EpisodeCommentsTests : FunSpec({
	test("unmatched and ambiguous titles have no comments preview") {
		for (status in listOf(404, 409)) {
			val api = mockk<ApiClient>()
			coEvery { api.request(any(), any(), any(), any(), any()) } throws InvalidStatusException(status)
			EpisodeCommentsApi(api).getPreview(UUID.randomUUID()) shouldBe null
		}
	}

	test("connection and upstream errors are not mistaken for missing matches") {
		val api = mockk<ApiClient>()
		coEvery { api.request(any(), any(), any(), any(), any()) } throws InvalidStatusException(502)
		shouldThrow<InvalidStatusException> { EpisodeCommentsApi(api).getPreview(UUID.randomUUID()) }.status shouldBe 502
	}

	test("a matched title with zero comments remains a valid empty discussion") {
		val api = mockk<ApiClient>()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"page":1,"pageCount":0,"totalItems":0,"comments":[]}""".toByteArray(), 200, emptyMap(),
		)
		EpisodeCommentsApi(api).getPreview(UUID.randomUUID())?.comments shouldBe emptyList()
	}

	test("old episode-only server hides movie and series sections") {
		val api = mockk<ApiClient>()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"pluginId":"79672a4d-f247-4370-9e41-f508bd891631","apiVersion":1,"configured":true}""".toByteArray(), 200, emptyMap(),
		)
		EpisodeCommentsApi(api).isAvailable(BaseItemKind.MOVIE) shouldBe false
		EpisodeCommentsApi(api).isAvailable(BaseItemKind.SERIES) shouldBe false
		EpisodeCommentsApi(api).isAvailable(BaseItemKind.EPISODE) shouldBe true
	}

	test("new server advertises movie and series support without enabling unrelated item types") {
		val api = mockk<ApiClient>()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"pluginId":"79672a4d-f247-4370-9e41-f508bd891631","apiVersion":1,"configured":true,"supportedItemTypes":["Movie","Series","Episode"]}""".toByteArray(), 200, emptyMap(),
		)
		EpisodeCommentsApi(api).isAvailable(BaseItemKind.MOVIE) shouldBe true
		EpisodeCommentsApi(api).isAvailable(BaseItemKind.SERIES) shouldBe true
		EpisodeCommentsApi(api).isAvailable(BaseItemKind.PERSON) shouldBe false
	}

	test("a configured compatible plugin enables comments") {
		val api = mockk<ApiClient>()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"pluginId":"79672a4d-f247-4370-9e41-f508bd891631","apiVersion":1,"configured":true,"provider":"Trakt"}""".toByteArray(), 200, emptyMap(),
		)
		EpisodeCommentsApi(api).isAvailable() shouldBe true
		coVerify { api.request(pathTemplate = "/EpisodeComments/v1/Capabilities") }
	}

	test("removing the server plugin disables comments on the next check") {
		val api = mockk<ApiClient>()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"pluginId":"79672a4d-f247-4370-9e41-f508bd891631","apiVersion":1,"configured":true}""".toByteArray(), 200, emptyMap(),
		)
		val comments = EpisodeCommentsApi(api)
		comments.isAvailable() shouldBe true
		coEvery { api.request(any(), any(), any(), any(), any()) } throws InvalidStatusException(404)
		comments.isAvailable() shouldBe false
	}

	test("absent unauthorized and unreachable plugin checks fail closed") {
		for (status in listOf(401, 403, 404, 500, 503)) {
			val api = mockk<ApiClient>()
			coEvery { api.request(any(), any(), any(), any(), any()) } throws InvalidStatusException(status)
			EpisodeCommentsApi(api).isAvailable() shouldBe false
		}
	}

	test("unconfigured incompatible and malformed capabilities hide the feature") {
		for (body in listOf(
			"""{"pluginId":"79672a4d-f247-4370-9e41-f508bd891631","apiVersion":1,"configured":false}""",
			"""{"pluginId":"79672a4d-f247-4370-9e41-f508bd891631","apiVersion":2,"configured":true}""",
			"""{"pluginId":"different-plugin","apiVersion":1,"configured":true}""",
			"<html>not found</html>",
		)) {
			val api = mockk<ApiClient>()
			coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(body.toByteArray(), 200, emptyMap())
			EpisodeCommentsApi(api).isAvailable() shouldBe false
		}
	}

	test("spoiler flags and inline tags both hide previews") {
		EpisodeComment(1, "reader", "secret", spoiler = true).containsSpoilers shouldBe true
		EpisodeComment(1, "reader", "before [SpOiLeR]secret[/spoiler] after").containsSpoilers shouldBe true
		EpisodeComment(1, "reader", "safe").containsSpoilers shouldBe false
	}

	test("comments use the current Jellyfin SDK session and versioned item route") {
		val api = mockk<ApiClient>()
		val itemId = UUID.randomUUID()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"page":2,"pageCount":5,"totalItems":99,"match":{"episodeId":100},"comments":[{"id":77,"author":"reader","text":"safe","replyCount":2,"futureField":true}]}""".toByteArray(),
			200, emptyMap(),
		)
		val page = EpisodeCommentsApi(api).getComments(itemId, "likes", 2)
		page.page shouldBe 2
		page.comments.single().replyCount shouldBe 2
		coVerify {
			api.request(
				pathTemplate = "/EpisodeComments/v1/Items/{itemId}/Comments",
				pathParameters = mapOf("itemId" to itemId),
				queryParameters = mapOf("page" to 2, "limit" to 20, "sort" to "likes"),
			)
		}
	}

	test("reply pages include item context and omit unsupported sort") {
		val api = mockk<ApiClient>()
		val itemId = UUID.randomUUID()
		coEvery { api.request(any(), any(), any(), any(), any()) } returns RawResponse(
			"""{"page":3,"pageCount":3,"totalItems":45,"comments":[]}""".toByteArray(), 200, emptyMap(),
		)
		EpisodeCommentsApi(api).getComments(itemId, "likes", 3, 77).page shouldBe 3
		coVerify {
			api.request(
				pathTemplate = "/EpisodeComments/v1/Items/{itemId}/Comments/{commentId}/Replies",
				pathParameters = mapOf("itemId" to itemId, "commentId" to 77L),
				queryParameters = mapOf("page" to 3, "limit" to 20),
			)
		}
	}
})
