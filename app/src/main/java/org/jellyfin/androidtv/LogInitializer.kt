package org.jellyfin.androidtv

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import timber.log.Timber

class LogInitializer : Initializer<Unit> {
	override fun create(context: Context) {
		// Enable improved logging for leaking resources
		// https://wh0.github.io/2020/08/12/closeguard.html
		if (BuildConfig.DEBUG) {
			try {
				Class.forName("dalvik.system.CloseGuard")
					.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
					.invoke(null, true)
			} catch (e: ReflectiveOperationException) {
				@Suppress("TooGenericExceptionThrown")
				throw RuntimeException(e)
			}
		}

		// Initialize the logging library
		if (BuildConfig.DEBUG) {
			Timber.plant(Timber.DebugTree())
		} else {
			Timber.plant(object : Timber.Tree() {
				override fun isLoggable(tag: String?, priority: Int) = priority >= Log.WARN
				override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
					Log.println(priority, tag ?: "Jellyfin", message)
				}
			})
		}
	}

	override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}
