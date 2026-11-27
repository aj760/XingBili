package com.mytvb.feature.player

import com.mytvb.model.dm.DmModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuPublishDeduplicatePolicyTest {

    @Test
    fun distinctRegularDanmaku_prefersRealIdAndKeepsFirstCopy() {
        val first = dm(id = 101L, progress = 1_000, content = "first")
        val duplicate = dm(id = 101L, progress = 130_000, content = "same-id-from-tail")
        val result = listOf(first, duplicate).distinctRegularDanmaku()

        assertEquals(listOf(first), result)
    }

    @Test
    fun distinctRegularDanmaku_usesStableFallbackForIdlessItems() {
        val first = dm(id = 0L, progress = 125_000, content = "tail", midHash = "u1")
        val duplicate = first.copy(weight = 20, aiFlagScore = -3)
        val next = first.copy(progress = 126_000)
        val result = listOf(first, duplicate, next).distinctRegularDanmaku()

        assertEquals(listOf(first, next), result)
    }

    @Test
    fun distinctRegularDanmaku_treatsNumericIdStrAsSameIdentityAsId() {
        val first = dm(id = 123L, progress = 1_000, content = "first")
        val duplicate = dm(id = 0L, progress = 130_000, content = "tail copy").copy(idStr = "123")
        val result = listOf(first, duplicate).distinctRegularDanmaku()

        assertEquals(listOf(first), result)
    }

    @Test
    fun distinctBatchKeepsIdentityKeysAlignedAfterSorting() {
        val later = dm(id = 2L, progress = 2_000, content = "later")
        val earlier = dm(id = 1L, progress = 1_000, content = "earlier")

        val result = listOf(later, earlier).distinctRegularDanmakuBatch()

        assertEquals(listOf(earlier, later), result.items)
        assertEquals(listOf("id:1", "id:2"), result.identityKeys)
    }

    @Test
    fun appendStyleFilteringDropsExistingItemsFromFullTailResponse() {
        val current = listOf(
            dm(id = 1L, progress = 1_000, content = "old"),
            dm(id = 2L, progress = 119_000, content = "boundary")
        )
        val tailFromFullSegment = listOf(
            dm(id = 1L, progress = 1_000, content = "old"),
            dm(id = 2L, progress = 119_000, content = "boundary"),
            dm(id = 3L, progress = 120_000, content = "new"),
            dm(id = 4L, progress = 130_000, content = "newer")
        )
        val existingKeys = current.mapTo(HashSet(current.size)) { it.danmakuIdentityKey() }

        val appended = tailFromFullSegment
            .filter { it.progress >= 120_000 }
            .filter { existingKeys.add(it.danmakuIdentityKey()) }

        assertEquals(listOf(3L, 4L), appended.map { it.id })
    }

    private fun dm(
        id: Long,
        progress: Int,
        content: String,
        midHash: String = ""
    ): DmModel =
        DmModel(
            id = id,
            progress = progress,
            content = content,
            mode = 1,
            color = 0xFFFFFF,
            fontSize = 25,
            midHash = midHash
        )
}
