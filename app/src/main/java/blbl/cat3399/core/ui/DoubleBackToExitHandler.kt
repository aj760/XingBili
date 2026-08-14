package blbl.cat3399.core.ui

import android.content.Context
import android.os.SystemClock

internal class DoubleBackToExitHandler(
    private val context: Context,
    private val windowMs: Long,
    private val hintText: String = "再按一次退出播放器",
    private val onFirstBack: () -> Unit = {},
) {
    private var lastBackAtMs: Long = 0L

    fun shouldExit(enabled: Boolean): Boolean {
        if (!enabled) return true
        val now = SystemClock.uptimeMillis()
        val isSecond = now - lastBackAtMs <= windowMs
        if (isSecond) return true
        lastBackAtMs = now
        AppToast.show(context, hintText)
        onFirstBack()
        return false
    }
}
