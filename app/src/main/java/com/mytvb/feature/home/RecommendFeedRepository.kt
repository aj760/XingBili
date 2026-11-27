package com.mytvb.feature.home

import android.content.Context
import android.os.SystemClock
import com.mytvb.MyBLBLApplication
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.common.log.HomeVideoCardDebugLogger
import com.mytvb.model.video.VideoModel
import com.mytvb.network.api.BiliApi
import com.mytvb.network.NetworkManager
import com.mytvb.repository.cache.HomeCacheStore
import com.mytvb.core.common.cache.OnDemandRequestCache
import com.mytvb.core.common.coroutine.catchingRequest
import com.mytvb.network.session.RequestSessionScope
import com.mytvb.network.session.RequestSessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecommendFeedRepository(
    context: Context
) {

    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "RecommendFeedRepository"
        private const val CACHE_KEY = "recommendCacheList"
        private const val MAX_CACHED_RECOMMEND_ITEMS = 24
        private const val PRELOAD_PAGE_SIZE = 24
    }

    private data class FirstPageKey(val session: RequestSessionSnapshot, val pageSize: Int)
    private val firstPages = OnDemandRequestCache<FirstPageKey, NetworkPage>(2, 0)
    @Volatile
    private var preloadedFirstPage: Pair<RequestSessionSnapshot, NetworkPage>? = null

    suspend fun preloadFirstPage() {
        val startMs = SystemClock.elapsedRealtime()
        AppLog.i(TAG, "STARTUP T2 preloadFirstPage start")
        catchingRequest {
            loadSharedFirstPage(pageSize = PRELOAD_PAGE_SIZE, reason = "preload")
        }.onSuccess { page ->
            AppLog.i(TAG, "STARTUP T3 preload api done items=${page.items.size} hasMore=${page.hasMore}")
            writeCache(page.items)
        }.onFailure { throwable ->
            AppLog.w(TAG, "STARTUP preloadFirstPage failed: ${throwable.message}")
        }
        AppLog.i(TAG, "STARTUP T3b preload end elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
    }

    suspend fun loadSharedFirstPage(
        pageSize: Int = PRELOAD_PAGE_SIZE,
        reason: String
    ): NetworkPage {
        ensureDataRuntimeReady("sharedFirstPage/$reason")
        val session = RequestSessionScope.capture()
        preloadedFirstPage?.takeIf { it.first == session }?.let { return it.second }
        return firstPages.getOrLoad(FirstPageKey(session, pageSize)) {
            loadNetworkPage(1, pageSize, 1, 1).getOrThrow().also { page ->
                RequestSessionScope.requireCurrent(session)
                preloadedFirstPage = session to page
            }
        }.also { RequestSessionScope.requireCurrent(session) }
    }

    private suspend fun ensureDataRuntimeReady(reason: String) {
        val app = appContext as? MyBLBLApplication ?: return
        withContext(Dispatchers.IO) {
            app.ensureDataRuntimeReady(reason)
        }
    }

    data class CachedFeed(
        val items: List<VideoModel>,
        val savedAtMs: Long,
        val schemaVersion: Int
    )

    data class NetworkPage(
        val items: List<VideoModel>,
        val hasMore: Boolean,
        val rawCount: Int,
        val requestFreshIdx: Int,
        val requestFetchRow: Int,
        val source: String
    )

    suspend fun readCachedFeed(): CachedFeed {
        val startMs = SystemClock.elapsedRealtime()
        AppLog.i(TAG, "APP_STARTUP recommend cache read start")
        val cached = HomeCacheStore.readCachedVideos(CACHE_KEY)
        AppLog.i(
            TAG,
            "APP_STARTUP recommend cache read end elapsed=${SystemClock.elapsedRealtime() - startMs}ms count=${cached.items.size} ageMs=${HomeCacheStore.cacheAgeMs(cached.savedAtMs)} schema=${cached.schemaVersion}"
        )
        return CachedFeed(
            items = cached.items.take(MAX_CACHED_RECOMMEND_ITEMS),
            savedAtMs = cached.savedAtMs,
            schemaVersion = cached.schemaVersion
        )
    }

    suspend fun loadNetworkPage(
        page: Int,
        pageSize: Int,
        freshIdx: Int,
        fetchRow: Int
    ): Result<NetworkPage> = catchingRequest {
        loadAppFeedPageIfAvailable(pageSize, freshIdx, fetchRow)
            ?: loadWebFeedPage(page, pageSize, freshIdx, fetchRow)
    }

    /**
     * 供播放页抖音队列复用：按「首页推荐来源」设置取一页推荐，返回纯列表。
     * 与首页完全同源（APP 端流失败自动回退网页端 V8），保证上下滑切换的内容
     * 和用户在首页看到的推荐流一致，而不是另走一条无签名的旧接口。
     */
    suspend fun loadFeedPageForQueue(
        pageSize: Int,
        freshIdx: Int,
        fetchRow: Int
    ): List<VideoModel> {
        val page = loadNetworkPage(
            page = 1,
            pageSize = pageSize,
            freshIdx = freshIdx,
            fetchRow = fetchRow
        ).getOrElse { throwable ->
            AppLog.w(TAG, "loadFeedPageForQueue failed freshIdx=$freshIdx: ${throwable.message}")
            return emptyList()
        }
        return page.items
    }

    /**
     * 已登录（有 app access_token）时优先走 app 端推荐流；接口失败或解析为空返回 null，
     * 由调用方回退 web 流。app 流内容按 app 账号个性化，与 web 推荐池不同源。
     */
    private suspend fun loadAppFeedPageIfAvailable(
        pageSize: Int,
        freshIdx: Int,
        fetchRow: Int
    ): NetworkPage? {
        if (!FeedSourceSetting.isAppSource(FeedSourceSetting.KEY_HOME_FEED_SOURCE)) {
            AppLog.i(TAG, "appFeed disabled by setting, use web feed")
            return null
        }
        val accessKey = NetworkManager.getAppAccessToken()
        if (accessKey.isBlank()) {
            AppLog.i(TAG, "appFeed skip: no access_token, use web feed")
            return null
        }
        // app 流 idx 从 0 计（0=刷新），web freshIdx 从 1 计
        val idx = (freshIdx - 1).coerceAtLeast(0)
        return try {
            val t0 = SystemClock.elapsedRealtime()
            val result = BiliApi.appRecommend(idx = idx, accessKey = accessKey, ps = pageSize)
            val filtered = result.items.filter { it.isSupportedHomeVideoCard }
            if (filtered.isEmpty()) {
                AppLog.w(TAG, "appFeed empty raw=${result.rawCount}, fallback web")
                null
            } else {
                AppLog.i(
                    TAG,
                    "appFeed ok idx=$idx raw=${result.rawCount} filtered=${filtered.size} elapsed=${SystemClock.elapsedRealtime() - t0}ms"
                )
                NetworkPage(
                    items = filtered,
                    hasMore = true,
                    rawCount = result.rawCount,
                    requestFreshIdx = freshIdx,
                    requestFetchRow = fetchRow,
                    source = result.source
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            AppLog.w(TAG, "appFeed failed: ${throwable.message}, fallback web")
            null
        }
    }

    /**
     * 首页刷新成功后同步内存预加载缓存。切 tab 回来会重建 Fragment 并走
     * loadSharedFirstPage 优先吃 preloadedFirstPage；若不更新，用户刚刷出来的
     * 新内容会被启动时的旧首页顶掉（重新登录后复现：刷新→切走→切回变旧数据）。
     */
    fun updatePreloadedFirstPage(page: NetworkPage) {
        if (page.items.isEmpty()) return
        AppLog.i(TAG, "updatePreloadedFirstPage items=${page.items.size} source=${page.source}")
        preloadedFirstPage = RequestSessionScope.capture() to page
    }

    private suspend fun loadWebFeedPage(
        page: Int,
        pageSize: Int,
        freshIdx: Int,
        fetchRow: Int
    ): NetworkPage {
        val t0 = SystemClock.elapsedRealtime()
        val source = "recommend_v8(page=$page,freshIdx=$freshIdx,fetchRow=$fetchRow,ps=$pageSize)"
        val recommendResult = catchingRequest {
            BiliApi.recommendV8(freshIdx = freshIdx, ps = pageSize, fetchRow = fetchRow)
        }.getOrElse { throwable ->
            AppLog.w(TAG, "$source failed: ${throwable.message}; fallback V1")
            val fallbackItems = BiliApi.recommend(freshIdx, pageSize)
            BiliApi.RecommendPageResult(
                items = fallbackItems,
                rawCount = fallbackItems.size,
                source = "V1_FALLBACK"
            )
        }.let { result ->
            if (result.items.isNotEmpty()) {
                result
            } else {
                AppLog.w(TAG, "$source empty; fallback V1")
                val fallbackItems = BiliApi.recommend(freshIdx, pageSize)
                BiliApi.RecommendPageResult(
                    items = fallbackItems,
                    rawCount = fallbackItems.size,
                    source = "V1_FALLBACK_EMPTY"
                )
            }
        }
        val rawItems = recommendResult.items
        val t1 = SystemClock.elapsedRealtime()
        AppLog.i(TAG, "$source apiSource=${recommendResult.source} raw=${recommendResult.rawCount} parsed=${rawItems.size} api=${t1 - t0}ms")
        HomeVideoCardDebugLogger.logRejectedCards(
            source = "$source apiSource=${recommendResult.source}",
            items = rawItems
        )
        val filtered = rawItems.filter { it.isSupportedHomeVideoCard }
        val t2 = SystemClock.elapsedRealtime()
        AppLog.i(TAG, "$source filter=${t2 - t1}ms api=${t1 - t0}ms raw=${recommendResult.rawCount} parsed=${rawItems.size}->${filtered.size}")
        return NetworkPage(
            items = filtered,
            hasMore = recommendResult.rawCount >= pageSize,
            rawCount = recommendResult.rawCount,
            requestFreshIdx = freshIdx,
            requestFetchRow = fetchRow,
            source = recommendResult.source
        )
    }

    suspend fun writeCache(items: List<VideoModel>) {
        HomeCacheStore.writeVideos(
            cacheKey = CACHE_KEY,
            videos = items.take(MAX_CACHED_RECOMMEND_ITEMS)
        )
    }

    fun trimCacheItems(items: List<VideoModel>): List<VideoModel> {
        return items.take(MAX_CACHED_RECOMMEND_ITEMS)
    }

}
