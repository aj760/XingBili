package com.mytvb.network.interceptor

import okhttp3.HttpUrl

internal data class PageHeaders(val referer: String, val origin: String)

internal fun isBilibiliHost(host: String): Boolean =
    listOf("bilibili.com", "biliapi.net", "biliapi.com", "hdslb.com", "bilivideo.com", "bilivideo.cn")
        .any { host == it || host.endsWith(".$it") }

/** Referer is a page URL; Origin must never contain a page path. WBI is unchanged. */
internal fun resolvePageHeaders(url: HttpUrl): PageHeaders {
    val path = url.encodedPath
    val referer = when {
        path.contains("/wbi/") -> "https://www.bilibili.com"
        url.host == "api.live.bilibili.com" ->
            "https://live.bilibili.com/" + (url.queryParameter("room_id") ?: url.queryParameter("id")).orEmpty()
        path.contains("/web-space/") || path.contains("/x/space/") ->
            "https://space.bilibili.com/" + (url.queryParameter("mid") ?: url.queryParameter("vmid")).orEmpty()
        path.contains("/x/polymer/web-dynamic/") || path.contains("/x/dynamic/") ->
            "https://t.bilibili.com/"
        path.contains("/x/v3/fav/") -> url.queryParameter("media_id")?.let {
            "https://www.bilibili.com/list/ml$it"
        } ?: "https://space.bilibili.com/"
        !url.queryParameter("bvid").isNullOrBlank() ->
            "https://www.bilibili.com/video/${url.queryParameter("bvid")}"
        else -> "https://www.bilibili.com"
    }
    return PageHeaders(referer, "https://" + referer.removePrefix("https://").substringBefore('/'))
}
