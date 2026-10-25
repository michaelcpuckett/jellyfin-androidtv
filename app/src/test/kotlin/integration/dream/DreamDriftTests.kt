package org.jellyfin.androidtv.integration.dream.composable

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual

class DreamDriftTests : FunSpec({
	test("moving clock and logo stay completely inside common TV screen sizes") {
		for (screen in listOf(IntSize(960, 540), IntSize(1920, 1080), IntSize(3840, 2160))) {
			for (element in listOf(IntSize(120, 40), IntSize(480, 200))) {
				for (step in 0..100) {
					val offset = dreamDriftOffset(screen, element, step / 100f, 1f - step / 100f)
					offset.x shouldBeGreaterThanOrEqual 0
					offset.y shouldBeGreaterThanOrEqual 0
					(offset.x + element.width) shouldBeLessThanOrEqual screen.width
					(offset.y + element.height) shouldBeLessThanOrEqual screen.height
				}
			}
		}
	}

	test("motion reaches different edges without leaving a fixed center logo") {
		val screen = IntSize(960, 540)
		val logo = IntSize(240, 100)
		dreamDriftOffset(screen, logo, 0f, 0f) shouldBe IntOffset.Zero
		dreamDriftOffset(screen, logo, 1f, 1f) shouldBe IntOffset(720, 440)
		dreamDriftOffset(screen, logo, 0.5f, 0.5f) shouldBe IntOffset(360, 220)
	}

	test("small displays and enlarged content never produce negative or excessive offsets") {
		dreamDriftOffset(IntSize(100, 50), IntSize(240, 100), 1f, 1f) shouldBe IntOffset.Zero
		dreamDriftOffset(IntSize(960, 540), IntSize(240, 100), -0.1f, 1.1f) shouldBe IntOffset(0, 440)
	}
})
