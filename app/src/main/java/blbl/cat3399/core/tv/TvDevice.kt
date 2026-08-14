package blbl.cat3399.core.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

fun Context.isTvDevice(): Boolean {
    val appContext = applicationContext
    val packageManager = appContext.packageManager
    if (packageManager.hasSystemFeature(FEATURE_TELEVISION)) return true
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true

    val uiModeManager = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

private const val FEATURE_TELEVISION = "android.hardware.type.television"
