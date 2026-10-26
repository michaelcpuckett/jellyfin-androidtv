package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.browsing.focusNavigationSidebar
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject

class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		val toolbarFocusRequester = remember { FocusRequester() }
		var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
		var selectedItem by remember { mutableStateOf<BaseRowItem?>(null, referentialEqualityPolicy()) }
		var focusArea by rememberSaveable { mutableStateOf("content") }
		val initialFocusArea = remember { focusArea }
		var rowsHaveFocus by remember { mutableStateOf(false) }
		val requestContentFocus = { rowsSupportFragment?.requestContentFocus() ?: false }
		val requestSidebarFocus = { view?.focusNavigationSidebar() ?: false }
		val requestToolbarFocus = { toolbarFocusRequester.requestFocus() }

		LaunchedEffect(rowsSupportFragment) {
			if (rowsSupportFragment != null) {
				if (initialFocusArea == "toolbar") requestToolbarFocus()
				else rowsFocusRequester.requestFocus()
			}
		}
		BackHandler(rowsHaveFocus) { requestSidebarFocus() }

		BoxWithConstraints(Modifier.fillMaxSize()) {
			// Reserve the 95dp toolbar and a complete poster row, caption, header and focus padding.
			val previewMaxHeight = (maxHeight - 387.dp).coerceIn(80.dp, 192.dp)
			Column(Modifier.fillMaxSize().background(Color(0xFF10151E))) {
				Column(
					Modifier
						.zIndex(1f)
						.background(Color(0xFF10151E))
						.focusRequester(toolbarFocusRequester)
						.onFocusChanged { if (it.hasFocus) focusArea = "toolbar" }
						.onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && requestContentFocus() }
						.focusGroup()
				) {
					MainToolbar(MainToolbarActiveButton.Home)
				}
				HomeSelectionDetails(selectedItem, previewMaxHeight)
				AndroidFragment<HomeRowsFragment>(
					modifier = Modifier
						.weight(1f)
						.focusRequester(rowsFocusRequester)
						.onFocusChanged {
							rowsHaveFocus = it.hasFocus
							if (it.hasFocus) focusArea = "content"
						}
						.focusProperties {
							onExit = {
								val keyCode = when (requestedFocusDirection) {
									FocusDirection.Left -> KeyEvent.KEYCODE_DPAD_LEFT
									FocusDirection.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
									FocusDirection.Up -> KeyEvent.KEYCODE_DPAD_UP
									FocusDirection.Down -> KeyEvent.KEYCODE_DPAD_DOWN
									else -> null
								}
								if (keyCode != null) when (rowsSupportFragment?.focusTargetForKey(keyCode)) {
									HomeFocusTarget.Sidebar -> if (!requestSidebarFocus()) cancelFocusChange()
									HomeFocusTarget.Toolbar -> if (!requestToolbarFocus()) cancelFocusChange()
									null -> cancelFocusChange()
								}
							}
						}
						.focusGroup()
						.fillMaxSize(),
					maxLifecycle = Lifecycle.State.RESUMED,
					onUpdate = { fragment ->
						rowsSupportFragment = fragment
						fragment.onFocusSidebar = requestSidebarFocus
						fragment.onFocusToolbar = requestToolbarFocus
						fragment.onItemFocused = { selectedItem = it }
						selectedItem = fragment.selectedItem
					},
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session -> if (session == null) null else serverRepository.getServer(session.serverId) }
			.onEach { server -> notificationRepository.updateServerNotifications(server) }
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}
