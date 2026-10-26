package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val settingsViewModel by activityViewModel<SettingsViewModel>()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, userPreferences) }

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true
	private var homeRowsLoaded = false
	private var discoveryEnabled = false
	private var discoveryJob: Job? = null
	private val discoveryRows = mutableListOf<Row>()
	private var recentlyWatchedRow: ListRow? = null
	internal var onFocusSidebar: (() -> Boolean)? = null
	internal var onFocusToolbar: (() -> Boolean)? = null
	internal var onItemFocused: ((BaseRowItem?) -> Unit)? = null
	internal val selectedItem get() = currentItem

	internal fun focusTargetForKey(keyCode: Int): HomeFocusTarget? {
		val grid = verticalGridView ?: return null
		val rowHolder = getRowViewHolder(selectedPosition) as? ListRowPresenter.ViewHolder
		return homeFocusTarget(keyCode, selectedPosition, rowHolder?.gridView?.selectedPosition ?: -1, grid.layoutDirection)
	}

	/** Re-enter the selected card, rather than resetting the dashboard to its first row. */
	internal fun requestContentFocus(): Boolean {
		val grid = verticalGridView ?: return false
		val rowHolder = getRowViewHolder(selectedPosition) as? ListRowPresenter.ViewHolder
		val rowGrid = rowHolder?.gridView
		val selectedCard = rowGrid?.findViewHolderForAdapterPosition(rowGrid.selectedPosition)?.itemView
		if (selectedCard?.requestFocus() == true) return true
		return grid.requestFocus()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		verticalGridView?.setOnKeyInterceptListener { event ->
			if (event.action != KeyEvent.ACTION_DOWN) false
			else when (focusTargetForKey(event.keyCode)) {
				HomeFocusTarget.Sidebar -> onFocusSidebar?.invoke() ?: false
				HomeFocusTarget.Toolbar -> onFocusToolbar?.invoke() ?: false
				null -> false
			}
		}
	}

	override fun onDestroyView() {
		verticalGridView?.setOnKeyInterceptListener(null)
		onFocusSidebar = null
		onFocusToolbar = null
		onItemFocused = null
		super.onDestroyView()
	}

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter())

		lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// Start out with default sections
			val homesections = homeDashboardSections(userSettingPreferences.activeHomesections)

			// Make sure the rows are empty
			val rows = mutableListOf<HomeFragmentRow>()

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Actually add the sections
			for (section in homesections) when (section) {
				HomeSectionType.LATEST_MEDIA -> rows.add(helper.loadRecentlyAdded(userViewsRepository.views.first()))
				HomeSectionType.LIBRARY_TILES_SMALL -> rows.add(HomeFragmentViewsRow(small = false))
				HomeSectionType.LIBRARY_BUTTONS -> rows.add(HomeFragmentViewsRow(small = true))
				HomeSectionType.RESUME -> rows.add(helper.loadResumeVideo())
				HomeSectionType.RESUME_AUDIO -> rows.add(helper.loadResumeAudio())
				HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
				HomeSectionType.ACTIVE_RECORDINGS -> rows.add(helper.loadLatestLiveTvRecordings())
				HomeSectionType.NEXT_UP -> rows.add(helper.loadNextUp())
				HomeSectionType.LIVE_TV -> if (currentUser.policy?.enableLiveTvAccess == true) {
					rows.add(HomeFragmentLiveTVRow(requireActivity(), userRepository))
					rows.add(helper.loadOnNow())
				}

				HomeSectionType.NONE -> Unit
			}

			// Add sections to layout
			withContext(Dispatchers.Main) {
				val cardPresenter = CardPresenter(true, ImageType.POSTER, 180, false, homeLayout = true)

				// Add rows in order
				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				updateRecentlyWatchedRow()

				// Wire up Live TV sibling rows so the On Now row removes the buttons row when empty
				@Suppress("UNCHECKED_CAST")
				val rowsAdapter = adapter as MutableObjectAdapter<Row>
				for (i in 0 until rowsAdapter.size()) {
					val listRow = rowsAdapter.get(i) as? ListRow ?: continue
					val itemAdapter = listRow.adapter as? ItemRowAdapter ?: continue
					if (itemAdapter.queryType == QueryType.LiveTvProgram && i > 0) {
						val previousRow = rowsAdapter.get(i - 1)
						if (previousRow != null) itemAdapter.setSiblingRow(previousRow)
					}
				}
				homeRowsLoaded = true
				updateDiscoveryRows()
			}
		}

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
					else -> Unit
				}
			}.launchIn(lifecycleScope)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				settingsViewModel.visible
					.onEach { visible -> if (!visible && homeRowsLoaded) updateRecentlyWatchedRow() }
					.launchIn(this)

				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (verticalGridView?.hasFocus() != true) return false
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()
		// Home shows metadata on a plain background, without loading full-screen backdrops.
		backgroundService.clearBackgrounds()
		backgroundService.disable()
		updateDiscoveryRows()

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch(Dispatchers.IO) {
			if (delayed) delay(1.5.seconds)
			withContext(Dispatchers.Main) { updateRecentlyWatchedRow() }

			repeat(adapter.size()) { i ->
				val rowAdapter = (adapter[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun updateRecentlyWatchedRow() {
		val rowsAdapter = adapter as MutableObjectAdapter<Row>
		if (!userPreferences[UserPreferences.homeRecentlyWatchedEnabled]) {
			recentlyWatchedRow?.let(rowsAdapter::remove)
			return
		}
		val row = recentlyWatchedRow
		if (row == null) {
			helper.loadRecentlyWatched().addToRowsAdapter(requireContext(),
				CardPresenter(true, ImageType.POSTER, 180, false, homeLayout = true), rowsAdapter)
			recentlyWatchedRow = rowsAdapter[rowsAdapter.size() - 1] as? ListRow
		} else if (!rowsAdapter.contains(row)) {
			// Empty history removes its row. Retain the adapter so the first completed video can restore it.
			rowsAdapter.add(row)
			(row.adapter as ItemRowAdapter).Retrieve()
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun updateDiscoveryRows() {
		if (!homeRowsLoaded) return
		val enabled = userPreferences[UserPreferences.homeDiscoveryEnabled]
		if (enabled == discoveryEnabled) return
		discoveryEnabled = enabled
		discoveryJob?.cancel()
		val rowsAdapter = adapter as MutableObjectAdapter<Row>
		discoveryRows.forEach(rowsAdapter::remove)
		discoveryRows.clear()
		if (!enabled) return
		discoveryJob = lifecycleScope.launch {
			try {
				val context = requireContext()
				val definitions = withContext(Dispatchers.IO) {
					loadHomeDiscoveryRows(context, api, userViewsRepository.views.first(),
						userRepository.currentUser.value?.configuration?.latestItemsExcludes.orEmpty())
				}
				val presenter = CardPresenter(true, ImageType.POSTER, 180, false, homeLayout = true)
				definitions.forEach { definition ->
					HomeFragmentBrowseRowDefRow(definition).addToRowsAdapter(context, presenter, rowsAdapter)
					rowsAdapter[rowsAdapter.size() - 1]?.let(discoveryRows::add)
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (error: Exception) {
				discoveryEnabled = false // Allow a later resume to retry without breaking normal rows.
				Timber.w(error, "Could not load optional Home discovery rows")
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onDestroy() {
		super.onDestroy()

		mediaManager.removeAudioEventListener(this)
	}

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item is GridButton) {
				when (item.id) {
					LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvGuide)
					LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
					LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
					LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
				}
			}

			if (item !is BaseRowItem) return
			if (row !is ListRow) return
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item !is BaseRowItem) {
				currentItem = null
				onItemFocused?.invoke(null)
			} else {
				currentItem = item
				onItemFocused?.invoke(item)
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

			}
		}
	}
}
