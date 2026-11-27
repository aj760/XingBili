package com.mytvb.feature.player

import androidx.core.view.isVisible
import com.mytvb.feature.player.sponsor.SponsorProgressMarkerView
import com.mytvb.feature.player.sponsor.SponsorSegment

class SlimTimelineRenderer(
    private val markerView: SponsorProgressMarkerView
) : TimelineRenderer {

    private var active = false

    override fun show(positionMs: Long, durationMs: Long) {
        active = true
        markerView.isVisible = true
        markerView.setProgress(positionMs, durationMs)
    }

    override fun showPreview(targetPositionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) {
            hide()
            return
        }
        active = true
        markerView.isVisible = true
        markerView.setProgress(targetPositionMs, durationMs)
    }

    override fun hide() {
        active = false
        markerView.isVisible = false
    }

    override fun isActive(): Boolean = active

    fun setSegments(segments: List<SponsorSegment>) {
        markerView.setSegments(segments)
    }

    fun setSponsorDuration(durationMs: Long) {
        markerView.setSponsorDuration(durationMs)
    }

    fun setChapterStarts(startsMs: List<Long>, durationMs: Long) {
        markerView.setChapterStarts(startsMs, durationMs)
    }
}
