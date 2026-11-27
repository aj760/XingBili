package com.mytvb.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartSeekResolverTest {

    @Test
    fun warmReuseNearEndSeekResetsToStart() {
        val resolution = PlaybackStartSeekResolver.resolve(
            requestedSeekMs = 60_290L,
            durationMs = 60_200L,
            reuseSameSource = true
        )

        assertEquals(0L, resolution.positionMs)
        assertTrue(resolution.nearEndReset)
    }

    @Test
    fun warmReuseMidVideoSeekIsKept() {
        val resolution = PlaybackStartSeekResolver.resolve(
            requestedSeekMs = 42_000L,
            durationMs = 60_200L,
            reuseSameSource = true
        )

        assertEquals(42_000L, resolution.positionMs)
        assertFalse(resolution.nearEndReset)
    }

    @Test
    fun coldStartKeepsExplicitSeek() {
        val resolution = PlaybackStartSeekResolver.resolve(
            requestedSeekMs = 60_290L,
            durationMs = 60_200L,
            reuseSameSource = false
        )

        assertEquals(60_290L, resolution.positionMs)
        assertFalse(resolution.nearEndReset)
    }
}
