package org.jellyfin.androidtv.ui.playback

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jellyfin.playback.media3.session.MediaSkipKeyHandler

/** ExoPlayer contains one stream; expose Jellyfin's queue to Bluetooth/system media controls. */
@OptIn(UnstableApi::class)
class RegularVideoMediaSession(context: Context, player: Player, controller: PlaybackController) {
	private val queuePlayer = object : ForwardingSimpleBasePlayer(player) {
		private val queue = controller.queueItems.mapIndexed { index, item ->
			MediaItemData.Builder(index)
				.setMediaItem(MediaItem.Builder().setMediaId(item.id.toString()).setMediaMetadata(
					MediaMetadata.Builder().setTitle(item.name).setArtist(item.seriesName).build()
				).build())
				.setDurationUs(item.runTimeTicks?.div(10) ?: C.TIME_UNSET)
				.setIsSeekable(true)
				.build()
		}

		override fun getState(): State {
			val state = super.getState()
			val index = controller.queueIndex
			if (state.playlist.isEmpty() || index !in queue.indices || controller.isLiveTv) return state
			val active = state.playlist.getOrNull(state.currentMediaItemIndex) ?: return state
			val playlist = queue.toMutableList()
			playlist[index] = active.buildUpon()
				.setUid(index).setMediaItem(queue[index].mediaItem).build()
			val commands = state.availableCommands.buildUpon()
				.remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).remove(Player.COMMAND_SET_MEDIA_ITEM)
				.remove(Player.COMMAND_SET_REPEAT_MODE).remove(Player.COMMAND_SET_SHUFFLE_MODE)
				.remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
				.remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
				.addIf(Player.COMMAND_SEEK_TO_NEXT, controller.hasNextItem())
				.addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, controller.hasNextItem())
				.addIf(Player.COMMAND_SEEK_TO_PREVIOUS, controller.hasPreviousItem())
				.addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, controller.hasPreviousItem())
				.build()
			return state.buildUpon().setPlaylist(playlist).setCurrentMediaItemIndex(index).setAvailableCommands(commands).build()
		}

		// Keep reporting, transcoding seeks and pause state inside the regular controller.
		override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
			if (controller.hasFragment()) {
				if (playWhenReady) controller.play(controller.currentPosition) else controller.pause()
			}
			return Futures.immediateVoidFuture()
		}

		override fun handleStop(): ListenableFuture<*> {
			controller.stop()
			controller.fragment?.closePlayer()
			return Futures.immediateVoidFuture()
		}

		override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
			controller.setPlaybackSpeed(playbackParameters.speed)
			return Futures.immediateVoidFuture()
		}

		override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
			if (!controller.hasFragment()) return Futures.immediateVoidFuture()
			when (seekCommand) {
				Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> controller.next()
				Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.prev()
				else -> {
					if (mediaItemIndex == controller.queueIndex) controller.seek(if (positionMs == C.TIME_UNSET) 0 else positionMs)
					else controller.playQueueItem(mediaItemIndex)
				}
			}
			invalidateState()
			return Futures.immediateVoidFuture()
		}
	}
	private val keys = MediaSkipKeyHandler({ controller.hasFragment() }, { controller.next() }, { controller.prev() })
	private val session = MediaSession.Builder(context, queuePlayer)
		.setId("regular-video-${System.identityHashCode(controller)}")
		.setCallback(object : MediaSession.Callback {
			override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
				if (intent.action != Intent.ACTION_MEDIA_BUTTON) return false
				val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
				return keys.onKey(event.keyCode, event.action, event.repeatCount, event.isCanceled)
			}
		}).build()

	fun release() = session.release()
}
