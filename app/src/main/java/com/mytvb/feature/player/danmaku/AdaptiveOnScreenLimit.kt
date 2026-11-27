package com.mytvb.feature.player.danmaku

import kotlin.math.max
import kotlin.math.min

/**
 * 自适应同屏弹幕上限控制器（引擎 action 线程私有）。
 *
 * 策略（语义：下限固定，上限按渲染帧率自动增减）：
 * - 帧率 ≥ 刷新率×[GROW_FPS_RATIO] 且窗口内同屏数顶到当前上限（有需求且有余量）
 *   → 连续 [CONFIRM_WINDOWS] 个窗口确认后 +[stepUp]；
 * - 帧率 ≤ 刷新率×[SHRINK_FPS_RATIO] 且同屏数 ≥ 上限×[SHRINK_ACT_RATIO]
 *   （同屏够多，掉帧才可能怪弹幕）→ 连续确认后 -[stepDown]，降比升快；
 * - 同屏数很低时不动作：此时掉帧与弹幕无关，不误伤；
 * - 暂停/seek 造成的陈旧大间隔窗口不判定，只重置。
 *
 * 反馈回路收敛性：上限↑ → 同屏↑ → 帧率若不足则触发降档，天然稳定在
 * "设备当前能流畅渲染的最大密度"附近。
 */
internal class AdaptiveOnScreenLimit(
    seed: Int,
    private val floor: Int,
    private val ceiling: Int,
    private val refreshHz: Float,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val stepUp: Int = DEFAULT_STEP_UP,
    private val stepDown: Int = DEFAULT_STEP_DOWN,
) {
    /** 当前生效的同屏上限。 */
    var limit: Int = seed.coerceIn(floor, ceiling)
        private set

    /** 最近一个完整窗口的实测帧率（诊断/日志用）。 */
    var lastWindowFps: Float = 0f
        private set

    /** 最近一个完整窗口内的同屏峰值（诊断/日志用）。 */
    var lastWindowMaxAct: Int = 0
        private set

    private var windowActive = false
    private var windowStartMs = 0L
    private var windowFrames = 0
    private var windowMaxAct = 0
    private var growStreak = 0
    private var shrinkStreak = 0

    /**
     * 每帧回调。 [wallNowMs] 为系统墙钟毫秒；[activeCount] 当前同屏数；
     * [clockAdvanced] 本帧弹幕时钟有前进（近似"正在播放"：暂停/缓冲时时钟冻结）。
     *
     * @return 调整后的上限值；本帧未调整返回 null。
     */
    fun onFrame(wallNowMs: Long, activeCount: Int, clockAdvanced: Boolean): Int? {
        if (!windowActive) {
            windowActive = true
            windowStartMs = wallNowMs
            windowFrames = 0
            windowMaxAct = 0
        }
        windowFrames++
        if (activeCount > windowMaxAct) windowMaxAct = activeCount
        val elapsed = wallNowMs - windowStartMs
        if (elapsed < windowMs) return null

        val fps = if (elapsed > 0) windowFrames * 1000f / elapsed else 0f
        val maxAct = windowMaxAct
        windowStartMs = wallNowMs
        windowFrames = 0
        windowMaxAct = 0
        lastWindowFps = fps
        lastWindowMaxAct = maxAct

        // 陈旧窗口（暂停/seek 后的大间隔）：样本不可信，只重置不判定。
        if (!clockAdvanced || elapsed > windowMs * 4) {
            growStreak = 0
            shrinkStreak = 0
            return null
        }

        val highHz = refreshHz * GROW_FPS_RATIO
        val lowHz = refreshHz * SHRINK_FPS_RATIO
        when {
            fps >= highHz && maxAct >= limit -> {
                growStreak++
                shrinkStreak = 0
                if (growStreak >= CONFIRM_WINDOWS && limit < ceiling) {
                    limit = min(limit + stepUp, ceiling)
                    growStreak = 0
                    return limit
                }
            }
            fps <= lowHz && maxAct >= limit * SHRINK_ACT_RATIO -> {
                shrinkStreak++
                growStreak = 0
                if (shrinkStreak >= CONFIRM_WINDOWS && limit > floor) {
                    limit = max(limit - stepDown, floor)
                    shrinkStreak = 0
                    return limit
                }
            }
            else -> {
                growStreak = 0
                shrinkStreak = 0
            }
        }
        return null
    }

    private companion object {
        /** 调整判定窗口（毫秒）。 */
        const val DEFAULT_WINDOW_MS = 2_500L

        /** 单次上调步长（条）。 */
        const val DEFAULT_STEP_UP = 16

        /** 单次下调步长（条），降比升快——掉帧的代价高于少看几条弹幕。 */
        const val DEFAULT_STEP_DOWN = 32

        /** 帧率健康线（刷新率占比）：达到才允许上调。 */
        const val GROW_FPS_RATIO = 0.90f

        /** 帧率劣化线（刷新率占比）：跌破才考虑下调。 */
        const val SHRINK_FPS_RATIO = 0.72f

        /** 连续确认窗口数：单次抖动不调整。 */
        const val CONFIRM_WINDOWS = 2

        /** 下调判定所需的最小同屏数（相对上限）。 */
        const val SHRINK_ACT_RATIO = 0.6f
    }
}
