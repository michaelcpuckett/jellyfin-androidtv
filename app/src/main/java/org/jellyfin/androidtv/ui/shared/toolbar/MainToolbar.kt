package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.screensaver.ScreenOffDialog
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun MainToolbar() {
	val focusRequester = remember { FocusRequester() }
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	var screenOff by rememberSaveable { mutableStateOf(false) }

	if (screenOff) ScreenOffDialog(onDismissRequest = { screenOff = false })

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
					onClick = { screenOff = true },
					modifier = Modifier.focusRequester(focusRequester),
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_tv_off),
						contentDescription = stringResource(R.string.screen_off),
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
