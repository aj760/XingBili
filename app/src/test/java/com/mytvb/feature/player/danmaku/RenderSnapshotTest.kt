package com.mytvb.feature.player.danmaku

import com.mytvb.feature.player.danmaku.model.DanmakuItem
import com.mytvb.feature.player.danmaku.model.DanmakuKind
import com.mytvb.feature.player.danmaku.model.RenderSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSnapshotTest {

    @Test
    fun writerCannotReuseSnapshotWhileMainThreadHoldsReadLease() {
        val snapshot = RenderSnapshot()

        assertTrue(snapshot.tryAcquireRead())
        assertFalse(snapshot.tryBeginWrite())

        snapshot.releaseRead()
        assertTrue(snapshot.tryBeginWrite())
        snapshot.endWrite()
    }

    @Test
    fun readerCannotObserveSnapshotDuringPublication() {
        val snapshot = RenderSnapshot()

        assertTrue(snapshot.tryBeginWrite())
        assertFalse(snapshot.tryAcquireRead())

        snapshot.endWrite()
        assertTrue(snapshot.tryAcquireRead())
        snapshot.releaseRead()
    }

    @Test
    fun cacheResultOnlyCommitsToCurrentGenerationPendingRequest() {
        // 接受条件：样式代际一致 + 条目上有本次在途请求（pending/rendering 匹配）。
        // "已退场/被丢弃"由移除路径重置 pendingCacheGeneration 的纪律表达；
        // 未入场条目允许挂图（时间线预取，入场时按缓存命中直接开动）。
        assertTrue(shouldApplyBlblCacheResult(3, 3, 3, rendering = true))
        assertFalse(shouldApplyBlblCacheResult(2, 3, 2, rendering = true))
        assertFalse(shouldApplyBlblCacheResult(3, 3, 2, rendering = true))
        assertFalse(shouldApplyBlblCacheResult(3, 3, 3, rendering = false))
        // 退场条目：pending 已被移除路径重置为 -1 → 拒绝，不会错误复活。
        assertFalse(shouldApplyBlblCacheResult(3, 3, -1, rendering = false))
    }

    @Test
    fun firstCacheResultStartsMotionAtReadyTime() {
        assertEquals(1_240, cacheReadyStartTime(false, currentStartTimeMs = 1_000, nowMs = 1_240))
    }

    @Test
    fun styleCacheRebuildDoesNotRestartExistingMotion() {
        assertEquals(1_000, cacheReadyStartTime(true, currentStartTimeMs = 1_000, nowMs = 1_240))
    }

    @Test
    fun itemWaitingForCacheEventuallyReleasesItsLane() {
        assertFalse(isCacheWaitExpired(false, admittedAtMs = 1_000, nowMs = 2_599, timeoutMs = 1_600))
        assertTrue(isCacheWaitExpired(false, admittedAtMs = 1_000, nowMs = 2_600, timeoutMs = 1_600))
        assertFalse(isCacheWaitExpired(true, admittedAtMs = 1_000, nowMs = 9_000, timeoutMs = 1_600))
    }

    @Test
    fun trimmingConsumedLiveHistoryOnlyAdjustsTimelineIndex() {
        assertEquals(1_900, adjustedTimelineIndexAfterPrefixTrim(index = 2_000, droppedCount = 100))
        assertEquals(0, adjustedTimelineIndexAfterPrefixTrim(index = 40, droppedCount = 100))
    }

    @Test
    fun fixedDanmakusAreWrittenAfterRollingDanmakus() {
        val top = item(DanmakuKind.TOP, "top")
        val rolling = item(DanmakuKind.SCROLL, "rolling")
        val bottom = item(DanmakuKind.BOTTOM, "bottom")
        val laterRolling = item(DanmakuKind.SCROLL, "later rolling")
        val snapshot = RenderSnapshot()

        writeDanmakuRenderOrder(listOf(top, rolling, bottom, laterRolling), snapshot)

        assertEquals(
            listOf("rolling", "later rolling", "top", "bottom"),
            (0 until snapshot.count).map { snapshot.items[it]?.data?.text },
        )
    }

    @Test
    fun clearingASnapshotDropsEveryCapturedCacheSlotBeforeReuse() {
        val snapshot = RenderSnapshot()
        snapshot.ensureCapacity(2)
        snapshot.count = 2
        snapshot.cacheGenerations[0] = 7
        snapshot.cacheGenerations[1] = 8

        snapshot.clear()

        assertEquals(0, snapshot.count)
        assertEquals(-1, snapshot.cacheGenerations[0])
        assertEquals(-1, snapshot.cacheGenerations[1])
    }

    private fun item(kind: DanmakuKind, text: String): DanmakuItem =
        DanmakuItem(
            Danmaku(
                timeMs = 0,
                mode = 1,
                text = text,
                color = 0xFFFFFF,
                fontSize = 25,
                weight = 0,
            )
        ).also { it.kind = kind }
}
