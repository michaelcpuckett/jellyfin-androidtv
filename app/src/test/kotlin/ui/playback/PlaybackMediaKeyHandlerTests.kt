package org.jellyfin.androidtv.ui.playback

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlaybackMediaKeyHandlerTests : FunSpec({
	for (key in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD)) {
		test("video takes priority over a retained music queue for key $key and skips once per press") {
			var index = 1
			var audioSkips = 0
			val handler = PlaybackMediaKeyHandler({ true }, { index++ }, { index-- }, { true }, { audioSkips++ }, { audioSkips++ })
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0) shouldBe true
			for (repeat in 1..5) handler.onKey(key, KeyEvent.ACTION_DOWN, repeat) shouldBe true
			handler.onKey(key, KeyEvent.ACTION_UP, 0) shouldBe true
			index shouldBe if (key == KeyEvent.KEYCODE_MEDIA_NEXT || key == KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD) 2 else 0
			audioSkips shouldBe 0
		}
	}
	test("a press at either video queue boundary never spills into the music queue") {
		var index = 0
		var audioSkips = 0
		val handler = PlaybackMediaKeyHandler({ true }, { if (index < 1) index++ }, { if (index > 0) index-- }, { true }, { audioSkips++ }, { audioSkips++ })
		for (key in listOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_NEXT)) {
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0) shouldBe true
			handler.onKey(key, KeyEvent.ACTION_UP, 0) shouldBe true
		}
		index shouldBe 1
		audioSkips shouldBe 0
	}
	test("music regains its media keys when the video player is closed") {
		var videoActive = true
		var videoSkips = 0
		var audioSkips = 0
		val handler = PlaybackMediaKeyHandler({ videoActive }, { videoSkips++ }, {}, { true }, { audioSkips++ }, {})
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) shouldBe true
		videoActive = false
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) shouldBe true
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		videoSkips shouldBe 1
		audioSkips shouldBe 1
	}
	test("ordinary playback and navigation keys retain their existing handlers") {
		var skips = 0
		val handler = PlaybackMediaKeyHandler({ true }, { skips++ }, { skips++ }, { true }, { skips++ }, { skips++ })
		for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)) {
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0) shouldBe false
			handler.onKey(key, KeyEvent.ACTION_UP, 0) shouldBe false
		}
		skips shouldBe 0
	}
	test("canceled video skips do not change the queue") {
		var skips = 0
		val handler = PlaybackMediaKeyHandler({ true }, { skips++ }, { skips++ }, { false }, {}, {})
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0, canceled = true) shouldBe true
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		skips shouldBe 0
	}
})
