package com.mytvb.feature.marmot.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tencent.smtt.export.external.interfaces.SslError
import com.tencent.smtt.export.external.interfaces.SslErrorHandler
import com.tencent.smtt.export.external.interfaces.WebResourceRequest
import com.tencent.smtt.export.external.interfaces.WebResourceResponse
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient
import com.mytvb.feature.marmot.MarmotLiveData
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * X5 WebView 直播 WebViewClient（对标参考 `WebViewClientImpl.java` type=1 + 系统版 [MarmotSystemWebViewClient]）。
 *
 * 完整的 shouldInterceptRequest 拦截（X5 包名版本）：
 * 1. 拦截 tv-web 本地资源（js/css/html/字体）返回本地文件
 * 2. 拦截直播 API（风行 FLV 重定向、广东台 API 转发、m3u8 sessionStorage 注入）
 * 3. 无图模式
 * 4. onPageFinished 注入 tv.user.js
 */
class MarmotX5WebViewClient(
    private val context: Context,
    private val onProgress: (Int) -> Unit,
    private val onPageLoaded: (url: String) -> Unit
) : WebViewClient() {
    companion object {
        private const val TAG = "MarmotX5WebViewClient"
        private const val UTF8 = "utf-8"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var currentUrl: String? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        Log.i(TAG, "onPageStarted: $url")
        currentUrl = url
        onProgress(0)
    }

    override fun onPageFinished(view: WebView, url: String) {
        Log.i(TAG, "onPageFinished: $url progress=${view.progress}")
        onProgress(100)
        if (view.progress == 100) {
            injectLiveScripts(view, url)
            onPageLoaded(url)
        }
    }

    private fun injectLiveScripts(view: WebView, url: String) {
        if (url.contains("tv-web")) return
        // CCTV 原生播放页（createLivePlayer 自构 HTML）：不注入 tv.user.js，避免与 CCTV 播放器脚本冲突
        if (url.contains(MarmotSystemWebViewClient.MYBILI_CCTV_NATIVE_MARKER)) return
        val script = MarmotLiveData.readFileWithFallback(context, "tv-web/js/tv.user.js")
        if (script.isBlank()) {
            Log.w(TAG, "tv.user.js 为空，跳过注入")
            return
        }
        // 关键：把 _tvState.platform 占位符 "//platform//" 替换为 "android"。
        // 否则 platform 保持占位符，tv.user.js 会用 marmot://res.vonchange.com 加载本地脚本，
        // 而部分官网（如 tv.gxtv.cn）带 CSP（default-src *）会拒绝 marmot:// 自定义 scheme，
        // 导致 common.js/detail.js 加载失败、全屏劫持不生效。替换后改用官网 https 域名加载，
        // scheme 为 https 且 URL 含 tv-web，会被 shouldInterceptRequest 拦截返回本地资源。
        val finalScript = script.replace("//platform//", "android")
        view.evaluateJavascript(finalScript, null)
        Log.i(TAG, "已注入 tv.user.js（${finalScript.length} 字符）到 $url")
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        Log.w(TAG, "onReceivedSslError: ${error?.primaryError}")
        handler?.proceed()
    }

    /** 完整的请求拦截（对标系统版 [MarmotSystemWebViewClient.shouldInterceptRequest]，X5 包名）。 */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val method = request.method
        val accept = request.requestHeaders?.get("Accept")
        try {
            // 风行直播 FLV 重定向
            if (url.startsWith("https://tlive.fengshows.com/live/") ||
                url.startsWith("https://hkmolive.fengshows.com/live/")
            ) {
                val realUrl = "https://qctv.fengshows.cn" + url.substring(url.indexOf("/live"))
                val input = httpGetStream(realUrl) ?: return super.shouldInterceptRequest(view, request)
                val resp = WebResourceResponse("video/x-flv", UTF8, input)
                resp.setResponseHeaders(mapOf("access-control-allow-origin" to "*"))
                return resp
            }

            // 广东台 API 转发
            if (url.startsWith("https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel")) {
                if (method.equals("GET", ignoreCase = true)) {
                    val headers = request.requestHeaders?.toMap()?.toMutableMap()?.apply {
                        remove("x-requested-with")
                    } ?: emptyMap()
                    val json = httpGetString(url, headers)
                    val resp = WebResourceResponse(
                        "application/json;charset=UTF-8", UTF8,
                        ByteArrayInputStream(json.toByteArray(Charset.defaultCharset()))
                    )
                    resp.setResponseHeaders(mapOf("access-control-allow-origin" to "*"))
                    return resp
                }
            }

            // m3u8 拦截
            if (url.contains(".m3u8") && currentUrl != null && currentUrl!!.contains("u-link=1")) {
                val js = "sessionStorage.setItem(\"u-m3u8\",\"$url\");sessionStorage.setItem(\"u-loc\",\"$currentUrl\");"
                view.post { view.evaluateJavascript(js, null) }
            }

            // 无图模式
            if (accept != null && accept.startsWith("image/") && !isImageAllowed(url)) {
                return WebResourceResponse(null, null, null as InputStream?)
            }

            // tv-web 本地资源拦截（核心）
            val tvWebIndex = url.indexOf("tv-web")
            if (tvWebIndex < 0) {
                return super.shouldInterceptRequest(view, request)
            }
            return interceptTvWebResource(url, tvWebIndex)
        } catch (t: Throwable) {
            Log.w(TAG, "shouldInterceptRequest 异常: $url - ${t.message}")
            return super.shouldInterceptRequest(view, request)
        }
    }

    private fun interceptTvWebResource(url: String, tvWebIndex: Int): WebResourceResponse? {
        // tvImg=1 图片
        if (url.endsWith("tvImg=1")) {
            val fileName = url.substring(tvWebIndex, url.indexOf("?"))
            val stream = MarmotLiveData.readStreamWithFallback(context, fileName) ?: return null
            return WebResourceResponse("image/jpeg", UTF8, stream)
        }
        var cleanUrl = url
        val qIdx = cleanUrl.indexOf("?")
        if (qIdx > 0) cleanUrl = cleanUrl.substring(0, qIdx)
        val fileName = cleanUrl.substring(tvWebIndex)
        Log.d(TAG, "本地资源: $fileName")
        return when {
            cleanUrl.endsWith(".js") -> {
                val stream = MarmotLiveData.readStreamWithFallback(context, fileName) ?: return null
                WebResourceResponse("text/javascript", UTF8, stream)
            }
            cleanUrl.endsWith(".css") -> {
                val stream = MarmotLiveData.readStreamWithFallback(context, fileName) ?: return null
                WebResourceResponse("text/css", UTF8, stream)
            }
            cleanUrl.endsWith(".html") -> {
                val html = MarmotLiveData.readFileWithFallback(context, fileName)
                val replaced = html.replace("base.js", "basex.js")
                WebResourceResponse("text/html", UTF8,
                    ByteArrayInputStream(replaced.toByteArray(Charset.defaultCharset())))
            }
            cleanUrl.endsWith(".woff2") -> {
                val stream = MarmotLiveData.readStreamWithFallback(context, fileName) ?: return null
                WebResourceResponse("font/woff2", UTF8, stream)
            }
            else -> null
        }
    }

    private fun isImageAllowed(url: String): Boolean =
        url.contains("tvImg") || url.contains("cctvpic.com") ||
            url.contains("player.cntv.cn") || url.contains("default")

    private fun httpGetStream(url: String): InputStream? = try {
        client.newCall(Request.Builder().url(url).get().build()).execute().body?.byteStream()
    } catch (t: Throwable) { null }

    private fun httpGetString(url: String, headers: Map<String, String>): String = try {
        val req = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.get().build()
        client.newCall(req).execute().body?.string() ?: ""
    } catch (t: Throwable) { "" }
}
