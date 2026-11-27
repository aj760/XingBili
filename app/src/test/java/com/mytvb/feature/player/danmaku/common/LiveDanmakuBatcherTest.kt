package com.mytvb.feature.player.danmaku.common

import com.mytvb.model.dm.DmModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveDanmakuBatcherTest {
    @Test
    fun mergeDisabledEmitsEveryItemImmediately() {
        val batcher = LiveDanmakuBatcher()

        val first = batcher.offer(dm("来了"), 1_000L, mergeEnabled = false, displayCapacity = 10)
        val second = batcher.offer(dm("来了"), 1_010L, mergeEnabled = false, displayCapacity = 10)

        assertEquals(listOf("来了"), first.map { it.content })
        assertEquals(listOf("来了"), second.map { it.content })
        assertEquals(0, batcher.pendingCount())
    }

    @Test
    fun mergeEnabledCombinesDuplicatesAfterWindow() {
        val batcher = LiveDanmakuBatcher()
        repeat(3) { index ->
            assertEquals(
                emptyList<DmModel>(),
                batcher.offer(dm("抽奖"), 1_000L + index * 10L, mergeEnabled = true, displayCapacity = 100),
            )
        }

        val output = batcher.flushExpired(1_820L, displayCapacity = 100)

        assertEquals(listOf("抽奖 ×3"), output.map { it.content })
        assertEquals(0, batcher.pendingCount())
    }

    @Test
    fun throttleCapsIngressEvenWhenMergeIsDisabled() {
        val batcher = LiveDanmakuBatcher()
        val output = ArrayList<DmModel>()

        repeat(35) { output += batcher.offer(dm("#$it"), 2_000L, false, 100) }

        assertEquals(30, output.size)
    }

    @Test
    fun displayCapacityCapsUnmergedAndDistinctMergedItems() {
        val unmerged = LiveDanmakuBatcher()
        val immediate = ArrayList<DmModel>()
        repeat(10) { immediate += unmerged.offer(dm("#$it"), 1_000L, false, 3) }

        val merged = LiveDanmakuBatcher()
        repeat(5) { merged.offer(dm("组$it"), 2_000L, true, 2) }
        val delayed = merged.flushExpired(2_800L, displayCapacity = 2)

        assertEquals(3, immediate.size)
        assertEquals(2, delayed.size)
    }

    @Test
    fun nextFlushDeadlineIsAnchoredToEarliestPendingItem() {
        val batcher = LiveDanmakuBatcher()
        batcher.offer(dm("第一条"), 1_000L, true, 10)
        batcher.offer(dm("第二条"), 1_700L, true, 10)

        assertEquals(100L, batcher.nextFlushDelayMs(1_700L))
    }

    @Test
    fun disablingMergeFlushesOldWindowThenEmitsNewItemsImmediately() {
        val batcher = LiveDanmakuBatcher()
        batcher.offer(dm("旧窗口"), 1_000L, mergeEnabled = true, displayCapacity = 1)
        batcher.offer(dm("旧窗口"), 1_010L, mergeEnabled = true, displayCapacity = 1)

        val oldWindow = batcher.flushAll(1_100L, displayCapacity = 1)
        val newItem = batcher.offer(dm("新弹幕"), 1_110L, mergeEnabled = false, displayCapacity = 2)

        assertEquals(listOf("旧窗口"), oldWindow.map { it.content })
        assertEquals(listOf("新弹幕"), newItem.map { it.content })
    }

    @Test
    fun clearDropsBufferedItemsAcrossLiveSessions() {
        val batcher = LiveDanmakuBatcher()
        batcher.offer(dm("旧直播"), 1_000L, mergeEnabled = true, displayCapacity = 10)

        batcher.clear()

        assertEquals(emptyList<DmModel>(), batcher.flushAll(5_000L, displayCapacity = 10))
    }

    private fun dm(content: String): DmModel =
        DmModel(
            content = content,
            mode = DanmakuProtocolMode.ROLLING,
            color = 0xFFFFFF,
            fontSize = 25,
        )
}
