package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.constant.HomeSectionType

/** Libraries already live in the rail. Keep library-only dashboards usable for existing users. */
internal fun homeDashboardSections(sections: List<HomeSectionType>): List<HomeSectionType> {
	val supported = sections.filterNot { it == HomeSectionType.NONE || it == HomeSectionType.RESUME_BOOK }
	val librarySections = setOf(HomeSectionType.LIBRARY_TILES_SMALL, HomeSectionType.LIBRARY_BUTTONS)
	return if (supported.any { it !in librarySections }) supported.filterNot { it in librarySections } else supported
}
