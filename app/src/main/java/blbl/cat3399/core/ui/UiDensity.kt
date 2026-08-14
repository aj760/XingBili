package blbl.cat3399.core.ui

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Force UI density to be based on the chosen baseline, so that the visual size is stable across:
 * - different resolutions (e.g. 720p / 1080p / 4K)
 * - different "screen scale" / density settings (e.g. 1.5x / 3x)
 *
 * Baseline tuning target: 1920x1080 with density x1.50.
 *
 * Implementation: override [Configuration.densityDpi] for the Activity context.
 * This makes XML dp/sp resources and runtime dp/sp conversions behave consistently without
 * requiring every screen to manually multiply by a scale factor.
 */
object UiDensity {
    fun wrap(base: Context): Context {
        val res = base.resources
        val dm = res.displayMetrics

        val shortSidePx =
            min(dm.widthPixels, dm.heightPixels)
                .toFloat()
                .takeIf { it.isFinite() && it > 0f }
                ?: UiScale.BASELINE_SHORT_SIDE_PX

        val targetDensity =
            (UiScale.BASELINE_DENSITY * (shortSidePx / UiScale.BASELINE_SHORT_SIDE_PX))
                .takeIf { it.isFinite() && it > 0f }
                ?: dm.density

        val targetDensityDpi =
            (targetDensity * DisplayMetrics.DENSITY_DEFAULT)
                .roundToInt()
                .coerceAtLeast(1)

        val currentDensityDpi = res.configuration.densityDpi
        if (currentDensityDpi == targetDensityDpi) return base

        val config = Configuration(res.configuration)
        config.densityDpi = targetDensityDpi
        return base.createConfigurationContext(config)
    }
}

