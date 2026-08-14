package blbl.cat3399.core.ui

import android.content.res.Resources
import android.view.View
import blbl.cat3399.R
import kotlin.math.roundToInt

object BackButtonSizingHelper {
    fun applySidebarSizing(
        view: View,
        resources: Resources,
        sidebarScale: Float,
        sizeDimenRes: Int = R.dimen.sidebar_settings_size,
        paddingDimenRes: Int = R.dimen.sidebar_settings_padding,
    ) {
        val sizePx =
            (resources.getDimensionPixelSize(sizeDimenRes) * sidebarScale)
                .roundToInt()
                .coerceAtLeast(1)
        val paddingPx =
            (resources.getDimensionPixelSize(paddingDimenRes) * sidebarScale)
                .roundToInt()
                .coerceAtLeast(0)
        applySizeAndPadding(view = view, sizePx = sizePx, paddingPx = paddingPx)
    }

    fun applySizeAndPadding(view: View, sizePx: Int, paddingPx: Int) {
        val lp = view.layoutParams ?: return
        if (lp.width != sizePx || lp.height != sizePx) {
            lp.width = sizePx
            lp.height = sizePx
            view.layoutParams = lp
        }
        if (
            view.paddingLeft != paddingPx ||
            view.paddingTop != paddingPx ||
            view.paddingRight != paddingPx ||
            view.paddingBottom != paddingPx
        ) {
            view.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }
    }
}

