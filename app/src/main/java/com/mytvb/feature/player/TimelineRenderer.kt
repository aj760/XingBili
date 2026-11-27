package com.mytvb.feature.player

interface TimelineRenderer {
    fun show(positionMs: Long, durationMs: Long)
    fun showPreview(targetPositionMs: Long, durationMs: Long)
    fun hide()
    fun isActive(): Boolean
}
