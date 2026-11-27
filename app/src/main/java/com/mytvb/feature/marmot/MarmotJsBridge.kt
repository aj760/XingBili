package com.mytvb.feature.marmot

import com.mytvb.core.common.json.GsonHolder
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mytvb.feature.marmot.domain.HzItem
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.mytvb.core.ui.base.AppToast

/**
 * JS ↔ 原生桥梁（注册名 `_api`，对标参考 utao `LiveActivity.JsInterface`）。
 *
 * 页面 JS 通过 `window._api.xxx()` 调用原生能力：
 * - [message]：接收页面事件（videoQuality 画质数据 / js 执行脚本 / key 模拟按键 / history 收藏历史）
 * - [getJson]/[postJson]/[getHtml]：非 http URL 读本地 tv-web 资源；http URL 走 OkHttp（绕过跨域）
 * - [toast]：原生 Toast 提示
 *
 * @param context Activity 上下文
 * @param callbacks 回调到 Activity 的事件处理（画质更新、JS 执行、按键模拟）
 */
class MarmotJsBridge(
    private val context: Context,
    private val callbacks: Callbacks
) {
    companion object {
        private const val TAG = "MarmotJsBridge"
        private const val JS_BRIDGE_NAME = "_api"
    }

    interface Callbacks {
        /** 页面上报画质数据（JSON 字符串，解析为 [HzItem] 列表）。 */
        fun onVideoQuality(rawData: String)
        /** 页面请求执行 JS（在主线程 evaluateJavascript）。 */
        fun onEvalJs(js: String)
        /** 页面请求模拟按键（keyCode 数字字符串）。 */
        fun onSimulateKey(keyCodeStr: String)
    }

    private val gson = GsonHolder.DEFAULT
    /** 独立 client：读 tv-web 本地资源无需网络；http 请求不注入 B 站 header。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** JS 桥注册名。 */
    val bridgeName: String get() = JS_BRIDGE_NAME

    @JavascriptInterface
    fun toast(message: String) {
        Log.i(TAG, "toast: $message")
        mainHandler.post { AppToast.show(context, message) }
    }

    /**
     * 页面事件消息（对标参考 `JsInterface.message`）。
     * @param service 事件类型：videoQuality / js / key / keyNum / history.save / history.update / menuShow
     * @param data 事件数据
     */
    @JavascriptInterface
    fun message(service: String, data: String) {
        Log.i(TAG, "message: service=$service data=$data")
        when (service) {
            "videoQuality" -> callbacks.onVideoQuality(data)
            "js" -> callbacks.onEvalJs(data)
            "key" -> callbacks.onSimulateKey(data)
            "keyNum" -> callbacks.onSimulateKey(data)
            "menuShow" -> { /* 页面菜单显隐通知，暂不处理 */ }
            "history.save", "history.update" -> { /* 历史记录，暂不处理 */ }
            else -> Log.d(TAG, "未处理的 service: $service")
        }
    }

    /**
     * GET 请求或读本地资源（对标参考 `JsInterface.getJson`）。
     * @param url http URL 走网络；否则读 filesDir/tv-web/<url> 或 assets/tv-web/<url>
     * @param header JSON 格式请求头（可为空字符串）
     */
    @JavascriptInterface
    fun getJson(url: String, header: String): String {
        if (!url.startsWith("http")) {
            val path = normalizeTvWebPath(url)
            return MarmotLiveData.readFileWithFallback(context, path)
        }
        return try {
            val req = Request.Builder().url(url).apply { applyHeaders(header) }.get().build()
            client.newCall(req).execute().body?.string() ?: ""
        } catch (t: Throwable) {
            Log.w(TAG, "getJson 失败: $url - ${t.message}")
            ""
        }
    }

    /**
     * POST 请求（对标参考 `JsInterface.postJson`）。
     */
    @JavascriptInterface
    fun postJson(url: String, header: String, body: String): String {
        if (!url.startsWith("http")) {
            val path = normalizeTvWebPath(url)
            return MarmotLiveData.readFileWithFallback(context, path)
        }
        return try {
            val req = Request.Builder().url(url).apply { applyHeaders(header) }
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().body?.string() ?: ""
        } catch (t: Throwable) {
            Log.w(TAG, "postJson 失败: $url - ${t.message}")
            ""
        }
    }

    /**
     * GET HTML（对标参考 `JsInterface.getHtml`，用于抓取页面内容）。
     */
    @JavascriptInterface
    fun getHtml(url: String, header: String): String = getJson(url, header)

    /** 将页面请求的路径归一化为 tv-web/ 前缀（对标参考 `LiveActivity.l()`）。 */
    private fun normalizeTvWebPath(path: String): String {
        var p = path.trim()
        if (p.startsWith("/")) p = p.substring(1)
        return if (p.startsWith("tv-web/")) p else "tv-web/$p"
    }

    /** 应用 JSON 格式请求头到 Request.Builder。 */
    private fun Request.Builder.applyHeaders(headerJson: String) {
        if (headerJson.isBlank()) return
        try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val headers: Map<String, String> = gson.fromJson(headerJson, type) ?: return
            headers.forEach { (k, v) -> header(k, v) }
        } catch (t: Throwable) {
            Log.w(TAG, "解析 header 失败: ${t.message}")
        }
    }
}
