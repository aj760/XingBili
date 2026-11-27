package com.mytvb.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytvb.R
import com.mytvb.core.common.log.AppLog
import com.mytvb.feature.player.danmaku.LiveDanmakuManager
import com.mytvb.feature.player.danmaku.LiveSuperChatSignal
import com.mytvb.model.dm.DmModel
import com.mytvb.model.live.LiveDUrlModel
import com.mytvb.repository.LiveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale

class LivePlayerViewModel(
    private val repository: LiveRepository,
    private val danmakuManager: LiveDanmakuManager,
    private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "LivePlayerViewModel"
        private const val HEARTBEAT_INTERVAL_MS = 60_000L
    }

    private val _playUrl = MutableStateFlow<String?>(null)
    val playUrl: StateFlow<String?> = _playUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _qualities = MutableStateFlow<List<LiveQualityInfo>>(emptyList())
    val qualities: StateFlow<List<LiveQualityInfo>> = _qualities.asStateFlow()
    private val _selectedQuality = MutableStateFlow<LiveQualityInfo?>(null)
    val selectedQuality: StateFlow<LiveQualityInfo?> = _selectedQuality.asStateFlow()

    private val _lines = MutableStateFlow<List<LiveLineInfo>>(emptyList())
    val lines: StateFlow<List<LiveLineInfo>> = _lines.asStateFlow()

    private val _selectedLineIndex = MutableStateFlow(0)
    val selectedLineIndex: StateFlow<Int> = _selectedLineIndex.asStateFlow()

    private val _liveDuration = MutableStateFlow("")
    val liveDuration: StateFlow<String> = _liveDuration.asStateFlow()

    private val _roomTitle = MutableStateFlow("")
    val roomTitle: StateFlow<String> = _roomTitle.asStateFlow()

    private val _anchorName = MutableStateFlow("")
    val anchorName: StateFlow<String> = _anchorName.asStateFlow()

    private val _refreshEvent = Channel<String>(Channel.BUFFERED)
    val refreshEvent = _refreshEvent.receiveAsFlow()

    // 弹幕
    val liveDanmaku: SharedFlow<DmModel> = danmakuManager.danmakuFlow
    val danmakuConnected: StateFlow<Boolean> = danmakuManager.isConnected

    // SC（醒目留言）：独立于弹幕开关的左侧卡片队列
    val superChat: SharedFlow<LiveSuperChatSignal> = danmakuManager.superChatFlow

    private var currentRoomId: Long = 0
    private var heartbeatJob: Job? = null
    private var durationJob: Job? = null
    private var heartbeatIntervalMs: Long = HEARTBEAT_INTERVAL_MS

    fun loadLiveStream(roomId: Long) {
        currentRoomId = roomId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            runCatching { repository.getIpInfo() }
                .onFailure { AppLog.w(TAG, "getIpInfo failed: ${it.message}") }

            repository.getLivePlayInfo(roomId).fold(
                onSuccess = { data ->
                    val durl = data.durl
                    if (!durl.isNullOrEmpty()) {
                        applyLines(durl)

                        AppLog.d(TAG, "loadLiveStream: liveTime=${data.liveTime} title=${data.roomTitle} anchor=${data.anchorName} qualities=${data.qualityDescription?.size}")
                        _roomTitle.value = data.roomTitle ?: ""
                        _anchorName.value = data.anchorName ?: ""
                        data.liveTime?.let { startLiveDuration(it) }

                        data.qualityDescription?.let { qualities ->
                            val mappedQualities = qualities.map {
                                LiveQualityInfo(it.qn, it.desc)
                            }.distinctBy { it.qn }
                            _qualities.value = mappedQualities
                            _selectedQuality.value = mappedQualities.firstOrNull {
                                it.qn == data.currentQn
                            } ?: mappedQualities.firstOrNull()
                        }
                    } else {
                        _error.value = appContext.getString(R.string.live_error_no_stream_url)
                    }

                    launchSupplementaryCalls(roomId)

                    // 启动实时弹幕连接
                    danmakuManager.start(roomId)
                },
                onFailure = { e ->
                    _error.value = e.message ?: appContext.getString(R.string.live_error_load_failed)
                }
            )

            _isLoading.value = false
        }
    }

    private fun launchSupplementaryCalls(roomId: Long) {
        viewModelScope.launch {
            runCatching { repository.reportRoomEntry(roomId) }
                .onFailure { AppLog.w(TAG, "reportRoomEntry failed: ${it.message}") }
        }

        viewModelScope.launch {
            repository.getHeartbeatKey(roomId)
                .onSuccess { data ->
                    val intervalSec = runCatching { data["heartbeat_interval"]?.asLong }.getOrNull() ?: 60L
                    heartbeatIntervalMs = (intervalSec.coerceAtLeast(15L) * 1000L)
                }
                .onFailure { AppLog.w(TAG, "getHeartbeatKey failed: ${it.message}") }
        }

        viewModelScope.launch {
            runCatching { repository.getUserRoomInfo(roomId) }
                .onFailure { AppLog.w(TAG, "getUserRoomInfo failed: ${it.message}") }
        }

        // 历史弹幕（作为初始弹幕数据展示）
        viewModelScope.launch {
            repository.getDanmuHistory(roomId).onSuccess { jsonObj ->
                AppLog.d(TAG, "getDanmuHistory: received keys=${jsonObj.keySet()}")
            }.onFailure { AppLog.w(TAG, "getDanmuHistory failed: ${it.message}") }
        }

        startHeartbeat(roomId)
    }

    private fun startHeartbeat(roomId: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(heartbeatIntervalMs)
                runCatching { repository.sendLiveHeartbeat(roomId) }
                    .onFailure { AppLog.w(TAG, "sendLiveHeartbeat failed: ${it.message}") }
            }
        }
    }

    private fun startLiveDuration(liveTime: String) {
        durationJob?.cancel()
        val startMs = liveTime.toLongOrNull()?.let { it * 1000L }
        if (startMs == null) {
            AppLog.w(TAG, "startLiveDuration: failed to parse liveTime=$liveTime")
            return
        }
        AppLog.d(TAG, "startLiveDuration: liveTime=$liveTime startMs=$startMs")
        durationJob = viewModelScope.launch {
            while (true) {
                val elapsed = (System.currentTimeMillis() - startMs) / 1000
                val h = elapsed / 3600
                val m = (elapsed % 3600) / 60
                val s = elapsed % 60
                val days = h / 24
                val remainHours = h % 24
                _liveDuration.value = when {
                    days > 0 -> String.format(Locale.getDefault(), "%dD %02d:%02d:%02d", days, remainHours, m, s)
                    h > 0   -> String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
                    else    -> String.format(Locale.getDefault(), "%02d:%02d", m, s)
                }
                delay(1000)
            }
        }
    }

    /** 把 durl 列表刷新到 lines，重置选中为 0，并同步 _playUrl。 */
    private fun applyLines(durl: List<LiveDUrlModel>?) {
        val mapped = durl.orEmpty().mapIndexed { idx, item ->
            LiveLineInfo(
                index = idx,
                name = item.cdnName.ifBlank { appContext.getString(R.string.live_line_fallback_name_format, idx + 1) },
                url = item.url
            )
        }
        _lines.value = mapped
        _selectedLineIndex.value = 0
        mapped.firstOrNull()?.let { _playUrl.value = it.url }
    }

    fun switchQuality(qn: Int) {
        if (currentRoomId > 0) {
            viewModelScope.launch {
                repository.getLivePlayInfo(currentRoomId, qn).fold(
                    onSuccess = { data ->
                        _selectedQuality.value = _qualities.value.firstOrNull { it.qn == qn }
                        applyLines(data.durl)
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: appContext.getString(R.string.live_error_switch_quality_failed)
                    }
                )
            }
        }
    }

    fun switchLine(index: Int) {
        val lines = _lines.value
        if (index !in lines.indices) return
        if (index == _selectedLineIndex.value) return
        _selectedLineIndex.value = index
        _playUrl.value = lines[index].url
    }

    fun refreshLiveStream(roomId: Long) {
        viewModelScope.launch {
            _error.value = null
            repository.getLivePlayInfo(roomId).fold(
                onSuccess = { data ->
                    applyLines(data.durl)
                    _refreshEvent.trySend(appContext.getString(R.string.live_refresh_success))
                },
                onFailure = { e ->
                    _error.value = e.message ?: appContext.getString(R.string.live_error_refresh_failed)
                }
            )
        }
    }

    fun retryLiveStream(roomId: Long) {
        val qn = _selectedQuality.value?.qn
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            repository.getLivePlayInfo(roomId, qn ?: 0).fold(
                onSuccess = { data ->
                    applyLines(data.durl)
                },
                onFailure = { e ->
                    _error.value = e.message ?: appContext.getString(R.string.live_error_retry_failed)
                }
            )
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        danmakuManager.release()
        heartbeatJob?.cancel()
        heartbeatJob = null
        durationJob?.cancel()
        durationJob = null
    }
}

data class LiveQualityInfo(
    val qn: Int,
    val desc: String
)

data class LiveLineInfo(
    val index: Int,
    val name: String,
    val url: String
)
