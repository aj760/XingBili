package com.mytvb.core.ui.focus

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.core.ui.base.RecyclerViewFocusRestoreHelper

object TabContentFocusHelper {

    data class RecyclerFocusResult(
        val handled: Boolean,
        val deferred: Boolean,
        val position: Int,
        val source: String
    ) {
        val resolved: Boolean
            get() = handled || deferred
    }

    fun requestVisibleFocus(vararg candidates: View?): Boolean {
        for (candidate in candidates) {
            if (candidate == null || candidate.visibility != View.VISIBLE || !candidate.isShown) {
                continue
            }
            if (candidate.requestFocus()) {
                return true
            }
        }
        return false
    }

    fun requestRecyclerPrimaryFocus(
        recyclerView: RecyclerView,
        itemCount: Int,
        fallbackPosition: Int = 0,
        focusRequester: (RecyclerView.ViewHolder) -> Boolean = { holder ->
            holder.itemView.requestFocus()
        }
    ): RecyclerFocusResult {
        if (itemCount <= 0) {
            return RecyclerFocusResult(
                handled = false,
                deferred = false,
                position = RecyclerView.NO_POSITION,
                source = "empty"
            )
        }

        // 触摸模式下卡片非 focusableInTouchMode（项目方针：touch mode 统一不聚焦），
        // 下面两轮 requestFocus 必然失败，落到 fallback 只会 scrollToPosition(0) 把
        // 用户滑动到的位置顶回顶部——触摸进播放器返回列表即回顶的根因，直接短路。
        if (recyclerView.isInTouchMode) {
            return RecyclerFocusResult(
                handled = false,
                deferred = false,
                position = RecyclerView.NO_POSITION,
                source = "touchMode"
            )
        }

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
        if (firstVisiblePosition != RecyclerView.NO_POSITION) {
            if (requestFocusAtPosition(recyclerView, firstVisiblePosition, focusRequester, scrollIfMissing = false)) {
                return RecyclerFocusResult(
                    handled = true,
                    deferred = false,
                    position = firstVisiblePosition,
                    source = "firstVisible"
                )
            }
        }

        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index) ?: continue
            if (child.visibility != View.VISIBLE) {
                continue
            }
            val holder = runCatching { recyclerView.getChildViewHolder(child) }.getOrNull()
            val handled = holder?.let(focusRequester) ?: child.requestFocus()
            if (handled) {
                return RecyclerFocusResult(
                    handled = true,
                    deferred = false,
                    position = recyclerView.getChildAdapterPosition(child),
                    source = "visibleChild"
                )
            }
        }

        val targetPosition = fallbackPosition.coerceIn(0, itemCount - 1)
        val deferredResult = RecyclerViewFocusRestoreHelper.requestFocusAtPosition(
            recyclerView = recyclerView,
            position = targetPosition,
            focusRequester = focusRequester
        )
        return RecyclerFocusResult(
            handled = deferredResult.handled,
            deferred = deferredResult.deferred,
            position = targetPosition,
            source = "targetPosition"
        )
    }

    fun requestSpatialOrPrimary(
        anchorView: View?,
        root: View,
        direction: Int = View.FOCUS_RIGHT,
        fallback: () -> Boolean
    ): Boolean {
        val handled = SpatialFocusNavigator.requestBestDescendant(
            anchorView = anchorView,
            root = root,
            direction = direction,
            fallback = null
        )
        return handled || fallback()
    }

    private fun requestFocusAtPosition(
        recyclerView: RecyclerView,
        position: Int,
        focusRequester: (RecyclerView.ViewHolder) -> Boolean,
        scrollIfMissing: Boolean
    ): Boolean {
        val result = RecyclerViewFocusRestoreHelper.requestFocusAtPosition(
            recyclerView = recyclerView,
            position = position,
            scrollIfMissing = scrollIfMissing,
            focusRequester = focusRequester
        )
        return result.handled || result.deferred
    }
}
