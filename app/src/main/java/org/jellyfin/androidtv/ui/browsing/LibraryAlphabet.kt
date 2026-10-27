package org.jellyfin.androidtv.ui.browsing

internal fun isAlphabetEdge(position: Int, size: Int, columns: Int): Boolean =
	position >= 0 && position < size && (position % columns == columns - 1 || position == size - 1)

internal fun verticalLibraryPosition(position: Int, size: Int, columns: Int, up: Boolean): Int? = when {
	position !in 0 until size -> null
	up -> (position - columns).takeIf { it >= 0 }
	position / columns < (size - 1) / columns -> minOf(position + columns, size - 1)
	else -> null
}

internal data class GridRange(val start: Int, val count: Int)

/** A contiguous window in the full server result; offsets always preserve grid columns. */
internal class LibraryGridWindow<T>(val pageSize: Int) {
	var start = 0
		private set
	var total = 0
		private set
	var items: List<T> = emptyList()
		private set

	fun replace(offset: Int, page: List<T>, totalCount: Int) {
		start = offset
		items = page
		total = totalCount
	}

	fun merge(offset: Int, page: List<T>, totalCount: Int) {
		require(offset + page.size == start || offset == start + items.size)
		if (offset < start) {
			items = page + items
			start = offset
		} else items = items + page
		total = totalCount
	}

	fun around(target: Int): GridRange = GridRange(((target / pageSize) - 1).coerceAtLeast(0) * pageSize, pageSize * 3)

	fun next(position: Int): GridRange? = when {
		items.isEmpty() || position !in items.indices -> null
		start > 0 && position < pageSize / 2 -> GridRange((start - pageSize).coerceAtLeast(0), minOf(start, pageSize))
		start + items.size < total && position >= items.size - pageSize / 2 -> GridRange(start + items.size, pageSize)
		else -> null
	}
}
