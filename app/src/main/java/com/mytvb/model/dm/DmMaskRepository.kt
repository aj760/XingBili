package com.mytvb.model.dm

import android.util.LruCache
import com.mytvb.core.common.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * webmask 下载 + 缓存。**采用 HTTP Range 流式加载策略**：
 *
 * 1. [downloadAndParse]：只下载文件头（16B）+ 段索引表（~2KB），约 200~500ms 完成。
 *    返回的 [DmMaskData] 已经包含所有段的时间/位置信息，但**段数据未下载**。
 * 2. [preloadSegmentFrames]：被 [com.mytvb.feature.player.view.DmMaskController]
 *    在播放进度触达时按需调用——内部先 Range 下载该段字节（~100KB，几百 ms），
 *    然后解析帧、释放字节数据（避免 N 段 × 100KB 内存堆积）。
 *
 * 收益：mask 首次可见时间从「整文件 9.8MB / 380KB/s ≈ 26 秒」降到「header + 首段 ≈ 1-2 秒」。
 * B 站官方 web 端就是这套做法（先 GET range 16 字节探测，再分批拉段数据）。
 */
class DmMaskRepository {

    companion object {
        private const val TAG = "DmMaskRepository"
        private const val MAX_CACHE_SIZE = 3
        private const val UA = "Mozilla/5.0 (Android) MyBLBL/1.0"
        private const val REFERER = "https://www.bilibili.com"
        private val sharedCache = LruCache<Long, DmMaskData>(MAX_CACHE_SIZE)
        private val sharedTimelineCache = LruCache<Long, DmMaskTimeline>(MAX_CACHE_SIZE)
        private val sharedUrlCache = ConcurrentHashMap<Long, String>()
        private val sharedSegmentParseLocks = ConcurrentHashMap<String, Any>()
        private val sharedHeaderLoadLocks = ConcurrentHashMap<Long, Any>()
        private val activeRangeConnections = ConcurrentHashMap<HttpURLConnection, Any>()
        private val cancelledRequestOwners = ConcurrentHashMap<Any, Boolean>()
    }

    private val cache = sharedCache
    private val timelineCache = sharedTimelineCache
    private val segmentParseLocks = sharedSegmentParseLocks
    private val headerLoadLocks = sharedHeaderLoadLocks

    /** cid → 完整 mask 资源 URL（用于按需 Range 下载段数据）。 */
    private val urlCache = sharedUrlCache

    /**
     * 加载 mask 头部 + 段索引表，立即返回可用的 [DmMaskData]（段数据未下载）。
     * 总耗时通常 200~500ms（两个小 Range 请求）。
     */
    suspend fun downloadAndParse(maskUrl: String, cid: Long, fps: Int, requestOwner: Any? = null): DmMaskData? {
        cache.get(cid)?.let { return it }
        return withContext(Dispatchers.IO) {
            val lock = headerLoadLocks.getOrPut(cid) { Any() }
            synchronized(lock) {
                cache.get(cid)?.let { return@withContext it }
                downloadAndParseLocked(maskUrl, cid, fps, requestOwner)
            }
        }
    }

    private fun downloadAndParseLocked(maskUrl: String, cid: Long, fps: Int, requestOwner: Any?): DmMaskData? {
        val startNs = System.nanoTime()
        try {
            val url = if (maskUrl.startsWith("//")) "https:$maskUrl" else maskUrl
            urlCache[cid] = url

            // 1. 拉前 16 字节 header → 解析 segmentCount
            val headerBytes = rangeFetch(url, 0L, (WebmaskParser.HEADER_SIZE - 1).toLong(), requestOwner)
                ?: return null
            val segCount = WebmaskParser.parseSegmentCount(headerBytes)
            if (segCount <= 0) return null

            // 2. 拉段索引表 → 解析所有 LazyMaskSegment
            val metaStart = WebmaskParser.HEADER_SIZE.toLong()
            val metaEnd = metaStart + segCount.toLong() * WebmaskParser.META_ENTRY_SIZE - 1L
            val (metaBytes, totalSize) = rangeFetchWithTotal(url, metaStart, metaEnd, requestOwner)
                ?: return null

            val maskData = WebmaskParser.parseSegmentMeta(metaBytes, segCount, totalSize, fps)
                ?: return null

            // 3. 构建 timeline（首段不预解析——交给 Controller 触发 preloadSegmentFrames）
            DmMaskTimeline.build(maskData)?.let { timelineCache.put(cid, it) }
            cache.put(cid, maskData)

            val totalMs = (System.nanoTime() - startNs) / 1_000_000L
            AppLog.d(TAG, "Webmask header ready: cid=$cid segs=$segCount fps=$fps " +
                "fileSize=${totalSize / 1024}KB hdr=${totalMs}ms")
            return maskData
        } catch (e: Exception) {
            AppLog.e(TAG, "Download webmask error: ${e.message}")
            return null
        } finally {
            headerLoadLocks.remove(cid)
        }
    }

    fun hasMask(cid: Long): Boolean = cache.get(cid) != null && timelineCache.get(cid) != null

    fun getTimeline(cid: Long): DmMaskTimeline? = timelineCache.get(cid)

    /**
     * 同步：确保指定段的字节数据已下载到 [LazyMaskSegment.segData]。
     * 调用方负责在 IO 线程触发（preloadSegmentFrames 已经在后台线程）。
     */
    private fun ensureSegmentDataLoaded(
        cid: Long,
        segment: LazyMaskSegment,
        segIndex: Int,
        requestOwner: Any?,
    ): Boolean {
        if (segment.segData != null) return true
        val url = urlCache[cid] ?: run {
            AppLog.e(TAG, "No URL cached for cid=$cid (segment $segIndex)")
            return false
        }
        return try {
            val startNs = System.nanoTime()
            val bytes = rangeFetch(url, segment.byteOffset, segment.byteEnd - 1L, requestOwner)
            if (bytes != null) {
                segment.segData = bytes
                val ms = (System.nanoTime() - startNs) / 1_000_000L
                if (ms > 1000L) {
                    // 单段超过 1s 才报警（避免噪声）
                    AppLog.d(TAG, "Segment $segIndex dl=${ms}ms size=${bytes.size / 1024}KB (slow)")
                }
                true
            } else false
        } catch (e: Exception) {
            AppLog.e(TAG, "Download segment $segIndex failed: ${e.message}")
            false
        }
    }

    /**
     * 后台预解析指定段：自动下载段字节 → 解析帧 → 释放字节数据。
     * 必须在 IO 线程调用。已用 lock 去重，多次同 segIdx 调用安全。
     */
    fun preloadSegmentFrames(cid: Long, segIndex: Int, requestOwner: Any? = null) {
        val maskData = cache.get(cid) ?: return
        val segments = maskData.rawSegments
        if (segIndex < 0 || segIndex >= segments.size) return
        val segment = segments[segIndex]
        if (segment.cachedFrames != null) return

        val lockKey = "$cid:$segIndex"
        val lock = segmentParseLocks.getOrPut(lockKey) { Any() }
        try {
            synchronized(lock) {
                if (segment.cachedFrames != null) return
                val startedNs = System.nanoTime()
                // 1. 确保字节数据
                if (!ensureSegmentDataLoaded(cid, segment, segIndex, requestOwner)) {
                    AppLog.w(TAG, "Webmask segment preload failed: cid=$cid seg=$segIndex noData")
                    return
                }
                // 2. 解析帧
                val segDurationMs = if (segIndex + 1 < segments.size) {
                    (segments[segIndex + 1].timeMs - segment.timeMs).coerceAtLeast(1)
                } else {
                    (300L * 1000L / maskData.fps.coerceAtLeast(1)).coerceAtLeast(1)
                }
                val frames = WebmaskParser.parseSegmentFrames(segment, maskData.fps, segDurationMs) ?: emptyList()
                segment.cachedFrames = frames
                val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
                val firstPts = frames.firstOrNull()?.presentationTimeMs
                val lastPts = frames.lastOrNull()?.presentationTimeMs
                AppLog.d(
                    TAG,
                    "Webmask segment cached: cid=$cid seg=$segIndex frames=${frames.size} " +
                        "segTime=${segment.timeMs} firstPts=$firstPts lastPts=$lastPts cost=${elapsedMs}ms"
                )
                // 3. 释放字节数据（节省内存：每段 ~100KB × 124 段 = ~12MB）
                segment.segData = null
            }
        } finally {
            segmentParseLocks.remove(lockKey, lock)
        }
    }

    fun clear(cid: Long) {
        cache.remove(cid)
        timelineCache.remove(cid)
        urlCache.remove(cid)
        segmentParseLocks.keys.removeAll { it.startsWith("$cid:") }
    }

    fun clearAll() {
        cache.evictAll()
        timelineCache.evictAll()
        urlCache.clear()
        segmentParseLocks.clear()
    }

    fun cancelRequests(requestOwner: Any) {
        cancelledRequestOwners[requestOwner] = true
        activeRangeConnections.entries.forEach { entry ->
            if (entry.value === requestOwner && activeRangeConnections.remove(entry.key, entry.value)) {
                runCatching { entry.key.disconnect() }
            }
        }
    }

    fun finishRequests(requestOwner: Any) {
        cancelledRequestOwners.remove(requestOwner)
    }

    // ---- HTTP Range 下载工具 ----

    /** Range 拉取 [start, end] 区间字节（闭区间，符合 HTTP 标准）。 */
    private fun rangeFetch(url: String, start: Long, end: Long, requestOwner: Any? = null): ByteArray? {
        return rangeFetchWithTotal(url, start, end, requestOwner)?.first
    }

    /**
     * Range 拉取 + 从响应头提取文件总大小。
     * 200 OK 和 206 Partial Content 都接受（部分 CDN 对 0-N 整文件请求返回 200）。
     */
    private fun rangeFetchWithTotal(
        url: String,
        start: Long,
        end: Long,
        requestOwner: Any? = null,
    ): Pair<ByteArray, Long>? {
        if (requestOwner != null && cancelledRequestOwners.containsKey(requestOwner)) return null
        val conn = openRangeConn(url, start, end)
        if (requestOwner != null) {
            activeRangeConnections[conn] = requestOwner
            if (cancelledRequestOwners.containsKey(requestOwner)) {
                activeRangeConnections.remove(conn)
                conn.disconnect()
                return null
            }
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                AppLog.e(TAG, "Range fetch failed: code=$code range=$start-$end")
                return null
            }
            val bytes = conn.inputStream.buffered(16 * 1024).use { it.readBytes() }
            if (bytes.isEmpty()) {
                AppLog.e(TAG, "Empty body for range $start-$end")
                return null
            }
            val total = parseTotalFromContentRange(conn.getHeaderField("Content-Range"))
                ?: conn.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }?.plus(start)
                ?: bytes.size.toLong()
            return Pair(bytes, total)
        } finally {
            activeRangeConnections.remove(conn)
            conn.disconnect()
        }
    }

    private fun openRangeConn(url: String, start: Long, end: Long): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Range", "bytes=$start-$end")
        conn.setRequestProperty("User-Agent", UA)
        // mask 段内部已是 gzip，HTTP 层再 gzip 反而浪费 CPU。
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.setRequestProperty("Referer", REFERER)
        return conn
    }

    /** 从 `Content-Range: bytes 0-15/9779322` 解出 9779322。 */
    private fun parseTotalFromContentRange(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val idx = s.lastIndexOf('/')
        if (idx < 0 || idx == s.length - 1) return null
        return s.substring(idx + 1).toLongOrNull()
    }
}
