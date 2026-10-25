package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment
import org.jellyfin.androidtv.ui.browsing.BrowseViewFragment
import org.jellyfin.androidtv.ui.browsing.ByGenreFragment
import org.jellyfin.androidtv.ui.browsing.ByLetterFragment
import org.jellyfin.androidtv.ui.browsing.CollectionFragment
import org.jellyfin.androidtv.ui.browsing.GenericFolderFragment
import org.jellyfin.androidtv.ui.browsing.SuggestedMoviesFragment
import org.jellyfin.androidtv.ui.itemdetail.FullDetailsFragment
import org.jellyfin.androidtv.ui.playback.CustomPlaybackOverlayFragment
import org.jellyfin.androidtv.ui.player.photo.PhotoPlayerFragment
import org.jellyfin.androidtv.ui.player.video.VideoPlayerFragment

class BrowsingSidebarDestinationTests : FunSpec({
	test("Home and every library presentation retain the navigation rail") {
		for (fragment in listOf(HomeFragment::class, BrowseGridFragment::class, BrowseViewFragment::class, ByGenreFragment::class, ByLetterFragment::class, CollectionFragment::class, GenericFolderFragment::class, SuggestedMoviesFragment::class)) {
			showsBrowsingSidebar(fragment) shouldBe true
		}
	}
	test("details and all players use the whole screen") {
		for (fragment in listOf(FullDetailsFragment::class, CustomPlaybackOverlayFragment::class, VideoPlayerFragment::class, PhotoPlayerFragment::class)) {
			showsBrowsingSidebar(fragment) shouldBe false
		}
	}
})
