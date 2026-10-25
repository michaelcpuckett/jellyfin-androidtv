package org.jellyfin.androidtv.ui.home

import android.view.KeyEvent
import android.view.View

internal enum class HomeFocusTarget {
	Sidebar,
	Toolbar,
}

/** Only the start of a row and the top of the dashboard lead out of the content. */
internal fun homeFocusTarget(
	keyCode: Int,
	rowPosition: Int,
	itemPosition: Int,
	layoutDirection: Int,
): HomeFocusTarget? {
	if (rowPosition < 0) return null
	val startKey = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
	return when {
		keyCode == startKey && itemPosition == 0 -> HomeFocusTarget.Sidebar
		keyCode == KeyEvent.KEYCODE_DPAD_UP && rowPosition == 0 -> HomeFocusTarget.Toolbar
		else -> null
	}
}
