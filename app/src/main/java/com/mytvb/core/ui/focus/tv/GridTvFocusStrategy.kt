package com.mytvb.core.ui.focus.tv

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.core.common.log.AppLog

class GridTvFocusStrategy(
    private val spanCountProvider: () -> Int
) : TvFocusStrategy {

    companion object {
        private const val TAG = "GridTvFocus"

        /**
         * 以 RecyclerView 当前的 LayoutManager 实际 spanCount 为单一事实来源构造策略。
         * 不要在调用方硬编码列数：网格列数来自 adaptiveSpanCount()（分辨率档 × 卡片大小
         * 偏移），硬编码值一旦与实际布局脱节，row/column 换算整体错位，方向键焦点会
         * 斜跳到错误卡片（且行首放行框架后 FocusFinder 按真实几何找到的又是另一张卡）。
         * 运行时 setSpanCount 的页面也因此自动保持一致。
         */
        fun from(recyclerView: RecyclerView, fallback: Int = 4): GridTvFocusStrategy =
            GridTvFocusStrategy {
                (recyclerView.layoutManager as? GridLayoutManager)?.spanCount ?: fallback
            }
    }

    private val spanCount: Int
        get() = spanCountProvider().coerceAtLeast(1)

    override fun anchorFor(
        position: Int,
        stableKey: String?,
        offsetTop: Int,
        source: TvFocusAnchor.Source
    ): TvFocusAnchor {
        val span = spanCount
        return TvFocusAnchor(
            stableKey = stableKey,
            adapterPosition = position,
            row = position / span,
            column = position % span,
            offsetTop = offsetTop,
            source = source
        )
    }

    override fun nextPosition(anchor: TvFocusAnchor, direction: Int, itemCount: Int): Int? {
        if (itemCount <= 0 || anchor.adapterPosition !in 0 until itemCount) {
            AppLog.w(TAG, "nextPosition: INVALID anchor=${anchor.adapterPosition} itemCount=$itemCount")
            return null
        }
        val span = spanCount
        val current = anchor.adapterPosition
        val dirName = when (direction) {
            View.FOCUS_UP -> "UP"
            View.FOCUS_DOWN -> "DOWN"
            View.FOCUS_LEFT -> "LEFT"
            View.FOCUS_RIGHT -> "RIGHT"
            else -> "OTHER"
        }
        val result = when (direction) {
            View.FOCUS_UP -> (current - span).takeIf { it >= 0 }
            View.FOCUS_DOWN -> (current + span).takeIf { it < itemCount }
            View.FOCUS_LEFT -> (current - 1).takeIf { it >= 0 && it / span == anchor.row }
            View.FOCUS_RIGHT -> (current + 1).takeIf { it < itemCount && it / span == anchor.row }
            else -> null
        }
        AppLog.d(TAG, "nextPosition: $dirName pos=$current(${anchor.row},${anchor.column}) span=$span items=$itemCount → $result")
        return result
    }
}
