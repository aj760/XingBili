package com.mytvb.feature.marmot.quality

import com.mytvb.feature.marmot.domain.HzItem

/**
 * 直播画质 Provider（方案 C：逐播放源维护画质）。
 *
 * 不同播放源的画质机制差异极大（CCTV 用 br 参数重建播放器、央视频用 DOM 点击、省台各不相同），
 * 每个 Provider 封装一个源的「读取可用画质 / 定位当前画质 / 执行切换」三件事，
 * 由 [com.mytvb.ui.activity.MarmotLiveActivity.showQualityMenu] 按当前频道 url 路由。
 *
 * 新增一个播放源的画质支持 = 实现一个 [LiveQualityProvider]，不改动 Activity 和菜单 UI。
 *
 * 所有方法在主线程调用。
 */
interface LiveQualityProvider {
    /** 该源支持的画质列表（用于菜单显示）。空表示不支持切画质。 */
    fun availableQualities(): List<HzItem>

    /**
     * 当前画质在 [availableQualities] 返回列表中的索引（高亮定位）。
     * 无法确定时返回 0。
     */
    fun currentQualityIndex(items: List<HzItem>): Int

    /**
     * 执行画质切换。
     * @param item 用户选中的画质项（来自 [availableQualities]）
     * @param onSwitched 切换动作执行完的回调（如重建播放器后需要 Activity 重新加载）
     * @return true 已处理切换；false 该项无效或与当前相同（不切）
     */
    fun switchTo(item: HzItem, onSwitched: () -> Unit): Boolean
}
