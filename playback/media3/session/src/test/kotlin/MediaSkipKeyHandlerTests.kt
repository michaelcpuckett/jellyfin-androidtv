package org.jellyfin.playback.media3.session

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MediaSkipKeyHandlerTests : FunSpec({
	for (key in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD)) {
		test("mapped key $key skips once across down, held repeats and release") {
			var next = 0
			var previous = 0
			val handler = MediaSkipKeyHandler({ true }, { next++ }, { previous++ })
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0) shouldBe true
			for (repeat in 1..5) handler.onKey(key, KeyEvent.ACTION_DOWN, repeat) shouldBe true
			handler.onKey(key, KeyEvent.ACTION_UP, 0) shouldBe true
			val forward = key == KeyEvent.KEYCODE_MEDIA_NEXT || key == KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
			next shouldBe if (forward) 1 else 0
			previous shouldBe if (forward) 0 else 1
		}
	}
	test("separate presses and software down-only events each advance once") {
		var skips = 0
		val handler = MediaSkipKeyHandler({ true }, { skips++ }, {})
		repeat(3) { handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) }
		skips shouldBe 3
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		skips shouldBe 3
	}
	test("empty audio queues leave keys to the regular video player or system") {
		var skips = 0
		val handler = MediaSkipKeyHandler({ false }, { skips++ }, { skips++ })
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) shouldBe false
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe false
		skips shouldBe 0
	}
	test("release stays consumed if skipping or another action clears the queue") {
		var queuePresent = true
		var skips = 0
		val handler = MediaSkipKeyHandler({ queuePresent }, { skips++; queuePresent = false }, {})
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) shouldBe true
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		skips shouldBe 1
	}
	test("pause, play, center, directions and volume retain their existing handlers") {
		var skips = 0
		val handler = MediaSkipKeyHandler({ true }, { skips++ }, { skips++ })
		for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
			KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_VOLUME_UP)) {
			handler.onKey(key, KeyEvent.ACTION_DOWN, 0) shouldBe false
			handler.onKey(key, KeyEvent.ACTION_UP, 0) shouldBe false
		}
		skips shouldBe 0
	}
	test("canceled presses and orphan releases never advance tracks") {
		var skips = 0
		val handler = MediaSkipKeyHandler({ true }, { skips++ }, {})
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0, canceled = true) shouldBe true
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe true
		skips shouldBe 0
	}
	test("a later video key-up is not swallowed by an old down-only audio event") {
		var queuePresent = true
		val handler = MediaSkipKeyHandler({ queuePresent }, {}, {})
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) shouldBe true
		queuePresent = false
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, 0) shouldBe false
		handler.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, 0) shouldBe false
	}
})
