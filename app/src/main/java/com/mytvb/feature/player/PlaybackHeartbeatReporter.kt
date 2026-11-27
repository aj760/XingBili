package com.mytvb.feature.player

import com.mytvb.core.common.json.GsonHolder
import com.google.gson.Gson
import com.mytvb.core.common.log.AppLog
import com.mytvb.network.ServerClock
import com.mytvb.network.WbiGenerator
import com.mytvb.network.api.ApiService
import com.mytvb.network.session.NetworkSessionGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

/**
 * 播放心跳与起播上报的协作对象。
 *
 * 从 VideoPlayerViewModel 拆分而来，仅负责 heartbeat / click-h5 上报逻辑。
 * 所有播放上下文（aid/cid/位置/时长/画质等）通过 [HeartbeatContext] 回调读取，
 * 不持有可变播放状态。生命周期跟随 ViewModel 注入的 [scope]。
 */
internal class PlaybackHeartbeatReporter(
    private val apiService: ApiService,
    private val sessionGateway: NetworkSessionGateway,
    private val scope: CoroutineScope,
    private val context: HeartbeatContext
) {

    /** 心跳读取的播放上下文，由 ViewModel 实现。 */
    interface HeartbeatContext {
        val currentAid: Long?
        val currentCid: Long
        val currentBvid: String?
        val pendingSeekPositionMs: Long
        val currentPositionMs: Long
        val durationMs: Long
        val playInfoDurationMs: Long
        val qualityId: Int
        val currentSeasonId: Long?
        val currentEpId: Long?
        /** PGC 剧集类型（1番剧 2电影 3纪录片 4国创 5电视剧 6综艺），0 表示非 PGC 或详情未回填。 */
        val currentSeasonType: Int
    }

    companion object {
        private const val TAG = "VideoPlayerViewModel"
        private const val WEB_LOCATION_PLAYER = "1315873"
        private const val PLAYER_SPMID = "333.788.0.0"
        private const val DEFAULT_FROM_SPMID = "333.1007.tianma.1-3-3.click"
        private const val WEB_PLAYER_VERSION = "4.9.78"
        /** 起播上报的 play_type 标识。 */
        const val PLAY_TYPE_START = 1
    }

    private val gson = GsonHolder.DEFAULT

    private var sessionStartTimestampMs: Long = 0L
    private var lastReportedHeartbeatPositionSec: Long = -1L
    private var playbackReportSession: String = ""
    private var playbackStartReported: Boolean = false

    /**
     * 上报一次播放心跳。
     *
     * @param force 关键收尾时机（STATE_ENDED、切集、退出 onStop）传 true：
     *  1. 绕过同秒去重，保证最后一条必达；
     *  2. UGC 额外走 [reportHistoryDirect]（x/v2/history/report 直写官方历史进度，
     *     与官方 App 同通道），双通道对齐 blbl 的收尾上报。
     * @param videoEnded 视频自然播放到结尾（STATE_ENDED）时传 true：把 played_time 上报为
     *  -1。B 站官方约定「played_time 播放完成为 -1」（见 bilibili-API-collect
     *  docs/video/report.md），官方端据此把该稿件标记为「已看完」。仅把进度钉到时长
     *  （见下方 durationSec）并不足以触发该标记——这正是此前「差最后一秒、不显示已看完」
     *  的根因。仅 STATE_ENDED 传 true；切集/退出不算看完，仍上报真实进度。
     */
    fun reportPlaybackHeartbeat(playType: Int = 0, force: Boolean = false, videoEnded: Boolean = false) {
        val aid = context.currentAid
        val cid = context.currentCid
        var positionMs = context.currentPositionMs.coerceAtLeast(0L)
        if (positionMs <= 0L) {
            positionMs = context.pendingSeekPositionMs.coerceAtLeast(0L)
        }
        // 播完（ENDED）或已播到总时长尾声时，把进度钉到「总时长」——取播放器时长与 API
        // 标称时长的较大者：DASH 清单常比官方标称短半秒~1 秒，只用播放器时长做阈值与封顶时，
        // floor 后会停在 duration-1，官方历史记录显示「差一秒」（如 10:35/10:36）。
        val playerDurationMs = context.durationMs
        val apiDurationMs = context.playInfoDurationMs
        val totalDurationMs = max(playerDurationMs, apiDurationMs)
        if (totalDurationMs > 0L && (videoEnded || positionMs >= totalDurationMs - 500L)) {
            positionMs = max(positionMs, totalDurationMs)
        }
        val positionSec = positionMs / 1000L
        // B 站约定 played_time==-1 表示已播放完成。播完（STATE_ENDED）发 -1 哨兵，
        // 官方端据此把稿件标记为「已看完」；其余时机发真实进度秒数。
        val reportedPlayedTimeSec = if (videoEnded) -1L else positionSec
        val csrf = sessionGateway.getCsrfToken()
        if (aid == null || aid <= 0L) {
            return
        }
        if (cid <= 0L || csrf.isBlank()) {
            return
        }
        if (positionSec <= 0L && playType != PLAY_TYPE_START) {
            return
        }
        if (positionSec == lastReportedHeartbeatPositionSec && !force) {
            return
        }
        lastReportedHeartbeatPositionSec = positionSec
        val startTimestampSec = ((sessionStartTimestampMs.takeIf { it > 0L } ?: ServerClock.nowMs()) / 1000L)
            .coerceAtLeast(1L)
        // start_ts/stime/ftime 用 click/now 校准过的服务器时钟（官方 web 上报同源），
        // 防设备本地时钟漂移；realtime 是同一校准钟上的差值，语义不受偏移影响。
        val realtimeSec = (ServerClock.nowSec() - startTimestampSec).coerceAtLeast(0L)
        val durationSec = ((context.durationMs.takeIf { it > 0L } ?: context.playInfoDurationMs) / 1000L)
            .coerceAtLeast(positionSec)
        if (force) {
            // 收尾补发打点（ENDED / 切集 / 退出 / 连播预上报）：核对是否按「播完」上报 -1、
            // 进度是否钉到总时长，用于排查官方端「差一秒 / 不显示已看完」。
            AppLog.i(
                TAG,
                "heartbeat_force aid=$aid cid=$cid ended=$videoEnded posMs=$positionMs posSec=$positionSec " +
                    "playerDurMs=$playerDurationMs apiDurMs=$apiDurationMs durSec=$durationSec playedTime=$reportedPlayedTimeSec"
            )
        }
        val userInfo = sessionGateway.getUserInfo()
        val mid = userInfo?.mid?.takeIf { it > 0L }
        val quality = context.qualityId
        val session = ensurePlaybackReportSession()

        val seasonId = context.currentSeasonId?.takeIf { it > 0L }
        val epId = context.currentEpId?.takeIf { it > 0L }
        // PGC（番剧/影视）进度只认 type=4 心跳：必须带 epid+sid+sub_type，
        // 否则服务端不推进「看到第X集」与历史聚合，追番进度会一直停在旧记录。
        val isPgc = epId != null || seasonId != null
        val subType = if (isPgc) context.currentSeasonType.takeIf { it in 1..6 } ?: 1 else 0
        if (isPgc) {
            AppLog.d(
                TAG,
                "heartbeat pgc aid=$aid cid=$cid epid=${epId ?: 0L} sid=${seasonId ?: 0L} sub_type=$subType pos=${positionSec}s"
            )
        }

        scope.launch {
            // 收尾补发常发生在退出播放器瞬间（onStop/切集）：viewModelScope 随 ViewModel
            // 销毁会取消协程，重试等待被砍掉导致最后一条丢失。NonCancellable 保证上报体
            // 一旦启动就完整执行（对齐 blbl 退出上报跑全局 IO 协程的行为）。
            withContext(NonCancellable) {
                reportPlaybackStartIfNeeded(
                    aid = aid,
                    cid = cid,
                    mid = mid,
                    csrf = csrf,
                    startTimestampSec = startTimestampSec,
                    session = session,
                    seasonId = seasonId,
                    epId = epId,
                    subType = subType
                )

                if (force && !isPgc) {
                    reportHistoryDirect(aid = aid, cid = cid, progressSec = reportedPlayedTimeSec, csrf = csrf)
                }

                val params = linkedMapOf(
                    "start_ts" to startTimestampSec.toString(),
                    "aid" to aid.toString(),
                    "cid" to cid.toString(),
                    "played_time" to reportedPlayedTimeSec.toString(),
                    "realtime" to realtimeSec.toString(),
                    "real_played_time" to positionSec.toString(),
                    "type" to if (isPgc) "4" else "3",
                    "sub_type" to subType.toString(),
                    "dt" to "2",
                    "play_type" to playType.toString(),
                    "refer_url" to buildPlaybackReferUrl(),
                    "quality" to quality.toString(),
                    "is_auto_qn" to "1",
                    "video_duration" to durationSec.toString(),
                    "last_play_progress_time" to positionSec.toString(),
                    "max_play_progress_time" to positionSec.toString(),
                    "outer" to "0",
                    "statistics" to buildWebStatistics(),
                    "mobi_app" to "web",
                    "device" to "web",
                    "platform" to "web",
                    "cur_language_vt" to "{}",
                    "perfer_type" to "{}",
                    "play_mode" to if (playType == PLAY_TYPE_START) "1" else "8",
                    "spmid" to PLAYER_SPMID,
                    "from_spmid" to DEFAULT_FROM_SPMID,
                    "session" to session,
                    "track_id" to "",
                    "extra" to buildPlaybackExtra(),
                    "csrf" to csrf
                )
                if (isPgc) {
                    params["epid"] = (epId ?: 0L).toString()
                    params["sid"] = (seasonId ?: 0L).toString()
                }
                mid?.let { params["mid"] = it.toString() }
                val queryParams = buildHeartbeatWbiParams(
                    aid = aid,
                    mid = mid,
                    startTimestampSec = startTimestampSec,
                    realtimeSec = realtimeSec,
                    playedTimeSec = reportedPlayedTimeSec,
                    progressSec = positionSec,
                    durationSec = durationSec
                )
                var attempt = 0
                while (attempt < 2) {
                    val result = runCatching {
                        sessionGateway.syncAuthState(
                            apiService.playVideoHeartbeatSigned(queryParams, params),
                            source = "player.playVideoHeartbeat"
                        )
                    }
                    if (result.isSuccess) break
                    attempt++
                    if (attempt < 2) {
                        delay(2000L)
                    } else {
                        AppLog.e(TAG, "reportPlaybackHeartbeat failed after retries: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                    }
                }
            }
        }
    }

    /**
     * x/v2/history/report 直写官方历史进度（官方 App 同通道，参数 aid/cid/progress/platform）。
     * 仅在关键收尾时机（force 且非 PGC）与 heartbeat 双发：PGC 双发会产生重复历史条目，
     * 高频 tick 直写也无必要——对齐 blbl 的双通道收尾上报。
     */
    private suspend fun reportHistoryDirect(aid: Long, cid: Long, progressSec: Long, csrf: String) {
        val result = runCatching {
            sessionGateway.syncAuthState(
                apiService.reportHistoryProgress(
                    linkedMapOf(
                        "aid" to aid.toString(),
                        "cid" to cid.toString(),
                        "progress" to progressSec.toString(),
                        "platform" to "android",
                        "csrf" to csrf
                    )
                ),
                source = "player.reportHistoryProgress"
            )
        }
        if (result.isSuccess) {
            AppLog.d(TAG, "history_report ok aid=$aid cid=$cid progress=${progressSec}s")
        } else {
            AppLog.w(TAG, "history_report failed: ${result.exceptionOrNull()?.message}")
        }
    }

    private suspend fun reportPlaybackStartIfNeeded(
        aid: Long,
        cid: Long,
        mid: Long?,
        csrf: String,
        startTimestampSec: Long,
        session: String,
        seasonId: Long? = null,
        epId: Long? = null,
        subType: Int = 0
    ) {
        if (playbackStartReported || csrf.isBlank()) return
        playbackStartReported = true
        val isPgc = subType in 1..6
        val nowSec = ServerClock.nowSec()
        val queryParams = buildClickH5WbiParams(
            aid = aid,
            startTimestampSec = startTimestampSec,
            reportTimestampSec = nowSec,
            isPgc = isPgc,
            seasonId = seasonId,
            epId = epId,
            subType = subType
        )
        val params = linkedMapOf(
            "aid" to aid.toString(),
            "cid" to cid.toString(),
            "part" to "1",
            "lv" to (sessionGateway.getUserInfo()?.levelInfo?.currentLevel ?: 0).toString(),
            "ftime" to startTimestampSec.toString(),
            "stime" to nowSec.toString(),
            "type" to if (isPgc) "4" else "3",
            "sub_type" to subType.toString(),
            "refer_url" to buildPlaybackReferUrl(),
            "outer" to "0",
            "statistics" to buildWebStatistics(),
            "mobi_app" to "web",
            "device" to "web",
            "platform" to "web",
            "cur_language" to "",
            "perfer_type" to "",
            "play_mode" to "1",
            "spmid" to PLAYER_SPMID,
            "from_spmid" to DEFAULT_FROM_SPMID,
            "session" to session,
            "track_id" to "",
            "extra" to buildPlaybackExtra(includePlayerVersion = false),
            "csrf" to csrf
        )
        if (isPgc) {
            params["epid"] = (epId ?: 0L).toString()
            params["sid"] = (seasonId ?: 0L).toString()
        }
        mid?.let { params["mid"] = it.toString() }
        runCatching {
            sessionGateway.syncAuthState(
                apiService.reportVideoClickH5(queryParams, params),
                source = "player.reportVideoClickH5"
            )
        }.onFailure {
            playbackStartReported = false
            AppLog.w(TAG, "reportPlaybackStart failed: ${it.message}")
        }
    }

    private suspend fun buildHeartbeatWbiParams(
        aid: Long,
        mid: Long?,
        startTimestampSec: Long,
        realtimeSec: Long,
        playedTimeSec: Long,
        progressSec: Long,
        durationSec: Long
    ): Map<String, String> {
        if (sessionGateway.areWbiKeysStale()) {
            runCatching { sessionGateway.ensureWbiKeys() }
                .onFailure { AppLog.w(TAG, "heartbeat ensureWbiKeys failed: ${it.message}") }
        }
        val (imgKey, subKey) = sessionGateway.getWbiKeys()
        val params = linkedMapOf(
            "w_start_ts" to startTimestampSec.toString(),
            "w_aid" to aid.toString(),
            "w_dt" to "2",
            "w_realtime" to realtimeSec.toString(),
            "w_played_time" to playedTimeSec.toString(),
            "w_real_played_time" to progressSec.toString(),
            "w_video_duration" to durationSec.toString(),
            "w_last_play_progress_time" to progressSec.toString(),
            "web_location" to WEB_LOCATION_PLAYER
        )
        mid?.let { params["w_mid"] = it.toString() }
        return WbiGenerator.generateWbiParams(params, imgKey, subKey)
    }

    private suspend fun buildClickH5WbiParams(
        aid: Long,
        startTimestampSec: Long,
        reportTimestampSec: Long,
        isPgc: Boolean = false,
        seasonId: Long? = null,
        epId: Long? = null,
        subType: Int = 0
    ): Map<String, String> {
        if (sessionGateway.areWbiKeysStale()) {
            runCatching { sessionGateway.ensureWbiKeys() }
                .onFailure { AppLog.w(TAG, "clickH5 ensureWbiKeys failed: ${it.message}") }
        }
        val (imgKey, subKey) = sessionGateway.getWbiKeys()
        val params = linkedMapOf(
            "w_aid" to aid.toString(),
            "w_part" to "1",
            "w_ftime" to startTimestampSec.toString(),
            "w_stime" to reportTimestampSec.toString(),
            "w_type" to if (isPgc) "4" else "3",
            "web_location" to WEB_LOCATION_PLAYER
        )
        if (isPgc) {
            // 官方 web 端 PGC 起播上报的签名参数镜像：type 参与校验，epid/sid/sub_type 同步入签。
            params["w_sub_type"] = subType.toString()
            params["w_sid"] = (seasonId ?: 0L).toString()
            params["w_epid"] = (epId ?: 0L).toString()
        }
        return WbiGenerator.generateWbiParams(params, imgKey, subKey)
    }

    private fun ensurePlaybackReportSession(): String {
        if (playbackReportSession.isBlank()) {
            playbackReportSession = UUID.randomUUID().toString().replace("-", "")
        }
        return playbackReportSession
    }

    /** 开始一次新的播放会话上报（对应 applyPreparedPlayback 非 replaceInPlace 分支）。 */
    fun beginNewReportSession() {
        // 顺手刷新服务器时钟偏移（10 分钟冷却），会话起点与后续 stime 全走校准钟
        ServerClock.refreshAsync(apiService)
        sessionStartTimestampMs = ServerClock.nowMs()
        lastReportedHeartbeatPositionSec = -1L
        playbackReportSession = UUID.randomUUID().toString().replace("-", "")
        playbackStartReported = false
    }

    /** 清空上报会话状态（对应切集 reset）。 */
    fun clear() {
        sessionStartTimestampMs = 0L
        lastReportedHeartbeatPositionSec = -1L
        playbackReportSession = ""
        playbackStartReported = false
    }

    private fun buildPlaybackReferUrl(): String {
        val bvid = context.currentBvid?.takeIf { it.isNotBlank() }
        return if (bvid != null) {
            "https://www.bilibili.com/video/$bvid/"
        } else {
            "https://www.bilibili.com/"
        }
    }

    private fun buildWebStatistics(): String {
        return """{"appId":100,"platform":5,"abtest":"","version":""}"""
    }

    private fun buildPlaybackExtra(includePlayerVersion: Boolean = true): String {
        val values = linkedMapOf<String, Any>(
            "play_method" to 2,
            "play_volume" to 1,
            "auto_play" to 0
        )
        if (includePlayerVersion) {
            values["player_version"] = WEB_PLAYER_VERSION
        }
        return gson.toJson(values)
    }
}
