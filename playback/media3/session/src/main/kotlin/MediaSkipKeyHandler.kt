package org.jellyfin.playback.media3.session

import android.view.KeyEvent

/** Handles either directly injected keys or session media-button events, once per press. */
class MediaSkipKeyHandler(
	private val hasQueue: () -> Boolean,
	private val next: () -> Unit,
	private val previous: () -> Unit,
) {
	private val pressedKeys = mutableSetOf<Int>()

	fun onKey(keyCode: Int, action: Int, repeatCount: Int, canceled: Boolean = false): Boolean {
		val forward = when (keyCode) {
			KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> true
			KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> false
			else -> return false
		}
		if (action == KeyEvent.ACTION_UP && pressedKeys.remove(keyCode)) return true
		if (!hasQueue()) {
			if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) pressedKeys.remove(keyCode)
			return false
		}
		return when (action) {
			KeyEvent.ACTION_DOWN -> {
				pressedKeys.add(keyCode)
				if (repeatCount == 0 && !canceled) {
					if (forward) next() else previous()
				}
				true
			}
			KeyEvent.ACTION_UP -> true
			else -> false
		}
	}
}
