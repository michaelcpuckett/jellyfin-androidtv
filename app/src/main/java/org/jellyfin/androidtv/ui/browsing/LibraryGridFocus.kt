package org.jellyfin.androidtv.ui.browsing

import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ViewHolderTask

object LibraryGridFocus {
	@JvmStatic
	fun move(grid: BaseGridView, position: Int) {
		val card = grid.findViewHolderForAdapterPosition(position)?.itemView
		if (card != null) {
			grid.setSelectedPosition(position)
			card.requestFocus()
		} else {
			grid.setSelectedPosition(position, ViewHolderTask { holder ->
				if (grid.hasFocus()) holder.itemView.requestFocus()
			})
		}
	}

	/** Request the card itself: the native grid container can accept focus without focusing a card. */
	@JvmStatic
	fun focus(grid: BaseGridView, count: Int, mayFocus: () -> Boolean): Boolean {
		if (count <= 0 || !mayFocus()) return false
		val position = grid.selectedPosition.coerceIn(0, count - 1)
		val card = grid.findViewHolderForAdapterPosition(position)?.itemView
		if (card != null) {
			grid.setSelectedPosition(position)
			return card.requestFocus()
		}
		// The selected card may need laying out after a filter response. Keep the rail focused
		// until it exists; don't require grid.hasFocus() before transferring focus to it.
		grid.setSelectedPosition(position, ViewHolderTask { holder ->
			if (mayFocus()) holder.itemView.requestFocus()
		})
		return true
	}
}
