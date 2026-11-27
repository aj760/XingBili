package com.mytvb.core.ui.base

import com.mytvb.core.common.settings.AppSettingsDataStore

/**
 * 全局 UI 文字缩放档位。默认 100%，与历史版本视觉完全一致。
 *
 * 布局文字以 @dimen/pxXX 声明（与间距共用一个 px 池），系统 fontScale/sp 通道
 * 对其无效，故由 [ScaledTextView] 在字号应用环节统一乘以本系数，实现应用内
 * 的"系统字体大小"式缩放。弹幕与字幕各有独立字号设置，不在本设置范围内。
 */
object UiTextScale {

    const val KEY_UI_TEXT_SCALE = "ui_text_scale"
    const val DEFAULT_PERCENT = 100

    /** 档位百分比与显示名一一对应；追加档位（如 175/200）直接扩展两个数组。 */
    val PERCENTS = intArrayOf(90, 100, 115, 130, 150)
    val NAMES = arrayOf("小", "标准", "大", "特大", "超大")

    @Volatile
    private var cachedPercent = DEFAULT_PERCENT

    /** 从设置缓存刷新档位；BaseActivity 每次创建时调用，读内存 cache 无 IO。 */
    fun refresh(settings: AppSettingsDataStore) {
        cachedPercent = normalize(
            settings.getCachedString(KEY_UI_TEXT_SCALE)?.toIntOrNull() ?: DEFAULT_PERCENT
        )
    }

    fun scale(): Float = cachedPercent / 100f

    fun indexOf(percent: Int): Int = PERCENTS.indexOf(normalize(percent)).coerceAtLeast(0)

    fun nameOf(percent: Int): String = NAMES.getOrNull(indexOf(percent)) ?: "标准"

    private fun normalize(percent: Int): Int =
        if (PERCENTS.contains(percent)) percent else DEFAULT_PERCENT
}
