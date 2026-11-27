package com.mytvb.feature.player.view

import com.mytvb.feature.player.danmaku.common.DanmakuDuplicateMergePolicy
import com.mytvb.model.dm.DmModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuDuplicateMergePolicyTest {

    @Test
    fun merge_mergesThreeDuplicatesWithCountSuffix() {
        val result = DanmakuDuplicateMergePolicy.merge(
            listOf(
                dm(id = 1, progress = 1_000, content = "抽奖", weight = 1),
                dm(id = 2, progress = 1_600, content = "抽奖", weight = 5),
                dm(id = 3, progress = 2_200, content = "抽奖", weight = 3)
            )
        )

        assertEquals(1, result.size)
        assertEquals("抽奖 ×3", result.first().content)
        assertEquals(5, result.first().weight)
        assertEquals(27, result.first().fontSize)
    }

    @Test
    fun merge_keepsTwoDuplicatesWithoutSuffix() {
        val result = DanmakuDuplicateMergePolicy.merge(
            listOf(
                dm(id = 1, progress = 1_000, content = "来了"),
                dm(id = 2, progress = 1_400, content = "来了")
            )
        )

        assertEquals(1, result.size)
        assertEquals("来了", result.first().content)
    }

    @Test
    fun merge_doesNotMergeAcrossWindow() {
        val result = DanmakuDuplicateMergePolicy.merge(
            listOf(
                dm(id = 1, progress = 1_000, content = "哈哈"),
                dm(id = 2, progress = 4_001, content = "哈哈")
            )
        )

        assertEquals(2, result.size)
    }

    @Test
    fun merge_skipsAdvancedAndScriptDanmaku() {
        val result = DanmakuDuplicateMergePolicy.merge(
            listOf(
                dm(id = 1, progress = 1_000, content = "高级", mode = 7),
                dm(id = 2, progress = 1_100, content = "高级", mode = 7),
                dm(id = 3, progress = 1_200, content = "def text", mode = 9),
                dm(id = 4, progress = 1_300, content = "def text", mode = 9)
            )
        )

        assertEquals(4, result.size)
        assertFalse(result.any { it.content.contains("×") })
    }

    @Test
    fun merge_unsortedInputStillProducesSortedMergedResult() {
        // 有序性守卫兜底：调用方已保证有序（快路径零排序），乱序输入回退到显式排序，
        // 合并语义与排序输入完全一致。
        val result = DanmakuDuplicateMergePolicy.merge(
            listOf(
                dm(id = 2, progress = 1_600, content = "抽奖", weight = 5),
                dm(id = 3, progress = 2_200, content = "抽奖", weight = 3),
                dm(id = 5, progress = 3_000, content = "单条"),
                dm(id = 1, progress = 1_000, content = "抽奖", weight = 1),
                dm(id = 4, progress = 2_800, content = "单条")
            )
        )

        assertEquals(2, result.size)
        // 输出按 progress 升序。
        assertTrue(result.zipWithNext().all { (a, b) -> a.progress <= b.progress })
        // 2 秒窗口内的三条"抽奖"合并为一条（≥3 条显示计数）；
        // 2_800 与 3_000 相差 0.2s 合并为一条（2 条不显示计数）。
        assertEquals("抽奖 ×3", result[0].content)
        assertEquals("单条", result[1].content)
    }

    private fun dm(
        id: Long,
        progress: Int,
        content: String,
        mode: Int = 1,
        color: Int = 0xFFFFFF,
        weight: Int = 0
    ): DmModel =
        DmModel(
            id = id,
            progress = progress,
            content = content,
            mode = mode,
            color = color,
            fontSize = 25,
            weight = weight
        )
}
