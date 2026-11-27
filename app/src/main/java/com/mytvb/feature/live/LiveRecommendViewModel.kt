package com.mytvb.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytvb.model.live.LiveListWrapper
import com.mytvb.repository.LiveRepository
import com.mytvb.core.common.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveRecommendViewModel(
    private val liveRepository: LiveRepository
) : ViewModel() {
    private var lastLoadedAt = 0L

    enum class LiveRecommendStatus {
        Idle,
        Content,
        Empty,
        Error
    }

    private val _recommendData = MutableStateFlow<LiveListWrapper?>(null)
    val recommendData: StateFlow<LiveListWrapper?> = _recommendData.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _status = MutableStateFlow(LiveRecommendStatus.Idle)
    val status: StateFlow<LiveRecommendStatus> = _status.asStateFlow()

    fun loadData(forceRefresh: Boolean = false) {
        if (_loading.value && !forceRefresh) {
            AppLog.d("LivePerf", "LiveRecommendVM.loadData: 已在加载中, 跳过")
            return
        }

        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _status.value = LiveRecommendStatus.Idle

            val t0 = System.currentTimeMillis()
            AppLog.d("LivePerf", "LiveRecommendVM.loadData: 开始请求推荐数据, forceRefresh=$forceRefresh")

            liveRepository.getLiveRecommend(forceRefresh = forceRefresh).fold(
                onSuccess = { data ->
                    val elapsed = System.currentTimeMillis() - t0
                    AppLog.d("LivePerf", "LiveRecommendVM.loadData: 推荐数据返回, 耗时=${elapsed}ms, " +
                        "recommendRoomCount=${data.recommendRoomList?.size ?: 0}, " +
                        "sectionCount=${data.roomList?.size ?: 0}")
                    _recommendData.value = data
                    lastLoadedAt = System.currentTimeMillis()
                    val hasContent = hasContent(data)
                    _status.value = if (hasContent) {
                        LiveRecommendStatus.Content
                    } else {
                        LiveRecommendStatus.Empty
                    }
                },
                onFailure = { exception ->
                    AppLog.e("LivePerf", "LiveRecommendVM.loadData: 失败, 耗时=${System.currentTimeMillis() - t0}ms, ${exception.message}")
                    _recommendData.value = null
                    _error.value = exception.message
                    _status.value = LiveRecommendStatus.Error
                }
            )

            _loading.value = false
        }
    }

    private fun hasContent(data: LiveListWrapper): Boolean {
        return data.recommendRoomList.orEmpty().isNotEmpty() || data.roomList.orEmpty().any {
            it.list.orEmpty().isNotEmpty()
        }
    }

    fun shouldRefresh(ttlMs: Long): Boolean {
        if (_recommendData.value == null) {
            return true
        }
        return System.currentTimeMillis() - lastLoadedAt >= ttlMs
    }
}
