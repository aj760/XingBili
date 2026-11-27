package com.mytvb.core.ui.base

import com.mytvb.core.common.settings.AppSettingsDataStore

/**
 * 全局视频卡片大小档位。默认"标准"（现状），向大/特大放大，向紧凑缩小。
 *
 * 卡片宽度由网格几何决定：固定列数页（首页/收藏/历史等）按列数偏移缩放；
 * 自适应卡宽页（番剧推荐等按基准卡宽排布）按宽度系数缩放。
 * 卡片内部文字不随卡片缩放——字号由"UI文字大小"设置独立管辖。
 */
object UiCardSize {

    const val KEY_UI_CARD_SIZE = "ui_card_size"

    /** 档位偏移与显示名一一对应：偏移为网格列数增减（正=更多列=更小卡片）。 */
    val OFFSETS = intArrayOf(1, 0, -1, -2)
    val NAMES = arrayOf("紧凑", "标准", "大", "特大")

    /** 自适应卡宽页的基准卡宽系数（与 OFFSETS 一一对应）。 */
    private val WIDTH_FACTORS = floatArrayOf(0.8f, 1f, 4f / 3f, 2f)

    @Volatile
    private var cachedIndex = 1

    /** 从设置缓存刷新档位；BaseActivity 每次创建时调用，读内存 cache 无 IO。 */
    fun refresh(settings: AppSettingsDataStore) {
        val saved = settings.getCachedString(KEY_UI_CARD_SIZE)?.toIntOrNull() ?: 0
        cachedIndex = OFFSETS.indexOf(saved).coerceAtLeast(0)
    }

    /** 网格列数偏移：+1 紧凑 / 0 标准 / -1 大 / -2 特大。 */
    fun offset(): Int = OFFSETS[cachedIndex]

    /** 自适应卡宽页的基准卡宽系数。 */
    fun widthFactor(): Float = WIDTH_FACTORS[cachedIndex]

    fun indexOf(offset: Int): Int = OFFSETS.indexOf(offset).coerceAtLeast(0)

    fun nameOf(offset: Int): String = NAMES.getOrNull(indexOf(offset)) ?: "标准"

    fun offsetAt(index: Int): Int = OFFSETS.getOrElse(index) { 0 }
}
