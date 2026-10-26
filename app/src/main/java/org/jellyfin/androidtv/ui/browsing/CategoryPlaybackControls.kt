package org.jellyfin.androidtv.ui.browsing

import android.app.AlertDialog
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

object CategoryPlaybackControls {
	private const val TAG = "category-playback-actions"

	@JvmStatic
	fun focus(root: View?): Boolean = root?.findViewWithTag<ViewGroup>(TAG)?.getChildAt(0)?.requestFocus() == true

	@JvmStatic
	fun connectGrid(root: View, grid: View) {
		root.findViewWithTag<ViewGroup>(TAG)?.getChildAt(0)?.let { grid.nextFocusUpId = it.id }
	}

	@JvmStatic
	fun install(fragment: Fragment, folder: BaseItemDto?, root: View, anchor: View, content: View) {
		if (folder == null || !supportsCategoryPlayback(folder) || root !is RelativeLayout || root.findViewWithTag<View>(TAG) != null) return
		val context = root.context
		val actions = LinearLayout(context).apply {
			id = View.generateViewId()
			tag = TAG
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
		}
		val margin = Utils.convertDpToPixel(context, 48)
		// Keep the settings overlay (and other existing foreground views) above these controls.
		root.addView(actions, 0, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.convertDpToPixel(context, 40)).apply {
			addRule(RelativeLayout.BELOW, anchor.id)
			marginStart = margin
			marginEnd = margin
			topMargin = Utils.convertDpToPixel(context, 8)
		})
		(content.layoutParams as RelativeLayout.LayoutParams).apply {
			addRule(RelativeLayout.BELOW, actions.id)
			topMargin = Utils.convertDpToPixel(context, 12)
			content.layoutParams = this
		}
		val controller = Controller(fragment, folder)
		val buttons = listOf(R.string.lbl_play_all, R.string.category_shuffle_limit).mapIndexed { index, label ->
			Button(context, null, 0, R.style.Button_Default).apply {
				id = View.generateViewId()
				text = if (index == 1) context.getString(label, CATEGORY_SHUFFLE_LIMIT) else context.getString(label)
				isAllCaps = false
				textSize = 14f
				isSingleLine = true
				setCompoundDrawablesWithIntrinsicBounds(if (index == 0) R.drawable.ic_play else R.drawable.ic_shuffle, 0, 0, 0)
				compoundDrawablePadding = Utils.convertDpToPixel(context, 8)
				actions.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
					marginEnd = Utils.convertDpToPixel(context, 12)
				})
				setOnKeyListener { view, key, event ->
					if (event.action != KeyEvent.ACTION_DOWN) false
					else when (key) {
						KeyEvent.KEYCODE_DPAD_DOWN -> content.requestFocus()
						KeyEvent.KEYCODE_DPAD_UP -> anchor.requestFocus()
						else -> {
							val startKey = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
							index == 0 && key == startKey && view.focusNavigationSidebar()
						}
					}
				}
			}
		}
		buttons.forEachIndexed { index, button -> button.setOnClickListener { controller.play(index == 1, buttons) } }
	}

	private class Controller(private val fragment: Fragment, private val folder: BaseItemDto) : KoinComponent {
		private val api by inject<ApiClient>()
		private val users by inject<UserRepository>()
		private val launcher by inject<PlaybackLauncher>()
		private var loading = false

		fun play(shuffle: Boolean, buttons: List<Button>) {
			if (loading) return
			loading = true
			buttons.forEach { it.isEnabled = false }
			val selected = buttons[if (shuffle) 1 else 0]
			val label = selected.text
			selected.setText(R.string.loading)
			fragment.viewLifecycleOwner.lifecycleScope.launch {
				try {
					val userId = requireNotNull(users.currentUser.value).id
					val mediaTypes = categoryPlaybackMediaTypes(folder)
					val (audio, video) = withContext(Dispatchers.IO) {
						coroutineScope {
							val music = async {
								if (MediaType.AUDIO in mediaTypes)
									loadCategoryPlaybackItems(categoryPlaybackRequest(folder.id, userId, MediaType.AUDIO, shuffle)) { api.itemsApi.getItems(it).content.items }
								else emptyList()
							}
							val videos = if (MediaType.VIDEO in mediaTypes)
								loadCategoryPlaybackItems(categoryPlaybackRequest(folder.id, userId, MediaType.VIDEO, shuffle)) { api.itemsApi.getItems(it).content.items }
							else emptyList()
							music.await() to videos
						}
					}
					// Do not start playback if the user left this page while the queue was loading.
					if (!fragment.isResumed) return@launch
					when {
						audio.isEmpty() && video.isEmpty() -> Toast.makeText(fragment.requireContext(), R.string.lbl_no_items, Toast.LENGTH_SHORT).show()
						audio.isNotEmpty() && video.isNotEmpty() -> chooseMedia(audio, video, shuffle)
						else -> launcher.launch(fragment.requireContext(), audio.ifEmpty { video }, shuffle = shuffle)
					}
				} catch (cancelled: CancellationException) {
					throw cancelled
				} catch (error: Exception) {
					Timber.e(error, "Could not load category playback queue")
					if (fragment.isResumed) Toast.makeText(fragment.requireContext(), R.string.category_playback_failed, Toast.LENGTH_LONG).show()
				} finally {
					loading = false
					selected.text = label
					buttons.forEach { it.isEnabled = true }
				}
			}
		}

		private fun chooseMedia(audio: List<BaseItemDto>, video: List<BaseItemDto>, shuffle: Boolean) {
			val lifecycle = fragment.viewLifecycleOwner.lifecycle
			val dialog = AlertDialog.Builder(fragment.requireContext())
				.setTitle(if (shuffle) fragment.getString(R.string.category_shuffle_limit, CATEGORY_SHUFFLE_LIMIT) else fragment.getString(R.string.lbl_play_all))
				.setItems(arrayOf(fragment.getString(R.string.category_play_music), fragment.getString(R.string.category_play_videos))) { _, index ->
					launcher.launch(fragment.requireContext(), if (index == 0) audio else video, shuffle = shuffle)
				}.create()
			val observer = object : DefaultLifecycleObserver {
				override fun onDestroy(owner: LifecycleOwner) = dialog.dismiss()
			}
			lifecycle.addObserver(observer)
			dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
			dialog.show()
		}
	}
}
