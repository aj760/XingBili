package blbl.cat3399.feature.player.danmaku

import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.Choreographer
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.Danmaku
import blbl.cat3399.feature.player.danmaku.model.RenderSnapshot
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * AkDanmaku-style player loop:
 * - Choreographer drives frame pacing (vsync).
 * - ActionThread does per-frame act/update.
 * - Main thread does draw.
 * - Semaphore provides backpressure between draw and act to avoid update piling up.
 */
internal class DanmakuPlayer(
    private val view: DanmakuView,
) {
    companion object {
        private const val TAG = "DanmakuPlayer"

        private const val MSG_FRAME_UPDATE = 2101
        private const val MSG_OP_SET = 3101
        private const val MSG_OP_APPEND = 3102
        private const val MSG_OP_TRIM_RANGE = 3103
        private const val MSG_OP_TRIM_MAX = 3104
        private const val MSG_OP_SEEK = 3105
        private const val MSG_OP_CLEAR = 3106
        private const val MSG_OP_VIEWPORT = 3201
        private const val MSG_OP_CONFIG = 3202
        private const val MSG_OP_RELEASE = 3999
    }

    private val cacheManager =
        CacheManager(
            density = view.resources.displayMetrics.density,
            mainLooper = Looper.getMainLooper(),
            onRenderSign = { view.invalidateDanmakuAreaOnAnimation() },
        )

    private val engineMain: DanmakuEngineMainApi
    private val engineAction: DanmakuEngineActionApi
    private val timer = DanmakuTimer()

    private val drawSemaphore = Semaphore(0)

    private val actionThread = HandlerThread("Danmaku-Action").apply { start() }
    private val actionHandler = ActionHandler(actionThread.looper)
    private val frameCallback = FrameCallback(actionHandler)

    private val seekSerial = AtomicInteger(0)
    private val uiFrameId = AtomicInteger(0)

    private val perfSampleRequested = AtomicBoolean(false)

    @Volatile
    private var perfLastActMs: Float = 0f

    @Volatile
    private var perfLastActAtUptimeMs: Long = 0L

    @Volatile
    private var debugEnabled: Boolean = false

    private val updateNsTotal = AtomicLong(0L)
    private val updateNsMax = AtomicLong(0L)
    private val updateCount = AtomicLong(0L)

    @Volatile
    private var started: Boolean = false

    @Volatile
    private var released: Boolean = false

    @Volatile
    private var viewportWidth: Int = 0

    @Volatile
    private var viewportHeight: Int = 0

    @Volatile
    private var viewportTopInsetPx: Int = 0

    @Volatile
    private var viewportBottomInsetPx: Int = 0

    @Volatile
    private var latestConfig: DanmakuConfig? = null

    internal fun debugSnapshot(): RenderSnapshot = engineMain.renderSnapshot()

    private var lastEnabled: Boolean = true

    init {
        val engine =
            DanmakuEngine(
                displayMetrics = view.resources.displayMetrics,
                cacheManager = cacheManager,
            )
        engineMain = engine
        engineAction = engine
    }

    fun startIfNeeded() {
        if (released) return
        if (started) return
        started = true
        actionHandler.post { postFrameCallback() }
        view.postInvalidateOnAnimation()
    }

    fun setDebugEnabled(enabled: Boolean) {
        if (debugEnabled == enabled) return
        debugEnabled = enabled
        updateNsTotal.set(0L)
        updateNsMax.set(0L)
        updateCount.set(0L)
    }

    data class DebugState(
        val updateAvgMs: Float,
        val updateMaxMs: Float,
        val cachedDrawn: Int,
        val fallbackDrawn: Int,
        val cacheQueueDepth: Int,
        val poolCount: Int,
        val poolBytes: Long,
        val poolMaxBytes: Long,
        val bitmapCreated: Long,
        val bitmapReused: Long,
        val bitmapPutToPool: Long,
        val bitmapRecycled: Long,
    )

    fun debugState(): DebugState {
        val count = updateCount.get().coerceAtLeast(1L)
        val avgMs = (updateNsTotal.get().toDouble() / count.toDouble() / 1_000_000.0).toFloat()
        val maxMs = (updateNsMax.get().toDouble() / 1_000_000.0).toFloat()
        val pool = cacheManager.poolSnapshot()
        val stats = cacheManager.statsSnapshot()
        return DebugState(
            updateAvgMs = avgMs,
            updateMaxMs = maxMs,
            cachedDrawn = engineMain.lastDrawCachedCount(),
            fallbackDrawn = engineMain.lastDrawFallbackCount(),
            cacheQueueDepth = cacheManager.queueDepth(),
            poolCount = pool.count,
            poolBytes = pool.bytes,
            poolMaxBytes = pool.maxBytes,
            bitmapCreated = stats.bitmapCreated,
            bitmapReused = stats.bitmapReused,
            bitmapPutToPool = stats.bitmapPutToPool,
            bitmapRecycled = stats.bitmapRecycled,
        )
    }

    data class PerfSample(
        val actMs: Float,
        val actAtUptimeMs: Long,
    )

    fun requestPerfSample() {
        perfSampleRequested.set(true)
    }

    fun perfSample(): PerfSample = PerfSample(actMs = perfLastActMs, actAtUptimeMs = perfLastActAtUptimeMs)

    fun stop() {
        if (!started) return
        started = false
        releaseSemaphoreIfNeeded()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun release() {
        if (released) return
        released = true
        started = false
        releaseSemaphoreIfNeeded()
        runCatching {
            actionHandler.obtainMessage(MSG_OP_RELEASE).sendToTarget()
        }
    }

    fun onViewportChanged(width: Int, height: Int, topInsetPx: Int, bottomInsetPx: Int) {
        viewportWidth = width.coerceAtLeast(0)
        viewportHeight = height.coerceAtLeast(0)
        viewportTopInsetPx = topInsetPx.coerceAtLeast(0)
        viewportBottomInsetPx = bottomInsetPx.coerceAtLeast(0)
        actionHandler.removeMessages(MSG_OP_VIEWPORT)
        actionHandler.sendEmptyMessage(MSG_OP_VIEWPORT)
    }

    fun updateConfig(config: DanmakuConfig) {
        latestConfig = config
        actionHandler.removeMessages(MSG_OP_CONFIG)
        actionHandler.sendEmptyMessage(MSG_OP_CONFIG)
    }

    fun setDanmakus(list: List<Danmaku>) {
        actionHandler.obtainMessage(MSG_OP_SET, list).sendToTarget()
    }

    fun appendDanmakus(list: List<Danmaku>, maxItems: Int, alreadySorted: Boolean) {
        val payload = AppendPayload(list = list, maxItems = maxItems, alreadySorted = alreadySorted)
        actionHandler.obtainMessage(MSG_OP_APPEND, payload).sendToTarget()
    }

    fun trimToTimeRange(minTimeMs: Long, maxTimeMs: Long) {
        actionHandler.obtainMessage(MSG_OP_TRIM_RANGE, TrimRangePayload(minTimeMs, maxTimeMs)).sendToTarget()
    }

    fun seekTo(positionMs: Long) {
        seekSerial.incrementAndGet()
        actionHandler.obtainMessage(MSG_OP_SEEK, positionMs).sendToTarget()
    }

    fun draw(
        canvas: Canvas,
        rawPositionMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float,
        config: DanmakuConfig,
    ) {
        if (released) return
        if (!config.enabled) {
            if (lastEnabled || started) {
                stop()
            }
            if (lastEnabled) {
                requestClear()
            }
            lastEnabled = false
            return
        }
        lastEnabled = true
        if (isPlaying) {
            startIfNeeded()
        } else if (started) {
            // Freeze danmaku on pause/buffering: no need to keep 60fps update loop running.
            started = false
            releaseSemaphoreIfNeeded()
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        val frameId = uiFrameId.incrementAndGet()
        engineMain.drainReleasedBitmaps(frameId)
        val nowNanos = System.nanoTime()
        val smoothPos =
            timer.step(
                nowNanos = nowNanos,
                rawPositionMs = rawPositionMs,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                seekSerial = seekSerial.get(),
            )

        engineMain.stepTime(positionMs = smoothPos, uiFrameId = frameId)

        // Drain extra permits so we never accumulate >1.
        drawSemaphore.tryAcquire()

        // Obtain render snapshot first, then release semaphore to allow ActionThread to compute next frame.
        val snapshot = engineMain.renderSnapshot()
        releaseSemaphoreIfNeeded()
        engineMain.draw(canvas, snapshot, config)
    }

    private fun postFrameCallback() {
        if (released) return
        if (!started) return
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun requestClear() {
        if (released) return
        actionHandler.removeMessages(MSG_OP_CLEAR)
        actionHandler.sendEmptyMessage(MSG_OP_CLEAR)
    }

    private fun releaseSemaphoreIfNeeded() {
        if (drawSemaphore.availablePermits() == 0) {
            drawSemaphore.release()
        }
    }

    private inner class ActionHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_FRAME_UPDATE -> {
                    if (released || !started) return
                    postFrameCallback()
                    try {
                        engineAction.preAct()
                        drawSemaphore.acquire()
                        if (released || !started) return
                        val sampleAct = perfSampleRequested.getAndSet(false)
                        val shouldMeasure = debugEnabled || sampleAct
                        val t0 = if (shouldMeasure) System.nanoTime() else 0L
                        engineAction.act()
                        if (shouldMeasure) {
                            val t1 = System.nanoTime()
                            val ns = (t1 - t0).coerceAtLeast(0L)
                            if (debugEnabled) {
                                updateCount.incrementAndGet()
                                updateNsTotal.addAndGet(ns)
                                updateMax(updateNsMax, ns)
                            }
                            if (sampleAct) {
                                perfLastActMs = (ns.toDouble() / 1_000_000.0).toFloat()
                                perfLastActAtUptimeMs = SystemClock.uptimeMillis()
                            }
                        }
                        view.invalidateDanmakuAreaOnAnimation()
                    } catch (ie: InterruptedException) {
                        // Ignore.
                    } catch (t: Throwable) {
                        AppLog.w(TAG, "updateFrame crashed", t)
                    }
                }

                MSG_OP_SET -> {
                    @Suppress("UNCHECKED_CAST")
                    engineAction.setDanmakus(msg.obj as? List<Danmaku> ?: emptyList())
                    renderOnceIfPaused()
                }

                MSG_OP_APPEND -> {
                    val p = msg.obj as? AppendPayload ?: return
                    engineAction.appendDanmakus(p.list, alreadySorted = p.alreadySorted)
                    if (p.maxItems > 0) engineAction.trimToMax(p.maxItems)
                    renderOnceIfPaused()
                }

                MSG_OP_TRIM_RANGE -> {
                    val p = msg.obj as? TrimRangePayload ?: return
                    engineAction.trimToTimeRange(p.minTimeMs, p.maxTimeMs)
                    renderOnceIfPaused()
                }

                MSG_OP_SEEK -> {
                    val pos = (msg.obj as? Long) ?: 0L
                    engineAction.seekTo(pos)
                    renderOnceIfPaused(positionMs = pos)
                }

                MSG_OP_TRIM_MAX -> {
                    val maxItems = msg.arg1
                    engineAction.trimToMax(maxItems)
                    renderOnceIfPaused()
                }

                MSG_OP_CLEAR -> {
                    engineAction.clear()
                }

                MSG_OP_VIEWPORT -> {
                    engineAction.updateViewport(
                        width = viewportWidth,
                        height = viewportHeight,
                        topInsetPx = viewportTopInsetPx,
                        bottomInsetPx = viewportBottomInsetPx,
                    )
                    engineAction.seekTo(engineAction.currentPositionMs())
                    renderOnceIfPaused()
                }

                MSG_OP_CONFIG -> {
                    latestConfig?.let {
                        engineAction.updateConfig(it)
                        // Reset layout on config changes (text size/speed/area) to keep correctness simple.
                        engineAction.seekTo(engineAction.currentPositionMs())
                        renderOnceIfPaused()
                    }
                }

                MSG_OP_RELEASE -> {
                    removeCallbacksAndMessages(null)
                    Choreographer.getInstance().removeFrameCallback(frameCallback)
                    started = false
                    runCatching { actionThread.quitSafely() }
                    runCatching { actionThread.join(80L) }
                    engineAction.release()
                    cacheManager.release()
                }
            }
        }

        private fun renderOnceIfPaused(positionMs: Long? = null) {
            if (released || started) return
            val pos = positionMs ?: engineAction.currentPositionMs()
            engineAction.stepTime(positionMs = pos, uiFrameId = uiFrameId.get())
            val sampleAct = perfSampleRequested.getAndSet(false)
            val shouldMeasure = debugEnabled || sampleAct
            val t0 = if (shouldMeasure) System.nanoTime() else 0L
            runCatching { engineAction.act() }
            if (shouldMeasure) {
                val t1 = System.nanoTime()
                val ns = (t1 - t0).coerceAtLeast(0L)
                if (debugEnabled) {
                    updateCount.incrementAndGet()
                    updateNsTotal.addAndGet(ns)
                    updateMax(updateNsMax, ns)
                }
                if (sampleAct) {
                    perfLastActMs = (ns.toDouble() / 1_000_000.0).toFloat()
                    perfLastActAtUptimeMs = SystemClock.uptimeMillis()
                }
            }
            view.invalidateDanmakuAreaOnAnimation()
        }
    }

    private class FrameCallback(
        private val handler: Handler,
    ) : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            handler.removeMessages(MSG_FRAME_UPDATE)
            handler.sendEmptyMessage(MSG_FRAME_UPDATE)
        }
    }

    private fun updateMax(target: AtomicLong, v: Long) {
        while (true) {
            val cur = target.get()
            if (v <= cur) return
            if (target.compareAndSet(cur, v)) return
        }
    }

    private data class AppendPayload(
        val list: List<Danmaku>,
        val maxItems: Int,
        val alreadySorted: Boolean,
    )

    private data class TrimRangePayload(
        val minTimeMs: Long,
        val maxTimeMs: Long,
    )
}
