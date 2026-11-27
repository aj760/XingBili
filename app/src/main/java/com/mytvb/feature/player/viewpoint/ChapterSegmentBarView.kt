package com.mytvb.feature.player.viewpoint

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.mytvb.R
import com.mytvb.core.common.log.AppLog

/**
 * 控制栏隐藏时常驻底部的章节分段进度条（对齐官方 TV 版形态）：
 * 按章节时长比例分段的胶囊块，段间留隙；已播段整段点亮、当前段按播放进度
 * 部分填充、未播段低亮度；触摸点击某段直接 seek 到该章起点。
 *
 * 触摸命中区为整个 View 高度（布局给足 px60 便于手指点击），
 * 可见的条只绘制在最底部 [R.dimen.px7] 高度内。
 */
class ChapterSegmentBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        private const val TAG = "ChapterSegmentBar"
        /** 已播段：B 站粉 90%。 */
        private const val PLAYED_COLOR = 0xE6FB7299.toInt()
        /** 未播段：白 25%。 */
        private const val UNPLAYED_COLOR = 0x40FFFFFF
        /** 段间间隙（4px 物理宽在低分屏也可见）。 */
        private const val GAP_PX = 4f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    /** 可见条的高度（px7），绘制贴 View 底部。 */
    private val barHeightPx = resources.getDimension(R.dimen.px7)

    private var segments: List<NormalizedViewPointSegment> = emptyList()
    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var onSegmentClick: ((Long) -> Unit)? = null

    fun setOnSegmentClickListener(listener: (Long) -> Unit) {
        onSegmentClick = listener
    }

    fun setSegments(segments: List<NormalizedViewPointSegment>, durationMs: Long) {
        this.segments = segments
        this.durationMs = durationMs
        invalidate()
    }

    fun hasSegments(): Boolean = segments.isNotEmpty()

    fun setProgress(positionMs: Long, durationMs: Long) {
        this.positionMs = positionMs
        this.durationMs = durationMs
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (segments.isEmpty() || durationMs <= 0L || width <= 0) {
            return false
        }
        if (event.actionMasked != MotionEvent.ACTION_UP) {
            return true
        }
        val ratio = (event.x / width.toFloat()).coerceIn(0f, 1f)
        val targetMs = (ratio * durationMs).toLong()
        val segment = segments.lastOrNull { targetMs >= it.fromMs } ?: return true
        AppLog.d(TAG, "viewpoints_trace segment_tap from=${segment.fromMs} content=${segment.content}")
        onSegmentClick?.invoke(segment.fromMs)
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty() || durationMs <= 0L || width <= 0f || height <= 0f) {
            return
        }
        val gapTotal = GAP_PX * (segments.size - 1)
        val barWidth = width - paddingLeft - paddingRight - gapTotal
        if (barWidth <= 0f) {
            return
        }
        // 条只画在最底部 barHeightPx 高度内（其余高度是触摸命中区）
        val bottom = height - paddingBottom.toFloat()
        val top = bottom - barHeightPx
        val radius = barHeightPx / 2f
        var left = paddingLeft.toFloat()
        for (segment in segments) {
            val widthPx = barWidth * ((segment.toMs - segment.fromMs).toFloat() / durationMs)
            val right = left + widthPx
            val playedFraction = playedFractionOf(segment)
            // 未播底色整段铺，已播部分（含当前段的部分进度）叠亮色
            paint.color = UNPLAYED_COLOR
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (playedFraction > 0f) {
                paint.color = PLAYED_COLOR
                rect.set(left, top, left + widthPx * playedFraction, bottom)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            left = right + GAP_PX
        }
    }

    /** 段内已播比例：当前段=进度比例，更早的段=1，更晚的段=0。 */
    private fun playedFractionOf(segment: NormalizedViewPointSegment): Float {
        if (positionMs >= segment.toMs) return 1f
        if (positionMs <= segment.fromMs) return 0f
        val span = (segment.toMs - segment.fromMs).coerceAtLeast(1L)
        return ((positionMs - segment.fromMs).toFloat() / span).coerceIn(0f, 1f)
    }
}
