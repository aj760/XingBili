package blbl.cat3399.feature.following

import android.content.Context
import blbl.cat3399.R
import blbl.cat3399.core.ui.UiScale
import blbl.cat3399.core.ui.uiScaler

fun followingSpanCountForWidth(context: Context): Int {
    val resources = context.resources
    val dm = resources.displayMetrics
    val widthPx = dm.widthPixels
    val recyclerPaddingPx = (8f * dm.density).toInt() * 2 // activity_following_list.xml recycler padding=8dp
    val availablePx = (widthPx - recyclerPaddingPx).coerceAtLeast(1)

    val uiScale = UiScale.factor(context)
    val scaler = context.uiScaler(uiScale)
    fun scaledPx(id: Int): Int = scaler.scaledDimenPx(id)
    val itemMargin = scaledPx(R.dimen.following_grid_item_margin)
    val itemPadding = scaledPx(R.dimen.following_grid_item_padding)
    val avatarSize = scaledPx(R.dimen.following_grid_avatar_size)

    // Match item_following_grid + FollowingGridAdapter sizing:
    // columnWidth >= left/right margins + left/right padding + avatarSize
    val minItemWidthPx = (itemMargin * 2) + (itemPadding * 2) + avatarSize
    val raw = (availablePx / minItemWidthPx).coerceAtLeast(1)

    // Avoid becoming overly dense on very wide screens.
    val maxSpan = 14
    return raw.coerceAtMost(maxSpan)
}
