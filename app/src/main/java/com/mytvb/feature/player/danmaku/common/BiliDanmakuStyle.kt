package com.mytvb.feature.player.danmaku.common

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * B 站风格弹幕文字规则。
 *
 * 已从 B 站反编译代码确认的点播弹幕配置默认值（DanmakuParams.reset()）：
 * - danmaku_alpha_factor: 0.8          （弹幕整体透明度因子）
 * - danmaku_textsize_scale_factor: 1.0 （字号缩放）
 * - danmaku_stroke_width_scaling: 0.8  （描边宽度缩放，点播默认 0.8）
 * - danmaku_duration_factor reset 后为 7.0
 *
 * 描边宽度 = 基础系数(textSize) × stroke_width_scaling(0.8)。
 * scaling 0.8 与官方一致，描边粗细仅由基础系数决定。
 */
internal object BiliDanmakuStyle {
    const val DEFAULT_ALPHA_FACTOR = 0.8f
    const val DEFAULT_TEXT_SIZE_SCALE_FACTOR = 1.0f
    const val DEFAULT_STROKE_WIDTH_SCALING = 0.8f
    const val DEFAULT_DURATION_FACTOR_SECONDS = 7.0f

    fun normalizeProtocolColor(color: Int): Int =
        if (color == 0) {
            Color.WHITE
        } else {
            color or 0xFF000000.toInt()
        }

    fun resolveStrokeColor(rgb: Int, opacityAlpha: Int): Int {
        val luminance = (0.2126f * Color.red(rgb) +
            0.7152f * Color.green(rgb) +
            0.0722f * Color.blue(rgb)) / 255f
        val baseAlpha = if (luminance > 0.5f) 230 else 210
        val strokeAlpha = ((opacityAlpha * baseAlpha) / 255).coerceIn(0, 255)
        val baseRgb = if (luminance > 0.5f) 0x000000 else 0xFFFFFF
        return (strokeAlpha shl 24) or baseRgb
    }

    /**
     * 描边宽度（像素）。
     *
     * fontBorder 语义见 DanmakuConfig：
     * - 0 DEFAULT 默认描边：textSize × 0.13，夹在 [2.5, 4.5]
     * - 1 HEAVY  重描边  ：textSize × 0.16，夹在 [3, 5.4]
     * - 2 SHADOW 投影    ：走 setShadowLayer，这里返回与默认档同宽，仅作 paint 备用
     * - 3 NONE   无描边  ：0
     *
     * 最终宽度再乘 stroke_width_scaling(0.8)，与官方一致。
     */
    fun resolveStrokeWidth(
        textSizePx: Float,
        fontBorder: Int
    ): Float {
        val baseWidth = when (fontBorder) {
            1 -> (textSizePx * 0.16f).coerceIn(3f, 5.4f)
            2 -> (textSizePx * 0.13f).coerceIn(2.5f, 4.5f)
            3 -> return 0f
            else -> (textSizePx * 0.13f).coerceIn(2.5f, 4.5f)
        }
        return baseWidth * DEFAULT_STROKE_WIDTH_SCALING
    }

    fun resolveBaseStrokeWidth(textSizePx: Float, fontBorder: Int): Float =
        when (fontBorder) {
            1 -> (textSizePx * 0.16f).coerceIn(3f, 5.4f)
            2 -> (textSizePx * 0.13f).coerceIn(2.5f, 4.5f)
            3 -> 0f
            else -> (textSizePx * 0.13f).coerceIn(2.5f, 4.5f)
        }

    /**
     * VIP 渐变弹幕描边宽度。VIP 弹幕描边色由贴图决定（colorful_src），
     * 这里只给宽度。系数与默认描边档一致。
     */
    fun resolveVipStrokeWidth(textSizePx: Float): Float =
        (textSizePx * 0.13f).coerceIn(2.5f, 4.5f) * DEFAULT_STROKE_WIDTH_SCALING

    fun useShadowLayer(fontBorder: Int): Boolean = fontBorder == 2

    fun strokeWidthForCache(
        textSizePx: Float,
        fontBorder: Int
    ): Int =
        resolveStrokeWidth(textSizePx, fontBorder).roundToInt().coerceAtLeast(0)
}
