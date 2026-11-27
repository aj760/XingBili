package com.mytvb.model.dm

import android.graphics.Path

data class DmMaskData(
    val fps: Int,
    val rawSegments: List<LazyMaskSegment>,
    /** webmask 文件总字节数，由 HTTP Content-Range/Length 拿到。 */
    val totalFileSize: Long = 0L,
)

/**
 * 单个 mask 段的延迟加载条目。
 *
 * 设计为可变 class（不是 data class）：
 *  - `byteOffset`/`byteEnd` 是文件中的绝对位置（不依赖全文件 ByteArray）
 *  - `segData` 是该段独立的字节切片，**按需 Range 下载填充**
 *  - `cachedFrames` 是 [WebmaskParser.parseSegmentFrames] 解析后的帧列表
 *
 * 三阶段：未加载 → 已下载未解析（segData != null）→ 已解析（cachedFrames != null）。
 * 解析完成后 [segData] 会被释放（避免占用约 100KB × N 段的内存）。
 */
class LazyMaskSegment(
    val timeMs: Long,
    val byteOffset: Long,
    val byteEnd: Long,
) {
    @Volatile
    var segData: ByteArray? = null

    @Volatile
    var cachedFrames: List<MaskFrame>? = null

    /** 该段字节数（= byteEnd - byteOffset）。 */
    fun byteLength(): Int = (byteEnd - byteOffset).toInt()
}

data class MaskFrame(
    /** 该帧对应的视频 PTS（毫秒），优先来自 webmask 帧头。 */
    val presentationTimeMs: Long,
    val paths: List<Path>,
    /** SVG 标定宽度（width 属性值），0 表示未知。 */
    val svgWidth: Int = 0,
    /** SVG 标定高度（height 属性值），0 表示未知。 */
    val svgHeight: Int = 0,
    /** SVG viewBox X 偏移（原始值，通常为 0）。 */
    val viewBoxX: Float = 0f,
    /** SVG viewBox Y 偏移（原始值，通常为 0）。 */
    val viewBoxY: Float = 0f,
    /**
     * viewBox 宽度（原始值，不 × 0.1），用于坐标映射的缩放分母。
     * 0 表示未提取到 viewBox，此时 fallback 到 [svgWidth]。
     * 实际 webmask SVG 中 viewBox="0 0 320 180" 与 width="320" 同值，
     * path 坐标经过 × 0.1 后处于 [0, 320] 空间 = viewBoxWidth。
     */
    val viewBoxWidth: Float = 0f,
    /** viewBox 高度（原始值，不 × 0.1），语义同 [viewBoxWidth]。 */
    val viewBoxHeight: Float = 0f,
)
