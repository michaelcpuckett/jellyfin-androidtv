package org.jellyfin.androidtv.ui.playback

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlaybackControlsKeyHandlerTests : FunSpec({
	for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
		test("first press reveals controls and consumes release, second press reaches Pause for key $key") {
			val handler = PlaybackControlsKeyHandler()
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0, false) shouldBe true
			// Showing the controls changes visibility before the same press is released.
			handler.onKey(key, KeyEvent.ACTION_UP, 0, true) shouldBe true
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0, true) shouldBe false
			handler.onKey(key, KeyEvent.ACTION_UP, 0, true) shouldBe false
		}
		test("holding the reveal button never clicks through to Pause for key $key") {
			val handler = PlaybackControlsKeyHandler()
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0, false) shouldBe true
			for (repeat in 1..5) handler.onKey(key, KeyEvent.ACTION_DOWN, repeat, true) shouldBe true
			handler.onKey(key, KeyEvent.ACTION_UP, 0, true) shouldBe true
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0, true) shouldBe false
		}
	}
	test("visible controls and special UI receive normal center presses") {
		val handler = PlaybackControlsKeyHandler()
		handler.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0, true) shouldBe false
		handler.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, 0, true) shouldBe false
	}
	test("media Play Pause and directional keys keep their normal behavior") {
		val handler = PlaybackControlsKeyHandler()
		for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK)) {
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0, false) shouldBe false
			handler.onKey(key, KeyEvent.ACTION_UP, 0, false) shouldBe false
		}
	}
	test("controls can be revealed again after auto-hide or Back") {
		val handler = PlaybackControlsKeyHandler()
		repeat(3) {
			handler.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0, false) shouldBe true
			handler.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, 0, true) shouldBe true
		}
	}
	test("leaving the player during a press clears the consumed-key state") {
		val handler = PlaybackControlsKeyHandler()
		handler.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0, false) shouldBe true
		handler.reset()
		handler.onKey(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0, true) shouldBe false
	}
})
