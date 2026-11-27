package com.mytvb.feature.player.danmaku

import com.mytvb.core.common.log.AppLog
import kotlin.math.abs
import kotlin.math.exp

/**
 * AkDanmaku-style timer:
 * - Uses System.nanoTime() for smooth advancement.
 * - Prioritizes a monotonic local clock while playing for stable motion.
 * - Re-anchors hard only on explicit playback events (seek).
 * - Corrections (resume/drift/idle gaps) converge gradually instead of hard
 *   re-anchoring, so on-screen danmaku never visibly jump in a single frame.
 */
internal class DanmakuTimer {
    @Volatile
    private var lastFrameNanos: Long = 0L

    @Volatile
    private var smoothPositionMs: Double = 0.0

    @Volatile
    private var lastSeekSerial: Int = 0

    @Volatile
    private var lastPlaying: Boolean = false

    @Volatile
    private var lastPlaybackSpeed: Double = 1.0

    /**
     * 漂移监督器的软同步因子（1 ± 5%）：只缩放每帧推进量，不参与
     * speed 变化判定（真实倍速变化才允许重锚到 raw），否则每次微调都会
     * 触发 anchor kind=speed 的硬重锚，软同步就退化成硬跳变。
     */
    @Volatile
    var softSyncFactor: Double = 1.0
        set(value) {
            field = value.coerceIn(0.9, 1.1)
        }

    /**
     * 速率自适应环开关：直播开（raw 推进速率可比墙钟慢 5~10% 时需自适应跟随），
     * 点播关（raw 匀速推进无需自适应；且部分设备 currentPosition 按帧阶梯刷新
     * 会让估计偏离 1.0，平滑时钟恒定超速→周期性触发追赶收敛，观感为滚动弹幕
     * 忽快忽慢，芝杜 Z9X8K 实测 rate 恒 1.08 即此病）。默认关：DanmakuView 会随
     * 播放页销毁重建，timer 回到默认值，点播必须开箱即安全；直播经 startLive()
     * 显式开启。
     */
    @Volatile
    var rateLoopEnabled: Boolean = false

    /** 渐进追赶进行中（未吸合）：用于日志只记首尾、不逐帧刷屏。 */
    @Volatile
    private var correcting: Boolean = false

    // 速率环状态（step 主线程私有写；lastRawForRateMs 用 NaN 表示"待重建基准"）。
    @Volatile
    private var rateEstimate: Double = 1.0

    @Volatile
    private var lastRawForRateMs: Double = Double.NaN

    // 当前估计窗口的累计量：expected 累计满 RATE_WINDOW_MS 出一次比率样本。
    private var windowExpectedMs: Double = 0.0
    private var windowRawMs: Double = 0.0

    /**
     * 轻量时钟校准（硬同步）：只移动平滑位置，不动 lastFrameNanos，
     * dt 连续性保持。与 reset() 的区别是不打 anchor 日志、不算 seek。
     * 回退方向的校准由引擎 stepTime 的单调钳制吸收（在屏弹幕冻结等 raw 追上，
     * 而不是位置倒跳重滚一遍）。
     */
    fun syncTo(positionMs: Long) {
        smoothPositionMs = positionMs.coerceAtLeast(0L).toDouble()
        correcting = false
        resetRateSampling()
    }

    fun reset(
        positionMs: Long,
        nowNanos: Long,
        seekSerial: Int,
        isPlaying: Boolean,
        playbackSpeed: Float,
    ) {
        lastFrameNanos = nowNanos
        smoothPositionMs = positionMs.coerceAtLeast(0L).toDouble()
        lastSeekSerial = seekSerial
        lastPlaying = isPlaying
        lastPlaybackSpeed = normalizeSpeed(playbackSpeed)
        correcting = false
        resetRateSampling()
    }

    fun currentPositionMs(): Long = smoothPositionMs.toLong()

    /** 速率环当前估计（诊断/性能行用）。 */
    fun currentRateEstimate(): Float = rateEstimate.toFloat()

    /**
     * 直播流速率自适应环：直播 ExoPlayer currentPosition 的实际推进速率可比墙钟慢
     * 5~10%（低延迟流追帧/解码抖动固有特性）。若平滑时钟恒按墙钟速率推进，负向
     * gap 会以恒定速率累积、每 2~4 秒越过追赶死区一次，触发回拉收敛——在引擎单调
     * 钳制下表现为在屏弹幕集体冻结 300~500ms，即"直播弹幕周期性颤动"。
     *
     * 此环用 raw 实测推进速率微调每帧推进量，使平滑时钟与 raw 同速推进，gap 稳定
     * 在死区内、追赶不再触发。视频(VOD)模式由 [rateLoopEnabled] 直接关闭（见该
     * 属性注释）。
     *
     * 估计用"大窗口累计比率"（ΣrawDt ÷ Σexpected）而非逐帧样本滑动平均：部分设备
     * currentPosition 按帧呈阶梯刷新（多次 0 增量 + 一次大增量），逐帧 EMA 的采样窗
     * 只收跳变帧、全拒 0 增量帧，估计系统性偏高（Z9X8K 实测恒 1.08，而 3 秒尺度
     * ΣrawDt/Σ墙钟实为 1.000）；窗口比率把 0 增量帧一并无偏计入，量化截断误差被
     * 窗口时长摊薄（1s 窗口、~40ms 量化粒度 → 单窗噪声 ~4%，出样平滑后 <1%）。
     *
     * 采信窗外的样本（缓冲停走 rawDt<0、追帧突进）作废当前窗口只重置基准，防止
     * 瞬时跳变污染比率。与漂移监督器 softSyncFactor 的分工：本环管速率匹配
     * （消除漂移来源，快环），监督器/追赶管残余偏差（慢环，稳态下基本不触发）。
     */
    private fun advanceRate(raw: Double, dtMs: Double, speed: Double): Double {
        if (!rateLoopEnabled) {
            resetRateSampling()
            return 1.0
        }
        val expected = dtMs * speed
        if (expected <= 0.0) return rateEstimate.coerceIn(RATE_MIN, RATE_MAX)
        if (lastRawForRateMs.isNaN()) {
            lastRawForRateMs = raw
            return rateEstimate.coerceIn(RATE_MIN, RATE_MAX)
        }
        val rawDt = raw - lastRawForRateMs
        lastRawForRateMs = raw
        if (rawDt >= 0.0 && rawDt <= expected * RATE_SAMPLE_MAX_RATIO + RATE_SAMPLE_PAD_MS) {
            windowRawMs += rawDt
            windowExpectedMs += expected
            if (windowExpectedMs >= RATE_WINDOW_MS) {
                val sample = (windowRawMs / windowExpectedMs).coerceIn(RATE_MIN, RATE_MAX)
                val alpha = 1.0 - exp(-windowExpectedMs / RATE_TIME_CONSTANT_MS)
                rateEstimate += (sample - rateEstimate) * alpha
                windowRawMs = 0.0
                windowExpectedMs = 0.0
            }
        } else {
            // 野点（回退/追帧突进）：作废半窗、从新基准重采，防止跳变污染比率。
            windowRawMs = 0.0
            windowExpectedMs = 0.0
        }
        return rateEstimate.coerceIn(RATE_MIN, RATE_MAX)
    }

    private fun resetRateSampling() {
        lastRawForRateMs = Double.NaN
        windowRawMs = 0.0
        windowExpectedMs = 0.0
    }

    fun step(
        nowNanos: Long,
        rawPositionMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float,
        seekSerial: Int,
    ): Long {
        val raw = rawPositionMs.coerceAtLeast(0L).toDouble()
        val speed = normalizeSpeed(playbackSpeed)
        val lastNanos = lastFrameNanos

        if (lastNanos == 0L || seekSerial != lastSeekSerial) {
            val firstInit = lastNanos == 0L
            val before = smoothPositionMs
            reset(
                positionMs = rawPositionMs,
                nowNanos = nowNanos,
                seekSerial = seekSerial,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
            )
            AppLog.i(
                DIAG_TAG,
                "anchor kind=${if (firstInit) "init" else "seekSerial"} " +
                    "before=${before.toLong()}ms after=${smoothPositionMs.toLong()}ms " +
                    "delta=${deltaLabel(smoothPositionMs - before)} play=$isPlaying"
            )
            return smoothPositionMs.toLong()
        }

        val dtNanos = (nowNanos - lastNanos).coerceAtLeast(0L)
        val dtMs = dtNanos.toDouble() / 1_000_000.0
        lastFrameNanos = nowNanos
        lastSeekSerial = seekSerial

        if (!isPlaying) {
            // 暂停中 raw 停走/回退，速率采样基准失效：恢复后从新基准重采。
            resetRateSampling()
            // 暂停/恢复瞬间 ExoPlayer 的 raw position 常会回退几十~上百毫秒
            // （解码器缓冲固有行为）。暂停瞬间保留当前平滑位置不动，绝不回退。
            if (lastPlaying) {
                // 刚从播放切到暂停：保持弹幕停在当前平滑位置，绝不回退。
                // 记录暂停边界的 raw 差值：恢复瞬间若偏差偏大，这里是上游证据。
                AppLog.i(
                    DIAG_TAG,
                    "pause-edge smooth=${smoothPositionMs.toLong()}ms raw=${raw.toLong()}ms " +
                        "rawDelta=${deltaLabel(raw - smoothPositionMs)}"
                )
            } else {
                // 暂停中只允许向前纠偏（禁止回退原则不变）；由一次性硬锚改为渐进收敛，
                // 避免暂停画面上弹幕瞬间整体前跳。
                val forwardGap = raw - smoothPositionMs
                if (correcting) {
                    if (forwardGap <= CATCH_UP_TOLERANCE_MS) {
                        correcting = false
                        AppLog.i(
                            DIAG_TAG,
                            "anchor kind=idle-end gap=${deltaLabel(forwardGap)} " +
                                "smooth=${smoothPositionMs.toLong()}ms"
                        )
                    } else {
                        smoothPositionMs = convergeTo(raw, dtMs)
                    }
                } else if (forwardGap >= IDLE_REANCHOR_THRESHOLD_MS) {
                    correcting = true
                    AppLog.i(
                        DIAG_TAG,
                        "anchor kind=idle-begin gap=${deltaLabel(forwardGap)} " +
                            "smooth=${smoothPositionMs.toLong()}ms raw=${raw.toLong()}ms"
                    )
                }
            }
            lastPlaying = false
            lastPlaybackSpeed = speed
            return smoothPositionMs.toLong()
        }

        // 恢复播放/倍速变化：不再一次性硬锚到 raw（吸收 PR #53 的收敛思路）——
        // 缓冲期间弹幕停在原地而 raw 已前进时，硬锚会让屏上所有弹幕同帧整体前移
        // （"统一大跳"）。这里只记录偏差，交由下方连续渐进追赶无感吸收。
        if (!lastPlaying || abs(speed - lastPlaybackSpeed) >= SPEED_CHANGE_EPSILON) {
            val kind = if (!lastPlaying) "resume" else "speed"
            AppLog.i(
                DIAG_TAG,
                "anchor kind=$kind smooth=${smoothPositionMs.toLong()}ms raw=${raw.toLong()}ms " +
                    "gap=${deltaLabel(raw - smoothPositionMs)} speed=$speed"
            )
            lastPlaying = true
            lastPlaybackSpeed = speed
            return smoothPositionMs.toLong()
        }

        if (dtNanos > 0L) {
            smoothPositionMs += dtMs * speed * softSyncFactor * advanceRate(raw, dtMs, speed)
        }

        // Clamp for safety.
        if (!smoothPositionMs.isFinite() || abs(smoothPositionMs) > 1e15) {
            smoothPositionMs = raw
            correcting = false
        }
        if (smoothPositionMs < 0.0) smoothPositionMs = 0.0

        // 连续渐进追赶：吸收 resume 残留偏差与未报告漂移（原 EXTREME_DRIFT 硬锚取消）。
        // 与漂移监督器（BlblDanmakuController 三段式）的分工：死区(250ms)内维持
        // "不动"语义；超过死区由本体的指数收敛快速对齐（±5% 软同步每秒仅收敛 5%，
        // 对缓冲/暂停恢复残留太慢）；超过硬阈值的 seek 重建仍由监督器主导，本收敛
        // 只是其去抖窗口内的平滑过渡。
        val gap = raw - smoothPositionMs
        if (!correcting && abs(gap) >= CATCH_UP_ENGAGE_GAP_MS) {
            correcting = true
            AppLog.i(
                DIAG_TAG,
                "anchor kind=catchup-begin gap=${deltaLabel(gap)} " +
                    "smooth=${smoothPositionMs.toLong()}ms raw=${raw.toLong()}ms " +
                    "rate=${"%.3f".format(java.util.Locale.US, rateEstimate)}"
            )
        }
        if (correcting) {
            runCatchUp(raw, dtMs)
        }

        lastPlaying = true
        lastPlaybackSpeed = speed
        return smoothPositionMs.toLong()
    }

    /**
     * 将平滑位置向 raw 渐进收敛（指数衰减，时间常数 [CATCH_UP_TIME_CONSTANT_MS]），
     * 偏差收敛到 [CATCH_UP_TOLERANCE_MS] 内吸合并结束本次追赶。
     * 偏差超过 [HARD_REANCHOR_GAP_MS] 时退回一次性硬锚——数秒级的未报告跳变若用
     * 指数收敛，弹幕会长时间数倍速快进，观感反而差于直接跳变。
     * 回退方向的收敛由引擎 stepTime 的单调钳制吸收（在屏弹幕冻结等 raw 追上）。
     */
    private fun runCatchUp(raw: Double, deltaMs: Double) {
        val gap = raw - smoothPositionMs
        if (abs(gap) >= HARD_REANCHOR_GAP_MS) {
            val before = smoothPositionMs
            smoothPositionMs = raw
            correcting = false
            AppLog.w(
                DIAG_TAG,
                "anchor kind=catchup-hard before=${before.toLong()}ms " +
                    "after=${smoothPositionMs.toLong()}ms delta=${deltaLabel(smoothPositionMs - before)}"
            )
            return
        }
        smoothPositionMs = convergeTo(raw, deltaMs)
        if (abs(raw - smoothPositionMs) <= CATCH_UP_TOLERANCE_MS) {
            correcting = false
            AppLog.i(
                DIAG_TAG,
                "anchor kind=catchup-end gap=${deltaLabel(raw - smoothPositionMs)} " +
                    "smooth=${smoothPositionMs.toLong()}ms"
            )
        }
    }

    /**
     * 将平滑位置向目标 target 渐进收敛：以指数衰减速率逼近，避免把 raw 的一次性
     * 偏差放大成"所有弹幕同帧整体跳变"；偏差小于 [CATCH_UP_TOLERANCE_MS] 时直接吸合。
     */
    private fun convergeTo(target: Double, deltaMs: Double): Double {
        val diff = target - smoothPositionMs
        if (!diff.isFinite()) return target
        if (abs(diff) <= CATCH_UP_TOLERANCE_MS) return target
        val rate = 1.0 - exp(-deltaMs.coerceAtLeast(0.0) / CATCH_UP_TIME_CONSTANT_MS)
        return smoothPositionMs + diff * rate
    }

    private fun deltaLabel(delta: Double): String =
        (if (delta >= 0) "+" else "") + delta.toLong() + "ms"

    private fun normalizeSpeed(playbackSpeed: Float): Double =
        playbackSpeed
            .takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: 1.0

    private companion object {
        private const val DIAG_TAG = "BlblDmDiag"

        private const val IDLE_REANCHOR_THRESHOLD_MS = 120.0
        private const val SPEED_CHANGE_EPSILON = 0.0001

        // 渐进追赶触发阈值：对齐漂移监督器死区（DRIFT_NEUTRAL_TOLERANCE_MS = 250ms）。
        private const val CATCH_UP_ENGAGE_GAP_MS = 250.0
        // 偏差收敛到该值内直接吸合（肉眼看不出跳变）。
        private const val CATCH_UP_TOLERANCE_MS = 30.0
        // 收敛时间常数（ms）：单帧(~16ms)收敛约 5%，300ms 收敛约 63%，1s 收敛约 96%。
        private const val CATCH_UP_TIME_CONSTANT_MS = 300.0
        // 超过该偏差退回一次性硬锚（监督器 >2s 的 seek 重建通常已介入，此处为兜底）。
        private const val HARD_REANCHOR_GAP_MS = 5_000.0

        // 速率环：估计收敛时间常数（直播流速率漂移约 -5~-10%，3s 常数下过渡期
        // 累积偏差 < 死区 250ms，不会触发追赶）。
        private const val RATE_TIME_CONSTANT_MS = 3_000.0

        // 速率环输出钳制：±15% 覆盖常见直播流漂移，超出部分仍由追赶/监督器兜底。
        private const val RATE_MIN = 0.85
        private const val RATE_MAX = 1.15

        // 估计出样窗口：窗口内 ΣrawDt/Σexpected 出一次样本（再按时间常数平滑）。
        // 窗口须远大于设备位置量化粒度（实测 ~20-40ms），把逐帧量化噪声摊薄成
        // 窗口截断误差（1s 窗口、40ms 粒度 → ~4%，出样平滑后稳态波动 <1%）。
        private const val RATE_WINDOW_MS = 1_000.0

        // 采样窗上界：rawDt 超过期望推进量 2 倍 + 100ms 视为追帧突进野点，作废
        // 半窗重采；下界放行 0 增量（阶梯刷新设备的常态帧，必须计入才是无偏）。
        private const val RATE_SAMPLE_MAX_RATIO = 2.0
        private const val RATE_SAMPLE_PAD_MS = 100.0
    }
}
