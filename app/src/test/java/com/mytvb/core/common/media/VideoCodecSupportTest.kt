package com.mytvb.core.common.media

import com.mytvb.model.video.quality.VideoCodecEnum
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCodecSupportTest {

    @Test
    fun keepsPreferredCodecFirstWhenHardwareSupportsIt() {
        assertEquals(
            listOf(VideoCodecEnum.HEVC, VideoCodecEnum.AV1, VideoCodecEnum.AVC),
            VideoCodecSupport.orderCandidates(
                availableCodecs = listOf(VideoCodecEnum.AVC, VideoCodecEnum.HEVC, VideoCodecEnum.AV1),
                preferredCodec = VideoCodecEnum.HEVC,
                hardwareSupportedCodecs = listOf(VideoCodecEnum.AVC, VideoCodecEnum.HEVC, VideoCodecEnum.AV1)
            )
        )
    }

    @Test
    fun usesHardwareCodecWhenPreferredCodecIsSoftwareOnly() {
        assertEquals(
            listOf(VideoCodecEnum.AV1, VideoCodecEnum.AVC, VideoCodecEnum.HEVC),
            VideoCodecSupport.orderCandidates(
                availableCodecs = listOf(VideoCodecEnum.AVC, VideoCodecEnum.HEVC, VideoCodecEnum.AV1),
                preferredCodec = VideoCodecEnum.HEVC,
                hardwareSupportedCodecs = listOf(VideoCodecEnum.AVC, VideoCodecEnum.AV1)
            )
        )
    }

    @Test
    fun fallsBackToAvcWhenHevcHasNoHardwareSupport() {
        assertEquals(
            listOf(VideoCodecEnum.AVC, VideoCodecEnum.HEVC),
            VideoCodecSupport.orderCandidates(
                availableCodecs = listOf(VideoCodecEnum.AVC, VideoCodecEnum.HEVC),
                preferredCodec = VideoCodecEnum.HEVC,
                hardwareSupportedCodecs = listOf(VideoCodecEnum.AVC)
            )
        )
    }

    @Test
    fun prefersAvcWhenNoHardwareSupportExists() {
        assertEquals(
            listOf(VideoCodecEnum.AVC, VideoCodecEnum.HEVC, VideoCodecEnum.AV1),
            VideoCodecSupport.orderCandidates(
                availableCodecs = listOf(VideoCodecEnum.HEVC, VideoCodecEnum.AV1, VideoCodecEnum.AVC),
                preferredCodec = VideoCodecEnum.HEVC,
                hardwareSupportedCodecs = emptyList()
            )
        )
    }

    @Test
    fun keepsSoftwareHevcAheadOfAv1WhenAvcIsUnavailable() {
        assertEquals(
            listOf(VideoCodecEnum.HEVC, VideoCodecEnum.AV1),
            VideoCodecSupport.orderCandidates(
                availableCodecs = listOf(VideoCodecEnum.HEVC, VideoCodecEnum.AV1),
                preferredCodec = VideoCodecEnum.AV1,
                hardwareSupportedCodecs = emptyList()
            )
        )
    }
}
