package blbl.cat3399.feature.feed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import blbl.cat3399.R

/**
 * Bilibili-style "一键三连" overlay for the immersive feed.
 *
 * - [startHoldProgress] shows a 5s circular progress bar + hint while the user holds the OK key.
 * - [cancelHoldProgress] hides everything (released early → normal OK = open player).
 * - [playSuccess] fires the celebratory animation: an expanding shine ring, the like / coin / fav
 *   icons popping in sequence, a bold "三连" stamp, and an outward particle burst.
 *
 * All child views are created in code and tinted via [R.color] theme resources, so no layout XML
 * carries a hardcoded color (keeps the `checkThemeTokens` build task happy).
 */
class TripleActionOverlay(private val container: FrameLayout) {

    private val ctx: Context get() = container.context
    private val dm get() = ctx.resources.displayMetrics

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Runnable>()

    var isPlaying: Boolean = false
        private set

    private var holdGroup: LinearLayout? = null
    private var holdBar: ProgressBar? = null
    private var holdText: TextView? = null
    private var holdAnim: ValueAnimator? = null

    private fun schedule(delay: Long, block: () -> Unit) {
        val r = Runnable(block)
        pending.add(r)
        mainHandler.postDelayed(r, delay)
    }

    private fun clearScheduled() {
        pending.forEach { mainHandler.removeCallbacks(it) }
        pending.clear()
    }

    fun startHoldProgress() {
        clearScheduled()
        holdAnim?.cancel()
        container.visibility = View.VISIBLE
        container.alpha = 1f
        container.setBackgroundColor(0x00000000)
        if (holdGroup == null) {
            holdGroup = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            holdBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (220 * dm.density).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                max = 100
                progress = 0
                progressTintList = ContextCompat.getColorStateList(ctx, R.color.blbl_pink)
            }
            holdText = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (12 * dm.density).toInt() }
                text = ctx.getString(R.string.feed_triple_holding)
                setTextColor(ContextCompat.getColor(ctx, R.color.blbl_text))
                textSize = 16f
                gravity = Gravity.CENTER
            }
            holdGroup!!.addView(holdBar)
            holdGroup!!.addView(holdText)
        }
        if (holdGroup!!.parent == null) {
            container.addView(
                holdGroup,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
        holdGroup!!.visibility = View.VISIBLE
        holdBar!!.progress = 0
        holdAnim = ValueAnimator.ofInt(0, 100).apply {
            duration = 5000L
            addUpdateListener { holdBar!!.progress = it.animatedValue as Int }
            start()
        }
    }

    /** Hide everything (released early → normal OK behaviour opens the player). */
    fun cancelHoldProgress() {
        holdAnim?.cancel(); holdAnim = null
        clearScheduled()
        holdGroup?.visibility = View.GONE
        container.visibility = View.GONE
        container.alpha = 1f
    }

    /** Hide only the hold UI; the container stays visible for the success animation. */
    fun endHoldForSuccess() {
        holdAnim?.cancel(); holdAnim = null
        holdGroup?.visibility = View.GONE
    }

    fun playSuccess() {
        clearScheduled()
        endHoldForSuccess()
        isPlaying = true
        container.visibility = View.VISIBLE
        container.alpha = 0f
        container.setBackgroundColor(0x00000000)
        container.animate().alpha(1f).setDuration(180).start()
        container.removeAllViews()
        holdGroup = null
        holdBar = null
        holdText = null

        val w = container.measuredWidth.takeIf { it > 0 } ?: dm.widthPixels
        val h = container.measuredHeight.takeIf { it > 0 } ?: dm.heightPixels
        val pink = ContextCompat.getColor(ctx, R.color.blbl_pink)

        // 1) expanding shine ring
        val shineSize = (46 * dm.density).toInt()
        val shine = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((4 * dm.density).toInt(), pink)
                setColor(0x00000000)
            }
            layoutParams = FrameLayout.LayoutParams(shineSize, shineSize, Gravity.CENTER)
            scaleX = 0.3f
            scaleY = 0.3f
            alpha = 0.85f
        }
        container.addView(shine)
        shine.animate().scaleX(4.2f).scaleY(4.2f).alpha(0f).setDuration(720)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()

        // 2) three action icons popping in sequence
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
        val iconRes = listOf(
            R.drawable.ic_action_like,
            R.drawable.ic_action_coin,
            R.drawable.ic_action_fav,
        )
        val margin = (20 * dm.density).toInt()
        val iconSize = (56 * dm.density).toInt()
        val icons = iconRes.map { res ->
            ImageView(ctx).apply {
                setImageResource(res)
                imageTintList = ContextCompat.getColorStateList(ctx, R.color.blbl_pink)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                scaleX = 0f
                scaleY = 0f
                alpha = 0f
            }.also { row.addView(it) }
        }
        container.addView(row)
        icons.forEachIndexed { i, v ->
            schedule(140L + i * 200) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360)
                    .setInterpolator(OvershootInterpolator(2.4f)).start()
            }
        }

        // 3) "三连" stamp + particle burst
        val badge = TextView(ctx).apply {
            text = "三连"
            setTextColor(pink)
            textSize = 32f
            gravity = Gravity.CENTER
            paint.isFakeBoldText = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply { topMargin = (96 * dm.density).toInt() }
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
        }
        container.addView(badge)
        schedule(820L) {
            badge.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420)
                .setInterpolator(AnticipateOvershootInterpolator()).start()
            burstParticles(w, h, pink)
        }

        // 4) fade out & cleanup
        schedule(2100L) {
            container.animate().alpha(0f).setDuration(420)            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    container.visibility = View.GONE
                    container.removeAllViews()
                    holdGroup = null
                    holdBar = null
                    holdText = null
                    isPlaying = false
                }
            }).start()
        }
    }

    private fun burstParticles(w: Int, h: Int, pink: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val dotSize = (9 * dm.density).toInt()
        repeat(16) { i ->
            val ang = Math.PI * 2 * i / 16
            val dist = (130 + (i % 3) * 46) * dm.density
            val dot = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(pink)
                }
                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER)
            }
            container.addView(dot)
            val dx = (Math.cos(ang) * dist).toFloat() + (cx - w / 2f)
            val dy = (Math.sin(ang) * dist).toFloat() + (cy - h / 2f)
            dot.animate().translationX(dx).translationY(dy).alpha(0f)
                .scaleX(0.2f).scaleY(0.2f).setDuration(720)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    fun cancelAll() {
        clearScheduled()
        holdAnim?.cancel(); holdAnim = null
        container.animate().cancel()
        container.visibility = View.GONE
        container.alpha = 1f
        container.removeAllViews()
        holdGroup = null
        holdBar = null
        holdText = null
        isPlaying = false
    }
}
