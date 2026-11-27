package com.mytvb.feature.player

import com.mytvb.model.player.PlayInfoModel
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URL

/** 播放地址相关的纯工具：协议归一与 CDN 签名过期时间解析。 */
internal object VideoPlayerUrlUtils {

    fun normalizeUrl(rawUrl: String): String {
        return when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            else -> "https://$rawUrl"
        }
    }

    fun extractUrlExpiryMs(url: String): Long {
        val uri = Uri.parse(url)
        val expiresParam = uri.getQueryParameter("expires")
            ?: uri.getQueryParameter("deadline")
            ?: return 0L
        val expiresSeconds = expiresParam.toLongOrNull() ?: return 0L
        return expiresSeconds * 1000L
    }

    fun resolveSessionExpiryMs(route: DashRoute): Long {
        val videoExpiry = extractUrlExpiryMs(route.videoRepresentation.baseUrl)
        val audioExpiry = route.audioRepresentation?.baseUrl?.let(::extractUrlExpiryMs) ?: Long.MAX_VALUE
        return minOf(videoExpiry, audioExpiry).takeIf { it > 0L } ?: 0L
    }
}

/** 对即将播放的 CDN 主机做 HEAD 预连接，减少首帧建连耗时。 */
internal class CdnPreconnector(private val okHttpClient: OkHttpClient) {

    suspend fun preconnectHosts(videoUrls: List<String>, audioUrls: List<String>) {
        val uniqueHosts = mutableSetOf<String>()
        for (url in videoUrls + audioUrls) {
            try {
                val host = URL(url).host
                if (host.isNotBlank()) {
                    uniqueHosts.add(host)
                }
            } catch (_: Exception) {
                continue
            }
        }

        if (uniqueHosts.isEmpty()) {
            return
        }

        coroutineScope {
            uniqueHosts.forEach { host ->
                launch(Dispatchers.IO) {
                    runCatching {
                        val request = Request.Builder()
                            .url("https://$host")
                            .head()
                            .build()
                        okHttpClient.newCall(request).execute().use { }
                    }
                }
            }
        }
    }

    suspend fun forRoute(route: DashRoute?) {
        if (route == null) return
        try {
            val allUrls = route.videoUrls + route.audioUrls
            if (allUrls.isEmpty()) return
            preconnectHosts(route.videoUrls, route.audioUrls)
        } catch (_: Exception) {
        }
    }

    suspend fun forPlayInfo(playInfo: PlayInfoModel) {
        val allUrls = extractPlayInfoUrls(playInfo)
        if (allUrls.isEmpty()) return
        preconnectHosts(allUrls, emptyList())
    }

    fun extractPlayInfoUrls(playInfo: PlayInfoModel): List<String> {
        return try {
            val dash = playInfo.dash ?: return emptyList()
            val videoUrls = dash.video?.mapNotNull { it.realBaseUrl.ifEmpty { null } }.orEmpty()
            val audioUrls = dash.audio?.mapNotNull { it.realBaseUrl.ifEmpty { null } }.orEmpty()
            videoUrls + audioUrls
        } catch (_: Exception) {
            emptyList()
        }
    }
}
