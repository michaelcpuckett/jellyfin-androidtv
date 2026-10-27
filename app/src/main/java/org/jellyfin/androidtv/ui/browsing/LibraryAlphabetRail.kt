package org.jellyfin.androidtv.ui.browsing

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import org.jellyfin.androidtv.R

/** All letters fit the available height; focusing a letter doesn't start a network request. */
class LibraryAlphabetRail(
	context: Context,
	alphabet: String,
	onSelected: (Char?) -> Unit,
	private val onExit: () -> Unit,
) : LinearLayout(context) {
	private val entries = listOf<Char?>(null) + ("#$alphabet").toList()
	private val letters = entries.map { letter ->
		TextView(context).apply {
			id = View.generateViewId()
			text = letter?.toString() ?: context.getString(R.string.library_alphabet_all)
			contentDescription = if (letter == null) context.getString(R.string.lbl_all_items) else context.getString(R.string.library_alphabet_jump, letter.toString())
			gravity = Gravity.CENTER
			includeFontPadding = false
			isFocusable = true
			isClickable = true
			setBackgroundResource(R.drawable.library_alphabet_background)
			setTextColor(ColorStateList(
				arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
				intArrayOf(Color.BLACK, Color.WHITE),
			))
			TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
			setOnClickListener { onSelected(letter) }
			setOnKeyListener { _, keyCode, event ->
				val edgeKey = if (layoutDirection == LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
				when (keyCode) {
					// Keep focus in the rail at its top/bottom/right edge.
					edgeKey -> true
					KeyEvent.KEYCODE_DPAD_UP -> letter == null
					KeyEvent.KEYCODE_DPAD_DOWN -> letter == alphabet.last()
					else -> false
				}
			}
		}
	}

	init {
		id = View.generateViewId()
		orientation = VERTICAL
		letters.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)) }
		letters.forEachIndexed { index, view ->
			view.nextFocusUpId = letters.getOrNull(index - 1)?.id ?: view.id
			view.nextFocusDownId = letters.getOrNull(index + 1)?.id ?: view.id
		}
	}

	fun selectLetter(letter: Char?) {
		letters.forEachIndexed { index, view -> view.isSelected = entries[index] == letter }
	}

	fun focusLetter(): Boolean = (letters.firstOrNull { it.isSelected } ?: letters.first()).requestFocus()

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		val exitKey = if (layoutDirection == LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
		if (event.keyCode == exitKey) {
			if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onExit()
			return true
		}
		return super.dispatchKeyEvent(event)
	}
}
