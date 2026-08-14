package blbl.cat3399.feature.player.danmaku.model

import android.graphics.Bitmap
import blbl.cat3399.core.model.Danmaku

internal enum class DanmakuKind {
    SCROLL,
    TOP,
    BOTTOM,
}

internal enum class DanmakuCacheState {
    Init,
    Measuring,
    Measured,
    Rendering,
    Rendered,
    Error,
}

internal class DanmakuItem(
    val data: Danmaku,
) {
    // ---- Measure/cache (updated by cache thread) ----
    @Volatile var measuredWidthPx: Float = Float.NaN
    @Volatile var measuredHeightPx: Float = Float.NaN
    @Volatile var measureGeneration: Int = -1

    @Volatile var cacheBitmap: Bitmap? = null
    @Volatile var cacheGeneration: Int = -1
    @Volatile var cacheState: DanmakuCacheState = DanmakuCacheState.Init

    // Optional: cached parse result for inline segments.
    @Volatile var inlineSegments: List<DanmakuInlineSegment>? = null

    // ---- Active state (action thread only) ----
    var kind: DanmakuKind = DanmakuKind.SCROLL
    var lane: Int = 0
    var startTimeMs: Int = 0
    var durationMs: Int = 0
    var pxPerMs: Float = 0f
    var textWidthPx: Float = 0f
    var layoutTopPx: Float = 0f

    fun timeMs(): Int = data.timeMs
}
