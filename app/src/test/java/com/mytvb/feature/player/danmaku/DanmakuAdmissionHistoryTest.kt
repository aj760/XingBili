package com.mytvb.feature.player.danmaku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuAdmissionHistoryTest {

    @Test
    fun rebuiltTimelineSkipsAnAlreadyLeftItemInsideTheRollingWindow() {
        val history = DanmakuAdmissionHistory()
        val item = danmaku(timeMs = 8_000, dmid = 1)
        history.record(item)

        val replay = history.replayBudget()

        assertTrue(replay.consume(item))
        assertFalse(replay.consume(item))
    }

    @Test
    fun repeatedIdenticalItemsKeepTheirOwnAdmissionSlots() {
        val history = DanmakuAdmissionHistory()
        val item = danmaku(timeMs = 8_000, dmid = null)
        history.record(item)
        history.record(item)

        val replay = history.replayBudget()

        assertTrue(replay.consume(item))
        assertTrue(replay.consume(item))
        assertFalse(replay.consume(item))
    }

    @Test
    fun lateItemThatWasNeverAdmittedRemainsEligibleAfterRebuild() {
        val history = DanmakuAdmissionHistory()
        val admitted = danmaku(timeMs = 8_000, dmid = 1)
        val late = danmaku(timeMs = 8_000, dmid = 2)
        history.record(admitted)

        val replay = history.replayBudget()

        assertTrue(replay.consume(admitted))
        assertFalse(replay.consume(late))
    }

    @Test
    fun backwardSeekStartsANewForwardAdmissionEpoch() {
        val history = DanmakuAdmissionHistory()
        val item = danmaku(timeMs = 8_000, dmid = 1)
        history.record(item)

        history.clear()

        assertFalse(history.replayBudget().consume(item))
    }

    @Test
    fun pruningKeepsOnlyTheCurrentRebuildWindow() {
        val history = DanmakuAdmissionHistory()
        val old = danmaku(timeMs = 1_000, dmid = 1)
        val current = danmaku(timeMs = 8_000, dmid = 2)
        history.record(old)
        history.record(current)

        history.pruneBefore(2_000)
        val replay = history.replayBudget()

        assertFalse(replay.consume(old))
        assertTrue(replay.consume(current))
    }

    @Test
    fun outOfOrderAppendOnlyReplacesTheFutureTailOutsideTheActiveGuard() {
        val patchFrom = resolveOutOfOrderAppendPatchStartMs(
            firstIncomingTimeMs = 4_000,
            currentPositionMs = 12_000L,
            rollingDurationMs = 6_000,
        )
        val replacement = mergeDanmakuFutureTail(
            existing = listOf(
                danmaku(timeMs = 1_000, dmid = 1),
                danmaku(timeMs = 12_000, dmid = 2),
                danmaku(timeMs = 20_000, dmid = 3),
            ),
            incoming = listOf(
                danmaku(timeMs = 4_000, dmid = 4),
                danmaku(timeMs = 18_000, dmid = 5),
                danmaku(timeMs = 22_000, dmid = 6),
            ),
            minTimeMs = patchFrom,
        )

        assertEquals(18_000, patchFrom)
        assertEquals(listOf(18_000, 20_000, 22_000), replacement.map { it.timeMs })
        assertEquals(listOf(5L, 3L, 6L), replacement.map { it.dmid })
    }

    @Test
    fun outOfOrderAppendCanPatchFromTheIncomingFutureBoundary() {
        val patchFrom = resolveOutOfOrderAppendPatchStartMs(
            firstIncomingTimeMs = 30_000,
            currentPositionMs = 12_000L,
            rollingDurationMs = 6_000,
        )

        assertEquals(30_000, patchFrom)
    }

    @Test
    fun sparseTimelineSleepsUntilTheNextMediaTimeAtNormalSpeed() {
        assertEquals(
            1_000L,
            resolveDanmakuIdleWakeDelayMs(
                nextWakeAtMs = 5_000,
                currentPositionMs = 1_000L,
                playbackSpeed = 1f,
            ),
        )
        assertEquals(
            250L,
            resolveDanmakuIdleWakeDelayMs(
                nextWakeAtMs = 1_500,
                currentPositionMs = 1_000L,
                playbackSpeed = 2f,
            ),
        )
    }

    @Test
    fun idleWakeIsNeverZeroAndInvalidSpeedFallsBackToNormalSpeed() {
        assertEquals(
            1L,
            resolveDanmakuIdleWakeDelayMs(
                nextWakeAtMs = 1_000,
                currentPositionMs = 1_000L,
                playbackSpeed = 1f,
            ),
        )
        assertEquals(
            600L,
            resolveDanmakuIdleWakeDelayMs(
                nextWakeAtMs = 1_600,
                currentPositionMs = 1_000L,
                playbackSpeed = Float.NaN,
            ),
        )
    }

    private fun danmaku(timeMs: Int, dmid: Long?): Danmaku =
        Danmaku(
            timeMs = timeMs,
            mode = 1,
            text = "same text",
            color = 0xFFFFFF,
            fontSize = 25,
            weight = 0,
            dmid = dmid,
        )
}
