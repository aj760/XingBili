package blbl.cat3399.core.ui

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.DimenRes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Centralized UI scaling helpers for applying the user-adjustable [UiScale] factor.
 *
 * This is intentionally separate from [UiDensity]:
 * - [UiDensity] normalizes device/resolution/system-density into a stable baseline.
 * - [UiScale] (and this helper) applies the user preference factor on top, but ONLY where call sites opt in.
 */
class UiScaler(
    private val resources: Resources,
    val scale: Float,
) {
    fun px(@DimenRes id: Int): Int = resources.getDimensionPixelSize(id)

    fun pxF(@DimenRes id: Int): Float = resources.getDimension(id)

    fun scaledDimenPx(@DimenRes id: Int, minPx: Int = 0): Int = scaledPx(px(id), minPx = minPx)

    fun scaledDimenPxF(@DimenRes id: Int, minPx: Float = 0f): Float = scaledPxF(pxF(id), minPx = minPx)

    fun scaledPx(basePx: Int, minPx: Int = 0): Int {
        return (basePx * scale).roundToInt().coerceAtLeast(minPx)
    }

    fun scaledPxF(basePx: Float, minPx: Float = 0f): Float {
        return (basePx * scale).coerceAtLeast(minPx)
    }
}

fun Context.uiScaler(scale: Float = UiScale.factor(this)): UiScaler {
    // When the view tree is inflated with a user-scaled Context (see [UiUserScale]),
    // dp/sp/@dimen are already scaled via densityDpi override. Applying [UiScale] again would double-scale.
    //
    // Keep the original API so existing call sites don't need to be migrated all at once.
    val effectiveScale =
        if (UiUserScale.isWrapped(this)) {
            1.0f
        } else {
            scale
        }
    return UiScaler(resources, effectiveScale)
}

fun TextView.setTextSizePxIfChanged(sizePx: Float, epsilonPx: Float = 0.01f) {
    if (!sizePx.isFinite() || sizePx <= 0f) return
    if (abs(textSize - sizePx) <= epsilonPx) return
    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
}
