package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.seriesThumbImage
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/** Prefer a series backdrop to a screenshot; never stretch portrait posters across the hero. */
internal fun homePreviewBackdrop(item: BaseItemDto?): JellyfinImage? = item?.let {
	it.itemBackdropImages.firstOrNull()
		?: it.itemImages[ImageType.BACKDROP]
		?: it.parentBackdropImages.firstOrNull()
		?: it.seriesThumbImage
		?: it.itemImages[ImageType.THUMB]
		?: it.itemImages[ImageType.PRIMARY]?.takeIf { _ ->
			it.type == BaseItemKind.EPISODE && (it.primaryImageAspectRatio ?: 0.0) > 1.0
		}
}
