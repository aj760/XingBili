package com.mytvb.feature.marmot.quality

import com.mytvb.core.common.json.GsonHolder
import com.mytvb.feature.marmot.domain.HzItem

/**
 * Marmot 方式画质 Provider（兜底，适用未单独适配的播放源）。
 *
 * 沿用原 Marmot 机制：页面上报 videoQuality（JSON 数组），切换靠执行 item.action 脚本
 * （通常是被注入脚本里的 hzChoose / 切画质 click）。
 * 不缓存画质状态——可用画质和当前项都来自页面上报的原始数据。
 *
 * @param videoQualityDataGetter 取页面上报的画质 JSON（Activity 的 videoQualityData，可变）
 * @param evalJs 执行切换脚本（Activity 的 webEngine.evaluateJavascript）
 */
class MarmotQualityProvider(
    private val videoQualityDataGetter: () -> String?,
    private val evalJs: (String) -> Unit
) : LiveQualityProvider {

    override fun availableQualities(): List<HzItem> = parseQualityData(videoQualityDataGetter())

    override fun currentQualityIndex(items: List<HzItem>): Int =
        items.indexOfFirst { it.isCurrent == true }.coerceAtLeast(0)

    override fun switchTo(item: HzItem, onSwitched: () -> Unit): Boolean {
        val action = item.action?.takeIf { it.trim().isNotEmpty() } ?: return false
        evalJs(action)
        onSwitched()
        return true
    }

    /** 解析画质数据（支持数组或 {data:{qualities|items}} 嵌套）。 */
    private fun parseQualityData(raw: String?): List<HzItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<HzItem>>() {}.type
            if (trimmed.startsWith("[")) {
                GsonHolder.DEFAULT.fromJson(trimmed, type) ?: emptyList()
            } else {
                val obj = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
                val qualities = obj.getAsJsonArray("qualities")
                    ?: obj.getAsJsonObject("data")?.getAsJsonArray("qualities")
                    ?: obj.getAsJsonObject("data")?.getAsJsonArray("items")
                if (qualities != null) {
                    GsonHolder.DEFAULT.fromJson(qualities, type) ?: emptyList()
                } else listOf(GsonHolder.DEFAULT.fromJson(trimmed, HzItem::class.java))
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
