package com.mytvb.feature.player.view

internal fun shouldRunMaskFrames(
    enabled: Boolean,
    danmakuVisible: Boolean,
    isPlaying: Boolean,
    playbackReady: Boolean,
    hasTimeline: Boolean,
): Boolean = enabled && danmakuVisible && isPlaying && playbackReady && hasTimeline

internal fun isCurrentMaskLoad(
    requestGeneration: Long,
    currentGeneration: Long,
    disposed: Boolean,
): Boolean = !disposed && requestGeneration == currentGeneration

internal fun shouldCompleteMaskSeek(
    disposed: Boolean,
    seeking: Boolean,
    expectedSequence: Long,
    currentSequence: Long,
): Boolean = !disposed && seeking && expectedSequence == currentSequence
