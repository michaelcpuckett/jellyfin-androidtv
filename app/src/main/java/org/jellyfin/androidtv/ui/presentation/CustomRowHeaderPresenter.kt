package org.jellyfin.androidtv.ui.presentation

import androidx.leanback.widget.RowHeaderPresenter
import android.graphics.Color
import android.graphics.Typeface
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
		}
	}

	override fun onSelectLevelChanged(holder: ViewHolder) = Unit
}
