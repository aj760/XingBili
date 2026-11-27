package com.mytvb.core.ui.focus.tv

import android.view.View
import androidx.recyclerview.widget.RecyclerView

class LinearTvFocusStrategy(
    private val orientation: Int = RecyclerView.VERTICAL
) : TvFocusStrategy {
    override fun anchorFor(
        position: Int,
        stableKey: String?,
        offsetTop: Int,
        source: TvFocusAnchor.Source
    ): TvFocusAnchor {
        return TvFocusAnchor(
            stableKey = stableKey,
            adapterPosition = position,
            row = position,
            column = 0,
            offsetTop = offsetTop,
            source = source
        )
    }

    override fun nextPosition(anchor: TvFocusAnchor, direction: Int, itemCount: Int): Int? {
        if (itemCount <= 0 || anchor.adapterPosition !in 0 until itemCount) {
            return null
        }
        return when (direction) {
            View.FOCUS_UP -> if (orientation == RecyclerView.VERTICAL) {
                (anchor.adapterPosition - 1).takeIf { it >= 0 }
            } else {
                null
            }
            View.FOCUS_DOWN -> if (orientation == RecyclerView.VERTICAL) {
                (anchor.adapterPosition + 1).takeIf { it < itemCount }
            } else {
                null
            }
            View.FOCUS_LEFT -> if (orientation == RecyclerView.HORIZONTAL) {
                (anchor.adapterPosition - 1).takeIf { it >= 0 }
            } else {
                null
            }
            View.FOCUS_RIGHT -> if (orientation == RecyclerView.HORIZONTAL) {
                (anchor.adapterPosition + 1).takeIf { it < itemCount }
            } else {
                null
            }
            else -> null
        }
    }
}
