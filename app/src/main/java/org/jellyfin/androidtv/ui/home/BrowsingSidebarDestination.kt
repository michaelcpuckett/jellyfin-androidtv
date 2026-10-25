package org.jellyfin.androidtv.ui.home

import androidx.fragment.app.Fragment
import org.jellyfin.androidtv.ui.browsing.BrowseFolderFragment
import org.jellyfin.androidtv.ui.browsing.BrowseGridFragment
import org.jellyfin.androidtv.ui.browsing.EnhancedBrowseFragment
import org.jellyfin.androidtv.ui.livetv.LiveTvGuideFragment
import kotlin.reflect.KClass

/** Library browsing shares the navigation shell; details and playback use the whole screen. */
internal fun showsBrowsingSidebar(fragment: KClass<out Fragment>): Boolean =
	fragment == HomeFragment::class ||
		BrowseGridFragment::class.java.isAssignableFrom(fragment.java) ||
		EnhancedBrowseFragment::class.java.isAssignableFrom(fragment.java) ||
		BrowseFolderFragment::class.java.isAssignableFrom(fragment.java) ||
		fragment == LiveTvGuideFragment::class
