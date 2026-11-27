package com.mytvb.model.dm

/**
 * 基于 segment 的懒加载时间线。
 *
 * 按 Bilibili 参考链路重构：
 *  - 播放器 clock 的 position 是唯一时间源
 *  - floor 选帧：只取当前 PTS **已经到达**的 mask 帧，不取未来帧
 *  - 空帧不前向填充、缺帧不复用旧遮罩 —— 防闪烁由 DanmakuMaskHostLayout 层兜底
 *
 * 主线程查询绝不触发 IO（dispatchDraw 不能同步阻塞）。
 */
class DmMaskTimeline(
    private val segments: List<LazyMaskSegment>,
    private val fps: Int,
) {

    companion object {
        fun build(data: DmMaskData): DmMaskTimeline? {
            val segments = data.rawSegments
            if (segments.isEmpty()) return null
            return DmMaskTimeline(segments, data.fps)
        }
    }

    /**
     * 查询指定 PTS 的 mask 帧。
     *
     * 策略：floor —— 只返回 [ptsMs] 时刻**已经存在**的帧，
     * 不提前取未来帧（参考文档 9.2：round-to-nearest 会让 mask 先于视频运动）。
     *
     * @return null 表示段未加载或帧列表为空；由调用方决定如何兜底
     */
    fun queryAt(ptsMs: Long): MaskFrame? {
        if (segments.isEmpty()) return null
        val segIdx = segmentIndexAt(ptsMs)
        return queryInSegment(segIdx, ptsMs)
    }

    /**
     * 根据 PTS 粗略定位当前段索引。用于预加载判断。
     */
    fun segmentIndexAt(ptsMs: Long): Int {
        if (segments.isEmpty()) return 0
        if (ptsMs <= segments.first().timeMs) return 0
        if (ptsMs >= segments.last().timeMs) return segments.lastIndex

        // binarySearchBy 返回:
        //   >= 0 : 精确命中
        //   < 0  : -(insertionPoint) - 1，insertionPoint 是第一个 > ptsMs 的位置
        //          我们需要前一个段（floor），所以取 -(it + 1) - 1
        return segments.binarySearchBy(ptsMs) { it.timeMs }
            .let { if (it < 0) -(it + 1) - 1 else it }
            .coerceIn(0, segments.lastIndex)
    }

    /** 检查指定段是否已缓存帧。越界索引返回 false（未缓存）。 */
    fun isSegmentCached(segIdx: Int): Boolean {
        if (segIdx < 0 || segIdx >= segments.size) return false
        return segments[segIdx].cachedFrames != null
    }

    fun totalSegments(): Int = segments.size

    // ---- 内部实现 ----

    private fun queryInSegment(segIdx: Int, ptsMs: Long): MaskFrame? {
        val segment = segments[segIdx]

        // 只查缓存，不触发 IO
        val frames = segment.cachedFrames ?: return null
        if (frames.isEmpty()) return null

        // 优先使用 webmask 帧头携带的真实 PTS。
        // 真实段格式每帧都有 uint64 presentationTimeMs；用它 floor 选帧，
        // 比按 segmentDuration / frameCount 推算更贴近参考。
        val byFramePts = frames.binarySearchBy(ptsMs) { it.presentationTimeMs }
            .let { if (it < 0) -(it + 1) - 1 else it }
        if (byFramePts >= 0) {
            return frames[byFramePts.coerceIn(0, frames.size - 1)]
        }

        return frames.first()
    }
}
