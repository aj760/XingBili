package com.mytvb.feature.player.sponsor

import com.mytvb.model.player.PgcClipInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialClipSegmentsTest {

    @Test
    fun convertsOpAndEdClips() {
        val segments = OfficialClipSegments.toSegments(
            listOf(
                PgcClipInfo(start = 0.0, end = 123.0, toastText = "即将跳过片头", clipType = "CLIP_TYPE_OP"),
                PgcClipInfo(start = 2696.0, end = 2799.0, toastText = "即将跳过片尾", clipType = "CLIP_TYPE_ED")
            )
        )
        assertEquals(2, segments.size)
        assertEquals(SponsorSegment.CATEGORY_OFFICIAL_OP, segments[0].category)
        assertEquals(SponsorSegment.CATEGORY_OFFICIAL_ED, segments[1].category)
        assertEquals(0L, segments[0].startTimeMs)
        assertEquals(123_000L, segments[0].endTimeMs)
        assertEquals(2_696_000L, segments[1].startTimeMs)
        assertEquals(2_799_000L, segments[1].endTimeMs)
        // UUID 唯一，供 use case 的 skippedIds 去重
        assertEquals(segments.map { it.UUID }.toSet().size, segments.size)
    }

    @Test
    fun ignoresUnknownClipType() {
        val segments = OfficialClipSegments.toSegments(
            listOf(PgcClipInfo(start = 0.0, end = 10.0, clipType = "CLIP_TYPE_OTHER"))
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun ignoresInvalidRange() {
        val segments = OfficialClipSegments.toSegments(
            listOf(
                PgcClipInfo(start = 30.0, end = 30.0, clipType = "CLIP_TYPE_OP"),
                PgcClipInfo(start = 50.0, end = 20.0, clipType = "CLIP_TYPE_ED")
            )
        )
        assertTrue(segments.isEmpty())
    }

    @Test
    fun nullOrEmptyListYieldsEmpty() {
        assertTrue(OfficialClipSegments.toSegments(null).isEmpty())
        assertTrue(OfficialClipSegments.toSegments(emptyList()).isEmpty())
    }
}
