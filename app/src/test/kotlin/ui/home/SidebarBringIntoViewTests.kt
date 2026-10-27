package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

@OptIn(ExperimentalFoundationApi::class)
class SidebarBringIntoViewTests : FunSpec({
	test("focusing any fully visible sidebar entry keeps the list still") {
		for (offset in listOf(0f, 48f, 160f, 320f, 440f)) {
			SidebarBringIntoViewSpec.calculateScrollDistance(offset, 40f, 480f) shouldBe 0f
		}
	}
	test("a clipped entry scrolls only as far as the nearest edge") {
		SidebarBringIntoViewSpec.calculateScrollDistance(-12f, 40f, 480f) shouldBe -12f
		SidebarBringIntoViewSpec.calculateScrollDistance(460f, 40f, 480f) shouldBe 20f
	}
	test("an offscreen entry remains reachable without moving it to a viewport pivot") {
		SidebarBringIntoViewSpec.calculateScrollDistance(-80f, 40f, 480f) shouldBe -80f
		SidebarBringIntoViewSpec.calculateScrollDistance(520f, 40f, 480f) shouldBe 80f
	}
})
