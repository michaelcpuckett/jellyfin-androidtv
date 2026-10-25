package org.jellyfin.androidtv.ui.home

import android.view.KeyEvent
import android.view.View
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HomeFocusTargetTests : FunSpec({
	test("left enters the sidebar only at the first card of every LTR row") {
		for (row in 0..8) {
			homeFocusTarget(KeyEvent.KEYCODE_DPAD_LEFT, row, 0, View.LAYOUT_DIRECTION_LTR) shouldBe HomeFocusTarget.Sidebar
			for (item in 1..10) {
				homeFocusTarget(KeyEvent.KEYCODE_DPAD_LEFT, row, item, View.LAYOUT_DIRECTION_LTR) shouldBe null
			}
		}
	}
	test("up enters the toolbar only from the first row without changing the selected card") {
		for (item in 0..10) {
			homeFocusTarget(KeyEvent.KEYCODE_DPAD_UP, 0, item, View.LAYOUT_DIRECTION_LTR) shouldBe HomeFocusTarget.Toolbar
			homeFocusTarget(KeyEvent.KEYCODE_DPAD_UP, 1, item, View.LAYOUT_DIRECTION_LTR) shouldBe null
		}
	}
	test("RTL uses right for the sidebar boundary and keeps left inside the content") {
		homeFocusTarget(KeyEvent.KEYCODE_DPAD_RIGHT, 4, 0, View.LAYOUT_DIRECTION_RTL) shouldBe HomeFocusTarget.Sidebar
		homeFocusTarget(KeyEvent.KEYCODE_DPAD_RIGHT, 4, 2, View.LAYOUT_DIRECTION_RTL) shouldBe null
		homeFocusTarget(KeyEvent.KEYCODE_DPAD_LEFT, 4, 0, View.LAYOUT_DIRECTION_RTL) shouldBe null
	}
	test("loading and unselected rows do not trigger a sidebar exit") {
		homeFocusTarget(KeyEvent.KEYCODE_DPAD_LEFT, -1, 0, View.LAYOUT_DIRECTION_LTR) shouldBe null
		homeFocusTarget(KeyEvent.KEYCODE_DPAD_LEFT, 0, -1, View.LAYOUT_DIRECTION_LTR) shouldBe null
	}
	test("down, forward and activation keys do not leave the rows") {
		for (key in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
			homeFocusTarget(key, 0, 0, View.LAYOUT_DIRECTION_LTR) shouldBe null
		}
	}
})
