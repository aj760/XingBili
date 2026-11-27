package com.mytvb.feature.player.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.mytvb.R
import com.mytvb.core.common.log.AppLog
import java.util.Locale

class SecondsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val previewContainer: FrameLayout
    private val triangleContainer: LinearLayout
    private val previewImage: ImageView
    private val tvSeconds: TextView
    private val icon1: ImageView
    private val icon2: ImageView
    private val icon3: ImageView

    var animator1: ValueAnimator? = null
    private var animator2: ValueAnimator? = null
    private var animator3: ValueAnimator? = null
    private var animator4: ValueAnimator? = null
    private var animator5: ValueAnimator? = null

    private var cycleDuration: Long = 750L
    private var seconds: Int = 0
    private var indicatorLoopActive = false
    private var hasPreviewBitmap = false
    var isForward: Boolean = true
        private set
    private var iconRes: Int = R.drawable.ic_play_triangle
    private val secondaryTextColor by lazy { Color.argb(196, 255, 255, 255) }

    private val previewSurface: FrameLayout

    companion object {
        private const val TAG = "SecondsView"

        // 预览卡是视频帧的放大镜，锚定画面尺寸而不是 UI 缩放池：宽取屏宽 30%，
        // 超宽屏（宽远大于高）时受屏高 50% 封顶，保证任何宽高比下都随分辨率放大
        private const val PREVIEW_WIDTH_FRACTION = 0.30f
        private const val PREVIEW_MAX_HEIGHT_FRACTION = 0.5f
        private const val PREVIEW_ASPECT_RATIO = 16f / 9f
    }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.seek_seconds_view, this, true)
        previewContainer = view.findViewById(R.id.preview_container)
        previewSurface = view.findViewById(R.id.preview_surface)
        triangleContainer = view.findViewById(R.id.triangle_container)
        previewImage = view.findViewById(R.id.image_preview)
        tvSeconds = view.findViewById(R.id.tv_seconds)
        icon1 = view.findViewById(R.id.icon_1)
        icon2 = view.findViewById(R.id.icon_2)
        icon3 = view.findViewById(R.id.icon_3)

        previewSurface.clipToOutline = true
        previewImage.clipToOutline = true

        applyPreviewCardSize()

        initAnimators()
        setForward(true)
        showPreviewLoading()
    }

    private fun applyPreviewCardSize() {
        val metrics = resources.displayMetrics
        val width = minOf(
            metrics.widthPixels * PREVIEW_WIDTH_FRACTION,
            metrics.heightPixels * PREVIEW_MAX_HEIGHT_FRACTION
        ).toInt().coerceAtLeast(1)
        val height = (width / PREVIEW_ASPECT_RATIO).toInt().coerceAtLeast(1)
        previewContainer.layoutParams?.width = width
        previewContainer.layoutParams?.height = height
        previewContainer.requestLayout()
        // px48 为布局内引用的资源，实际解析值可反推设备命中的 WxH 资源档位
        AppLog.i(
            TAG,
            "previewCard ${width}x${height} screen=${metrics.widthPixels}x${metrics.heightPixels}" +
                " density=${metrics.density} px48=${resources.getDimension(R.dimen.px48)}"
        )
    }

    private fun initAnimators() {
        val duration = cycleDuration / 5

        animator1 = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                icon1.alpha = value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    icon1.alpha = 0f
                    icon2.alpha = 0f
                    icon3.alpha = 0f
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (indicatorLoopActive) {
                        animator2?.start()
                    }
                }
            })
        }

        animator2 = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                icon2.alpha = value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    icon1.alpha = 1f
                    icon2.alpha = 0f
                    icon3.alpha = 0f
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (indicatorLoopActive) {
                        animator3?.start()
                    }
                }
            })
        }

        animator3 = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                icon1.alpha = 1f - value
                icon3.alpha = value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    icon1.alpha = 1f
                    icon2.alpha = 1f
                    icon3.alpha = 0f
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (indicatorLoopActive) {
                        animator4?.start()
                    }
                }
            })
        }

        animator4 = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                icon2.alpha = 1f - value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    icon1.alpha = 0f
                    icon2.alpha = 1f
                    icon3.alpha = 1f
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (indicatorLoopActive) {
                        animator5?.start()
                    }
                }
            })
        }

        animator5 = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                icon3.alpha = 1f - value
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    icon1.alpha = 0f
                    icon2.alpha = 0f
                    icon3.alpha = 1f
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (indicatorLoopActive) {
                        animator1?.start()
                    }
                }
            })
        }
    }

    fun cancel() {
        indicatorLoopActive = false
        cancelAnimators()
        resetIndicators()
    }

    fun m() {
        indicatorLoopActive = false
        cancelAnimators()
        resetIndicators()
    }

    fun start() {
        restartIndicatorLoop()
    }

    fun showPreviewLoading() {
        if (hasPreviewBitmap) {
            return
        }
        hasPreviewBitmap = false
        previewContainer.visibility = View.GONE
        previewImage.setImageDrawable(null)
        previewImage.visibility = View.GONE
        triangleContainer.visibility = View.VISIBLE
    }

    fun showPreviewBitmap(bitmap: Bitmap) {
        hasPreviewBitmap = true
        previewContainer.visibility = View.VISIBLE
        previewImage.setImageBitmap(bitmap)
        previewImage.visibility = View.VISIBLE
        triangleContainer.visibility = View.GONE
    }

    fun hidePreview() {
        hasPreviewBitmap = false
        previewContainer.visibility = View.GONE
        previewImage.setImageDrawable(null)
        previewImage.visibility = View.GONE
        triangleContainer.visibility = View.VISIBLE
    }

    fun ensureIndicatorLoop() {
        if (indicatorLoopActive) {
            return
        }
        indicatorLoopActive = true
        cancelAnimators()
        resetIndicators()
        animator1?.start()
    }

    fun restartIndicatorLoop() {
        indicatorLoopActive = true
        cancelAnimators()
        resetIndicators()
        animator1?.start()
    }

    fun isIndicatorLoopRunning(): Boolean {
        return indicatorLoopActive
    }

    fun hasPreviewBitmap(): Boolean {
        return hasPreviewBitmap
    }

    fun getCycleDuration(): Long {
        return cycleDuration
    }

    fun getIcon(): Int {
        return iconRes
    }

    fun getSeconds(): Int {
        return seconds
    }

    fun getTextView(): TextView {
        return tvSeconds
    }

    fun setCycleDuration(duration: Long) {
        val newDuration = duration / 5
        animator1?.duration = newDuration
        animator2?.duration = newDuration
        animator3?.duration = newDuration
        animator4?.duration = newDuration
        animator5?.duration = newDuration
        cycleDuration = duration
    }

    fun setForward(forward: Boolean) {
        triangleContainer.rotation = if (forward) 0f else 180f
        isForward = forward
    }

    fun setIcon(resId: Int) {
        if (resId > 0) {
            icon1.setImageResource(resId)
            icon2.setImageResource(resId)
            icon3.setImageResource(resId)
        }
        iconRes = resId
    }

    fun setSeekText(sec: Int, progressText: String? = null) {
        val quantity = if (sec == 1) R.plurals.quick_seek_x_second else R.plurals.quick_seek_x_second
        val secondsText = context.resources.getQuantityString(quantity, sec, sec)
        tvSeconds.text = if (progressText.isNullOrBlank()) {
            secondsText
        } else {
            String.format(Locale.getDefault(), "%s(%s)", secondsText, progressText)
        }
        seconds = sec
    }

    fun setDurationText(targetText: String, totalText: String) {
        val builder = SpannableStringBuilder(targetText)
        val secondaryStart = builder.length
        builder.append(" / ")
        builder.append(totalText)
        builder.setSpan(
            ForegroundColorSpan(secondaryTextColor),
            secondaryStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvSeconds.text = builder
        seconds = 0
    }

    fun setSpeedText(speed: Float) {
        val speedStr = if (speed == speed.toInt().toFloat()) {
            "${speed.toInt()}x"
        } else {
            String.format(Locale.getDefault(), "%.1fx", speed)
        }
        tvSeconds.text = speedStr
        seconds = 0
    }

    private fun cancelAnimators() {
        animator1?.cancel()
        animator2?.cancel()
        animator3?.cancel()
        animator4?.cancel()
        animator5?.cancel()
    }

    private fun resetIndicators() {
        icon1.alpha = 0f
        icon2.alpha = 0f
        icon3.alpha = 0f
    }
}
