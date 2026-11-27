package com.mytvb.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.mytvb.R
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.ui.base.UiScale

class SplashActivity : Activity() {

    private var forwarded = false
    private val createStartMs = SystemClock.elapsedRealtime()

    override fun attachBaseContext(newBase: Context) {
        UiScale.apply(newBase.resources, "splash")
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.i(TAG, "STARTUP SplashActivity.onCreate start")
        if (savedInstanceState == null && shouldFinishDuplicateLauncherLaunch()) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(createSplashView())
        AppLog.i(TAG, "STARTUP SplashActivity.onCreate end elapsed=${SystemClock.elapsedRealtime() - createStartMs}ms")
    }

    override fun onResume() {
        super.onResume()
        forwardToMainIfNeeded()
    }

    private fun createSplashView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundResource(R.color.systemBackgroundColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(ImageView(this@SplashActivity).apply {
                setImageResource(R.mipmap.ic_launcher_loading)
                // MATRIX + min(1, 容器/图) ：容器放得下按原始像素显示，放不下等比缩小，避免低分屏上下裁切
                scaleType = ImageView.ScaleType.MATRIX
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val d = drawable ?: return@addOnLayoutChangeListener
                    val scale = minOf(
                        1f,
                        v.width.toFloat() / d.intrinsicWidth,
                        v.height.toFloat() / d.intrinsicHeight
                    )
                    val dx = (v.width - d.intrinsicWidth * scale) / 2f
                    val dy = (v.height - d.intrinsicHeight * scale) / 2f
                    imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(dx, dy)
                    }
                }
            })
        }
    }

    private fun forwardToMainIfNeeded() {
        if (forwarded) return
        forwarded = true
        Choreographer.getInstance().postFrameCallback {
            if (isFinishing || isDestroyed) return@postFrameCallback
            AppLog.i(TAG, "STARTUP SplashActivity.forwardToMain elapsed=${SystemClock.elapsedRealtime() - createStartMs}ms")
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            finish()
            suppressTransitionAnimation()
        }
    }

    private fun shouldFinishDuplicateLauncherLaunch(): Boolean {
        val launchIntent = intent
        val isLauncherAction = launchIntent?.action == Intent.ACTION_MAIN
        val isLauncherCategory = launchIntent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true ||
            launchIntent?.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) == true
        return !isTaskRoot &&
            isLauncherAction &&
            isLauncherCategory
    }

    private fun suppressTransitionAnimation() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "AppStartup"
    }
}
