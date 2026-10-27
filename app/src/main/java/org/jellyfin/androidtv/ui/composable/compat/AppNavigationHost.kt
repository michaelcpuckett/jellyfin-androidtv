package org.jellyfin.androidtv.ui.composable.compat

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.browsing.DestinationFragmentView
import org.jellyfin.androidtv.ui.home.HomeFragment
import org.jellyfin.androidtv.ui.home.HomeSidebar
import org.jellyfin.androidtv.ui.home.showsBrowsingSidebar
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.Destination
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.search.SearchFragment
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
fun AppNavigationHost(
	modifier: Modifier = Modifier,
	navigationRepository: NavigationRepository = koinInject(),
) {
	val factory = remember { AppNavigationHostViewFactory() }
	val userViewsRepository = koinInject<UserViewsRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	var sidebarExpanded by remember { mutableStateOf(false) }
	var sidebarFocusRequest by remember { mutableStateOf(0) }
	var destination by remember { mutableStateOf<Destination.Fragment?>(null) }
	var libraries by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var loadFailed by remember { mutableStateOf(false) }
	var retry by remember { mutableStateOf(0) }
	var selectedLibrary by rememberSaveable { mutableStateOf<String?>(null) }
	var contentHasFocus by remember { mutableStateOf(false) }
	val sidebarVisible = destination?.fragment?.let(::showsBrowsingSidebar) == true
	val sidebarDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) FocusDirection.Right else FocusDirection.Left
	val requestSidebarFocus = {
		sidebarExpanded = true
		sidebarFocusRequest++
		true
	}
	val requestContentFocus = { factory.view.focusContent() }

	LaunchedEffect(sidebarVisible) {
		if (!sidebarVisible) sidebarExpanded = false
	}
	LaunchedEffect(retry) {
		try {
			libraries = userViewsRepository.views.first().toList()
			loadFailed = false
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: Exception) {
			Timber.w(exception, "Unable to load navigation sidebar libraries")
			loadFailed = true
		}
	}
	LaunchedEffect(destination, libraries) {
		if (destination?.fragment == HomeFragment::class) selectedLibrary = null
		else {
			val folder = destination?.arguments?.getString(Extras.Folder)?.let { Json.decodeFromString<BaseItemDto>(it) }
			if (folder != null && libraries.any { it.id == folder.id }) selectedLibrary = folder.id.toString()
		}
	}

	val canGoBack by remember {
		navigationRepository.currentAction.map { navigationRepository.canGoBack }.distinctUntilChanged()
	}.collectAsState(navigationRepository.canGoBack)
	BackHandler(canGoBack) { navigationRepository.goBack() }
	// Within a library, normal history still applies after Back has returned focus to the rail.
	BackHandler(sidebarVisible && !contentHasFocus && !canGoBack && selectedLibrary != null) {
		navigationRepository.reset(Destinations.home, clearHistory = true)
	}
	BackHandler(sidebarVisible && contentHasFocus) { factory.view.focusSidebar() }

	Box(modifier.fillMaxSize()) {
		Row(Modifier.fillMaxSize()) {
			// Keep poster geometry stable while the expanded menu overlays the content.
			if (sidebarVisible) Spacer(Modifier.width(80.dp))
			AndroidView(
				factory = factory,
				update = { view ->
					view.onDestinationChanged = { destination = it }
					view.onFocusSidebar = if (sidebarVisible) requestSidebarFocus else null
					view.currentDestination?.let { destination = it }
				},
				modifier = Modifier
					.weight(1f)
					.fillMaxSize()
					.onFocusChanged { contentHasFocus = it.hasFocus }
					.focusProperties {
						onExit = {
							// Native grids decide their boundary before dispatch. Never turn a failed
							// Compose focus search on a middle poster into a jump to the sidebar.
							if (sidebarVisible && requestedFocusDirection == sidebarDirection) cancelFocusChange()
						}
					}
					.focusGroup(),
			)
		}
		if (sidebarVisible) {
			HomeSidebar(
				libraries = libraries,
				loadFailed = loadFailed,
				onRetry = { retry++ },
				onLibraryClick = {
					selectedLibrary = it.id.toString()
					val target = if (it.collectionType == CollectionType.LIVETV) itemLauncher.getUserViewDestination(it)
					else Destinations.libraryPosterGrid(it)
					navigationRepository.reset(target, clearHistory = true)
				},
				onHomeClick = {
					selectedLibrary = null
					if (destination?.fragment == HomeFragment::class) requestContentFocus()
					else navigationRepository.reset(Destinations.home, clearHistory = true)
				},
				onSearchClick = {
					if (destination?.fragment == SearchFragment::class) requestContentFocus()
					else navigationRepository.navigate(Destinations.search())
				},
				searchSelected = destination?.fragment == SearchFragment::class,
				selectedLibraryId = selectedLibrary?.let(UUID::fromString),
				expanded = sidebarExpanded,
				focusRequest = sidebarFocusRequest,
				onFocusChanged = { sidebarExpanded = it },
				onContentFocus = requestContentFocus,
				onToolbarFocus = { false },
				modifier = Modifier.align(Alignment.CenterStart).zIndex(1f),
			)
		}
	}

	LaunchedEffect(Unit) {
		navigationRepository.currentAction.collect { action ->
			when (action) {
				is NavigationAction.NavigateFragment -> factory.view.navigate(action)
				NavigationAction.GoBack -> factory.view.goBack()
				NavigationAction.Nothing -> Unit
			}
		}
	}
}

private class AppNavigationHostViewFactory : (Context) -> DestinationFragmentView {
	private var _view: DestinationFragmentView? = null
	val view get() = requireNotNull(_view)
	override fun invoke(context: Context): DestinationFragmentView = DestinationFragmentView(context).also { _view = it }
}
