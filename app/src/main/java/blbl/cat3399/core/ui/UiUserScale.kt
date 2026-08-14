package blbl.cat3399.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import blbl.cat3399.R
import blbl.cat3399.core.theme.ThemePresets
import blbl.cat3399.core.prefs.AppPrefs
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Apply the user UI scale factor ([UiScale]) by overriding [Configuration.densityDpi] for a *subtree*.
 *
 * Why:
 * - [UiDensity] already normalizes device/resolution/system-density to the baseline at Activity attachBaseContext().
 * - [UiScale] is a user preference (0.70~1.40) and in B-plan we apply it explicitly.
 * - Wrapping a Context like this makes dp/sp/@dimen automatically reflect the user scale, removing lots of
 *   per-module manual `applySizing(uiScale)` code.
 *
 * Important:
 * - This is **opt-in**: do NOT replace [BaseActivity.attachBaseContext] with this, otherwise it becomes global.
 * - Avoid double-wrapping: this helper is idempotent (wrapping an already-wrapped context won't stack scales).
 */
object UiUserScale {
    fun isWrapped(context: Context): Boolean = findWrappedContext(context) != null

    fun unwrap(context: Context): Context = findWrappedContext(context)?.originalBase ?: context

    fun wrap(
        base: Context,
        scale: Float = UiScale.factor(base),
    ): Context {
        val desiredScaleRaw =
            scale
                .takeIf { it.isFinite() && it > 0f }
                ?: 1.0f
        val desiredScale = desiredScaleRaw.coerceIn(AppPrefs.UI_SCALE_FACTOR_MIN, AppPrefs.UI_SCALE_FACTOR_MAX)

        val existingWrapped = findWrappedContext(base)
        if (existingWrapped != null && abs(existingWrapped.uiScale - desiredScale) < 0.001f) {
            return base
        }

        val originalBase = existingWrapped?.originalBase ?: base
        if (desiredScale == 1.0f) return originalBase

        val res = originalBase.resources
        val currentDensityDpi = res.configuration.densityDpi
        val targetDensityDpi =
            (currentDensityDpi * desiredScale)
                .roundToInt()
                .coerceAtLeast(1)
        if (currentDensityDpi == targetDensityDpi) return originalBase

        val config = Configuration(res.configuration)
        config.densityDpi = targetDensityDpi

        // Important: use a themed wrapper so AppCompat/MaterialComponents views can inflate correctly.
        //
        // Requirements:
        // - Preserve the current theme preset applied to Activities (ThemePresets.applyTo(activity)),
        //   otherwise switching theme will "randomly not work" whenever UiScale != 1.0.
        // - Provide missing AppCompat/Material attributes for the subtree (e.g. listPreferredItemHeightSmall).
        //
        // Implementation:
        // - Create a configuration context (density override only).
        // - Apply the same theme preset as Activities, but do NOT rely on Theme.setTo(originalTheme):
        //   Theme copying is unreliable on Android 5.x when Resources instances differ.
        // - Apply Theme.Blbl as a fallback with force=false (do not override preset/overlays).
        val configContext = originalBase.createConfigurationContext(config)
        val spec = ThemePresets.resolve(originalBase)
        val themed =
            ContextThemeWrapper(configContext, spec.baseThemeRes).apply {
                spec.overlayRes?.let { theme.applyStyle(it, true) }
                // Fallback for missing AppCompat/Material attributes.
                theme.applyStyle(R.style.Theme_Blbl, false)
            }
        return UserScaledContext(
            base = themed,
            originalBase = originalBase,
            uiScale = desiredScale,
            targetDensityDpi = targetDensityDpi,
        )
    }

    private fun findWrappedContext(context: Context): UserScaledContext? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is UserScaledContext) return current
            current = current.baseContext
        }
        return current as? UserScaledContext
    }

    private class UserScaledContext(
        base: Context,
        val originalBase: Context,
        val uiScale: Float,
        val targetDensityDpi: Int,
    ) : ContextWrapper(base)
}

fun Context.userScaledContext(scale: Float = UiScale.factor(this)): Context = UiUserScale.wrap(this, scale)

fun LayoutInflater.cloneInUserScale(context: Context, scale: Float = UiScale.factor(context)): LayoutInflater {
    return cloneInContext(UiUserScale.wrap(context, scale))
}
