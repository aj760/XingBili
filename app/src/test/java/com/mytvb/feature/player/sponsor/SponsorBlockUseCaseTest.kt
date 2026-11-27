package com.mytvb.feature.player.sponsor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsorBlockUseCaseTest {

    @Test
    fun muteSegmentTriggersMuteOnceOnForwardEnter() {
        val useCase = SponsorBlockUseCase()
        useCase.loadForTest(
            listOf(muteSegment(uuid = "m1", startMs = 10_000L, endMs = 15_000L))
        )

        // 向前跨入片段起点 → MUTE（仅一次）
        val first = useCase.checkPosition(positionMs = 9_000L, autoSkip = true)
        assertNull(first)
        val entered = useCase.checkPosition(positionMs = 10_500L, autoSkip = true)
        assertEquals(SponsorBlockUseCase.SkipAction.MUTE, entered?.action)
        // 片段已消费，片段内后续轮询不再返回
        val inside = useCase.checkPosition(positionMs = 12_000L, autoSkip = true)
        assertNull(inside)
        val after = useCase.checkPosition(positionMs = 16_000L, autoSkip = true)
        assertNull(after)
    }

    @Test
    fun seekBackIntoMuteSegmentRetriggersAfterUserSeek() {
        val useCase = SponsorBlockUseCase()
        useCase.loadForTest(
            listOf(muteSegment(uuid = "m1", startMs = 10_000L, endMs = 15_000L))
        )

        useCase.checkPosition(positionMs = 10_500L, autoSkip = true) // 首次静音
        // 用户手动 seek 回片段前 → onUserSeek 重置已跳过集合
        useCase.onUserSeek(8_000L)
        val reEntered = useCase.checkPosition(positionMs = 10_500L, autoSkip = true)
        assertEquals(SponsorBlockUseCase.SkipAction.MUTE, reEntered?.action)
    }

    @Test
    fun consecutiveMuteSegmentsBothTrigger() {
        val useCase = SponsorBlockUseCase()
        useCase.loadForTest(
            listOf(
                muteSegment(uuid = "m1", startMs = 10_000L, endMs = 12_000L),
                muteSegment(uuid = "m2", startMs = 12_000L, endMs = 14_000L)
            )
        )

        assertEquals(
            SponsorBlockUseCase.SkipAction.MUTE,
            useCase.checkPosition(positionMs = 10_500L, autoSkip = true)?.action
        )
        assertEquals(
            SponsorBlockUseCase.SkipAction.MUTE,
            useCase.checkPosition(positionMs = 12_500L, autoSkip = true)?.action
        )
    }

    @Test
    fun autoSkipSegmentStillWorksAlongsideMuteSegments() {
        val useCase = SponsorBlockUseCase()
        useCase.loadForTest(
            listOf(
                skipSegment(uuid = "s1", startMs = 10_000L, endMs = 15_000L),
                muteSegment(uuid = "m1", startMs = 20_000L, endMs = 25_000L)
            )
        )

        val skipped = useCase.checkPosition(positionMs = 10_500L, autoSkip = true)
        assertEquals(SponsorBlockUseCase.SkipAction.AUTO_SKIP, skipped?.action)
        assertEquals(15_000L, skipped?.segment?.endTimeMs)
        // 跳过后位置直接落在片段尾（> mute 起点），mute 已过期不触发
        val next = useCase.checkPosition(positionMs = 15_200L, autoSkip = true)
        assertNull(next)
    }

    private fun muteSegment(uuid: String, startMs: Long, endMs: Long): SponsorSegment {
        return SponsorSegment(
            segment = listOf(startMs / 1000f, endMs / 1000f),
            UUID = uuid,
            category = SponsorSegment.CATEGORY_SPONSOR,
            actionType = "mute"
        )
    }

    private fun skipSegment(uuid: String, startMs: Long, endMs: Long): SponsorSegment {
        return SponsorSegment(
            segment = listOf(startMs / 1000f, endMs / 1000f),
            UUID = uuid,
            category = SponsorSegment.CATEGORY_SPONSOR,
            actionType = "skip"
        )
    }
}
