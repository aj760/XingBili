package blbl.cat3399.feature.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import blbl.cat3399.R
import blbl.cat3399.core.ui.ThemeColor
import com.google.android.material.card.MaterialCardView

interface HoldProgressHost {
    var holdProgress: Float
}

private class HoldProgressRingRenderer(
    context: Context,
) {
    private val ringBounds = RectF()
    private val ringStrokePx = context.resources.displayMetrics.density * 2.5f
    private val ringInsetPx = context.resources.displayMetrics.density * 3f

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = ThemeColor.resolve(context, android.R.attr.textColorSecondary, R.color.blbl_text_secondary)
            alpha = 88
        }

    private val progressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = ThemeColor.resolve(context, R.attr.blblAccent, R.color.blbl_blue)
        }

    fun draw(
        canvas: Canvas,
        progress: Float,
        width: Int,
        height: Int,
    ) {
        val safeProgress = progress.coerceIn(0f, 1f)
        if (safeProgress <= 0f) return

        trackPaint.strokeWidth = ringStrokePx
        progressPaint.strokeWidth = ringStrokePx

        val inset = ringInsetPx + ringStrokePx / 2f
        val left = inset
        val top = inset
        val right = width - inset
        val bottom = height - inset
        if (right <= left || bottom <= top) return

        ringBounds.set(left, top, right, bottom)
        canvas.drawArc(ringBounds, -90f, 360f, false, trackPaint)
        canvas.drawArc(ringBounds, -90f, 360f * safeProgress, false, progressPaint)
    }
}

class HoldProgressImageButton : AppCompatImageButton, HoldProgressHost {
    private val ringRenderer = HoldProgressRingRenderer(context)

    override var holdProgress: Float = 0f
        set(value) {
            val safe = value.coerceIn(0f, 1f)
            if (field == safe) return
            field = safe
            invalidate()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ringRenderer.draw(canvas = canvas, progress = holdProgress, width = width, height = height)
    }
}

class HoldProgressCardView : MaterialCardView, HoldProgressHost {
    private val ringRenderer = HoldProgressRingRenderer(context)

    override var holdProgress: Float = 0f
        set(value) {
            val safe = value.coerceIn(0f, 1f)
            if (field == safe) return
            field = safe
            invalidate()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        ringRenderer.draw(canvas = canvas, progress = holdProgress, width = width, height = height)
    }
}
