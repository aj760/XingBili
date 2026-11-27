package com.mytvb.feature.player.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DmMaskLifecyclePolicyTest {
    @Test
    fun framesRunOnlyForVisibleReadyPlayback() {
        assertTrue(shouldRunMaskFrames(true, true, true, true, true))
        assertFalse(shouldRunMaskFrames(true, true, false, true, true))
        assertFalse(shouldRunMaskFrames(true, true, true, false, true))
        assertFalse(shouldRunMaskFrames(true, false, true, true, true))
        assertFalse(shouldRunMaskFrames(false, true, true, true, true))
    }

    @Test
    fun staleAndDisposedLoadsCannotCommit() {
        assertTrue(isCurrentMaskLoad(4L, 4L, disposed = false))
        assertFalse(isCurrentMaskLoad(3L, 4L, disposed = false))
        assertFalse(isCurrentMaskLoad(4L, 4L, disposed = true))
    }

    @Test
    fun onlyTheLatestSeekCanLeaveFrozenState() {
        assertTrue(shouldCompleteMaskSeek(false, true, 7L, 7L))
        assertFalse(shouldCompleteMaskSeek(false, true, 6L, 7L))
        assertFalse(shouldCompleteMaskSeek(false, false, 7L, 7L))
        assertFalse(shouldCompleteMaskSeek(true, true, 7L, 7L))
    }
}
