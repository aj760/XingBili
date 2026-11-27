package com.mytvb.feature.player.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自适应同屏上限控制器的行为回归：
 * - 帧率健康 + 同屏顶格 → 连续确认后上调；帧率劣化 + 同屏够多 → 下调（更快）
 * - 同屏很低时帧率再差不误伤；死区内不动作；下限/硬顶钳制
 * - 暂停/大间隔陈旧窗口只重置不判定
 *
 * 时间轴在整个用例内连续（共用游标），帧间隔决定实际帧率。
 */
class AdaptiveOnScreenLimitTest {

    private companion object {
        const val FLOOR = 100
        const val CEILING = 400
        const val SEED = 160
        const val REFRESH = 60f
        const val WINDOW_MS = 2_500L

        // 帧间隔（毫秒）与实际帧率的对应：1000/interval
        const val FAST_INTERVAL = 16L   // 62.5 fps：健康，允许上调
        const val MID_INTERVAL = 20L    // 50 fps：死区，保持
        const val SLOW_INTERVAL = 25L   // 40 fps：劣化，触发下调
    }

    private var cursor = 0L

    /** 以固定帧间隔连续喂帧走完一个判定窗口（多喂 4 帧确保完成），返回调整结果。 */
    private fun feedWindow(
        controller: AdaptiveOnScreenLimit,
        intervalMs: Long,
        act: Int,
        playing: Boolean = true,
    ): Int? {
        var result: Int? = null
        val frames = (WINDOW_MS / intervalMs).toInt() + 4
        repeat(frames) {
            val r = controller.onFrame(
                wallNowMs = cursor,
                activeCount = act,
                clockAdvanced = playing,
            )
            if (r != null) result = r
            cursor += intervalMs
        }
        return result
    }

    private fun skip(ms: Long) {
        cursor += ms
    }

    @Test
    fun growsAfterConsecutiveHealthyWindowsWhenCapBinding() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        assertNull(feedWindow(c, FAST_INTERVAL, act = SEED))
        assertEquals(SEED, c.limit)
        // 第二个连续健康窗口：确认上调一个步长
        assertEquals(SEED + 16, feedWindow(c, FAST_INTERVAL, act = SEED))
    }

    @Test
    fun singleHealthyWindowDoesNotAdjust() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        assertNull(feedWindow(c, FAST_INTERVAL, act = SEED))
        // 死区窗口重置连击
        assertNull(feedWindow(c, MID_INTERVAL, act = SEED))
        assertNull(feedWindow(c, FAST_INTERVAL, act = SEED))
        assertEquals(SEED, c.limit)
    }

    @Test
    fun shrinksAfterConsecutiveLowFpsWindowsWhenActHigh() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        assertNull(feedWindow(c, SLOW_INTERVAL, act = (SEED * 0.8).toInt()))
        assertEquals(SEED - 32, feedWindow(c, SLOW_INTERVAL, act = (SEED * 0.8).toInt()))
    }

    @Test
    fun lowFpsWithLowActDoesNotShrink() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        repeat(4) { assertNull(feedWindow(c, SLOW_INTERVAL, act = 10)) }
        assertEquals(SEED, c.limit)
    }

    @Test
    fun midBandFpsHolds() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        repeat(4) { assertNull(feedWindow(c, MID_INTERVAL, act = SEED)) }
        assertEquals(SEED, c.limit)
    }

    @Test
    fun floorAndCeilingAreClamped() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        var guard = 0
        while (c.limit < CEILING && guard++ < 200) {
            feedWindow(c, FAST_INTERVAL, act = c.limit)
        }
        assertTrue("未到硬顶 limit=${c.limit}", c.limit == CEILING)
        guard = 0
        while (c.limit > FLOOR && guard++ < 400) {
            feedWindow(c, SLOW_INTERVAL, act = (c.limit * 0.9).toInt())
        }
        assertTrue("未回下限 limit=${c.limit}", c.limit == FLOOR)
    }

    @Test
    fun staleWindowAfterPauseDoesNotAdjust() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        assertNull(feedWindow(c, SLOW_INTERVAL, act = SEED))
        // 超长间隔（暂停）：陈旧窗口只重置不判定
        skip(60_000L)
        assertNull(c.onFrame(wallNowMs = cursor, activeCount = SEED, clockAdvanced = false))
        cursor += 16
        assertNull(feedWindow(c, SLOW_INTERVAL, act = SEED))
        assertEquals(SEED, c.limit)
    }

    @Test
    fun pausedFramesDoNotAdjust() {
        val c = AdaptiveOnScreenLimit(SEED, FLOOR, CEILING, REFRESH)
        repeat(6) { i ->
            assertNull(c.onFrame(wallNowMs = cursor + i * 100L, activeCount = SEED, clockAdvanced = false))
        }
        cursor += 600
        assertEquals(SEED, c.limit)
    }

    @Test
    fun seedIsClampedIntoFloorCeiling() {
        val low = AdaptiveOnScreenLimit(seed = 50, floor = FLOOR, ceiling = CEILING, refreshHz = REFRESH)
        assertEquals(FLOOR, low.limit)
        val high = AdaptiveOnScreenLimit(seed = 999, floor = FLOOR, ceiling = CEILING, refreshHz = REFRESH)
        assertEquals(CEILING, high.limit)
    }
}
