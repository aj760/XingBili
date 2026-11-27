package com.mytvb.core.ui.focus.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 返回恢复窗口仲裁决策的单元测试（返回焦点风暴修复的核心决策层）。
 *
 * 风暴背景：多路恢复并发时各自派生 RVFocusOp 重试链、focusToken 互踩 STALE，
 * 约 1 秒内数十次 requestFocus 全失败。仲裁不变量：
 * 1. 窗口内同一时刻只有一个恢复意图（目标不一致的外部请求并入，不派生新链）；
 * 2. 同目标的重复请求幂等（已有待聚焦意图时跳过，不重复排队）；
 * 3. 用户主动刷新中止恢复（意图已变），其余数据变化等待落地后单次恢复。
 */
class ReturnRestoreArbiterTest {

    // ---- shouldMergeRequest ----

    @Test
    fun noActiveIntentDoesNotMerge() {
        // 窗口未开（activePosition=null）：外部请求走正常路径
        assertFalse(
            ReturnRestoreArbiter.shouldMergeRequest(
                activePosition = null,
                requestedPosition = 5,
                hasPendingFocusForActive = false
            )
        )
    }

    @Test
    fun differentTargetMergesIntoActiveIntent() {
        // 风暴场景：Fragment 层旧轮询的目标(3)与窗口意图(5)不一致 → 并入窗口意图
        assertTrue(
            ReturnRestoreArbiter.shouldMergeRequest(
                activePosition = 5,
                requestedPosition = 3,
                hasPendingFocusForActive = false
            )
        )
    }

    @Test
    fun sameTargetWithPendingFocusIsIdempotent() {
        // 目标一致且 RVFocusOp 已有同位置待聚焦意图 → 幂等跳过
        assertTrue(
            ReturnRestoreArbiter.shouldMergeRequest(
                activePosition = 5,
                requestedPosition = 5,
                hasPendingFocusForActive = true
            )
        )
    }

    @Test
    fun sameTargetWithoutPendingFocusFallsThrough() {
        // 目标一致但无待聚焦意图（前一条链已结束）→ 放行重新派生
        assertFalse(
            ReturnRestoreArbiter.shouldMergeRequest(
                activePosition = 5,
                requestedPosition = 5,
                hasPendingFocusForActive = false
            )
        )
    }

    // ---- shouldAbortForDataChange ----

    @Test
    fun userRefreshAbortsRestore() {
        // 用户主动刷新：锚点即将被清、意图已变，恢复继续只会互踩
        assertTrue(ReturnRestoreArbiter.shouldAbortForDataChange(TvDataChangeReason.USER_REFRESH))
    }

    @Test
    fun dataChangesWaitForLandedRestore() {
        // 数据落地（刷新重建/追加/删除）：等待布局完成后单次恢复
        assertFalse(
            ReturnRestoreArbiter.shouldAbortForDataChange(TvDataChangeReason.REPLACE_PRESERVE_ANCHOR)
        )
        assertFalse(ReturnRestoreArbiter.shouldAbortForDataChange(TvDataChangeReason.APPEND))
        assertFalse(ReturnRestoreArbiter.shouldAbortForDataChange(TvDataChangeReason.REMOVE_ITEM))
    }
}
