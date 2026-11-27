package com.mytvb.network

import com.mytvb.core.common.log.AppLog
import com.mytvb.network.api.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 服务器时钟校准（x/click-interface/click/now）。
 *
 * 官方 web 播放器每次起播/心跳上报前都先拉 click/now，上报里的 start_ts/ftime/stime
 * 全用服务器时间。设备本地时钟不准（手动改时间、NTP 未同步）时这些绝对时间戳会漂移。
 * 这里缓存「服务器时间 - 本地时间」偏移做校准：拉取成功后生效，失败保持旧值
 * （首次失败偏移为 0，即退化为本地时钟，无害）。偏移为异步生效——调用后当次会话
 * 可能仍用旧偏移，时钟漂移是慢变量，可接受。
 */
object ServerClock {

    private const val TAG = "ServerClock"

    /** 偏移刷新冷却：官方每次上报前都拉，但时钟漂移缓慢，10 分钟足够。 */
    private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 服务器时间 - 本地时间，单位秒。 */
    @Volatile
    private var offsetSec: Long = 0L

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    @Volatile
    private var refreshInFlight: Boolean = false

    fun nowSec(): Long = System.currentTimeMillis() / 1000L + offsetSec

    fun nowMs(): Long = System.currentTimeMillis() + offsetSec * 1000L

    /** 当前偏移，诊断用。 */
    fun currentOffsetSec(): Long = offsetSec

    /**
     * 异步刷新偏移（带冷却与并发去重，调用方可无脑调）。
     * 失败不更新冷却时间，下次调用即重试。
     */
    fun refreshAsync(api: ApiService) {
        if (refreshInFlight) return
        if (System.currentTimeMillis() - lastRefreshAtMs < REFRESH_INTERVAL_MS) return
        refreshInFlight = true
        scope.launch {
            try {
                val response = runCatching { api.getServerTime() }.getOrNull()
                val serverNowSec = response?.data?.now?.takeIf { it > 0L }
                if (serverNowSec != null) {
                    offsetSec = serverNowSec - System.currentTimeMillis() / 1000L
                    lastRefreshAtMs = System.currentTimeMillis()
                    if (offsetSec != 0L) {
                        AppLog.i(TAG, "server clock offset ${offsetSec}s applied")
                    } else {
                        // 0 偏移也留痕：否则「请求成功且时钟已准」与「从未调用」无法从日志区分
                        AppLog.d(TAG, "server clock aligned, offset=0")
                    }
                } else {
                    AppLog.w(TAG, "getServerTime failed: code=${response?.code} msg=${response?.message}")
                }
            } finally {
                refreshInFlight = false
            }
        }
    }
}
