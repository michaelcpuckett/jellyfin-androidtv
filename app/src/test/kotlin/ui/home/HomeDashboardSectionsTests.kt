package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.constant.HomeSectionType

class HomeDashboardSectionsTests : FunSpec({
	test("media rows keep their configured order without repeating the sidebar libraries") {
		homeDashboardSections(listOf(HomeSectionType.LIBRARY_TILES_SMALL, HomeSectionType.RESUME, HomeSectionType.NEXT_UP, HomeSectionType.LATEST_MEDIA)) shouldBe
			listOf(HomeSectionType.RESUME, HomeSectionType.NEXT_UP, HomeSectionType.LATEST_MEDIA)
	}
	test("library-only dashboards still have usable content") {
		homeDashboardSections(listOf(HomeSectionType.LIBRARY_BUTTONS, HomeSectionType.NONE, HomeSectionType.RESUME_BOOK)) shouldBe listOf(HomeSectionType.LIBRARY_BUTTONS)
	}
	test("disabled and unsupported sections do not invent new feeds") {
		homeDashboardSections(listOf(HomeSectionType.NONE, HomeSectionType.RESUME_BOOK)) shouldBe emptyList()
	}
})
