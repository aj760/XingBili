package com.mytvb.feature.player.view

import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.os.SystemClock
import com.mytvb.core.ui.image.AvatarBadgeView
import com.mytvb.R
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.ui.base.ScaledTextView
import com.mytvb.core.ui.image.ImageLoader
import com.mytvb.feature.player.danmaku.LiveSuperChatSignal

/**
 * 直播 SC（醒目留言）卡片队列：屏幕左侧竖排、底部倒计时进度条、到期渐隐后下方上移。
 * 宿主为 MyPlayerView 浮层之一（弹幕层之上、控制层之下），自身不消费触摸与焦点。
 */
class LiveSuperChatHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LiveSuperChatHost"
        private const val TICK_MS = 100L
        private const val MAX_LEVEL = 10000
        private const val FADE_OUT_MS = 200L
    }

    /** 卡片从上屏到消失的截止时刻（挂钟），pending 补位时据此丢弃已过期条目。 */
    private class PendingEntry(val signal: LiveSuperChatSignal.New, val deadlineMs: Long)

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = false
        isFocusable = false
        layoutTransition = LayoutTransition()
    }

    private val cards: LinkedHashMap<Long, SuperChatCardView> = LinkedHashMap()
    private val pending = ArrayDeque<PendingEntry>()
    private var ticking = false
    private var maxVisible = 1

    private val tickRunnable: Runnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            cards.values.forEach { it.updateProgress(now) }
            cards.values.filter { it.isExpired(now) }.forEach { animateOut(it) }
            if (cards.isNotEmpty()) {
                postDelayed(tickRunnable, TICK_MS)
            } else {
                ticking = false
            }
        }
    }

    init {
        isClickable = false
        isFocusable = false
        val columnLp = LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
        ).apply {
            leftMargin = resources.getDimensionPixelSize(R.dimen.px24)
            topMargin = resources.getDimensionPixelSize(R.dimen.px60)
        }
        addView(column, columnLp)
    }

    fun show(signal: LiveSuperChatSignal.New) {
        // 开关关闭（host GONE）时直接丢弃，不进队列不跑倒计时
        if (visibility != VISIBLE) return
        if (cards.containsKey(signal.id)) return
        val deadline = SystemClock.elapsedRealtime() + signal.durationSec * 1000L
        if (cards.size >= maxVisible) {
            pending.addLast(PendingEntry(signal, deadline))
            AppLog.d(TAG, "SC queued: id=${signal.id} visible=${cards.size}/$maxVisible pending=${pending.size}")
            return
        }
        addCard(signal, deadline)
    }

    fun remove(id: Long) {
        cards[id]?.let { animateOut(it) }
    }

    fun clear() {
        removeCallbacks(tickRunnable)
        ticking = false
        pending.clear()
        // 离开页面场景：跳过布局过渡动画立即清空
        val transition = column.layoutTransition
        column.layoutTransition = null
        cards.values.forEach { column.removeView(it) }
        column.layoutTransition = transition
        cards.clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) recomputeMaxVisible()
    }

    /**
     * 数量上限按屏幕高度动态测算。优先用已上屏卡片的实测高度，没有卡片时用
     * 估算值（头像 px64 + 上下 padding px12/px20，marquee 文本行高随字体略有出入）。
     * 首尾各扣一个 gap 做安全余量，避免最后一张贴边/被裁切。
     */
    private fun recomputeMaxVisible() {
        val hostHeight = height
        if (hostHeight <= 0) return
        val topSafe = resources.getDimensionPixelSize(R.dimen.px60)
        val gap = resources.getDimensionPixelSize(R.dimen.px12)
        val measured = cards.values.firstOrNull()?.height ?: 0
        val cardH = if (measured > 0) measured else
            resources.getDimensionPixelSize(R.dimen.px72) +
                resources.getDimensionPixelSize(R.dimen.px8) +
                resources.getDimensionPixelSize(R.dimen.px14)
        val newMax = ((hostHeight - topSafe - gap) / (cardH + gap)).coerceAtLeast(1)
        if (newMax != maxVisible) {
            maxVisible = newMax
            AppLog.d(TAG, "maxVisible=$maxVisible (hostH=$hostHeight cardH=$cardH measured=${measured > 0})")
            drainPending()
        }
    }

    private fun addCard(signal: LiveSuperChatSignal.New, deadlineMs: Long) {
        val card = SuperChatCardView(context)
        card.bind(signal, deadlineMs)
        (card.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin =
            resources.getDimensionPixelSize(R.dimen.px12)
        column.addView(card)
        cards[signal.id] = card
        startTick()
        // 首张卡片实测高度出来后重算上限（估算值偏小时会多放行，实测后回收差额）
        post { recomputeMaxVisible() }
        AppLog.i(TAG, "SC shown: id=${signal.id} user=${signal.userName} visible=${cards.size}/$maxVisible")
    }

    private fun animateOut(card: SuperChatCardView) {
        if (card.isRemoving) return
        card.isRemoving = true
        card.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction {
            column.removeView(card)
            onCardRemoved(card)
        }
    }

    private fun onCardRemoved(card: SuperChatCardView) {
        val key = cards.entries.firstOrNull { it.value === card }?.key
        if (key != null) cards.remove(key)
        drainPending()
        if (cards.isEmpty()) {
            removeCallbacks(tickRunnable)
            ticking = false
        }
    }

    private fun drainPending() {
        val now = SystemClock.elapsedRealtime()
        while (cards.size < maxVisible && pending.isNotEmpty()) {
            val entry = pending.removeFirst()
            if (now < entry.deadlineMs - FADE_OUT_MS) {
                // 补位时剩余时间缩短，进度条从当前剩余比例起画
                addCard(entry.signal, entry.deadlineMs)
            }
        }
    }

    private fun startTick() {
        if (ticking) return
        ticking = true
        post(tickRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clear()
    }

    // ---- 单张 SC 卡片 ----

    /** 白字黑描边。不能在 super.onDraw 前直接改 paint.color——TextView.onDraw 会先
     *  赋 paint.drawableState，ColorStateList 把颜色覆盖回文字本色（描边变成白描白）。
     *  遍历改用 1x1 纯黑 BitmapShader 换色：shader 不被 drawableState 重置，drawText 认 shader。 */
    private class StrokeTextView(context: Context) : ScaledTextView(context) {

        private val strokeShader: Shader = android.graphics.BitmapShader(
            android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xF2000000.toInt())
            },
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP
        )

        override fun onDraw(canvas: Canvas) {
            val p = paint
            p.style = Paint.Style.STROKE
            p.strokeWidth = (p.textSize * 0.19f).coerceIn(3.5f, 6.5f)
            p.strokeJoin = Paint.Join.ROUND
            p.shader = strokeShader
            super.onDraw(canvas)
            p.style = Paint.Style.FILL
            p.shader = null
            super.onDraw(canvas)
        }
    }

    private class SuperChatCardView(context: Context) : LinearLayout(context) {

        var isRemoving = false

        private val bgDrawable: SuperChatBgDrawable
        private val avatarView: com.mytvb.core.ui.image.AvatarBadgeView
        private val nameText: ScaledTextView
        private val priceText: ScaledTextView
        private val msgText: StrokeTextView
        private val msgClip: FrameLayout
        private var marquee: MarqueeScroller? = null

        private var deadlineMs = 0L
        private var startMs = 0L

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = false
            isFocusable = false
            bgDrawable = SuperChatBgDrawable(context.resources.displayMetrics.density)
            background = bgDrawable

            fun dimen(resId: Int) = context.resources.getDimensionPixelSize(resId)

            setPadding(dimen(R.dimen.px8), dimen(R.dimen.px8), dimen(R.dimen.px8), dimen(R.dimen.px8))

            // 头像+头像框：AvatarBadgeView（框环外圈+头像内缩 0.78，对齐官方比例）
            avatarView = AvatarBadgeView(context).apply {
                layoutParams = LayoutParams(dimen(R.dimen.px72), dimen(R.dimen.px72))
            }
            addView(avatarView)

            nameText = ScaledTextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen(R.dimen.px22).toFloat())
                // B 站粉（#FB7299），对齐官方用户名用色；深色卡上对比足够，不描边（黑边显脏）
                setTextColor(0xFFFB7299.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            priceText = ScaledTextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen(R.dimen.px20).toFloat())
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFD700.toInt())
                maxLines = 1
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = dimen(R.dimen.px10)
                }
            }
            val nameRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = dimen(R.dimen.px8)
                }
            }
            nameRow.addView(nameText)
            nameRow.addView(priceText)

            // 内容区：固定宽裁剪容器 + 单行自然宽文本，超宽时由 MarqueeScroller 平移滚动
            msgText = StrokeTextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen(R.dimen.px32).toFloat())
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                // singleLine 让 wrap 宽=整行文本宽（不受父容器约束），才能测出滚动距离
                isSingleLine = true
                includeFontPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
            msgClip = FrameLayout(context).apply {
                isClickable = false
                isFocusable = false
                clipChildren = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dimen(R.dimen.px4)
                    leftMargin = dimen(R.dimen.px8)
                    rightMargin = dimen(R.dimen.px6)
                }
            }
            msgClip.addView(msgText)

            val textColumn = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                }
            }
            textColumn.addView(nameRow)
            textColumn.addView(msgClip)
            addView(textColumn)

            layoutParams = LinearLayout.LayoutParams(dimen(R.dimen.px500), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun bind(signal: LiveSuperChatSignal.New, deadline: Long) {
            deadlineMs = deadline
            startMs = SystemClock.elapsedRealtime()
            bgDrawable.setAccentColor(signal.backgroundColor)
            // 占位走 ImageLoader 内置深灰底，避免白底机器人图在深色卡上突兀
            ImageLoader.loadCircle(avatarView, signal.avatarUrl.ifBlank { null })
            avatarView.setPendant(signal.avatarFrameUrl.ifBlank { null })
            nameText.text = signal.userName
            priceText.text = "¥${signal.priceYuan}"
            priceText.setTextColor(signal.backgroundColor)
            msgText.text = signal.message
            updateProgress(startMs)
            marquee = MarqueeScroller(msgClip, msgText)
            // post 早于首次 layout（clip.width=0），必须等 layout 完成再测量启动
            msgClip.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    msgClip.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (isAttachedToWindow && !isRemoving) {
                        marquee?.restartIfNeeded()
                    }
                }
            })
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            marquee?.cancel()
        }

        fun isExpired(now: Long): Boolean = now >= deadlineMs

        fun updateProgress(now: Long) {
            val total = (deadlineMs - startMs).coerceAtLeast(1L)
            val remain = (deadlineMs - now).coerceIn(0L, total)
            bgDrawable.progressLevel = ((remain * MAX_LEVEL) / total).toInt()
        }
    }

    /**
     * SC 内容跑马灯：系统 MARQUEE 依赖焦点/selected 驱动，直播浮层拿不到焦点，
     * 改为自驱动 ValueAnimator。超宽时内容拼成「原文+间隔」两份，匀速循环滚动——
     * 滚过一个周期正好是第二份的开头，首尾无缝衔接（B 站直播弹幕同款观感）。
     * ≤15 字时不滚动。
     */
    private class MarqueeScroller(
        private val clip: FrameLayout,
        private val text: ScaledTextView
    ) {
        companion object {
            /** 首尾衔接处的间隔：4 个全角空格拉开距离。 */
            private const val SEPARATOR = "　　　　"
        }

        private var animator: ValueAnimator? = null

        fun restartIfNeeded() {
            cancel()
            if (!clip.isAttachedToWindow || clip.width <= 0) return
            val raw = text.text.toString()
            text.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val rawWidth = text.measuredWidth
            if (rawWidth <= clip.width + 1) {
                // 短文本静态显示
                text.layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT
                return
            }
            // 测量间隔宽，拼接两份「原文+间隔」，循环周期 = 一份的宽度
            text.text = SEPARATOR
            text.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val separatorWidth = text.measuredWidth
            text.text = raw + SEPARATOR + raw + SEPARATOR
            text.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val period = rawWidth + separatorWidth
            // wrap_content 会被父容器 AT_MOST 压到裁剪宽度、文本截断在 view 内，
            // 必须显式按全文本宽布局，平移才有完整的滚动内容
            text.layoutParams.width = text.measuredWidth
            text.requestLayout()
            val speedPxPerSec = 45f
            animator = ValueAnimator.ofFloat(0f, -period.toFloat()).apply {
                duration = (period / speedPxPerSec * 1000f).toLong()
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { anim ->
                    text.translationX = anim.animatedValue as Float
                }
                start()
            }
        }

        fun cancel() {
            animator?.cancel()
            animator = null
            text.translationX = 0f
        }
    }

    /**
     * 卡片背景：深色半透明竖直渐变（透出直播画面不糊字）+ 底部倒计时进度条。
     * 进度条用官方下发的 background_color 作 accent（亮蓝/亮金），深底上对比强。
     */
    private class SuperChatBgDrawable(private val density: Float) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var gradient: android.graphics.LinearGradient? = null
        private var gradientHeight = 0
        private var accentColor = 0xFF68C5F0.toInt()

        // 深蓝紫系底：上 80% 下 70%（用户指定，多次要求更透）。穿卡风险回升由黑描边兜底
        private val topColor = 0xCC252B4A.toInt()
        private val bottomColor = 0xB3181F38.toInt()

        var progressLevel: Int = MAX_LEVEL
            set(value) {
                field = value.coerceIn(0, MAX_LEVEL)
                invalidateSelf()
            }

        fun setAccentColor(color: Int) {
            accentColor = color
            invalidateSelf()
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (bounds.height() > 0) {
                gradientHeight = bounds.height()
                gradient = LinearGradient(
                    0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                    topColor, bottomColor, Shader.TileMode.CLAMP
                )
            }
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val bounds: Rect = bounds
            if (bounds.isEmpty) return
            val radius = 8f * density
            paint.shader = gradient
            canvas.drawRoundRect(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), radius, radius, paint
            )
            paint.shader = null
            if (progressLevel > 0) {
                val barH = 4f * density
                val w = bounds.width().toFloat() * progressLevel / MAX_LEVEL
                if (w > 0f) {
                    paint.color = accentColor
                    canvas.drawRoundRect(
                        bounds.left.toFloat(), bounds.bottom - barH,
                        bounds.left + w, bounds.bottom.toFloat(), radius, radius, paint
                    )
                }
            }
        }

        override fun setAlpha(alpha: Int) = Unit

        @Deprecated("Deprecated in Java")
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
