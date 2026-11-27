package com.mytvb.feature.player.sponsor

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorProgressMarkerGeometryTest {

    @Test
    fun playedEndIsZeroWhenDurationIsUnknown() {
        assertEquals(0f, SponsorProgressMarkerGeometry.playedEndPx(100f, 30_000L, 0L), 0f)
    }

    @Test
    fun playedEndIsZeroAtPlaybackStart() {
        assertEquals(0f, SponsorProgressMarkerGeometry.playedEndPx(100f, 0L, 60_000L), 0f)
    }

    @Test
    fun playedEndUsesPlaybackRatio() {
        assertEquals(25f, SponsorProgressMarkerGeometry.playedEndPx(100f, 15_000L, 60_000L), 0f)
    }

    @Test
    fun playedEndIsClampedToTrackWidth() {
        assertEquals(100f, SponsorProgressMarkerGeometry.playedEndPx(100f, 120_000L, 60_000L), 0f)
    }
}
