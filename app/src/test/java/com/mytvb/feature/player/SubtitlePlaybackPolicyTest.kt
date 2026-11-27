package com.mytvb.feature.player

import com.mytvb.model.subtitle.SubtitleInfoModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlePlaybackPolicyTest {

    @Test
    fun playerInfoTracksMustMatchDetailTrackIdentity() {
        val detailTracks = listOf(
            SubtitleInfoModel(id = 101L, lan = "ai-zh"),
            SubtitleInfoModel(id = 102L, lan = "ai-en")
        )
        val playerInfoTracks = listOf(
            SubtitleInfoModel(id = 999L, lan = "ai-zh", subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/wrong"),
            SubtitleInfoModel(id = 102L, lan = "ai-en", subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/correct")
        )

        assertEquals(
            listOf(SubtitleInfoModel(id = 102L, lan = "ai-en", subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/correct")),
            trustedPlayerInfoSubtitleTracks(detailTracks, playerInfoTracks)
        )
    }

    @Test
    fun playerInfoTracksAreAllowedWhenDetailHasNoTracks() {
        val track = SubtitleInfoModel(id = 999L, lan = "ai-zh", subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/track")

        assertEquals(listOf(track), trustedPlayerInfoSubtitleTracks(emptyList(), listOf(track)))
    }

    @Test
    fun unmatchedPlayerInfoTracksAreRetriedWhenDetailTracksExist() {
        val detailTracks = listOf(SubtitleInfoModel(id = 101L, lan = "ai-zh"))
        val staleTrack = SubtitleInfoModel(id = 999L, lan = "ai-zh", subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/stale")

        assertTrue(shouldRetryPlayerInfoSubtitleTracks(detailTracks, listOf(staleTrack)))
        assertFalse(shouldRetryPlayerInfoSubtitleTracks(detailTracks, emptyList()))
    }

    @Test
    fun staleSubtitleRequestIsRejected() {
        assertTrue(isSubtitleRequestCurrent(10L, "BV1current", 10L, "BV1current"))
        assertFalse(isSubtitleRequestCurrent(10L, "BV1previous", 11L, "BV1current"))
    }

    @Test
    fun cueCacheKeySeparatesVideosAndTracks() {
        val track = SubtitleInfoModel(id = 101L, lan = "ai-zh")
        val url = "https://aisubtitle.hdslb.com/bfs/ai_subtitle/prod/example"

        val base = buildSubtitleCueCacheKey("BV1one", 10L, track, url)
        assertFalse(base == buildSubtitleCueCacheKey("BV1one", 11L, track, url))
        assertFalse(base == buildSubtitleCueCacheKey("BV1two", 10L, track, url))
    }

    @Test
    fun staleSubtitleResultIsRejectedWhenNewSelectionStarted() {
        assertFalse(
            shouldApplySubtitleLoadResult(
                activeToken = 2L,
                resultToken = 1L,
                requestCid = 10L,
                requestBvid = "BV1current",
                currentCid = 10L,
                currentBvid = "BV1current"
            )
        )
    }

    @Test
    fun trackBindingUsesIdStringAndSubtitlePath() {
        val first = SubtitleInfoModel(
            id = 1L,
            idStr = "stable-track",
            lan = "ai-zh",
            subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/first?auth_key=old"
        )
        val refreshed = first.copy(subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/refreshed?auth_key=new")

        assertFalse(subtitleTrackBindingKey(first) == subtitleTrackBindingKey(refreshed))
        assertTrue(isTrustedBilibiliSubtitleUrl(first.subtitleUrl))
    }

    @Test
    fun onlyExpiredOrUnauthorizedSubtitleUrlsAreRefreshed() {
        assertTrue(shouldRetrySubtitleLoadWithPlayerInfo(403))
        assertTrue(shouldRetrySubtitleLoadWithPlayerInfo(410))
        assertFalse(shouldRetrySubtitleLoadWithPlayerInfo(500))
        assertFalse(shouldRetrySubtitleLoadWithPlayerInfo(null))
    }

    @Test
    fun missingSubtitleUrlRefreshesTrackBeforeTheFirstManualLoad() {
        assertTrue(shouldRefreshSubtitleTrack(SubtitleInfoModel(id = 101L, lan = "zh-CN"), null))
        assertFalse(
            shouldRefreshSubtitleTrack(
                SubtitleInfoModel(
                    id = 101L,
                    lan = "zh-CN",
                    subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/current"
                ),
                null
            )
        )
    }

    @Test
    fun refreshedTrackMustMatchOriginalIdentityRatherThanLanguage() {
        val original = SubtitleInfoModel(id = 101L, lan = "zh-CN")
        val wrongSameLanguage = SubtitleInfoModel(
            id = 202L,
            lan = "zh-CN",
            subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/wrong"
        )
        val refreshed = SubtitleInfoModel(
            id = 101L,
            lan = "zh-CN",
            subtitleUrl = "//aisubtitle.hdslb.com/bfs/subtitle/refreshed"
        )

        assertEquals(refreshed, refreshedSubtitleTrackFor(original, listOf(wrongSameLanguage, refreshed)))
        assertEquals(null, refreshedSubtitleTrackFor(original, listOf(wrongSameLanguage)))
    }

    @Test
    fun humanTracksArePreferredOverAiTracks() {
        val ai = SubtitleInfoModel(id = 2L, lan = "zh-CN", lanDoc = "AI 字幕", aiStatus = 1)
        val human = SubtitleInfoModel(id = 1L, lan = "zh-CN", lanDoc = "官方字幕")

        assertEquals(listOf(human, ai), orderSubtitleTracksByPreference(listOf(ai, human)))
    }
}
