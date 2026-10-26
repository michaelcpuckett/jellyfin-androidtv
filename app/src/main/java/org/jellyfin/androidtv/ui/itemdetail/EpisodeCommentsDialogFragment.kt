package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.UUID

@Serializable
private data class DiscussionLocation(val parentId: Long? = null, val page: Int = 1)

class EpisodeCommentsDialogFragment : DialogFragment() {
	private val api by inject<ApiClient>()
	private val locations = mutableListOf(DiscussionLocation())
	private var sort = "likes"
	private var loadJob: Job? = null
	private lateinit var comments: LinearLayout
	private lateinit var status: TextView
	private lateinit var scroll: ScrollView
	private lateinit var sortButton: Button
	private lateinit var previousButton: Button
	private lateinit var nextButton: Button
	private lateinit var backButton: Button

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sort = savedInstanceState?.getString("sort") ?: "likes"
		savedInstanceState?.getString("locations")?.let {
			locations.clear()
			locations.addAll(Json.decodeFromString<List<DiscussionLocation>>(it))
		}
		if (savedInstanceState == null) {
			requireArguments().getLong("replyTo").takeIf { it > 0 }?.let { locations.add(DiscussionLocation(it)) }
		}
		parentFragmentManager.setFragmentResultListener(REPLIES_RESULT, this) { _, result ->
			locations.add(DiscussionLocation(result.getLong("commentId")))
			load()
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString("sort", sort)
		outState.putString("locations", Json.encodeToString(locations))
	}

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val context = requireContext()
		val density = resources.displayMetrics.density
		val padding = (20 * density).toInt()
		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, padding / 2, padding, padding / 2)
		}
		val controls = LinearLayout(context)
		fun control(label: Int, action: () -> Unit) = Button(context).apply {
			setText(label)
			setOnClickListener { action() }
			controls.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
		}
		backButton = control(R.string.episode_comments_back) { goBack() }
		sortButton = control(R.string.episode_comments_sort) {
			val labels = intArrayOf(R.string.episode_comments_sort_likes, R.string.episode_comments_sort_newest, R.string.episode_comments_sort_oldest, R.string.episode_comments_sort_replies)
			val values = arrayOf("likes", "newest", "oldest", "replies")
			AlertDialog.Builder(context)
				.setTitle(R.string.episode_comments_sort)
				.setSingleChoiceItems(labels.map(::getString).toTypedArray(), values.indexOf(sort)) { dialog, index ->
					sort = values[index]
					locations.clear()
					locations.add(DiscussionLocation())
					dialog.dismiss()
					load()
				}.show()
		}
		previousButton = control(R.string.episode_comments_previous) { changePage(-1) }
		nextButton = control(R.string.episode_comments_next) { changePage(1) }
		root.addView(controls)
		status = TextView(context).apply { textSize = 16f; setPadding(0, padding / 2, 0, padding / 2) }
		root.addView(status)
		comments = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		scroll = ScrollView(context).apply { addView(comments); isFillViewport = true }
		root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.48f).toInt()))
		root.addView(TextView(context).apply { setText(R.string.episode_comments_attribution); textSize = 14f })
		return AlertDialog.Builder(context)
			.setTitle(R.string.episode_comments_title)
			.setView(root)
			.setPositiveButton(android.R.string.cancel, null)
			.create().apply {
				setOnKeyListener { _, keyCode, event ->
					if (keyCode == KeyEvent.KEYCODE_BACK && locations.size > 1) {
						if (event.action == KeyEvent.ACTION_UP) goBack()
						true
					} else false
				}
			}
	}

	override fun onStart() {
		super.onStart()
		dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
		load()
	}

	private fun changePage(change: Int) {
		locations[locations.lastIndex] = locations.last().copy(page = locations.last().page + change)
		load()
	}

	private fun goBack() {
		if (locations.size > 1) locations.removeAt(locations.lastIndex)
		load()
	}

	private fun load() {
		if (!::comments.isInitialized) return
		loadJob?.cancel()
		val location = locations.last()
		comments.removeAllViews()
		status.setText(R.string.episode_comments_loading)
		previousButton.isEnabled = false
		nextButton.isEnabled = false
		backButton.isVisible = locations.size > 1
		sortButton.isVisible = location.parentId == null
		loadJob = lifecycleScope.launch {
			try {
				val page = EpisodeCommentsApi(api).getComments(UUID.fromString(requireArguments().getString("itemId")), sort, location.page, location.parentId)
				status.text = if (page.comments.isEmpty()) getString(R.string.episode_comments_empty)
				else getString(if (location.parentId == null) R.string.episode_comments_page else R.string.episode_comments_replies_page, page.page, page.pageCount.coerceAtLeast(1), page.totalItems)
				for (comment in page.comments) {
					comments.addView(Button(requireContext()).apply {
						isAllCaps = false
						gravity = Gravity.START or Gravity.CENTER_VERTICAL
						text = getString(R.string.episode_comments_card, comment.author, comment.likes, comment.replyCount) + "\n" +
							if (comment.containsSpoilers) getString(R.string.episode_comments_spoiler) else comment.text.take(240)
						setOnClickListener { EpisodeCommentDetailDialogFragment.show(parentFragmentManager, comment) }
					}, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
				}
				previousButton.isEnabled = page.page > 1
				nextButton.isEnabled = page.page < page.pageCount
				focusFirstComment()
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				val statusCode = (error as? InvalidStatusException)?.status
				if (location.parentId == null && (statusCode == 404 || statusCode == 409)) {
					dismissAllowingStateLoss()
					return@launch
				}
				Timber.w("Episode comments request failed (%s)", error.javaClass.simpleName)
				status.setText(when ((error as? InvalidStatusException)?.status) {
					401, 403 -> R.string.episode_comments_auth_error
					404 -> R.string.episode_comments_missing_error
					409 -> R.string.episode_comments_mapping_error
					429 -> R.string.episode_comments_rate_error
					503 -> R.string.episode_comments_config_error
					else -> R.string.episode_comments_network_error
				})
				comments.addView(Button(requireContext()).apply {
					setText(R.string.episode_comments_retry)
					setOnClickListener { load() }
				})
				focusFirstComment()
			}
		}
	}

	private fun focusFirstComment() = scroll.post {
		scroll.scrollTo(0, 0)
		(comments.getChildAt(0) ?: if (backButton.isVisible) backButton else sortButton).requestFocus()
	}

	companion object {
		internal const val REPLIES_RESULT = "episode-comments-replies"
		private const val TAG = "episode-comments"

		@JvmStatic
		fun show(manager: FragmentManager, item: BaseItemDto) {
			showDiscussion(manager, item.id, null)
		}

		internal fun showReplies(manager: FragmentManager, itemId: UUID, commentId: Long) {
			showDiscussion(manager, itemId, commentId)
		}

		private fun showDiscussion(manager: FragmentManager, itemId: UUID, parentId: Long?) {
			if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
			EpisodeCommentsDialogFragment().apply {
				arguments = Bundle().apply {
					putString("itemId", itemId.toString())
					if (parentId != null) putLong("replyTo", parentId)
				}
			}.showNow(manager, TAG)
		}
	}
}

class EpisodeCommentDetailDialogFragment : DialogFragment() {
	private var revealed = false
	private var repliesRequested = false

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val comment = Json.decodeFromString<EpisodeComment>(requireArguments().getString("comment")!!)
		revealed = savedInstanceState?.getBoolean("revealed") ?: false
		val padding = (24 * resources.displayMetrics.density).toInt()
		val body = TextView(requireContext()).apply {
			textSize = 18f
			setPadding(padding, padding / 2, padding, padding / 2)
		}
		fun updateText() {
			body.text = if (comment.containsSpoilers && !revealed) getString(R.string.episode_comments_spoiler)
			else comment.text
		}
		updateText()
		val scroll = ScrollView(requireContext()).apply { addView(body); isFocusable = true }
		val builder = AlertDialog.Builder(requireContext())
			.setTitle(getString(R.string.episode_comments_detail_title, comment.author, comment.createdAt?.take(10) ?: ""))
			.setView(scroll)
			.setPositiveButton(android.R.string.ok, null)
		if (comment.containsSpoilers) builder.setNeutralButton(R.string.episode_comments_reveal, null)
		if (comment.replyCount > 0) builder.setNegativeButton(R.string.episode_comments_replies) { _, _ ->
			if (requireArguments().containsKey("itemId")) repliesRequested = true
			else parentFragmentManager.setFragmentResult(EpisodeCommentsDialogFragment.REPLIES_RESULT, Bundle().apply { putLong("commentId", comment.id) })
		}
		return builder.create().apply {
			setOnShowListener {
				scroll.layoutParams = scroll.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.5f).toInt() }
				window?.setLayout((resources.displayMetrics.widthPixels * 0.8f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
				if (comment.containsSpoilers) getButton(AlertDialog.BUTTON_NEUTRAL).apply {
					setText(if (revealed) R.string.episode_comments_hide else R.string.episode_comments_reveal)
					setOnClickListener {
						revealed = !revealed
						updateText()
						setText(if (revealed) R.string.episode_comments_hide else R.string.episode_comments_reveal)
					}
				}
				getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
			}
		}
	}

	override fun onDismiss(dialog: DialogInterface) {
		// A preview opens independently. Only the explicit Replies action opens a discussion,
		// after this comment dialog has closed.
		val manager = if (repliesRequested) parentFragmentManager else null
		super.onDismiss(dialog)
		if (manager != null) {
			repliesRequested = false
			val itemId = UUID.fromString(requireArguments().getString("itemId"))
			val comment = Json.decodeFromString<EpisodeComment>(requireArguments().getString("comment")!!)
			EpisodeCommentsDialogFragment.showReplies(manager, itemId, comment.id)
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putBoolean("revealed", revealed)
	}

	companion object {
		internal fun show(manager: FragmentManager, comment: EpisodeComment) {
			showDetail(manager, comment, null)
		}

		internal fun showFromDetails(manager: FragmentManager, itemId: UUID, comment: EpisodeComment) {
			showDetail(manager, comment, itemId)
		}

		private fun showDetail(manager: FragmentManager, comment: EpisodeComment, itemId: UUID?) {
			if (manager.isStateSaved || manager.findFragmentByTag("episode-comment-detail") != null) return
			EpisodeCommentDetailDialogFragment().apply {
				arguments = Bundle().apply {
					putString("comment", Json.encodeToString(comment))
					if (itemId != null) putString("itemId", itemId.toString())
				}
			}.show(manager, "episode-comment-detail")
		}
	}
}
