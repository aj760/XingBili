package blbl.cat3399.core.ui

import android.app.Activity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object Immersive {
    fun apply(activity: Activity, enabled: Boolean) {
        val window = activity.window ?: return
        WindowDisplayPolicy.applyWindow(activity, fullscreen = enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        WindowDisplayPolicy.requestApplyInsets(activity)
    }
}
