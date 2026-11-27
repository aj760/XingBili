package com.mytvb.feature.player.danmaku

import com.mytvb.feature.player.danmaku.common.BitmapMemoryBudget
import com.mytvb.feature.player.danmaku.common.estimatedArgb8888Bytes
import com.mytvb.feature.player.danmaku.common.reclaimUntilBitmapBudgetFits
import com.mytvb.feature.player.danmaku.common.resolveDanmakuBitmapBudgetBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapMemoryBudgetTest {
    @Test
    fun sameOwnerIsCountedOnceAcrossSizeUpdates() {
        val budget = BitmapMemoryBudget(maxBytes = 100L)
        val owner = Any()

        assertTrue(budget.trySetBytes(owner, 60L))
        assertTrue(budget.trySetBytes(owner, 80L))
        assertEquals(80L, budget.snapshot().usedBytes)
        assertEquals(1, budget.snapshot().bitmapCount)
    }

    @Test
    fun rejectsGrowthBeyondTheUnifiedLimit() {
        val budget = BitmapMemoryBudget(maxBytes = 100L)
        val first = Any()
        val second = Any()

        assertTrue(budget.trySetBytes(first, 70L))
        assertFalse(budget.trySetBytes(second, 40L))
        assertEquals(70L, budget.snapshot().usedBytes)
        assertTrue(budget.canFit(30L))
        assertFalse(budget.canFit(31L))
    }

    @Test
    fun releaseReturnsBudgetToZero() {
        val budget = BitmapMemoryBudget(maxBytes = 100L)
        val owner = Any()
        budget.trySetBytes(owner, 90L)

        assertEquals(90L, budget.release(owner))
        assertEquals(BitmapMemoryBudget.Snapshot(0L, 100L, 0), budget.snapshot())
    }

    @Test
    fun reservationCanTransferToTheActualBitmapOwner() {
        val budget = BitmapMemoryBudget(maxBytes = 100L)
        val reservation = Any()
        val bitmapOwner = Any()
        budget.trySetBytes(reservation, 60L)

        assertTrue(budget.replaceOwner(reservation, bitmapOwner, 64L))
        assertEquals(64L, budget.snapshot().usedBytes)
        assertEquals(0L, budget.release(reservation))
        assertEquals(64L, budget.release(bitmapOwner))
    }

    @Test
    fun argbEstimateUsesLongArithmetic() {
        assertEquals(8_000_000_000L, estimatedArgb8888Bytes(50_000, 40_000))
    }

    @Test
    fun pressureReclaimsPoolBeforeSharedLru() {
        val budget = BitmapMemoryBudget(maxBytes = 100L)
        val pooled = Any()
        val shared = Any()
        budget.trySetBytes(pooled, 40L)
        budget.trySetBytes(shared, 50L)
        val order = ArrayList<String>()

        assertTrue(
            reclaimUntilBitmapBudgetFits(
                budget = budget,
                requiredBytes = 60L,
                evictPooled = {
                    val released = budget.release(pooled) > 0L
                    if (released) order += "pool"
                    released
                },
                evictShared = {
                    val released = budget.release(shared) > 0L
                    if (released) order += "shared"
                    released
                },
            )
        )
        assertEquals(listOf("pool", "shared"), order)
        assertEquals(0L, budget.snapshot().usedBytes)
    }

    @Test
    fun pressureRejectsWhenOnlyActiveBitmapsRemain() {
        val budget = BitmapMemoryBudget(maxBytes = 100L)
        budget.trySetBytes(Any(), 90L)

        assertFalse(
            reclaimUntilBitmapBudgetFits(
                budget = budget,
                requiredBytes = 20L,
                evictPooled = { false },
                evictShared = { false },
            )
        )
    }

    @Test
    fun bothEnginesUseTheSameResolutionAndLowMemoryPolicy() {
        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L

        assertEquals(32L * mb, resolveDanmakuBitmapBudgetBytes(1280, 720, gb))
        assertEquals(50L * mb, resolveDanmakuBitmapBudgetBytes(1920, 1080, gb))
        assertEquals(72L * mb, resolveDanmakuBitmapBudgetBytes(3840, 2160, gb))
        assertEquals(16L * mb, resolveDanmakuBitmapBudgetBytes(1280, 720, 256L * mb))
    }
}
