package com.mytvb.feature.marmot.domain

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * TV 直播频道数据模型（对标参考 utao domain/live）。
 *
 * 频道表 JSON 结构（tv-web/js/cctv/tv2.json）：
 * { "data": [ { "tag": "cctv", "name": "央视", "vods": [ { "name": "CCTV-13", "url": "..." } ] } ] }
 */
object MarmotModels {

    /** 频道表外层包装：`{ data: [Live] }`。 */
    data class DataWrapper<T>(
        @SerializedName("data") val data: List<T>? = null
    )

    /**
     * 频道分组（一个省份/类别），对标参考 `Live`。
     * - [tag]：分组标识（如 cctv / favorite）
     * - [name]：分组显示名（如「央视」「收藏」）
     * - [vods]：该组下所有频道
     */
    data class Live(
        @SerializedName("tag") val tag: String = "",
        @SerializedName("name") val name: String = "",
        @SerializedName("index") val index: Int = 0,
        @SerializedName("vods") val vods: MutableList<Vod> = mutableListOf()
    ) : Serializable

    /**
     * 单个频道，对标参考 `Vod`。
     * - [tagIndex]/[detailIndex]/[key] 由 [com.mytvb.feature.marmot.MarmotLiveData.load] 加载时填充，用于四向切台导航。
     * - [favorite] 收藏标记（仅运行期使用，不参与序列化）。
     */
    data class Vod(
        @SerializedName("name") var name: String = "",
        @SerializedName("url") var url: String = ""
    ) : Serializable {
        @Transient var tagIndex: Int = 0
        @Transient var detailIndex: Int = 0
        @Transient var key: String = ""
        @Transient var favorite: Boolean = false
    }
}
