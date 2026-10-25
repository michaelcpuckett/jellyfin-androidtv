package org.jellyfin.androidtv.ui.navigation

import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView

// Leanback's canScrollHorizontally() is also true for multi-column vertical grids.
internal fun isHorizontalSidebarGrid(gridClass: Class<out BaseGridView>): Boolean =
	HorizontalGridView::class.java.isAssignableFrom(gridClass)

/** Use layout positions: Compose focus searches may return the current card anywhere in a row. */
internal fun isSidebarGridBoundary(
	selectedPosition: Int,
	selectedX: Int,
	firstItemX: Int?,
	attachedItemXs: List<Int>,
	horizontal: Boolean,
	rtl: Boolean,
): Boolean {
	if (selectedPosition < 0) return false
	val firstColumnX = if (horizontal) firstItemX
	else if (rtl) attachedItemXs.maxOrNull() else attachedItemXs.minOrNull()
	return firstColumnX != null && selectedX == firstColumnX
}

/** Adapter order is column-major in horizontal grids and row-major in vertical grids. */
internal fun previousGridPosition(selectedPosition: Int, horizontal: Boolean, spanCount: Int, atBoundary: Boolean): Int? {
	if (atBoundary || selectedPosition < 0) return null
	val step = if (horizontal) spanCount.coerceAtLeast(1) else 1
	return (selectedPosition - step).takeIf { it >= 0 }
}
