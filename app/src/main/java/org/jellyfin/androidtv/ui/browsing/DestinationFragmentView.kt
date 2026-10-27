package org.jellyfin.androidtv.ui.browsing

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ViewHolderTask
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.navigation.Destination
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.isSidebarGridBoundary
import org.jellyfin.androidtv.ui.navigation.isHorizontalSidebarGrid
import org.jellyfin.androidtv.ui.navigation.previousGridPosition
import org.jellyfin.androidtv.util.createBundle
import timber.log.Timber
import java.util.Stack

private class HistoryEntry(
	val name: Class<out Fragment>,
	val arguments: Bundle = createBundle(),

	var fragment: Fragment? = null,
	var savedState: Fragment.SavedState? = null,
) : Parcelable {
	override fun describeContents(): Int = 0

	override fun writeToParcel(dest: Parcel, flags: Int) {
		dest.writeString(name.name)
		dest.writeBundle(arguments)
		dest.writeParcelable(savedState, 0)
	}

	companion object CREATOR : Parcelable.Creator<HistoryEntry> {
		@Suppress("UNCHECKED_CAST")
		override fun createFromParcel(parcel: Parcel): HistoryEntry = HistoryEntry(
			name = Class.forName(parcel.readString()!!) as Class<out Fragment>,
			arguments = parcel.readBundle(this::class.java.classLoader)!!,
			fragment = null,
			savedState = ParcelCompat.readParcelable(parcel, this::class.java.classLoader, Fragment.SavedState::class.java)!!,
		)

		override fun newArray(size: Int): Array<HistoryEntry?> = arrayOfNulls(size)
	}
}

class DestinationFragmentView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
	var currentDestination: Destination.Fragment? = null
		private set
	var onDestinationChanged: ((Destination.Fragment) -> Unit)? = null
	var onFocusSidebar: (() -> Boolean)? = null
	private var lastContentFocus: View? = null

	fun focusSidebar(): Boolean {
		lastContentFocus = findFocus()
		return onFocusSidebar?.invoke() ?: false
	}

	fun focusContent(): Boolean {
		val previous = lastContentFocus
		if (previous?.isAttachedToWindow == true && previous.isShown && previous.requestFocus()) return true
		return fragmentManager.findFragmentByTag(FRAGMENT_TAG_CONTENT)?.view?.requestFocus() ?: false
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		val startKey = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
		if (onFocusSidebar != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == startKey) {
			val focused = findFocus()
			val grid = focused?.let { generateSequence(it) { view -> view.parent as? View }.filterIsInstance<BaseGridView>().firstOrNull() }
			if (grid != null) {
				val card = grid.findContainingItemView(focused)
				if (card != null) {
					val position = grid.getChildAdapterPosition(card)
					val children = (0 until grid.childCount).map { grid.getChildAt(it) }
					val horizontal = isHorizontalSidebarGrid(grid.javaClass)
					val atBoundary = isSidebarGridBoundary(
						selectedPosition = position,
						selectedX = card.left,
						firstItemX = grid.findViewHolderForAdapterPosition(0)?.itemView?.left,
						attachedItemXs = children.map { it.left },
						horizontal = horizontal,
						rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL,
					)
					if (atBoundary && focusSidebar()) return true
					val previous = previousGridPosition(position, horizontal, children.map { it.top }.distinct().size, atBoundary)
					if (previous != null && previous < (grid.adapter?.itemCount ?: 0)) {
						// Request the actual previous adapter item, including one scrolled off screen.
						// This bypasses Compose's unreliable spatial search between native poster views.
						if (grid.findViewHolderForAdapterPosition(previous)?.itemView?.requestFocus() == true) return true
						grid.setSelectedPositionSmooth(previous, ViewHolderTask { holder ->
							if (grid.hasFocus()) holder.itemView.requestFocus()
						})
						return true
					}
				}
			}
		}
		return super.dispatchKeyEvent(event)
	}

	private companion object {
		private const val FRAGMENT_TAG_CONTENT = "content"
		private const val BUNDLE_SUPER = "super"
		private const val BUNDLE_HISTORY = "history"
	}

	private val fragmentManager by lazy {
		FragmentManager.findFragmentManager(this)
	}

	private val container by lazy {
		FragmentContainerView(context).also { view ->
			view.id = R.id.container
			addView(view)
		}
	}

	private val history = Stack<HistoryEntry>()

	fun navigate(action: NavigationAction.NavigateFragment) {
		val entry = HistoryEntry(action.destination.fragment.java, action.destination.arguments)

		// Create the base transaction so we can mutate everything at once
		val transaction = fragmentManager.beginTransaction()

		if (action.clear) {
			// Clear all current fragments from the history before adding the new entry
			history.mapNotNull { it.fragment }.forEach { transaction.remove(it) }
			history.clear()
			history.push(entry)
		} else if (action.replace && action.addToBackStack && history.isNotEmpty()) {
			// Remove the top-most entry before replacing it with the next
			val currentFragment = history[history.size - 1].fragment
			if (currentFragment != null) transaction.remove(currentFragment)
			history[history.size - 1] = entry
		} else {
			// Add to the end of the history
			saveCurrentFragmentState()
			history.push(entry)
		}

		activateHistoryEntry(entry, transaction)
	}

	fun goBack(): Boolean {
		// Require at least 2 items (current & previous) to go back
		if (history.size < 2) return false

		// Create the base transaction so we can mutate everything at once
		val transaction = fragmentManager.beginTransaction()

		// Remove current entry
		val currentEntry = history.pop()

		// Make sure to remove the associated fragment
		val currentFragment = currentEntry.fragment
		if (currentFragment != null) transaction.remove(currentFragment)

		// Read & set previous entry
		val entry = history.last()
		activateHistoryEntry(entry, transaction)

		return true
	}

	private fun saveCurrentFragmentState() {
		if (history.isEmpty()) return

		// Update the top-most history entry with state from current fragment
		val fragment = requireNotNull(fragmentManager.findFragmentByTag(FRAGMENT_TAG_CONTENT))
		history[history.size - 1].savedState = fragmentManager.saveFragmentInstanceState(fragment)
	}

	@SuppressLint("CommitTransaction")
	private fun activateHistoryEntry(
		entry: HistoryEntry,
		transaction: FragmentTransaction,
	) {
		lastContentFocus = null
		currentDestination = Destination.Fragment(entry.name.kotlin, entry.arguments)
		onDestinationChanged?.invoke(currentDestination!!)
		var fragment = entry.fragment

		// Create if there is no existing fragment
		if (fragment == null) {
			fragment = fragmentManager.fragmentFactory.instantiate(context.classLoader, entry.name.name).apply {
				setInitialSavedState(entry.savedState)
			}
			entry.fragment = fragment
		}

		// Update arguments
		if (!fragmentManager.isStateSaved) {
			fragment.arguments = entry.arguments
		}

		transaction.apply {
			// Set options
			setReorderingAllowed(true)
			// Switch immediately: the browsing rail changes the content width on detail pages.
			// Animating the old and new layouts together makes that resize look like a slide.
			setCustomAnimations(0, 0, 0, 0)

			// Detach current fragment
			fragmentManager.findFragmentByTag(FRAGMENT_TAG_CONTENT)?.let(::detach)

			// Attach or add next fragment
			if (fragment.isDetached) attach(fragment)
			else replace(container.id, fragment, FRAGMENT_TAG_CONTENT)
		}

		if (fragmentManager.isDestroyed) {
			Timber.w("FragmentManager is already destroyed")
		} else if (fragmentManager.isStateSaved) {
			transaction.commitAllowingStateLoss()
		} else {
			transaction.commit()
		}
	}

	override fun onSaveInstanceState(): Parcelable {
		// Always retrieve current state before writing
		saveCurrentFragmentState()

		// Save state
		return createBundle {
			putParcelable(BUNDLE_SUPER, super.onSaveInstanceState())
			putParcelableArrayList(BUNDLE_HISTORY, ArrayList(history))
		}
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		// Ignore if not a bundle
		if (state !is Bundle) return super.onRestoreInstanceState(state)

		// Call parent
		@Suppress("DEPRECATION")
		val parent = state.getParcelable<Parcelable>(BUNDLE_SUPER)
		super.onRestoreInstanceState(parent)

		// Restore history
		val savedHistory = BundleCompat.getParcelableArrayList(state, BUNDLE_HISTORY, HistoryEntry::class.java)
		if (savedHistory != null) {
			history.clear()
			history.addAll(savedHistory)
			if (history.isNotEmpty()) activateHistoryEntry(history.last(), fragmentManager.beginTransaction())
		}
	}
}

/** Bridge the nested home rows' native focus boundary to the shared Compose sidebar. */
internal fun View.focusNavigationSidebar(): Boolean = generateSequence(this) { it.parent as? View }
	.filterIsInstance<DestinationFragmentView>()
	.firstOrNull()?.focusSidebar() ?: false
