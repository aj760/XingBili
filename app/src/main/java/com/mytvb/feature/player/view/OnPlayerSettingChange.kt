package com.mytvb.feature.player.view

import com.mytvb.feature.player.settings.AfterPlayMode
import com.mytvb.model.video.quality.VideoQuality
import com.mytvb.model.video.quality.AudioQuality
import com.mytvb.model.video.quality.VideoCodecEnum

interface OnPlayerSettingChange {
    fun onVideoQualityChange(quality: VideoQuality)
    fun onAudioQualityChange(quality: AudioQuality)
    fun onPlaybackSpeedChange(speed: Float)
    fun onSubtitleChange(position: Int)
    fun onVideoCodecChange(codec: VideoCodecEnum)
    fun onSoftwareDecoderChange(enabled: Boolean) {}
    fun onAspectRatioChange(ratio: Int)
    fun onScreenMirrorChange(enabled: Boolean) {}
    fun onLiveQualityChange(qn: Int) {}
    fun onLiveLineChange(index: Int) {}
    fun onAfterPlayModeChange(mode: AfterPlayMode) {}
}
