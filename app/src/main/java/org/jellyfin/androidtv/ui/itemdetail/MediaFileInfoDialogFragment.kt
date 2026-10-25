package org.jellyfin.androidtv.ui.itemdetail

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemDto

class MediaFileInfoDialogFragment : DialogFragment() {
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val context = requireContext()
		val density = resources.displayMetrics.density
		val padding = (24 * density).toInt()
		val content = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, padding / 2, padding, padding / 2)
		}
		fun text(value: String, heading: Boolean = false) {
			content.addView(TextView(context).apply {
				text = value
				textSize = if (heading) 18f else 16f
				if (heading) setTypeface(typeface, Typeface.BOLD)
				setPadding(0, (if (heading) 16 * density else 4 * density).toInt(), 0, (8 * density).toInt())
			})
		}
		val sources = Json.decodeFromString<List<FileInfoSource>>(requireArguments().getString(ARG_SOURCES)!!)
		for ((index, source) in sources.withIndex()) {
			if (sources.size > 1 || source.title != null) text(source.title ?: getString(R.string.file_info_version, index + 1), true)
			for (field in source.fields) {
				text(getString(field.label), true)
				text(field.value ?: getString(R.string.file_info_unavailable))
			}
		}
		val scroll = ScrollView(context).apply {
			isFocusable = true
			addView(content)
		}
		return AlertDialog.Builder(context)
			.setTitle(R.string.file_info)
			.setView(scroll)
			.setPositiveButton(android.R.string.ok, null)
			.create().also { dialog ->
				dialog.setOnShowListener {
					// Reserve space for the title and dismiss button on short TV screens.
					scroll.layoutParams = scroll.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.55f).toInt() }
					dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.78f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
					scroll.requestFocus()
				}
			}
	}

	companion object {
		private const val ARG_SOURCES = "sources"
		private const val TAG = "media-file-info"

		@JvmStatic
		fun canShow(item: BaseItemDto): Boolean = canShowFileInfo(item)

		@JvmStatic
		fun show(manager: FragmentManager, item: BaseItemDto) {
			if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
			MediaFileInfoDialogFragment().apply {
				arguments = Bundle().apply { putString(ARG_SOURCES, Json.encodeToString(mediaFileInfo(item))) }
			}.showNow(manager, TAG)
		}
	}
}
