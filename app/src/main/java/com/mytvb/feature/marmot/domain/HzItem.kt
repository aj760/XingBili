package com.mytvb.feature.marmot.domain

import com.google.gson.annotations.SerializedName

/**
 * 画质选项（对标 utao `domain/HzItem`）。
 *
 * 由页面 JS 通过 `_api.message("videoQuality", data)` 上报给原生。
 * - [name]：画质显示名（如「超清」「1080P」）
 * - [action]：选中该画质时要执行的 JS（Marmot 模式用；CCTV 原生模式不用，改走重建播放器）
 * - [isCurrent]：是否为当前画质（高亮显示）。JS 上报字段名为 `isCurrent`（对齐 utao/云端脚本）；
 *   `current` 作为兼容别名保留。
 * - [level]：画质码率数值（CCTV 用作 createLivePlayer 的 br 参数）
 * - [id]：画质项 DOM id（部分源用）
 */
data class HzItem(
    @SerializedName("name") val name: String? = "",
    @SerializedName("action") val action: String? = "",
    @SerializedName(value = "isCurrent", alternate = ["current"]) val isCurrent: Boolean? = null,
    @SerializedName("level") val level: Int? = null,
    @SerializedName("id") val id: String? = null
)
