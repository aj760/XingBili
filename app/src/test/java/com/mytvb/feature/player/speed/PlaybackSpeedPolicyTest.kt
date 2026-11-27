package com.mytvb.feature.player.speed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedPolicyTest {

    @Test
    fun defaultOptionsMatchLegacyHardcodedList() {
        // 与收敛前 MyPlayerSettingView/SettingsFragment 两处硬编码保持一致，行为零变化
        assertTrue(
            floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                .contentEquals(PlaybackSpeedPolicy.DEFAULT_PLAYBACK_SPEED_OPTIONS)
        )
    }

    @Test
    fun storedSpeedOptionsMatchLegacySettingDialogValues() {
        // 字面量与既有存储值（"1.0"/"2.0"/"3.0" 带小数位）一致，避免产生新存储字面量
        assertTrue(
            arrayOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "2.0", "3.0")
                .contentEquals(PlaybackSpeedPolicy.storedSpeedOptions())
        )
    }

    @Test
    fun nearestSnapsToClosestOption() {
        assertEquals(1.5f, PlaybackSpeedPolicy.nearestPlaybackSpeed(1.4f))
        assertEquals(1.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(0.9f))
        assertEquals(3.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(2.6f))
        assertEquals(3.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(8.0f))
        assertEquals(0.25f, PlaybackSpeedPolicy.nearestPlaybackSpeed(0.05f))
    }

    @Test
    fun nearestKeepsExactOptionValue() {
        DEFAULT_OPTIONS.forEach { option ->
            assertEquals(option, PlaybackSpeedPolicy.nearestPlaybackSpeed(option))
        }
    }

    @Test
    fun nonFiniteValueFallsBackToOne() {
        assertEquals(1.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(Float.NaN))
        assertEquals(1.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(Float.POSITIVE_INFINITY))
    }

    @Test
    fun emptyOptionsFallBackToOne() {
        assertEquals(1.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(2.0f, options = FloatArray(0)))
    }

    @Test
    fun tiePrefersSmallerOption() {
        // 1.125 距 1.0 与 1.25 等距，取靠前（较小）档
        assertEquals(1.0f, PlaybackSpeedPolicy.nearestPlaybackSpeed(1.125f))
        assertFalse(PlaybackSpeedPolicy.nearestPlaybackSpeed(1.125f) == 1.25f)
    }

    private companion object {
        private val DEFAULT_OPTIONS = PlaybackSpeedPolicy.DEFAULT_PLAYBACK_SPEED_OPTIONS
    }
}
