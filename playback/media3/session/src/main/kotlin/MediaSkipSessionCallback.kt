package org.jellyfin.playback.media3.session

import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

@OptIn(UnstableApi::class)
internal class MediaSkipSessionCallback(private val keys: MediaSkipKeyHandler) : MediaSession.Callback {
	override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
		if (intent.action != Intent.ACTION_MEDIA_BUTTON) return false
		val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
		return keys.onKey(event.keyCode, event.action, event.repeatCount, event.isCanceled)
	}
}
