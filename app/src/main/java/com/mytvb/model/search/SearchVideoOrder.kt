package com.mytvb.model.search

import androidx.annotation.StringRes
import com.mytvb.R

enum class SearchVideoOrder(
    val orderValue: String,
    val showName: String,
    /** 显示名的字符串资源（供 UI 层配合 context.getString 做多语言展示）。 */
    @StringRes val nameRes: Int
) {
    TotalRank("totalrank", "综合排序", R.string.model_search_order_total_rank),
    Click("click", "最多点击", R.string.model_search_order_click),
    PubDate("pubdate", "最新发布", R.string.model_search_order_pub_date),
    Dm("dm", "最多弹幕", R.string.model_search_order_dm),
    MostCollection("stow", "最多收藏", R.string.model_search_order_most_collection)
}
