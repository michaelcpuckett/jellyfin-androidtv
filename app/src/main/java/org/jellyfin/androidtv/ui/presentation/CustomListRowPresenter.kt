package org.jellyfin.androidtv.ui.presentation

import android.view.View
import androidx.core.view.isVisible
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

open class CustomListRowPresenter @JvmOverloads constructor(
	private val topPadding: Int? = null
) : ListRowPresenter() {
	init {
		headerPresenter = CustomRowHeaderPresenter()
		selectEffectEnabled = false
	}

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)
		styleRow(holder)

		val view = holder.view?.parent as? View ?: return
		if (topPadding != null) view.setPadding(view.paddingLeft, topPadding, view.paddingRight, view.paddingBottom)

		// Hide header view when the item doesn't have one
		holder.headerViewHolder.view.isVisible = !(item is ListRow && item.headerItem == null)
	}

	override fun onRowViewSelected(holder: RowPresenter.ViewHolder, selected: Boolean) {
		super.onRowViewSelected(holder, selected)
		styleRow(holder)
	}

	override fun onRowViewExpanded(holder: RowPresenter.ViewHolder, expanded: Boolean) {
		super.onRowViewExpanded(holder, expanded)
		styleRow(holder)
	}

	private fun styleRow(holder: RowPresenter.ViewHolder) {
		val grid = (holder as? ListRowPresenter.ViewHolder)?.gridView ?: return
		val density = grid.resources.displayMetrics.density
		grid.setHorizontalSpacing((20 * density).toInt())
		grid.setPadding(grid.paddingLeft, (16 * density).toInt(), grid.paddingRight, (20 * density).toInt())
		grid.clipChildren = false
		grid.clipToPadding = false
	}
}
