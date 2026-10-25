package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class SidebarActiveIndexTests : FunSpec({
	val libraries = List(30) { UUID.randomUUID() }
	test("Home is focused when no library is selected") {
		sidebarActiveIndex(libraries, null) shouldBe 0
	}
	test("returning from a library targets that entry including entries outside the viewport") {
		sidebarActiveIndex(libraries, libraries[1]) shouldBe 3
		sidebarActiveIndex(libraries, libraries[29]) shouldBe 31
	}
	test("a removed library safely falls back to Home") {
		sidebarActiveIndex(libraries, UUID.randomUUID()) shouldBe 0
		sidebarActiveIndex(emptyList(), libraries[0]) shouldBe 0
	}
})
