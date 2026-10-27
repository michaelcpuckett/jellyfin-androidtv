package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun MainToolbar() {
	val focusRequester = remember { FocusRequester() }
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()
	val currentUser by userRepository.currentUser.collectAsState()
	val avatarUrl = remember(currentUser, api) { currentUser?.primaryImage?.getUrl(api) }
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val activity = LocalActivity.current

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		start = {
			ToolbarButtons {
				NowPlayingComposable(
					onFocusableChange = {},
				)
			}
		},
		end = {
			ToolbarButtons {
				IconButton(
					onClick = {
						mediaManager.clearAudioQueue()
						sessionRepository.destroyCurrentSession()
						activity?.startActivity(ActivityDestinations.startup(activity))
						activity?.finishAfterTransition()
					},
					modifier = Modifier.focusRequester(focusRequester),
					contentPadding = PaddingValues(6.dp),
				) {
					ProfilePicture(
						url = avatarUrl,
						contentDescription = stringResource(R.string.nav_profile),
						modifier = Modifier.size(28.dp).clip(CircleShape),
					)
				}
				IconButton(
					onClick = { settingsViewModel.show() },
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				ToolbarClock()
			}
		}
	)
}
