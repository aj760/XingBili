package com.mytvb.feature.player.sponsor

import android.content.Context
import com.mytvb.R
import com.mytvb.core.common.json.GsonHolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mytvb.core.common.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SponsorBlockRepository {

    private const val BASE_URL = "https://bsbsb.top/api"
    /**
     * 备用入口（与 cat3399/blbl 同款，IP 直连、国内可访问）。
     * 主域名 bsbsb.top 在国内被墙/解析失败时回退到这里，避免必须挂梯子才能用空降助手。
     */
    private const val FALLBACK_BASE_URL = "http://154.222.28.109/api"
    private const val TAG = "SponsorBlock"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    // 无 context 时的中文回退（兼容未传 context 的旧调用方）
    private const val CONNECTION_ERROR_FALLBACK = "空降助手连接失败，请检查网络"

    private val gson = GsonHolder.DEFAULT

    private fun connectionError(context: Context?): String =
        context?.getString(R.string.player_sponsor_error_connection) ?: CONNECTION_ERROR_FALLBACK

    private data class CacheEntry(
        val segments: List<SponsorSegment>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    data class HashVideoResponse(
        val videoID: String = "",
        val segments: List<SponsorSegment> = emptyList()
    )

    /**
     * 探测单域名 /status：null=成功，-1=网络不可达（可回退），-2=其它异常，其余为 HTTP 状态码。
     */
    private fun probeStatusOnce(baseUrl: String): Int? = try {
        val request = Request.Builder()
            .url("$baseUrl/status")
            .header("User-Agent", "MyBLBL/1.0")
            .header("origin", "com.mytvb")
            .header("x-ext-version", "0.13.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 200) null else response.code
        }
    } catch (e: IOException) {
        AppLog.e(TAG, "连通性测试失败: ${e.message} base=$baseUrl")
        -1
    } catch (e: Exception) {
        AppLog.e(TAG, "连通性测试异常: ${e.message}", e)
        -2
    }

    suspend fun testConnection(context: Context? = null): String? = withContext(Dispatchers.IO) {
        val primary = probeStatusOnce(BASE_URL)
        val probe = if (primary == -1 && BASE_URL != FALLBACK_BASE_URL) {
            AppLog.w(TAG, "连通性测试主域名不可达，回退备用入口 ${FALLBACK_BASE_URL}")
            probeStatusOnce(FALLBACK_BASE_URL)
        } else {
            primary
        }
        when (probe) {
            null -> null
            -1 -> connectionError(context)
            -2 -> context?.getString(R.string.player_sponsor_test_failed) ?: "空降助手测试失败"
            else -> context?.getString(R.string.player_sponsor_service_error_format, probe)
                ?: "空降助手服务异常 (${probe})"
        }
    }

    /** 单域名拉取探测；返回 (展示文案, 是否网络不可达可回退)。 */
    private fun probeFetchOnce(baseUrl: String, context: Context?): Pair<String, Boolean> {
        return try {
            val url = "$baseUrl/skipSegments/a1b2?category=sponsor"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyBLBL/1.0")
                .header("origin", "com.mytvb")
                .header("x-ext-version", "0.13.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return (
                        context?.getString(R.string.player_sponsor_test_api_error_format, response.code)
                            ?: "测试失败：API 返回 ${response.code}"
                        ) to false
                }

                val body = response.body?.string()
                if (body == null) {
                    return (
                        context?.getString(R.string.player_sponsor_test_empty_response)
                            ?: "测试失败：响应为空"
                        ) to false
                }
                val type = object : TypeToken<List<HashVideoResponse>>() {}.type
                val videos = gson.fromJson<List<HashVideoResponse>>(body, type)
                val totalSegments = videos.sumOf { it.segments.size }
                val message = if (totalSegments > 0) {
                    context?.getString(R.string.player_sponsor_test_pass_segments_format, totalSegments)
                        ?: "测试通过：解析正常，获取到 $totalSegments 个片段"
                } else {
                    context?.getString(R.string.player_sponsor_test_pass_no_data)
                        ?: "测试通过：连接和解析正常（当前无测试数据）"
                }
                message to false
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "片段拉取测试网络失败: ${e.message} base=$baseUrl")
            val message = context?.getString(R.string.player_sponsor_test_error_message_format, e.message ?: "")
                ?: "测试失败：${e.message}"
            message to true
        } catch (e: Exception) {
            AppLog.e(TAG, "片段拉取测试失败: ${e.message}", e)
            val message = context?.getString(R.string.player_sponsor_test_error_message_format, e.message ?: "")
                ?: "测试失败：${e.message}"
            message to false
        }
    }

    suspend fun testFetch(context: Context? = null): String = withContext(Dispatchers.IO) {
        val (primaryMessage, networkFailure) = probeFetchOnce(BASE_URL, context)
        if (networkFailure && BASE_URL != FALLBACK_BASE_URL) {
            AppLog.w(TAG, "片段拉取测试主域名不可达，回退备用入口 ${FALLBACK_BASE_URL}")
            return@withContext probeFetchOnce(FALLBACK_BASE_URL, context).first
        }
        primaryMessage
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class SegmentResult(
        val segments: List<SponsorSegment> = emptyList(),
        val error: String? = null
    )

    /** 单次请求结果。[networkFailure] 为真表示可回退备用域名重试；[cacheable] 为真才写缓存。 */
    private data class FetchAttempt(
        val result: SegmentResult,
        val networkFailure: Boolean = false,
        val cacheable: Boolean = false
    )

    suspend fun getSegments(
        bvid: String,
        cid: Long = 0L,
        categories: List<String> = SponsorSegment.ALL_CATEGORIES
    ): SegmentResult = withContext(Dispatchers.IO) {
        val cacheKey = "$bvid:$cid"
        cache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                return@withContext SegmentResult(entry.segments)
            }
            cache.remove(cacheKey)
        }

        val primary = requestSegmentsOnce(BASE_URL, bvid, cid, categories)
        val attempt = if (primary.networkFailure && BASE_URL != FALLBACK_BASE_URL) {
            AppLog.w(TAG, "$bvid: 主域名不可达，回退备用入口 ${FALLBACK_BASE_URL}")
            requestSegmentsOnce(FALLBACK_BASE_URL, bvid, cid, categories)
        } else {
            primary
        }
        if (attempt.cacheable) {
            cache[cacheKey] = CacheEntry(attempt.result.segments)
        }
        attempt.result
    }

    private fun requestSegmentsOnce(
        baseUrl: String,
        bvid: String,
        cid: Long,
        categories: List<String>
    ): FetchAttempt {
        return try {
            val hashPrefix = sha256Prefix(bvid)
            val params = buildList {
                categories.forEach { add("category=$it") }
            }
            val url = "$baseUrl/skipSegments/$hashPrefix?${params.joinToString("&")}"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MyBLBL/1.0")
                .header("origin", "com.mytvb")
                .header("x-ext-version", "0.13.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: return FetchAttempt(SegmentResult())
                        val type = object : TypeToken<List<HashVideoResponse>>() {}.type
                        val videos = gson.fromJson<List<HashVideoResponse>>(body, type)
                        val allSegments = selectSegments(videos, bvid, cid)
                        val segments = normalizeSegments(allSegments)
                        FetchAttempt(SegmentResult(segments), cacheable = true)
                    }
                    404 -> FetchAttempt(SegmentResult())
                    else -> {
                        AppLog.w(TAG, "$bvid: API error ${response.code} base=$baseUrl")
                        FetchAttempt(SegmentResult(error = "空降助手服务异常 (${response.code})"))
                    }
                }
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "$bvid: ${e.message} base=$baseUrl")
            FetchAttempt(SegmentResult(error = CONNECTION_ERROR_FALLBACK), networkFailure = true)
        } catch (e: Exception) {
            AppLog.w(TAG, "$bvid: ${e.message}", e)
            FetchAttempt(SegmentResult(error = "空降助手加载失败"))
        }
    }

    private fun sha256Prefix(input: String, prefixLength: Int = 4): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(prefixLength)
    }

    internal fun selectSegments(
        videos: List<HashVideoResponse>,
        bvid: String,
        cid: Long
    ): List<SponsorSegment> {
        return videos
            .find { it.videoID == bvid }
            ?.segments
            ?.filter { segment -> cid <= 0L || segment.cid == cid.toString() }
            ?: emptyList()
    }

    internal fun normalizeSegments(
        segments: List<SponsorSegment>
    ): List<SponsorSegment> {
        return segments
            .filter { (it.isSkipType || it.isMuteType) && it.endTimeMs > it.startTimeMs }
            .groupBy { it.UUID.ifBlank { "${it.category}:${it.startTimeMs}:${it.endTimeMs}" } }
            .mapNotNull { (_, duplicates) ->
                // 同组重叠片段按 锁定 > 票数 > 起点更早 > 片段更短 取一条代表
                duplicates.maxWithOrNull(
                    compareBy<SponsorSegment> { it.locked }
                        .thenBy { it.votes }
                        .thenBy { -it.startTimeMs }
                        .thenBy { -(it.endTimeMs - it.startTimeMs) }
                )
            }
            .sortedBy { it.startTimeMs }
    }
}
