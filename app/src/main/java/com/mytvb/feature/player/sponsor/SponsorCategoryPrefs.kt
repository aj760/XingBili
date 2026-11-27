package com.mytvb.feature.player.sponsor

import androidx.annotation.StringRes
import com.mytvb.R

/**
 * 空降助手「跳过类型」偏好：每个类别一个开关，存储 key 形如 `sponsor_cat_<name>`。
 *
 * 默认仅开启「赞助/恰饭」一类：片头/片尾等类别误伤率高（片头片尾可能承载正片内容），
 * 一律默认关闭，由用户按需开启。
 */
object SponsorCategoryPrefs {

    private const val ON = "开"
    private const val OFF = "关"

    data class Option(
        val category: String,
        val key: String,
        @StringRes val titleRes: Int,
        val defaultOn: Boolean
    )

    /** 设置页展示顺序，与 SponsorBlock 官方分类一致；默认仅「赞助/恰饭」开启。 */
    val OPTIONS: List<Option> = listOf(
        Option(SponsorSegment.CATEGORY_SPONSOR, "sponsor_cat_sponsor", R.string.player_sponsor_category_sponsor, true),
        Option(SponsorSegment.CATEGORY_INTRO, "sponsor_cat_intro", R.string.player_sponsor_category_intro, false),
        Option(SponsorSegment.CATEGORY_OUTRO, "sponsor_cat_outro", R.string.player_sponsor_category_outro, false),
        Option(SponsorSegment.CATEGORY_SELF_PROMO, "sponsor_cat_selfpromo", R.string.player_sponsor_category_self_promo, false),
        Option(SponsorSegment.CATEGORY_INTERACTION, "sponsor_cat_interaction", R.string.player_sponsor_category_interaction, false),
        Option(SponsorSegment.CATEGORY_PREVIEW, "sponsor_cat_preview", R.string.player_sponsor_category_preview, false),
        Option(SponsorSegment.CATEGORY_MUSIC_OFFTOPIC, "sponsor_cat_music_offtopic", R.string.player_sponsor_category_music_offtopic, false),
        Option(SponsorSegment.CATEGORY_FILLER, "sponsor_cat_filler", R.string.player_sponsor_category_filler, false),
        Option(SponsorSegment.CATEGORY_EXCLUSIVE_ACCESS, "sponsor_cat_exclusive", R.string.player_sponsor_category_exclusive_access, false)
    )

    /** 所有类别开关的存储 key（供设置页点击分发与启动恢复使用）。 */
    val KEYS: Set<String> = OPTIONS.map { it.key }.toSet()

    fun defaultStored(option: Option): String = if (option.defaultOn) ON else OFF

    /**
     * 读取用户勾选的类别。空列表表示用户把所有类别都关了——调用方应据此跳过片段加载，
     * 而不是发一个空 category 的请求（那会被服务端理解为「全部类别」）。
     */
    fun selectedCategories(getStored: (String) -> String?): List<String> =
        OPTIONS.filter { (getStored(it.key) ?: defaultStored(it)) == ON }.map { it.category }
}
