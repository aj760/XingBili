package com.mytvb.feature.player.sponsor

import org.junit.Assert.assertEquals
import org.junit.Test

class SponsorBlockRepositoryTest {

    @Test
    fun selectsSegmentsForMatchingBvidAndCidFromHashResponse() {
        val videos = listOf(
            SponsorBlockRepository.HashVideoResponse(
                videoID = "BV-other",
                segments = listOf(segment(uuid = "other", cid = "100"))
            ),
            SponsorBlockRepository.HashVideoResponse(
                videoID = "BV-target",
                segments = listOf(
                    segment(uuid = "first-p", cid = "100"),
                    segment(uuid = "second-p", cid = "200")
                )
            )
        )

        val selected = SponsorBlockRepository.selectSegments(videos, "BV-target", 200L)

        assertEquals(listOf("second-p"), selected.map { it.UUID })
    }

    @Test
    fun keepsAllCidsWhenCidIsMissing() {
        val videos = listOf(
            SponsorBlockRepository.HashVideoResponse(
                videoID = "BV-target",
                segments = listOf(
                    segment(uuid = "first-p", cid = "100"),
                    segment(uuid = "second-p", cid = "200")
                )
            )
        )

        val selected = SponsorBlockRepository.selectSegments(videos, "BV-target", 0L)

        assertEquals(listOf("first-p", "second-p"), selected.map { it.UUID })
    }

    @Test
    fun normalizeKeepsMuteSegmentsAndDropsOtherActionTypes() {
        val normalized = SponsorBlockRepository.normalizeSegments(
            listOf(
                segment(uuid = "s1", actionType = "skip", startSec = 10f, endSec = 12f),
                segment(uuid = "m1", actionType = "mute", startSec = 20f, endSec = 22f),
                segment(uuid = "p1", actionType = "poi", startSec = 30f, endSec = 32f),
                segment(uuid = "f1", actionType = "full", startSec = 0f, endSec = 0f)
            )
        )

        assertEquals(listOf("s1", "m1"), normalized.map { it.UUID })
    }

    @Test
    fun normalizePicksLockedOverVotesWithinSameGroup() {
        // 同 UUID 重复提交（如跨 cid 重复下发）：锁定优先于票数
        val normalized = SponsorBlockRepository.normalizeSegments(
            listOf(
                segment(uuid = "dup", actionType = "skip", startSec = 10f, endSec = 12f, votes = 9),
                segment(uuid = "dup", actionType = "skip", startSec = 10.5f, endSec = 12.5f, locked = 1, votes = 2)
            )
        )

        assertEquals(1, normalized.size)
        assertEquals(1, normalized.first().locked)
    }

    @Test
    fun normalizeTieBreaksByEarlierStartThenShorterSpan() {
        // 同 locked 同 votes 的同 UUID 重复条目：起点更早者胜
        val earlier = SponsorBlockRepository.normalizeSegments(
            listOf(
                segment(uuid = "dup", actionType = "skip", startSec = 11f, endSec = 13f),
                segment(uuid = "dup", actionType = "skip", startSec = 10f, endSec = 13f)
            )
        )
        assertEquals(1, earlier.size)
        assertEquals(10_000L, earlier.first().startTimeMs)

        // 起点也相同：片段更短者胜
        val shorter = SponsorBlockRepository.normalizeSegments(
            listOf(
                segment(uuid = "dup", actionType = "skip", startSec = 10f, endSec = 14f),
                segment(uuid = "dup", actionType = "skip", startSec = 10f, endSec = 12f)
            )
        )
        assertEquals(1, shorter.size)
        assertEquals(12_000L, shorter.first().endTimeMs)
    }

    @Test
    fun differentUuidsOfSameCategoryAreKeptSeparately() {
        // 分组按 UUID 而非类别：同类别两段不重叠的片段（恰饭出现两次）都保留
        val normalized = SponsorBlockRepository.normalizeSegments(
            listOf(
                segment(uuid = "a", actionType = "skip", startSec = 30f, endSec = 40f),
                segment(uuid = "b", actionType = "skip", startSec = 300f, endSec = 310f)
            )
        )

        assertEquals(2, normalized.size)
    }

    private fun segment(
        uuid: String,
        cid: String = "100",
        actionType: String = "skip",
        startSec: Float = 1f,
        endSec: Float = 2f,
        locked: Int = 0,
        votes: Int = 0
    ): SponsorSegment {
        return SponsorSegment(
            segment = listOf(startSec, endSec),
            UUID = uuid,
            category = SponsorSegment.CATEGORY_SPONSOR,
            actionType = actionType,
            cid = cid,
            locked = locked,
            votes = votes
        )
    }
}
