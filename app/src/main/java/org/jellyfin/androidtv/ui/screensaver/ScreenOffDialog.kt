package org.jellyfin.androidtv.ui.screensaver

import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.ScreensaverLock
import org.jellyfin.androidtv.util.isMediaSessionKeyEvent

/** An opaque, focused window that leaves the music session and screensaver preferences alone. */
@Composable
fun ScreenOffDialog(onDismissRequest: () -> Unit) {
	val activity = LocalActivity.current
	ScreensaverLock(enabled = true)

	Dialog(
		onDismissRequest = onDismissRequest,
		properties = DialogProperties(
			dismissOnClickOutside = false,
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
		),
	) {
		val view = LocalView.current
		val focusRequester = remember { FocusRequester() }
		val description = stringResource(R.string.screen_off_description)

		DisposableEffect(view) {
			val window = (view.parent as DialogWindowProvider).window
			window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
			window.setWindowAnimations(0)
			// Prevent Android's own dream/idle screen from replacing the black window too.
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
			onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
		}

		Box(
			Modifier
				.fillMaxSize()
				.background(Color.Black)
				.semantics { contentDescription = description }
				.focusRequester(focusRequester)
				.onPreviewKeyEvent { event ->
					val native = event.nativeKeyEvent
					when {
						native.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || native.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
							activity?.dispatchKeyEvent(native) ?: false
						native.isMediaSessionKeyEvent() || native.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
							native.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || native.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE -> false
						else -> {
							// Consume both halves so the wake button cannot also activate Home controls.
							if (event.type == KeyEventType.KeyUp) onDismissRequest()
							true
						}
					}
				}
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
					onClick = onDismissRequest,
				),
		)
		LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
	}
}
