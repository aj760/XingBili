package com.mytvb.feature.player.viewpoint

import com.mytvb.model.player.ViewPoint

/** 归一化后的章节分段：时间已裁剪进片长、互不重叠、按 fromMs 升序。 */
data class NormalizedViewPointSegment(
    val fromMs: Long,
    val toMs: Long,
    val content: String
)

/**
 * 章节（view_points）归一化：过滤无效段、按 from 排序、越界裁剪、
 * 重叠段从上一段结尾处截开；章节之间的空隙保留为无分段区。
 */
object ViewPointSegments {

    fun normalize(viewPoints: List<ViewPoint>, durationMs: Long): List<NormalizedViewPointSegment> {
        if (durationMs <= 0L) return emptyList()
        return viewPoints.asSequence()
            .filter { it.content.isNotBlank() && it.toMs > it.fromMs }
            .sortedBy { it.fromMs }
            .fold(mutableListOf<NormalizedViewPointSegment>()) { acc, point ->
                val from = maxOf(point.fromMs, acc.lastOrNull()?.toMs ?: 0L)
                    .coerceIn(0L, durationMs)
                val to = point.toMs.coerceIn(0L, durationMs)
                if (to > from) {
                    acc += NormalizedViewPointSegment(fromMs = from, toMs = to, content = point.content)
                }
                acc
            }
            .toList()
    }

    /** 命中 positionMs 所在章节；最后一段之后仍算最后一章（延伸到片尾）。 */
    fun findSegmentAt(segments: List<NormalizedViewPointSegment>, positionMs: Long): NormalizedViewPointSegment? {
        if (segments.isEmpty()) return null
        val containing = segments.lastOrNull { positionMs >= it.fromMs && positionMs < it.toMs }
        if (containing != null) return containing
        val last = segments.last()
        return last.takeIf { positionMs >= last.toMs }
    }

    /**
     * 遥控跳章目标点：
     * forward=true → 第一个 fromMs 大于当前位置 1 秒后的章节起点（已在下一章开头附近则跳到再下一章）；
     * forward=false → 最后一个 fromMs 早于当前位置 3 秒的章节起点（刚跳到章首再按"上一章"能回到上一章而非原地）。
     * 无可跳目标返回 null。
     */
    fun seekChapterMs(
        segments: List<NormalizedViewPointSegment>,
        positionMs: Long,
        forward: Boolean
    ): Long? {
        if (segments.isEmpty()) return null
        return if (forward) {
            segments.firstOrNull { it.fromMs > positionMs + CHAPTER_FORWARD_EPSILON_MS }?.fromMs
        } else {
            segments.lastOrNull { it.fromMs < positionMs - CHAPTER_BACKWARD_EPSILON_MS }?.fromMs
        }
    }

    private const val CHAPTER_FORWARD_EPSILON_MS = 1_000L
    private const val CHAPTER_BACKWARD_EPSILON_MS = 3_000L
}
