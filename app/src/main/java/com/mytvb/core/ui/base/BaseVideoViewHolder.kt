package com.mytvb.core.ui.base

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView

abstract class BaseVideoViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {

    protected val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    protected var longPressTriggered = false

    protected open fun showCardMenu() {}

    protected fun isActivityAlive(): Boolean {
        val activity = itemView.context as? Activity ?: return false
        return !activity.isFinishing && !activity.isDestroyed
    }

    protected fun startLongPress() {
        cancelLongPress()
        longPressTriggered = false
        longPressRunnable = Runnable {
            longPressTriggered = true
            if (isActivityAlive()) {
                showCardMenu()
            }
        }
        handler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
    }

    protected fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }
}
