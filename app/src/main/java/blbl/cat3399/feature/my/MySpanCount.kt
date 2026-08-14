package blbl.cat3399.feature.my

import android.content.res.Resources
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.GridSpanPolicy

fun spanCountForWidth(resources: Resources): Int {
    val prefs = BiliClient.prefs
    val dm = resources.displayMetrics
    val widthDp = dm.widthPixels / dm.density
    return GridSpanPolicy.fixedSpanCountForWidthDp(
        widthDp = widthDp,
        overrideSpanCount = prefs.gridSpanCount,
    )
}
