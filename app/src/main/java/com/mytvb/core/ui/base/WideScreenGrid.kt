package com.mytvb.core.ui.base

import android.content.res.Resources
import kotlin.math.roundToInt

/** 超宽屏判定：宽高比 ≥ 2.8（如 5120×1600）。 */
fun Resources.isWideScreen(): Boolean {
    val metrics = displayMetrics
    return metrics.widthPixels >= metrics.heightPixels * 2.8f
}

/**
 * 超宽屏网格列数适配。
 *
 * 超宽屏沿用为常规比例设计的列数会让封面过大，统一放宽到 [wide] 列；
 * 常规比例屏幕保持 [base] 列。
 * 在此基础上叠加"视频卡片大小"档位偏移（[UiCardSize.offset]），
 * 各列表页（首页、搜索、历史、收藏、动态、直播等）一律走此入口，保证判定一致。
 *
 * 卡片宽度 = 屏宽/列数（物理像素，不随 density），而卡内文字走 pxN 池随
 * [UiScale] 缩放——界面缩放系数必须同步放大卡片（列数 = 基准列数/系数），
 * 否则大系数下文字相对卡片溢出（角标叠印、标题截断）。100% 时精确
 * 退化为固定列数，与历史行为一致。
 */
fun Resources.adaptiveSpanCount(base: Int = 4, wide: Int = 8): Int {
    val cols = if (isWideScreen()) wide else base
    val span = (cols / UiScale.scale()).roundToInt()
    // 叠加"视频卡片大小"偏移：紧凑 +1 列（更小卡片）、大 -1、特大 -2；任何页面最少保留 2 列
    return (span + UiCardSize.offset()).coerceAtLeast(2)
}
