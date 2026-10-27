package org.jellyfin.androidtv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

// The default interface policy scrolls only enough to reveal a clipped item. Android TV's
// composition-local default instead moves every focused item toward a viewport pivot.
@OptIn(ExperimentalFoundationApi::class)
internal object SidebarBringIntoViewSpec : BringIntoViewSpec
