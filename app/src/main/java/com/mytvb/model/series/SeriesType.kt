package com.mytvb.model.series

import androidx.annotation.StringRes
import com.mytvb.R

object SeriesType {
    const val ANIME = 1
    const val MOVIE = 2
    const val DOCUMENTARY = 3
    const val CHINA_ANIME = 4
    const val DRAMA = 5
    const val VARIETY = 7

    fun titleOf(type: Int): String {
        return when (type) {
            ANIME -> "番剧"
            CHINA_ANIME -> "国创"
            MOVIE -> "电影"
            VARIETY -> "综艺"
            DRAMA -> "电视剧"
            DOCUMENTARY -> "纪录片"
            else -> "番剧"
        }
    }

    /** 标题显示名的字符串资源（供 UI 层配合 context.getString 做多语言展示）。 */
    @StringRes
    fun titleResOf(type: Int): Int {
        return when (type) {
            ANIME -> R.string.animation
            CHINA_ANIME -> R.string.model_series_china_anime
            MOVIE -> R.string.movie
            VARIETY -> R.string.model_series_variety
            DRAMA -> R.string.series
            DOCUMENTARY -> R.string.documentary
            else -> R.string.animation
        }
    }
}
