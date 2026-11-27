package com.mytvb.core.ui.focus.tv

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * [ListAdapter] 到 [TvFocusableAdapter] 的桥接。
 *
 * Fragment 层把手写焦点恢复轮询统一到 [TvListFocusController.restoreFocusAfterReturn]
 * 时，无需把 ListAdapter 改继承 BaseAdapter——通过本桥接提供稳定 key 语义
 * （对应 BaseAdapter 的 stableKey：数据刷新后锚点按业务 key 重新解析位置）。
 *
 * 注意：本桥接不参与 D-pad 导航（不接 handleKey），仅供锚点捕获/返回恢复使用，
 * 页面原有按键行为保持不变。
 */
class ListAdapterTvFocusBridge<T>(
    private val listAdapter: ListAdapter<T, *>,
    private val keyOf: (T) -> String?
) : TvFocusableAdapter {

    override fun focusableItemCount(): Int = listAdapter.itemCount

    override fun stableKeyAt(position: Int): String? {
        val list = listAdapter.currentList
        if (position !in 0 until list.size) {
            return null
        }
        return keyOf(list[position])
    }

    override fun findPositionByStableKey(key: String): Int {
        val list = listAdapter.currentList
        for (position in list.indices) {
            if (keyOf(list[position]) == key) {
                return position
            }
        }
        return RecyclerView.NO_POSITION
    }
}
