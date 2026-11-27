package com.mytvb.feature.player.sponsor

/**
 * 空降助手跳转目标位置钳制：自动跳过 seek 到片段尾时，
 * 防止目标贴着视频总时长导致 Media3 直接进入 STATE_ENDED（画面定格/提前结算播放结束）。
 */
object SponsorSkipTargetPolicy {
    private const val TAIL_GUARD_MS = 1_000L

    fun resolveSkipTargetPositionMs(
        requestedMs: Long,
        durationMs: Long,
        category: String
    ): Long {
        val requested = requestedMs.coerceAtLeast(0L)
        if (durationMs <= 0L) return requested
        return if (category == SponsorSegment.CATEGORY_OUTRO || category == SponsorSegment.CATEGORY_OFFICIAL_ED) {
            // 片尾类允许顶到末尾，自然播完触发 ENDED 是期望行为
            requested.coerceIn(0L, durationMs)
        } else {
            requested.coerceIn(0L, (durationMs - TAIL_GUARD_MS).coerceAtLeast(0L))
        }
    }
}
