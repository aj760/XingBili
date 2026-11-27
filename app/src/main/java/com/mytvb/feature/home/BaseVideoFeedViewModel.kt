package com.mytvb.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytvb.core.common.content.ContentFilter
import com.mytvb.core.common.log.AppLog
import com.mytvb.model.video.VideoModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * 视频 Feed 分页骨架：初始加载/刷新/追加、状态流转、本地内容过滤与缓存回写。
 * 子类只负责 [fetchPage]（含各自的去重/fetchRow/首页共享逻辑）与缓存参数。
 */
abstract class BaseVideoFeedViewModel(
    context: Context,
    private val dislikeFeedback: RecommendDislikeFeedback
) : ViewModel(), VideoFeedViewModel {

    companion object {
        private const val TAG = "VideoFeedVM"
    }

    protected val appContext = context.applicationContext
    protected val _uiState = MutableStateFlow(FeedUiState<VideoModel>())
    override val uiState: StateFlow<FeedUiState<VideoModel>> = _uiState.asStateFlow()

    protected var currentPage = 0
    private var initialLoadRequested = false

    override val hasLoadedInitial: Boolean
        get() = initialLoadRequested

    /** 子类可覆盖初始加载的启动方式（如推荐流需要 UNDISPATCHED 抢首帧）。 */
    protected open val initialLaunchStart: CoroutineStart = CoroutineStart.DEFAULT

    protected data class FetchedPage(
        val items: List<VideoModel>,
        val hasMore: Boolean
    )

    protected abstract suspend fun fetchPage(
        page: Int,
        replace: Boolean,
        fromInitial: Boolean,
        fromRefresh: Boolean
    ): Result<FetchedPage>

    protected abstract fun errorMessage(throwable: Throwable): String

    protected abstract suspend fun writeCache(items: List<VideoModel>)

    protected open fun onInitialLaunched() {}

    protected open fun onPageFailed(page: Int, throwable: Throwable) {}

    final override fun loadInitial() {
        if (initialLoadRequested) return
        initialLoadRequested = true
        onInitialLaunched()
        viewModelScope.launch(start = initialLaunchStart) {
            loadPage(page = 1, replace = true, fromInitial = true)
        }
    }

    final override fun refresh() {
        viewModelScope.launch {
            loadPage(page = 1, replace = true, fromRefresh = true)
        }
    }

    final override fun loadMore() {
        val state = _uiState.value
        if (state.loadingInitial || state.refreshing || state.appending || !state.hasMore) {
            return
        }
        val nextPage = (currentPage + 1).coerceAtLeast(1)
        viewModelScope.launch {
            loadPage(page = nextPage, replace = false)
        }
    }

    final override fun consumeListChange() {
        val state = _uiState.value
        if (state.listChange != FeedListChange.NONE) {
            _uiState.value = state.copy(listChange = FeedListChange.NONE)
        }
    }

    protected suspend fun loadPage(
        page: Int,
        replace: Boolean,
        fromInitial: Boolean = false,
        fromRefresh: Boolean = false
    ) {
        val current = _uiState.value
        _uiState.value = current.copy(
            loadingInitial = fromInitial,
            refreshing = fromRefresh,
            appending = !replace,
            errorMessage = null,
            listChange = FeedListChange.NONE
        )

        // 登录瞬间推荐刷新"永久转圈"排查：finally 必然执行（含协程在挂起点被取消），
        // active=false 即协程被取消——onSuccess/onFailure 都不跑，refreshing 状态残留
        try {
            fetchPage(page, replace, fromInitial, fromRefresh)
                .onSuccess { result ->
                    val mergedItems = if (replace) {
                        result.items
                    } else {
                        current.items + result.items
                    }
                    currentPage = page
                    _uiState.value = FeedUiState(
                        items = mergedItems,
                        source = FeedSource.NETWORK,
                        listChange = if (replace) FeedListChange.REPLACE else FeedListChange.APPEND,
                        hasMore = result.hasMore
                    )
                    if (mergedItems.isNotEmpty()) {
                        writeCache(mergedItems)
                    }
                }.onFailure { throwable ->
                    _uiState.value = current.copy(
                        loadingInitial = false,
                        refreshing = false,
                        appending = false,
                        errorMessage = errorMessage(throwable),
                        listChange = FeedListChange.NONE
                    )
                    onPageFailed(page, throwable)
                }
        } finally {
            AppLog.i(
                TAG,
                "loadPage finally page=$page replace=$replace refresh=$fromRefresh active=${coroutineContext.isActive} items=${_uiState.value.items.size}"
            )
        }
    }

    protected suspend fun filterForDisplay(items: List<VideoModel>): List<VideoModel> {
        return withContext(Dispatchers.Default) {
            val kept = ContentFilter.filterVideos(appContext, items)
            // 闭环：被本地过滤掉的视频异步反馈 dislike，让 B 站算法减少同类推送
            if (kept.size < items.size) {
                val blockedKeys = kept.asSequence()
                    .mapNotNull { it.bvid.takeIf(String::isNotBlank) }
                    .toMutableSet()
                val blocked = items.filter { v -> v.bvid.isBlank() || v.bvid !in blockedKeys }
                if (blocked.isNotEmpty()) dislikeFeedback.feedbackBlocked(blocked)
            }
            kept
        }
    }
}
