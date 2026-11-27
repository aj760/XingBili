package com.mytvb.feature.player.view

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import com.mytvb.core.common.log.AppLog
import com.mytvb.model.dm.DmMaskRepository
import com.mytvb.model.dm.DmMaskTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 弹幕防挡蒙版控制器。
 *
 * 按 Bilibili 参考链路完全重构：
 *  - 播放器 clock（onPlayerClockChanged / player.currentPosition）是唯一时间源
 *  - 清晰的状态机：IDLE → READY → ACTIVE → SEEKING → ACTIVE
 *  - 预加载跟随 player clock，不跟 video frame PTS
 *  - Choreographer 只驱动 ACTIVE 状态下的 mask 重绘，不因 UI 卡顿自动关闭 mask
 */
class DmMaskController(
    private val maskHostProvider: () -> DanmakuMaskHostLayout?,
    private var repository: DmMaskRepository,
) {
    companion object {
        private const val TAG = "DmMaskController"

        /** seek 后等待 playbackReady 的硬超时。 */
        private const val SEEK_HARD_TIMEOUT_MS = 1500L

        /** 诊断日志节流间隔。 */
        private const val DIAG_LOG_INTERVAL_MS = 5000L
        private const val PRELOAD_DIAG_INTERVAL_MS = 1000L
        private const val PRELOAD_STARTUP_DELAY_MS = 1800L
        private const val PRELOAD_SEGMENT_STAGGER_MS = 500L

    }

    // ---- 状态 ----

    private enum class State { IDLE, READY, ACTIVE, SEEKING }

    private var state = State.IDLE
    private var currentCid: Long = 0L
    private var currentMaskUrl: String = ""
    private var currentFps: Int = 0
    private var currentTimeline: DmMaskTimeline? = null
    private var isPlaying: Boolean = false
    private var playbackReady: Boolean = false
    private var enabled: Boolean = false
    private var danmakuVisible: Boolean = true
    @Volatile
    private var contentGeneration: Long = 0L

    @Volatile
    private var disposed: Boolean = false
    private var loadRequestOwner: Any = Any()
    private var preloadRequestOwner: Any = Any()

    @Volatile
    private var activeLoadRequestOwner: Any? = null

    // ---- 时钟 ----

    /**
     * 播放器 position 提供者。由外部注入 `player?.currentPosition`。
     * 这是唯一的时间源（参考文档结论 1）。
     */
    var playerPositionProvider: (() -> Long)? = null

    @Volatile
    private var playbackSpeed: Float = 1.0f

    /**
     * 参考 Chronos 的 playback clock：onPlayerClockChanged(rate, position) 只校准基准点，
     * 后续查询用 elapsedRealtime 按 rate 连续推进，避免 Media3 currentPosition 刷新粒度造成
     * mask 卡顿或追不上视频帧。
     */
    @Volatile
    private var clockBasePositionMs: Long = 0L

    @Volatile
    private var clockBaseRealtimeMs: Long = 0L

    // ---- Seek 状态 ----

    private var seekSequence: Long = 0L

    // ---- 预加载 ----

    private var lastPreloadedSegIndex: Int = -1

    @Volatile
    private var preloadingSegIndex: Int = -1
    private var preloadAllowedRealtimeMs: Long = 0L
    private var preloadRetryScheduled: Boolean = false
    private var deferredPreloadGeneration: Long = 0L
    private var preloadJob: Job? = null

    private val frameInvalidator = FrameInvalidator()

    // ---- 诊断 ----

    @Volatile
    private var lastDiagLogMs: Long = 0L
    private var lastPreloadDiagMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deferredPreloadRunnable = Runnable {
        preloadRetryScheduled = false
        if (isCurrentMaskLoad(deferredPreloadGeneration, contentGeneration, disposed) && state == State.ACTIVE) {
            maybePreload(currentVideoPtsMs())
        }
    }

    // ========== 公开 API ==========

    fun setEnabled(enabled: Boolean) {
        if (disposed) return
        if (this.enabled == enabled) return
        this.enabled = enabled
        when {
            !enabled -> {
                cancelLoadRequest()
                contentGeneration++
                state = State.IDLE
                maskHostProvider()?.clearCachedMask()
                frameInvalidator.stop()
                stopPreloading()
            }
            currentTimeline != null -> {
                state = if (shouldAnimate()) State.ACTIVE else State.READY
                updateFrameInvalidator()
                if (state == State.ACTIVE) {
                    deferPreload()
                    maybePreload(currentVideoPtsMs())
                }
                invalidateMaskHost()
            }
        }
    }

    fun setDanmakuVisible(visible: Boolean) {
        if (disposed || danmakuVisible == visible) return
        danmakuVisible = visible
        if (state != State.SEEKING && currentTimeline != null && enabled) {
            state = if (shouldAnimate()) State.ACTIVE else State.READY
        }
        updateFrameInvalidator()
        if (state == State.ACTIVE) {
            maybePreload(currentVideoPtsMs())
            invalidateMaskHost()
        } else {
            stopPreloading()
        }
    }

    suspend fun loadMask(maskUrl: String, cid: Long, fps: Int): Boolean {
        if (disposed) return false
        if (currentCid == cid &&
            currentMaskUrl == maskUrl &&
            currentFps == fps &&
            currentTimeline != null
        ) {
            AppLog.d(TAG, "reuse current mask: cid=$cid fps=$fps")
            state = if (!enabled) State.IDLE else if (shouldAnimate()) State.ACTIVE else State.READY
            maskHostProvider()?.let { it.timeline = currentTimeline }
            updateFrameInvalidator()
            if (state == State.ACTIVE) {
                invalidateMaskHost()
            }
            return true
        }
        val requestGeneration = ++contentGeneration
        val requestRepository = repository
        cancelLoadRequest()
        stopPreloading()
        val requestOwner = loadRequestOwner
        currentCid = cid
        currentMaskUrl = maskUrl
        currentFps = fps
        state = State.IDLE
        currentTimeline = null
        lastPreloadedSegIndex = -1
        preloadingSegIndex = -1
        deferPreload()
        maskHostProvider()?.let { host ->
            host.timeline = null
            host.clearCachedMask()
        }

        activeLoadRequestOwner = requestOwner
        val data = try {
            requestRepository.downloadAndParse(maskUrl, cid, fps, requestOwner)
        } finally {
            if (activeLoadRequestOwner === requestOwner) {
                activeLoadRequestOwner = null
            }
            requestRepository.finishRequests(requestOwner)
        }
        if (!isCurrentMaskLoad(requestGeneration, contentGeneration, disposed)) {
            AppLog.d(TAG, "ignore stale mask load: cid=$cid generation=$requestGeneration")
            return false
        }
        if (data == null) {
            AppLog.e(TAG, "Mask load failed for cid=$cid")
            return false
        }

        currentTimeline = requestRepository.getTimeline(cid)
        maskHostProvider()?.let { it.timeline = currentTimeline }

        state = if (!enabled) State.IDLE else if (shouldAnimate()) State.ACTIVE else State.READY

        updateFrameInvalidator()
        if (state == State.ACTIVE) {
            invalidateMaskHost()
            // 初始遮罩分片加载让位给弹幕首屏测量和第一帧绘制。
            maybePreload(currentVideoPtsMs())
        }
        return true
    }

    /**
     * 唯一时钟入口。由播放器的 onPlayerClockChanged / syncDanmakuPosition 调用。
     * 对齐参考：onPlayerClockChanged(rate, position)。
     */
    fun onPlayerClockChanged(rate: Float, positionMs: Long) {
        if (rate.isFinite() && rate > 0f) {
            playbackSpeed = rate
        }
        clockBasePositionMs = positionMs.coerceAtLeast(0L)
        clockBaseRealtimeMs = SystemClock.elapsedRealtime()
        if (state == State.ACTIVE) {
            maybePreload(currentVideoPtsMs())
            invalidateMaskHost()
        }
    }

    /** 进度更新时调用（由 syncDanmakuPosition 调用）。 */
    fun pushMaskUpdate() {
        if (state != State.ACTIVE) return
        checkAndPreloadNext()
        invalidateMaskHost()
    }

    /** Seek 操作。进入 SEEKING 状态，保留旧遮罩冻结（不清除），等新 segment 加载后自动替换。 */
    fun onSeek() {
        val sequence = ++seekSequence
        state = State.SEEKING
        lastPreloadedSegIndex = -1
        frameInvalidator.stop()
        stopPreloading()
        mainHandler.postDelayed({
            if (shouldCompleteMaskSeek(disposed, state == State.SEEKING, sequence, seekSequence)) {
                transitionFromSeeking()
            }
        }, SEEK_HARD_TIMEOUT_MS)
        // 不调用 clearCachedMask()：旧遮罩在新 segment 加载完成前继续显示，
        // 避免 seek 后 100~300ms 的无遮罩窗口。
    }

    fun setPlaying(playing: Boolean) {
        if (disposed) return
        if (!playing && isPlaying) {
            clockBasePositionMs = currentVideoPtsMs()
            clockBaseRealtimeMs = SystemClock.elapsedRealtime()
        }
        isPlaying = playing
        if (state != State.SEEKING && currentTimeline != null && enabled) {
            state = if (shouldAnimate()) State.ACTIVE else State.READY
        }
        updateFrameInvalidator()
        if (state == State.ACTIVE) {
            maybePreload(currentVideoPtsMs())
            invalidateMaskHost()
        } else {
            stopPreloading()
        }
    }

    fun setPlaybackReady(ready: Boolean) {
        if (disposed) return
        playbackReady = ready
        if (ready && state == State.SEEKING) {
            transitionFromSeeking()
        } else if (state != State.SEEKING && currentTimeline != null && enabled) {
            state = if (shouldAnimate()) State.ACTIVE else State.READY
            updateFrameInvalidator()
            if (state == State.ACTIVE) {
                maybePreload(currentVideoPtsMs())
                invalidateMaskHost()
            } else {
                stopPreloading()
            }
        }
    }

    fun onPositionChanged() {
        if (state == State.SEEKING && playbackReady) {
            transitionFromSeeking()
        }
        if (state == State.ACTIVE) {
            invalidateMaskHost()
        }
    }

    /**
     * 返回 mask 当前应该查询 timeline 的 PTS（ms）。
     * 唯一时间源：参考 Chronos 的校准点 + elapsedRealtime 外推。
     */
    fun currentVideoPtsMs(): Long {
        val base = if (clockBaseRealtimeMs > 0L) {
            clockBasePositionMs
        } else {
            (playerPositionProvider?.invoke() ?: 0L).coerceAtLeast(0L)
        }
        if (!isPlaying || clockBaseRealtimeMs <= 0L) {
            return base.coerceAtLeast(0L)
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - clockBaseRealtimeMs).coerceAtLeast(0L)
        return (base + (elapsedMs * playbackSpeed).toLong()).coerceAtLeast(0L)
    }

    /**
     * HostLayout 调用：是否应该渲染遮罩。
     * IDLE → false。READY / ACTIVE / SEEKING → true（SEEKING 时用旧遮罩冻结）。
     */
    fun shouldRenderMask(): Boolean {
        return enabled && state != State.IDLE
    }

    /** HostLayout 调用：当前是否处于 SEEKING 冻结状态。 */
    fun isSeeking(): Boolean {
        return state == State.SEEKING
    }

    fun setRepository(repository: DmMaskRepository) {
        cancelLoadRequest()
        stopPreloading()
        this.repository = repository
    }

    fun hasCachedMask(cid: Long): Boolean {
        return (currentCid == cid && currentTimeline != null) || repository.hasMask(cid)
    }

    /** 后台回前台时调用：ACTIVE 状态下恢复 mask 重绘。 */
    fun onResume() {
        if (state == State.ACTIVE) {
            frameInvalidator.start()
            invalidateMaskHost()
        }
    }

    fun release() {
        cancelLoadRequest()
        contentGeneration++
        seekSequence++
        stopPreloading()
        mainHandler.removeCallbacksAndMessages(null)
        preloadRetryScheduled = false
        state = State.IDLE
        currentCid = 0L
        currentMaskUrl = ""
        currentFps = 0
        currentTimeline = null
        lastPreloadedSegIndex = -1
        frameInvalidator.stop()
        maskHostProvider()?.let { host ->
            host.timeline = null
            host.clearCachedMask()
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        release()
        preloadScope.cancel()
    }

    // ---- 诊断 API（保持兼容） ----

    fun reportFrameQuery(queryPtsMs: Long, framePtsMs: Long) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastDiagLogMs < DIAG_LOG_INTERVAL_MS) return
        lastDiagLogMs = nowMs
        val playerPos = playerPositionProvider?.invoke() ?: -1L
        AppLog.d(
            TAG,
            "pts diag: query=$queryPtsMs frame.pts=$framePtsMs frame-query=${framePtsMs - queryPtsMs}ms " +
                "playerPos=$playerPos speed=$playbackSpeed state=$state"
        )
    }

    // ========== 内部实现 ==========

    private fun transitionFromSeeking() {
        state = if (shouldAnimate()) State.ACTIVE else State.READY
        updateFrameInvalidator()
        if (state == State.ACTIVE) {
            deferPreload()
            maybePreload(currentVideoPtsMs())
            invalidateMaskHost()
        }
    }

    private fun checkAndPreloadNext() {
        val timeline = currentTimeline ?: return
        val pts = currentVideoPtsMs()
        val segIdx = timeline.segmentIndexAt(pts)
        if (segIdx > lastPreloadedSegIndex || !timeline.isSegmentCached(segIdx)) {
            preloadAhead(segIdx)
            lastPreloadedSegIndex = segIdx
        }
    }

    /**
     * 预加载：当前段 ± 2。由 player clock 驱动。
     */
    private fun maybePreload(ptsMs: Long) {
        val timeline = currentTimeline ?: return
        if (!isPreloadAllowed()) {
            scheduleDeferredPreload()
            return
        }
        val segIdx = timeline.segmentIndexAt(ptsMs)
        if (segIdx == preloadingSegIndex) return
        if (timeline.isSegmentCached(segIdx) && timeline.isSegmentCached(segIdx + 1)) {
            lastPreloadedSegIndex = segIdx
            return
        }
        preloadAhead(segIdx)
        lastPreloadedSegIndex = segIdx
    }

    private fun preloadAhead(currentSegIdx: Int) {
        val cid = currentCid
        val generation = contentGeneration
        val preloadRepository = repository
        val requestOwner = preloadRequestOwner
        val timeline = currentTimeline ?: return
        if (preloadJob?.isCompleted == false) return
        if (preloadingSegIndex == currentSegIdx) return
        preloadingSegIndex = currentSegIdx
        val totalSegs = timeline.totalSegments()
        val orderedSegments = listOf(
            currentSegIdx,
            currentSegIdx + 1,
            currentSegIdx - 1,
            currentSegIdx + 2
        ).filter { it in 0 until totalSegs }.distinct()
        maybeLogPreload(currentSegIdx, orderedSegments)
        preloadJob?.cancel()
        val job = preloadScope.launch {
            try {
                orderedSegments.forEachIndexed { index, idx ->
                    if (!isCurrentMaskLoad(generation, contentGeneration, disposed)) return@launch
                    if (index > 0) {
                        delay(PRELOAD_SEGMENT_STAGGER_MS.milliseconds)
                    }
                    if (!isCurrentMaskLoad(generation, contentGeneration, disposed)) return@launch
                    preloadRepository.preloadSegmentFrames(cid, idx, requestOwner)
                }
            } finally {
                preloadRepository.finishRequests(requestOwner)
                if (generation == contentGeneration && preloadingSegIndex == currentSegIdx) {
                    preloadingSegIndex = -1
                }
            }
        }
        job.invokeOnCompletion { preloadRepository.finishRequests(requestOwner) }
        preloadJob = job
    }

    private fun maybeLogPreload(currentSegIdx: Int, orderedSegments: List<Int>) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPreloadDiagMs < PRELOAD_DIAG_INTERVAL_MS) return
        lastPreloadDiagMs = now
        AppLog.d(
            TAG,
            "preload mask segments: state=$state cid=$currentCid current=$currentSegIdx " +
                "order=$orderedSegments pts=${currentVideoPtsMs()} enabled=$enabled playing=$isPlaying"
        )
    }

    private fun deferPreload(delayMs: Long = PRELOAD_STARTUP_DELAY_MS) {
        preloadAllowedRealtimeMs = SystemClock.elapsedRealtime() + delayMs
        mainHandler.removeCallbacks(deferredPreloadRunnable)
        preloadRetryScheduled = false
    }

    private fun isPreloadAllowed(): Boolean {
        return SystemClock.elapsedRealtime() >= preloadAllowedRealtimeMs
    }

    private fun scheduleDeferredPreload() {
        if (preloadRetryScheduled || disposed) return
        val delayMs = (preloadAllowedRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
        deferredPreloadGeneration = contentGeneration
        preloadRetryScheduled = true
        mainHandler.postDelayed(deferredPreloadRunnable, delayMs)
    }

    private fun stopPreloading() {
        mainHandler.removeCallbacks(deferredPreloadRunnable)
        preloadRetryScheduled = false
        val runningJob = preloadJob
        if (runningJob?.isCompleted == false) {
            runningJob.cancel()
            repository.cancelRequests(preloadRequestOwner)
        }
        preloadRequestOwner = Any()
        preloadingSegIndex = -1
    }

    private fun cancelLoadRequest() {
        activeLoadRequestOwner?.let(repository::cancelRequests)
        loadRequestOwner = Any()
    }

    private fun invalidateMaskHost() {
        maskHostProvider()?.postInvalidateOnAnimation()
    }

    private fun shouldAnimate(): Boolean = shouldRunMaskFrames(
        enabled = enabled,
        danmakuVisible = danmakuVisible,
        isPlaying = isPlaying,
        playbackReady = playbackReady,
        hasTimeline = currentTimeline != null,
    )

    private fun updateFrameInvalidator() {
        if (shouldAnimate() && state == State.ACTIVE) {
            frameInvalidator.start()
        } else {
            frameInvalidator.stop()
        }
    }

    // ========== FrameInvalidator ==========

    /**
     * 基于 Choreographer 驱动弹幕 mask 每个 vsync 重绘。
     * 参考不会因为 UI 卡顿自动关闭 mask；卡顿只会推迟下一帧提交。
     */
    private inner class FrameInvalidator : Choreographer.FrameCallback {
        @Volatile
        private var running: Boolean = false
        private var choreographer: Choreographer? = null

        fun start() {
            mainHandler.post {
                if (running || disposed || state != State.ACTIVE || !shouldAnimate()) return@post
                running = true
                val ch = choreographer ?: Choreographer.getInstance().also { choreographer = it }
                ch.postFrameCallback(this)
            }
        }

        fun stop() {
            mainHandler.post {
                if (!running) return@post
                running = false
                choreographer?.removeFrameCallback(this)
            }
        }

        override fun doFrame(frameTimeNs: Long) {
            if (!running) return
            if (disposed || state != State.ACTIVE || !shouldAnimate()) {
                running = false
                return
            }
            invalidateMaskHost()
            choreographer?.postFrameCallback(this)
        }
    }
}
