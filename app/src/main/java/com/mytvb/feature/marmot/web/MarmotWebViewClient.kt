package com.mytvb.feature.marmot.web

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mytvb.feature.marmot.MarmotLiveData
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * 系统 WebView 直播 WebViewClient（对标参考 utao `WebViewClientImpl.java`，type=1 直播模式）。
 *
 * 核心职责（直播能否播放的关键）：
 * 1. [shouldInterceptRequest]：拦截含 `tv-web` 的请求，返回本地 JS/CSS/HTML/字体资源；
 *    拦截特定直播 API（风行 FLV 重定向、广东台 API 转发、m3u8 sessionStorage 注入）；无图模式。
 * 2. [onPageFinished]：页面加载完成后注入 `tv.user.js`（直播脚本，驱动劫持播放）。
 *
 * X5 版本见 [MarmotX5WebViewClient]（逻辑相同，仅包名差异）。
 *
 * @param context Activity 上下文
 * @param onProgress 进度回调（0-100）
 * @param onPageLoaded 页面加载完成回调（注入脚本后）
 */
class MarmotSystemWebViewClient(
    private val context: Context,
    private val onProgress: (Int) -> Unit,
    private val onPageLoaded: (url: String) -> Unit
) : WebViewClient() {
    companion object {
        private const val TAG = "MarmotWebViewClient"
        private const val UTF8 = "utf-8"
        /** CCTV 原生播放页（createLivePlayer 自构 HTML）的 baseUrl 标记，用于跳过 tv.user.js 注入。 */
        const val MYBILI_CCTV_NATIVE_MARKER = "mybili-cctv-native"
    }

    /** 独立 client（直播 API 请求不注入 B 站 header）。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 当前加载的 URL（用于 m3u8 拦截判断 u-link=1）。 */
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

    /**
     * 注入直播脚本（对标新版土拨鼠的 tv.user.js 注入机制）。
     *
     * 新版机制（与 utao 旧版的 end.js+load_detail_tv.js 不同）：
     * - WebView 加载官网直播页（如 https://tv.cctv.com/live/cctv13/）
     * - 页面加载完成后注入 `tv-web/js/tv.user.js`
     * - tv.user.js 内部 `loadDetailByUrl()` 按当前 URL 决定加载哪个 `tv/xxx/detail.js`
     * - detail.js 解析页面、提取直播流地址、重定向到 live.html 用 xgplayer 播放
     *
     * tv-web 自身页面（live.html / index.html）不注入，避免循环。
     */
    private fun injectLiveScripts(view: WebView, url: String) {
        if (url.contains("tv-web")) return
        // CCTV 原生播放页（createLivePlayer 自构 HTML）：不注入 tv.user.js，避免与 CCTV 播放器脚本冲突
        if (url.contains(MYBILI_CCTV_NATIVE_MARKER)) return
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
        handler?.proceed() // 忽略 SSL 错误（参考行为，部分直播证书不规范）
    }

    /**
     * 请求拦截（对标参考 `shouldInterceptRequest` type=1 分支）。
     * 这是直播播放的核心：拦截 tv-web 本地资源 + 特定直播 API 重定向。
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val method = request.method
        val accept = request.requestHeaders?.get("Accept")
        try {
            // —— 直播 API 特殊拦截（参考 type=1 分支）——

            // 风行直播 FLV 重定向：tlive.fengshows.com / hkmolive.fengshows.com → qctv.fengshows.cn
            if (url.startsWith("https://tlive.fengshows.com/live/") ||
                url.startsWith("https://hkmolive.fengshows.com/live/")
            ) {
                val realUrl = "https://qctv.fengshows.cn" + url.substring(url.indexOf("/live"))
                Log.i(TAG, "风行 FLV 重定向: $realUrl")
                val input = httpGetStream(realUrl) ?: return super.shouldInterceptRequest(view, request)
                val resp = WebResourceResponse("video/x-flv", UTF8, input)
                resp.setResponseHeaders(mapOf("access-control-allow-origin" to "*"))
                return resp
            }

            // 广东台 API 转发（转发自定义请求头）
            if (url.startsWith("https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel")) {
                if (method.equals("GET", ignoreCase = true)) {
                    val headers = request.requestHeaders?.toMap()?.toMutableMap()?.apply {
                        remove("x-requested-with")
                    } ?: emptyMap()
                    val json = httpGetString(url, headers)
                    Log.i(TAG, "广东台 API: ${json.take(100)}")
                    val resp = WebResourceResponse(
                        "application/json;charset=UTF-8", UTF8,
                        ByteArrayInputStream(json.toByteArray(Charset.defaultCharset()))
                    )
                    resp.setResponseHeaders(mapOf("access-control-allow-origin" to "*"))
                    return resp
                }
            }

            // m3u8 拦截：当前页面含 u-link=1 时，将 m3u8 URL 存入 sessionStorage 供脚本读取
            if (url.contains(".m3u8") && currentUrl != null && currentUrl!!.contains("u-link=1")) {
                val js = "sessionStorage.setItem(\"u-m3u8\",\"$url\");sessionStorage.setItem(\"u-loc\",\"$currentUrl\");"
                view.post { view.evaluateJavascript(js, null) }
            }

            // —— 无图模式：拦截非必要图片 ——
            if (accept != null && accept.startsWith("image/") && !isImageAllowed(url)) {
                return WebResourceResponse(null, null, null)
            }

            // —— tv-web 本地资源拦截（核心：JS/CSS/HTML/字体）——
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

    /**
     * 拦截 tv-web 本地资源请求（对标参考 shouldInterceptRequest 的 tv-web 分支）。
     * 按扩展名返回对应 MIME 类型的本地资源流。
     */
    private fun interceptTvWebResource(url: String, tvWebIndex: Int): WebResourceResponse? {
        // tvImg=1 图片请求
        if (url.endsWith("tvImg=1")) {
            val fileName = url.substring(tvWebIndex, url.indexOf("?"))
            val stream = MarmotLiveData.readStreamWithFallback(context, fileName) ?: return null
            return WebResourceResponse("image/jpeg", UTF8, stream)
        }
        // 去掉查询参数
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
                // 参考：html 中 base.js 替换为 basex.js（模板化版本）
                val replaced = html.replace("base.js", "basex.js")
                WebResourceResponse(
                    "text/html", UTF8,
                    ByteArrayInputStream(replaced.toByteArray(Charset.defaultCharset()))
                )
            }
            cleanUrl.endsWith(".woff2") -> {
                val stream = MarmotLiveData.readStreamWithFallback(context, fileName) ?: return null
                WebResourceResponse("font/woff2", UTF8, stream)
            }
            else -> null
        }
    }

    /** 判断图片是否允许加载（对标参考 `imageLoad`）。 */
    private fun isImageAllowed(url: String): Boolean {
        return url.contains("tvImg") || url.contains("cctvpic.com") ||
            url.contains("player.cntv.cn") || url.contains("default")
    }

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
