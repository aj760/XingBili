package com.mytvb.feature.player.danmaku

import com.mytvb.feature.player.danmaku.common.BiliDanmakuStyle
import com.mytvb.feature.player.danmaku.common.DanmakuProtocolMode
import com.mytvb.feature.player.danmaku.common.DanmakuVipGradientStyle
import com.mytvb.model.dm.DmModel

/**
 * 把网络层弹幕模型 [DmModel] 转成弹幕引擎内部模型 [Danmaku]。
 *
 * 注意：[DmModel.progress] 已确认是**毫秒**（DmProtoParser 与 DanmakuPlaybackController 一致），
 * 直接作为 [Danmaku.timeMs]，无需单位换算。
 *
 * 过滤规则（对齐 BiliDanmakuFilterPolicy/LiteDanmakuController）：
 * - mode 7（特殊）/9（脚本）不转换，返回 null，交给上层过滤
 * - 空内容不转换
 */
fun DmModel.toDanmaku(allowVipColorful: Boolean = false): Danmaku? {
    if (content.isBlank()) return null
    // mode 7/9 是高级/脚本弹幕，blbl 引擎不支持，过滤掉
    if (mode == DanmakuProtocolMode.ADVANCED || mode == DanmakuProtocolMode.SCRIPT) return null
    if (content.contains("def text", ignoreCase = true)) return null
    // color 规范化：B 站协议 color=0 表示默认白色，这里统一转成 0xFFFFFF，
    // 对齐 akdanmaku 的 toDanmakuColor()（color==0 → Color.WHITE）。
    // 否则引擎会把 0 当黑色渲染（见 DanmakuEngine.drawFill.color = rgb or alpha）。
    val normalizedColor = if (color == 0) 0xFFFFFF else color
    val vipGradient = allowVipColorful && colorful == VipGradientRenderer.COLORFUL_VIP_GRADIENT
    return Danmaku(
        timeMs = progress.coerceIn(0, Int.MAX_VALUE),
        mode = mode,
        text = content,
        color = normalizedColor,
        fontSize = fontSize,
        weight = weight,
        midHash = midHash.takeIf { it.isNotBlank() },
        dmid = id.takeIf { it > 0L },
        attr = attr,
        vipGradient = vipGradient,
        vipGradientStyle = if (vipGradient) {
            DanmakuVipGradientStyle(
                fillTextureUrl = colorfulStyle.fillColorUrl,
                strokeTextureUrl = colorfulStyle.strokeColorUrl
            )
        } else {
            DanmakuVipGradientStyle.NONE
        },
    )
}

/** 批量转换，自动过滤 null。 */
fun List<DmModel>.toDanmakus(allowVipColorful: Boolean = false): List<Danmaku> =
    ArrayList<Danmaku>(size).also { out ->
        for (item in this) {
            item.toDanmaku(allowVipColorful)?.let(out::add)
        }
    }
