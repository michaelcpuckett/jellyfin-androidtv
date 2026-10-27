package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeSidebar(
	libraries: List<BaseItemDto>,
	loadFailed: Boolean,
	onRetry: () -> Unit,
	onLibraryClick: (BaseItemDto) -> Unit,
	onHomeClick: () -> Unit,
	onSearchClick: () -> Unit,
	searchSelected: Boolean,
	selectedLibraryId: UUID?,
	expanded: Boolean,
	focusRequest: Int,
	onFocusChanged: (Boolean) -> Unit,
	onContentFocus: () -> Boolean,
	onToolbarFocus: () -> Boolean,
	modifier: Modifier = Modifier,
) {
	val contentKey = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Key.DirectionLeft else Key.DirectionRight
	val activeRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()
	val initialFocusRequest = remember { focusRequest }
	val activeIndex = sidebarActiveIndex(libraries.map { it.id }, selectedLibraryId, searchSelected)
	LaunchedEffect(focusRequest) {
		if (focusRequest != initialFocusRequest) {
			withFrameNanos { }
			val layout = listState.layoutInfo
			val activeItem = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex }
			if (activeItem == null || activeItem.offset < layout.viewportStartOffset ||
				activeItem.offset + activeItem.size > layout.viewportEndOffset
			) {
				listState.scrollToItem(activeIndex)
				withFrameNanos { }
			}
			activeRequester.requestFocus()
		}
	}
	// Keep visible entries stationary instead of applying Android TV's focus pivot.
	CompositionLocalProvider(LocalBringIntoViewSpec provides SidebarBringIntoViewSpec) {
		LazyColumn(
			state = listState,
			modifier = modifier
				.width(if (expanded) 216.dp else 80.dp)
				.fillMaxHeight()
				.background(Color(0xFF081E2C))
				.onPreviewKeyEvent { event ->
					event.type == KeyEventType.KeyDown && event.key == contentKey && onContentFocus()
				}
				.onFocusChanged { onFocusChanged(it.hasFocus) }
				.focusGroup()
				.focusProperties {
					onExit = {
						when (requestedFocusDirection) {
							FocusDirection.Up -> if (!onToolbarFocus()) cancelFocusChange()
							FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
							else -> Unit
						}
					}
				},
			contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			item(key = "home") {
				HomeSidebarButton(
					label = stringResource(R.string.lbl_home),
					icon = R.drawable.ic_house,
					onClick = onHomeClick,
					selected = selectedLibraryId == null && !searchSelected,
					expanded = expanded,
					modifier = if (activeIndex == 0) Modifier.focusRequester(activeRequester) else Modifier,
				)
			}
			item(key = "search") {
				HomeSidebarButton(
					label = stringResource(R.string.lbl_search),
					icon = R.drawable.ic_search,
					onClick = onSearchClick,
					selected = searchSelected,
					expanded = expanded,
					modifier = if (activeIndex == 1) Modifier.focusRequester(activeRequester) else Modifier,
				)
			}
			item(key = "libraries-heading") {
				if (expanded) Text(
					text = stringResource(R.string.lbl_my_media),
					color = Color.White,
					modifier = Modifier.height(44.dp).padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
				) else Spacer(Modifier.height(44.dp))
			}
			items(libraries, key = { it.id }) { library ->
				HomeSidebarButton(
					label = library.name.orEmpty(),
					icon = when (library.collectionType) {
						CollectionType.MOVIES -> R.drawable.ic_movie
						CollectionType.TVSHOWS, CollectionType.LIVETV, CollectionType.MUSICVIDEOS -> R.drawable.ic_tv
						CollectionType.MUSIC -> R.drawable.ic_select_audio
						CollectionType.PLAYLISTS -> R.drawable.ic_playlists
						else -> R.drawable.ic_folder
					},
					onClick = { onLibraryClick(library) },
					selected = library.id == selectedLibraryId && !searchSelected,
					expanded = expanded,
					modifier = if (library.id == selectedLibraryId && !searchSelected) Modifier.focusRequester(activeRequester) else Modifier,
				)
			}
			if (loadFailed) {
				item(key = "libraries-error") {
					if (expanded) Text(stringResource(R.string.home_sidebar_load_error), modifier = Modifier.padding(12.dp))
				}
				item(key = "libraries-retry") {
					HomeSidebarButton(stringResource(R.string.home_sidebar_retry), R.drawable.ic_folder, onRetry, expanded = expanded)
				}
			}
		}
	}
}

@Composable
private fun HomeSidebarButton(
	label: String,
	icon: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	selected: Boolean = false,
	expanded: Boolean,
) {
	val accent = colorResource(R.color.jellyfin_blue)
	Button(
		onClick = onClick,
		modifier = modifier.fillMaxWidth(),
		shape = JellyfinTheme.shapes.medium,
		colors = ButtonDefaults.colors(
			containerColor = if (selected) accent.copy(alpha = 0.28f) else Color.Transparent,
			contentColor = if (selected) accent else Color(0xFFD1E6F0),
			focusedContainerColor = accent,
			focusedContentColor = Color(0xFF03131D),
		),
		contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
	) {
		Icon(ImageVector.vectorResource(icon), contentDescription = if (expanded) null else label, modifier = Modifier.size(20.dp))
		if (expanded) {
			Spacer(Modifier.width(10.dp))
			Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
		}
	}
}

// Home and Search occupy 0/1; the heading retains index 2 even while collapsed.
internal fun sidebarActiveIndex(libraries: List<UUID>, selectedLibraryId: UUID?, searchSelected: Boolean = false): Int =
	if (searchSelected) 1 else libraries.indexOf(selectedLibraryId).takeIf { it >= 0 }?.plus(3) ?: 0
