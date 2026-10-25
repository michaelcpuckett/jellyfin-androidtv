package org.jellyfin.androidtv.ui.presentation

import androidx.leanback.widget.RowHeaderPresenter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter

class CustomRowHeaderPresenter : RowHeaderPresenter() {
	override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder = super.onCreateViewHolder(parent).also { holder ->
		holder.view.findViewById<TextView>(androidx.leanback.R.id.row_header)?.apply {
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
			setTypeface(Typeface.DEFAULT, Typeface.BOLD)
			setTextColor(Color.WHITE)
			letterSpacing = 0.015f
			val density = resources.displayMetrics.density
			val accent = GradientDrawable().apply {
				setColor(Color.rgb(0, 164, 220))
				cornerRadius = density
				setBounds(0, 0, (3 * density).toInt(), (14 * density).toInt())
			}
			setCompoundDrawablesRelative(accent, null, null, null)
			compoundDrawablePadding = (9 * density).toInt()
		}
	}

	override fun onSelectLevelChanged(holder: ViewHolder) = Unit
}
