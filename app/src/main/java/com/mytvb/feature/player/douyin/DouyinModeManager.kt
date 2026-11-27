package com.mytvb.feature.player.douyin

import android.content.Context
import com.mytvb.core.common.content.ContentFilter
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.common.settings.AppSettingsDataStore
import com.mytvb.feature.home.FeedSourceSetting
import com.mytvb.feature.home.RecommendFeedRepository
import com.mytvb.model.video.VideoModel

class DouyinModeManager(
    private val appContext: Context,
    private val appSettings: AppSettingsDataStore,
    private val recommendFeedRepository: RecommendFeedRepository
) {

    companion object {
        private const val TAG = "DouyinQueue"
        const val KEY_DOUYIN_MODE = "douyin_mode"
        const val INITIAL_LOAD_COUNT = 10
        const val APPEND_LOAD_COUNT = 10
        const val HISTORY_WINDOW_SIZE = 5
        const val FUTURE_MIN_BUFFER_SIZE = 15
        const val MAX_APPEND_PAGES_PER_RUN = 3
    }

    private val recommendList = mutableListOf<VideoModel>()
    private var currentIndex: Int = -1
    private var sourceAid: Long = 0L
    private var initialized: Boolean = false
    private var appending: Boolean = false
    /** 首页推荐流页码（从 1 起），与「首页推荐来源」设置共用同一套翻页语义 */
    private var freshIdx: Int = 0
    private var generation: Long = 0L
    /** 建队时使用的推荐来源，用户切换设置后据此重建队列 */
    private var queueSourceIsApp: Boolean? = null

    /** 读取设置存储，判断抖音模式是否已启用 */
    fun isEnabled(): Boolean {
        return appSettings.getCachedString(KEY_DOUYIN_MODE) == "开"
    }

    /** 检查给定视频是否符合抖音模式条件 */
    fun isApplicable(video: VideoModel?): Boolean {
        if (video == null) return false
        if (!isEnabled()) return false
        return isDouyinPlayable(video)
    }

    /** 检查给定视频是否是抖音队列允许播放的普通视频 */
    fun isDouyinPlayable(video: VideoModel?): Boolean {
        if (video == null) return false
        if (video.isPgc) return false
        if (video.isLive) return false
        return video.hasPlaybackIdentity
    }

    /** 初始化推荐列表，当前视频作为起点，后续调用与首页同源的推荐流 */
    suspend fun initialize(sourceVideo: VideoModel) {
        if (!isDouyinPlayable(sourceVideo)) {
            reset()
            return
        }
        if (initialized && sourceAid == sourceVideo.aid) {
            return
        }
        reset()
        sourceAid = sourceVideo.aid
        queueSourceIsApp = currentSourceIsApp()
        freshIdx = 1
        recommendList.add(sourceVideo)
        currentIndex = 0
        initialized = true
        AppLog.i(
            TAG,
            "initialize source=${sourceVideo.queueId()} " +
                "feedSource=${if (queueSourceIsApp == true) "APP端" else "网页端"} freshIdx=$freshIdx"
        )
        appending = true
        try {
            fillFutureBuffer("initialize", INITIAL_LOAD_COUNT, MAX_APPEND_PAGES_PER_RUN)
        } finally {
            appending = false
        }
    }

    /**
     * 用户切换「首页推荐来源」后重建队列：来源变了继续用旧流会让上下滑内容
     * 与首页不一致，这里主动失效，让下一次取推荐时按新来源重建。
     */
    fun resetIfSourceChanged() {
        if (!initialized) return
        val current = currentSourceIsApp()
        if (queueSourceIsApp == current) return
        AppLog.i(
            TAG,
            "reset_source_changed from=${if (queueSourceIsApp == true) "APP端" else "网页端"} " +
                "to=${if (current) "APP端" else "网页端"}"
        )
        reset()
    }

    private fun currentSourceIsApp(): Boolean =
        FeedSourceSetting.isAppSource(FeedSourceSetting.KEY_HOME_FEED_SOURCE)

    /** 返回下一个推荐视频，到达末尾返回 null */
    fun next(): VideoModel? {
        if (!initialized || recommendList.isEmpty()) {
            return null
        }
        if (currentIndex >= recommendList.lastIndex) {
            return null
        }
        currentIndex++
        return recommendList[currentIndex]
    }

    /** 预览下一个视频（不移动索引），用于预加载 */
    fun peekNext(): VideoModel? {
        if (!initialized || recommendList.isEmpty()) return null
        val nextIndex = currentIndex + 1
        if (nextIndex > recommendList.lastIndex) return null
        return recommendList[nextIndex]
    }

    /** 预览上一个视频（不移动索引），用于手势预览 */
    fun peekPrevious(): VideoModel? {
        if (!initialized || recommendList.isEmpty()) return null
        val previousIndex = currentIndex - 1
        if (previousIndex < 0) return null
        return recommendList[previousIndex]
    }

    /** 返回上一个推荐视频，到达开头返回 null */
    fun previous(): VideoModel? {
        if (!initialized || recommendList.isEmpty()) {
            return null
        }
        if (currentIndex <= 0) {
            return null
        }
        currentIndex--
        return recommendList[currentIndex]
    }

    /** 列表是否已初始化且非空 */
    fun hasList(): Boolean = initialized && recommendList.isNotEmpty()

    /** 队列当前指向的视频 aid（0 表示无），用于识别「是不是队列自己跳过来的」。 */
    fun currentAid(): Long {
        if (!initialized) return 0L
        return recommendList.getOrNull(currentIndex)?.aid ?: 0L
    }

    /** 当前索引是否达到追加阈值 */
    fun shouldAppend(): Boolean {
        return initialized &&
            !appending &&
            futureCount() < FUTURE_MIN_BUFFER_SIZE
    }

    /** 重置所有状态 */
    fun reset() {
        generation++
        recommendList.clear()
        currentIndex = -1
        sourceAid = 0L
        initialized = false
        appending = false
        freshIdx = 0
    }

    /** 追加更多推荐视频，调用首页推荐 API 获取下一页 */
    suspend fun appendMore() {
        if (!initialized || appending) {
            return
        }
        if (!shouldAppend()) {
            return
        }
        appending = true
        try {
            fillFutureBuffer("append", APPEND_LOAD_COUNT, MAX_APPEND_PAGES_PER_RUN)
        } finally {
            appending = false
        }
    }

    private suspend fun fillFutureBuffer(reason: String, pageSize: Int, maxPages: Int) {
        val generationAtStart = generation
        var pagesFetched = 0
        var totalFetched = 0
        var totalAdded = 0

        while (
            pagesFetched < maxPages &&
            futureCount() < FUTURE_MIN_BUFFER_SIZE
        ) {
            val pageFreshIdx = freshIdx
            val more = fetchRecommend(pageFreshIdx, pageSize)
            if (generationAtStart != generation) {
                return
            }

            pagesFetched++
            freshIdx++
            totalFetched += more.size

            val toAdd = more
                .filterNot { candidate -> recommendList.any { it.isSameVideo(candidate) } }
            recommendList.addAll(toAdd)
            totalAdded += toAdd.size
            trimWindow()
        }

        AppLog.i(
            TAG,
            "fill_done reason=$reason pages=$pagesFetched fetched=$totalFetched added=$totalAdded " +
                "index=$currentIndex size=${recommendList.size} future=${futureCount()} nextFreshIdx=$freshIdx"
        )
    }

    /**
     * 取一页推荐：与首页推荐流完全同源（APP 端 / 网页端 V8，由「首页推荐来源」决定），
     * 不再自建一条无签名、无 cookie 的 V1 请求——那条路径在登录态下容易被风控拦成空列表，
     * 表现为上下滑时提示「该视频无推荐」。
     */
    private suspend fun fetchRecommend(idx: Int, count: Int): List<VideoModel> {
        return try {
            AppLog.i(
                TAG,
                "recommend_request freshIdx=$idx count=$count " +
                    "feedSource=${if (currentSourceIsApp()) "APP端" else "网页端"}"
            )
            val items = recommendFeedRepository.loadFeedPageForQueue(
                pageSize = count,
                freshIdx = idx,
                fetchRow = idx
            )
            val playableItems = items.filter { isDouyinPlayable(it) }
            val filteredItems = ContentFilter.filterVideos(appContext, playableItems)
            AppLog.i(
                TAG,
                "recommend_result freshIdx=$idx raw=${items.size} playable=${playableItems.size} " +
                    "filtered=${filteredItems.size} first=${filteredItems.take(3).joinToString("|") { it.queueId() }}"
            )
            filteredItems
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "recommend exception freshIdx=$idx count=$count message=${e.message}")
            emptyList()
        }
    }

    private fun trimWindow() {
        if (!initialized || recommendList.isEmpty()) return

        val removeBefore = (currentIndex - HISTORY_WINDOW_SIZE).coerceAtLeast(0)
        if (removeBefore > 0) {
            repeat(removeBefore) { recommendList.removeAt(0) }
            currentIndex -= removeBefore
        }

    }

    private fun futureCount(): Int {
        if (!initialized || recommendList.isEmpty()) return 0
        return (recommendList.lastIndex - currentIndex).coerceAtLeast(0)
    }

    private fun VideoModel.isSameVideo(other: VideoModel): Boolean {
        return when {
            aid > 0L && other.aid > 0L -> aid == other.aid
            bvid.isNotBlank() && other.bvid.isNotBlank() -> bvid == other.bvid
            else -> false
        }
    }

    private fun VideoModel.queueId(): String {
        return "aid=$aid,bvid=$bvid,cid=$cid,title=${title.take(16)}"
    }
}
