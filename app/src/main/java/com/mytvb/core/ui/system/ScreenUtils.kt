package com.mytvb.core.ui.system

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import androidx.appcompat.app.AppCompatActivity

/**
 * 物理屏分辨率 + 刷新率。
 *
 * 注意：[width]/[height] 是面板/输出的物理像素（如 4K 电视的 3840x2160），
 * 与 [Context.getResources] 的 displayMetrics（App 窗口尺寸，电视上常被系统锁在 1080p）不同。
 * 仅供展示/诊断用，UI 渲染仍按 App 窗口尺寸走。
 */
data class ScreenInfo(val width: Int, val height: Int, val refreshRate: Float) {

    /** 形如 `3840x2160@60Hz`。刷新率无效时不附加 @xxHz。 */
    override fun toString(): String {
        val w = "${width}x${height}"
        return if (refreshRate > 1f) "${w}@${refreshRate.toInt()}Hz" else w
    }
}

object ScreenUtils {

    private fun findActivity(context: Context): AppCompatActivity {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is AppCompatActivity) return ctx
            ctx = ctx.baseContext
        }
        throw IllegalStateException("Cannot find Activity from context")
    }

    fun getScreenWidth(context: Context): Int {
        val activity = findActivity(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    /**
     * 读取物理屏分辨率 + 刷新率。
     *
     * 与 [getScreenWidth]（App 窗口尺寸）的区别：
     * Android TV 上系统常把 App UI 锁在 1080p 渲染、4K 仅留给视频 Surface，
     * 导致 displayMetrics 报 1080p。本方法走 [Display.getMode]，返回面板物理像素。
     *
     * 不依赖 Activity：通过 [DisplayManager] 取默认 Display。取不到时回退到 displayMetrics。
     */
    fun getRealScreenInfo(context: Context): ScreenInfo {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            // Display.Mode.getPhysicalWidth/Height/refreshRate 均为 API 23+，minSdk=23 卡得正好。
            val mode = display.mode
            if (mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                return ScreenInfo(mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
            }
        }
        // 兜底：拿不到物理 Display 时退回窗口尺寸（刷新率未知，留 0 走 toString 的不带 @xxHz 分支）。
        val metrics = context.resources.displayMetrics
        return ScreenInfo(metrics.widthPixels, metrics.heightPixels, 0f)
    }
}
