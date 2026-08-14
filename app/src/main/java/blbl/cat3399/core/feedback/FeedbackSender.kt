package blbl.cat3399.core.feedback

import android.os.Build
import blbl.cat3399.BuildConfig
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 应用内反馈发送到自托管 Cloudflare Pages Functions（blbl-update.pages.dev）。
 * 反馈存入 KV，开发者可用 GET /admin?key=<密令> 拉取（HTML 表格 / ?fmt=json）。
 */
object FeedbackSender {
    const val FEEDBACK_URL = "https://blbl-update.pages.dev/feedback"

    data class Result(val success: Boolean, val message: String)

    suspend fun send(text: String): Result = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("appVersion", BuildConfig.VERSION_NAME)
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("sdk", Build.VERSION.SDK_INT)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(FEEDBACK_URL)
                .post(body)
                .build()
            val response = BiliClient.apiOkHttp.newCall(request).execute()
            val code = response.code
            response.body?.close()
            if (response.isSuccessful) {
                Result(true, "已提交，谢谢你的反馈！")
            } else {
                Result(false, "提交失败（HTTP $code）")
            }
        } catch (e: Exception) {
            AppLog.w("FeedbackSender", "send failed", e)
            Result(false, "提交失败：${e.message}")
        }
    }
}
