package com.mytvb.feature.marmot.domain

import com.google.gson.annotations.SerializedName

/**
 * 云端配置（对标参考 utao `domain/ConfigDTO` + `Res`）。
 *
 * 配置来源：`http://api.vonchange.com/utao/config/update.json`
 * 对应内置种子：`assets/tv-web/` 下无独立 update.json，由 [com.mytvb.feature.marmot.MarmotCloudUpdate] 写入 filesDir。
 *
 * - [res]：tv-web 前端资源（频道表+JS脚本+播放器）的版本与下载地址
 * - [x5Url]：X5 内核下载地址，key="64"/"32"，value=URL 列表（华为云 OCI tag 在新版土拨鼠里使用）
 * - [api]：备用 API host
 */
data class CloudConfig(
    /** tv-web 前端资源更新信息。 */
    @SerializedName("res") val res: CloudRes? = null,
    /** X5 内核下载地址：key="64"/"32"。 */
    @SerializedName("x5Url") val x5Url: Map<String, List<String>>? = null,
    /** 备用 API host（部分版本使用）。 */
    @SerializedName("api") val api: String? = null
)

/**
 * tv-web 前端资源引用（对标参考 `Res`）。
 * - [version]：资源版本号，递增
 * - [url]：tv-web.zip 下载地址
 * - [skipFirst]：解压时是否跳过 zip 内顶层目录（true=去一层目录前缀）
 * - [update]：是否允许更新
 */
data class CloudRes(
    @SerializedName("version") val version: Int = 0,
    @SerializedName("url") val url: String = "",
    @SerializedName("skipFirst") val skipFirst: Boolean? = null,
    @SerializedName("update") val update: Boolean = false
)
