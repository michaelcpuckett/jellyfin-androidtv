package org.jellyfin.androidtv.ui.browsing

/** Width is the measured content viewport, after the persistent sidebar has taken its space. */
object LibraryPosterGrid {
	const val COLUMNS = 6
	const val PADDING = 32
	const val GAP = 16

	@JvmStatic
	fun cardWidth(viewportWidth: Int): Int = ((viewportWidth - 2 * PADDING - (COLUMNS - 1) * GAP) / COLUMNS).coerceAtLeast(1)

	@JvmStatic
	fun cardHeight(viewportWidth: Int, square: Boolean): Int =
		if (square) cardWidth(viewportWidth) else cardWidth(viewportWidth) * 3 / 2

	@JvmStatic
	fun pageSize(visibleItems: Int): Int = ((visibleItems + COLUMNS).coerceAtLeast(30) + COLUMNS - 1) / COLUMNS * COLUMNS
}
