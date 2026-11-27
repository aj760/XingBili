package com.mytvb.feature.player.sponsor

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorSkipTargetPolicyTest {

    @Test
    fun nonOutroCategoryKeepsOneSecondTailGuard() {
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 119_500L,
            durationMs = 120_000L,
            category = SponsorSegment.CATEGORY_SPONSOR
        )
        assertEquals(119_000L, target)
    }

    @Test
    fun outroCategoryMayLandAtVideoEnd() {
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 119_900L,
            durationMs = 120_000L,
            category = SponsorSegment.CATEGORY_OUTRO
        )
        assertEquals(119_900L, target)
    }

    @Test
    fun officialEdCategoryMayLandAtVideoEnd() {
        // 官方片尾跳过=看完整集，允许顶到末尾自然触发 ENDED/连播
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 119_900L,
            durationMs = 120_000L,
            category = SponsorSegment.CATEGORY_OFFICIAL_ED
        )
        assertEquals(119_900L, target)
    }

    @Test
    fun officialOpCategoryKeepsOneSecondTailGuard() {
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 119_500L,
            durationMs = 120_000L,
            category = SponsorSegment.CATEGORY_OFFICIAL_OP
        )
        assertEquals(119_000L, target)
    }

    @Test
    fun outroTargetBeyondDurationClampsToDuration() {
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 150_000L,
            durationMs = 120_000L,
            category = SponsorSegment.CATEGORY_OUTRO
        )
        assertEquals(120_000L, target)
    }

    @Test
    fun unknownDurationKeepsRequestedTarget() {
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 88_000L,
            durationMs = 0L,
            category = SponsorSegment.CATEGORY_SPONSOR
        )
        assertEquals(88_000L, target)
    }

    @Test
    fun negativeRequestedClampsToZero() {
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = -5_000L,
            durationMs = 120_000L,
            category = SponsorSegment.CATEGORY_INTRO
        )
        assertEquals(0L, target)
    }

    @Test
    fun veryShortVideoTailGuardStillYieldsValidRange() {
        // 时长 800ms < 尾部保护 1s：上界退化为 0，不产生负值
        val target = SponsorSkipTargetPolicy.resolveSkipTargetPositionMs(
            requestedMs = 700L,
            durationMs = 800L,
            category = SponsorSegment.CATEGORY_SPONSOR
        )
        assertEquals(0L, target)
    }
}
