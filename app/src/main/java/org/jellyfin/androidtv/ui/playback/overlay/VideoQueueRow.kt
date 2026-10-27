package org.jellyfin.androidtv.ui.playback.overlay

import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter

/** Reuses the playback fragment's lower rows, with the queue index kept even for duplicate items. */
class VideoQueueRow(
	private val fragment: LeanbackOverlayFragment,
	private val controller: PlaybackController,
) {
	private val items = ArrayObjectAdapter(CardPresenter(true, ImageType.THUMB, 90, true))
	private var row: ListRow? = null
	var isBrowsing = false
		private set
	private val presenter = object : CustomListRowPresenter() {
		override fun onRowViewSelected(holder: RowPresenter.ViewHolder, selected: Boolean) {
			super.onRowViewSelected(holder, selected)
			if (selected && !isBrowsing) (holder as ListRowPresenter.ViewHolder).gridView.setSelectedPosition(controller.queueIndex)
			isBrowsing = selected
			if (fragment.isResumed) {
				fragment.setControlsOverlayAutoHideEnabled(!selected)
				fragment.setFading(!selected)
			}
		}
	}

	init {
		fragment.setOnItemViewClickedListener { _, item, _, _ ->
			if (item is VideoQueueItem) {
				controller.playQueueItem(item.index)
				fragment.setSelectedPosition(0, false)
				fragment.setFading(true)
				fragment.hideControlsOverlay(false)
			}
		}
	}

	fun update() {
		// The host adapter does not exist until PlaybackSupportFragment.onCreateView.
		val rows = fragment.adapter as? ArrayObjectAdapter ?: return
		val selector = rows.presenterSelector as? ClassPresenterSelector ?: return
		selector.addClassPresenter(ListRow::class.java, presenter)
		val queue = controller.queueItems
		if (controller.isLiveTv || queue.size < 2) {
			row?.let(rows::remove)
			row = null
			return
		}
		items.setItems(queue.mapIndexed { index, item -> VideoQueueItem(item, index, index == controller.queueIndex) }, null)
		val header = "${fragment.getString(R.string.current_queue)} (${controller.queueIndex + 1}/${queue.size})"
		val newRow = ListRow(HeaderItem(header), items)
		val oldIndex = row?.let(rows::indexOf) ?: -1
		row = newRow
		if (oldIndex >= 0) rows.replace(oldIndex, newRow) else rows.add(newRow)
	}
}

