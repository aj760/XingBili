package com.mytvb.core.ui.decoration

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 设置分组列表间距：组内条目间距为 0（贴合拼接为一框），组头行上方给组间距。
 * [isHeaderRow] 由外部注入（按 adapter position 判断该行是否组头）。
 */
class SettingGroupSpacingDecoration(
    private val groupGap: Int,
    private val isHeaderRow: (Int) -> Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position > 0 && isHeaderRow(position)) {
            outRect.top = groupGap
        }
    }
}
