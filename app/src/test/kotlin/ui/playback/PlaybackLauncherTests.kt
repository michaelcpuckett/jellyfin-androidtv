package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.os.Bundle
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.navigation.Destination
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaType

class PlaybackLauncherTests : FunSpec({
	afterTest {
		unmockkObject(Destinations)
		unmockkStatic(::createBundle)
	}

	test("internal playback uses the regular player even with a previously saved rewrite preference") {
		mockkStatic(::createBundle)
		every { createBundle(any()) } returns mockk<Bundle>(relaxed = true)
		mockkObject(Destinations)
		val context = mockk<Context>()
		val queue = mockk<VideoQueueManager>(relaxed = true)
		val navigation = mockk<NavigationRepository>(relaxed = true)
		val preferences = mockk<UserPreferences> {
			every { get(UserPreferences.useExternalPlayer) } returns false
			every { get(UserPreferences.playbackRewriteVideoEnabled) } returns true
		}
		val item = mockk<BaseItemDto> { every { mediaType } returns MediaType.VIDEO }
		val destination = mockk<Destination.Fragment>()
		every { Destinations.videoPlayer(12_000) } returns destination

		PlaybackLauncher(mockk(), queue, navigation, preferences).launch(context, listOf(item), position = 12_000)

		verify { navigation.navigate(destination, false) }
		verify { queue.setCurrentVideoQueue(listOf(item)) }
		verify(exactly = 0) { Destinations.videoPlayerNew(any()) }
		verify(exactly = 0) { context.startActivity(any()) }
	}
})
