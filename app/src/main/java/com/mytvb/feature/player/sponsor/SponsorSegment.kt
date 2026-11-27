package com.mytvb.feature.player.sponsor

import android.content.Context
import com.mytvb.R

data class SponsorSegment(
    val segment: List<Float> = emptyList(),
    val UUID: String = "",
    val category: String = "",
    val actionType: String = "skip",
    val cid: String = "",
    val locked: Int = 0,
    val votes: Int = 0,
    val videoDuration: Float = 0f
) {
    val startTimeMs: Long get() = ((segment.getOrNull(0) ?: 0f) * 1000).toLong()
    val endTimeMs: Long get() = ((segment.getOrNull(1) ?: 0f) * 1000).toLong()
    val isSkipType: Boolean get() = actionType == "skip"
    /** 静音型片段：进入后仅静音不跳转，播放到片段尾自动恢复音量。 */
    val isMuteType: Boolean get() = actionType == "mute"

    /** 本地化类别的显示名（推荐 UI 层使用）。 */
    fun categoryName(context: Context): String = when (category) {
        CATEGORY_SPONSOR -> context.getString(R.string.player_sponsor_category_sponsor)
        CATEGORY_INTRO -> context.getString(R.string.player_sponsor_category_intro)
        CATEGORY_OUTRO -> context.getString(R.string.player_sponsor_category_outro)
        CATEGORY_SELF_PROMO -> context.getString(R.string.player_sponsor_category_self_promo)
        CATEGORY_INTERACTION -> context.getString(R.string.player_sponsor_category_interaction)
        CATEGORY_PREVIEW -> context.getString(R.string.player_sponsor_category_preview)
        CATEGORY_MUSIC_OFFTOPIC -> context.getString(R.string.player_sponsor_category_music_offtopic)
        CATEGORY_POI_HIGHLIGHT -> context.getString(R.string.player_sponsor_category_poi_highlight)
        CATEGORY_FILLER -> context.getString(R.string.player_sponsor_category_filler)
        CATEGORY_PADDING -> context.getString(R.string.player_sponsor_category_padding)
        CATEGORY_EXCLUSIVE_ACCESS -> context.getString(R.string.player_sponsor_category_exclusive_access)
        CATEGORY_OFFICIAL_OP -> context.getString(R.string.official_clip_op)
        CATEGORY_OFFICIAL_ED -> context.getString(R.string.official_clip_ed)
        else -> category
    }

    /** 中文类别的显示名（兼容旧调用方；新代码请用 [categoryName]）。 */
    fun categoryName(): String = when (category) {
        CATEGORY_SPONSOR -> "赞助/恰饭"
        CATEGORY_INTRO -> "过场/开场动画"
        CATEGORY_OUTRO -> "鸣谢/结束画面"
        CATEGORY_SELF_PROMO -> "无偿/自我推广"
        CATEGORY_INTERACTION -> "三连/互动提醒"
        CATEGORY_PREVIEW -> "回顾/概要"
        CATEGORY_MUSIC_OFFTOPIC -> "音乐:非音乐部分"
        CATEGORY_POI_HIGHLIGHT -> "精彩时刻/重点"
        CATEGORY_FILLER -> "离题闲聊/玩笑"
        CATEGORY_PADDING -> "内容填充"
        CATEGORY_EXCLUSIVE_ACCESS -> "付费/专属内容"
        CATEGORY_OFFICIAL_OP -> "片头"
        CATEGORY_OFFICIAL_ED -> "片尾"
        else -> category
    }

    fun categoryColor(): Long = when (category) {
        CATEGORY_SPONSOR -> 0xFF00D400
        CATEGORY_INTRO -> 0xFF00FFFF
        CATEGORY_OUTRO -> 0xFF0202ED
        CATEGORY_SELF_PROMO -> 0xFFFFFF00
        CATEGORY_INTERACTION -> 0xFFCC00FF
        CATEGORY_PREVIEW -> 0xFF008FD6
        CATEGORY_MUSIC_OFFTOPIC -> 0xFFFF9900
        CATEGORY_POI_HIGHLIGHT -> 0xFFFF1684
        CATEGORY_FILLER -> 0xFF7300FF
        CATEGORY_PADDING -> 0xFF222222
        CATEGORY_EXCLUSIVE_ACCESS -> 0xFF008A5C
        // 官方打点：沿用空降片头/片尾的青/蓝，进度条上视觉同族
        CATEGORY_OFFICIAL_OP -> 0xFF00FFFF
        CATEGORY_OFFICIAL_ED -> 0xFF0202ED
        else -> 0xFF00D400
    }

    companion object {
        const val CATEGORY_SPONSOR = "sponsor"
        const val CATEGORY_INTRO = "intro"
        const val CATEGORY_OUTRO = "outro"
        const val CATEGORY_SELF_PROMO = "selfpromo"
        const val CATEGORY_INTERACTION = "interaction"
        const val CATEGORY_PREVIEW = "preview"
        const val CATEGORY_MUSIC_OFFTOPIC = "music_offtopic"
        const val CATEGORY_POI_HIGHLIGHT = "poi_highlight"
        const val CATEGORY_FILLER = "filler"
        const val CATEGORY_PADDING = "padding"
        const val CATEGORY_EXCLUSIVE_ACCESS = "exclusive_access"
        // 官方片头片尾（pgc playurl clip_info_list），非空降社区数据
        const val CATEGORY_OFFICIAL_OP = "official_op"
        const val CATEGORY_OFFICIAL_ED = "official_ed"
        // 拉取类别按用户明确选择只取三类：恰饭、片头、片尾。服务端其余类别
        // （selfpromo/interaction/preview/music_offtopic/filler 等）不请求。
        val ALL_CATEGORIES = listOf(
            CATEGORY_SPONSOR,
            CATEGORY_INTRO,
            CATEGORY_OUTRO
        )
    }
}
