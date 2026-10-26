package org.jellyfin.androidtv.ui.itemdetail

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import org.koin.android.ext.android.inject

internal fun regularEpisodeCast(seriesPeople: List<BaseItemPerson>, episodePeople: List<BaseItemPerson>): List<BaseItemPerson> {
	val guestIds = episodePeople.filter { it.type == PersonKind.GUEST_STAR }.map { it.id }.toSet()
	return (seriesPeople + episodePeople).filter { it.type == PersonKind.ACTOR && it.id !in guestIds }.distinctBy { it.id }
}

// Row IDs keep asynchronously loaded Cast and Comments in their intended positions.
internal fun episodeSectionIndex(headerIds: List<Long?>, sectionId: Long): Int =
	headerIds.indexOfFirst { it != null && it in 20L..70L && it > sectionId }.takeIf { it >= 0 } ?: headerIds.size

private fun insertSection(rows: MutableObjectAdapter<Row>, row: ListRow, episode: Boolean) {
	val index = if (episode) episodeSectionIndex(rows.map { (it as? ListRow)?.headerItem?.id }, row.headerItem.id) else rows.size()
	rows.add(index, row)
}

fun FullDetailsFragment.addEpisodeCast(rows: MutableObjectAdapter<Row>, item: BaseItemDto) {
	val api by inject<ApiClient>()
	viewLifecycleOwner.lifecycleScope.launch {
		val seriesPeople = try {
			item.seriesId?.let { id -> withContext(Dispatchers.IO) { api.userLibraryApi.getItem(id).content.people } }.orEmpty()
		} catch (error: CancellationException) {
			throw error
		} catch (_: Exception) {
			emptyList()
		}
		if (!isCurrentDetailsAdapter(rows, item.id)) return@launch
		val people = regularEpisodeCast(seriesPeople, item.people.orEmpty())
		if (people.isEmpty()) return@launch
		val cast = ItemRowAdapter(people, requireContext(), CardPresenter(true, 130), rows)
		val row = ListRow(HeaderItem(30, getString(R.string.episode_details_cast)), cast)
		insertSection(rows, row, true)
		cast.setRow(row)
		cast.Retrieve()
	}
}

class DiscussionCard(val title: String, val body: String, private val action: () -> Unit) {
	fun open() = action()
}

private class DiscussionCardPresenter : Presenter() {
	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val density = parent.resources.displayMetrics.density
		fun background(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = 10 * density }
		val card = TextView(parent.context).apply {
			layoutParams = ViewGroup.LayoutParams((340 * density).toInt(), (190 * density).toInt())
			setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
			textSize = 17f
			setTextColor(Color.WHITE)
			maxLines = 7
			ellipsize = TextUtils.TruncateAt.END
			isFocusable = true
			isFocusableInTouchMode = true
			background = StateListDrawable().apply {
				addState(intArrayOf(android.R.attr.state_focused), background(Color.rgb(72, 62, 112)))
				addState(intArrayOf(), background(Color.rgb(38, 38, 44)))
			}
		}
		return ViewHolder(card)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val card = item as? DiscussionCard ?: return
		(viewHolder.view as TextView).text = card.title + "\n\n" + card.body
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}

fun FullDetailsFragment.addCommentsSection(rows: MutableObjectAdapter<Row>, item: BaseItemDto) {
	val api by inject<ApiClient>()
	val client = EpisodeCommentsApi(api)
	viewLifecycleOwner.lifecycleScope.launch {
		if (!client.isAvailable(item.type) || !isCurrentDetailsAdapter(rows, item.id)) return@launch
		val cards = ArrayObjectAdapter(DiscussionCardPresenter())
		val row = ListRow(HeaderItem(50, getString(R.string.episode_comments_title)), cards)
		fun open(comment: EpisodeComment? = null) {
			viewLifecycleOwner.lifecycleScope.launch {
				if (!client.isAvailable(item.type)) {
					if (isCurrentDetailsAdapter(rows, item.id)) {
						rows.remove(row)
						Toast.makeText(requireContext(), R.string.episode_comments_unavailable, Toast.LENGTH_SHORT).show()
					}
				} else if (isCurrentDetailsAdapter(rows, item.id)) {
					if (comment == null) EpisodeCommentsDialogFragment.show(parentFragmentManager, item)
					else EpisodeCommentDetailDialogFragment.showFromDetails(parentFragmentManager, item.id, comment)
				}
			}
		}
		cards.add(DiscussionCard(getString(R.string.episode_comments_loading), getString(R.string.episode_comments_attribution)) { open() })
		insertSection(rows, row, item.type == BaseItemKind.EPISODE)
		try {
			val page = client.getPreview(item.id)
			if (!isCurrentDetailsAdapter(rows, item.id)) return@launch
			if (page == null) {
				rows.remove(row)
				return@launch
			}
			val previews = page.comments.take(5).map { comment ->
				DiscussionCard(getString(R.string.episode_comments_card, comment.author, comment.likes, comment.replyCount),
					if (comment.containsSpoilers) getString(R.string.episode_comments_spoiler) else comment.text.take(300)) { open(comment) }
			}
			val browse = DiscussionCard(getString(R.string.episode_comments_browse),
				if (previews.isEmpty()) getString(R.string.episode_comments_empty) else getString(R.string.episode_comments_browse_description, page.totalItems)) { open() }
			cards.setItems(previews + browse, null)
		} catch (error: CancellationException) {
			throw error
		} catch (_: Exception) {
			if (!isCurrentDetailsAdapter(rows, item.id)) return@launch
			if (!client.isAvailable(item.type)) rows.remove(row)
			else cards.setItems(listOf(DiscussionCard(getString(R.string.episode_comments_retry), getString(R.string.episode_comments_network_error)) { open() }), null)
		}
	}
}
