package com.mytvb.feature.player.progress

/**
 * 续播起播位置决策：三个进度候选（入口历史快照 / 本地进度 / 服务端 lastPlayTime）
 * 取最远者作为续播点，防止任一侧滞后导致进度回退：
 * - 入口历史快照：列表页打开时拉取的 video.historyProgress（可能是几分钟前的旧值）；
 * - 本地进度：PlaybackProgressStore 2s 节拍落盘，本设备即时准确；
 * - 服务端 lastPlayTime：playurl 响应实时返回，跨设备最新。
 *
 * 超过时长 95% 视为看完 → 归 0 从头播；尾部统一钳 1s 防起播即 ENDED。
 */
object PlaybackResumePositionPolicy {
    private const val WATCHED_COMPLETE_RATIO = 0.95f
    private const val TAIL_GUARD_MS = 1_000L

    /**
     * @param entryMs 入口带来的位置（历史快照 / 原地重载的当前位置），无则 0
     * @param localMs 本地 PlaybackProgressStore 进度，无则 0
     * @param serverMs 服务端 lastPlayTime（已确认同 cid），不可用时 0
     * @param durationMs 视频总时长，未知传 0（跳过看完归零与上界钳制）
     */
    fun resolveResumePositionMs(
        entryMs: Long,
        localMs: Long,
        serverMs: Long,
        durationMs: Long
    ): Long {
        val candidate = maxOf(entryMs, localMs, serverMs).coerceAtLeast(0L)
        if (durationMs <= 0L) return candidate
        if (candidate >= durationMs * WATCHED_COMPLETE_RATIO) return 0L
        return candidate.coerceIn(0L, (durationMs - TAIL_GUARD_MS).coerceAtLeast(0L))
    }
}
