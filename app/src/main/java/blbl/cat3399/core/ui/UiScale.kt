package blbl.cat3399.core.ui

import android.content.Context
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs

object UiScale {
    // Baseline chosen from a known-good TV layout:
    // 1920x1080 with screen scale shown as 1.5x in "设备信息 -> 屏幕".
    const val BASELINE_DENSITY = 1.5f
    const val BASELINE_SHORT_SIDE_PX = 1080f

    /**
     * User-adjustable UI scale factor.
     *
     * Device/resolution/system-scale normalization is handled by [UiDensity] (baseline = 1080p@1.5).
     * This factor is purely user preference, and is expected to be applied consistently by call sites.
     */
    fun factor(context: Context): Float {
        val prefs =
            try {
                BiliClient.prefs
            } catch (_: UninitializedPropertyAccessException) {
                AppPrefs(context.applicationContext)
            }
        val raw = prefs.uiScaleFactor
        return raw.coerceIn(AppPrefs.UI_SCALE_FACTOR_MIN, AppPrefs.UI_SCALE_FACTOR_MAX)
    }
}
