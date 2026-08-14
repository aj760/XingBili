package blbl.cat3399.feature.feed

import blbl.cat3399.core.api.video.VideoAudioKind
import blbl.cat3399.core.api.video.VideoPlayStream
import blbl.cat3399.core.api.video.VideoTrackInfo
import blbl.cat3399.feature.player.DashAudioKind
import blbl.cat3399.feature.player.DashSegmentBase
import blbl.cat3399.feature.player.DashTrackInfo
import blbl.cat3399.feature.player.Playable

/**
 * Converts a [VideoPlayStream] into a [Playable] that the shared [blbl.cat3399.feature.player.engine.ExoPlayerEngine]
 * can render. Prefers a self-contained progressive stream (audio+video in one url); otherwise builds a
 * DASH [Playable.Dash] by pairing the first usable video track with the first usable audio track.
 *
 * This mirrors the selection logic in PlayerActivity but trimmed down for the immersive feed, where we do not
 * need CDN preference scoring or Dolby/FLAC fallbacks — the engine's built-in CDN failover handles candidates.
 */
internal fun VideoPlayStream.toFeedPlayable(): Playable? {
    val prog = progressive.firstOrNull { it.urls.isNotEmpty() }
    if (prog != null) {
        val urls = prog.urls
        return Playable.Progressive(
            url = urls.first(),
            urlCandidates = urls,
            mediaRequestProfile = prog.mediaRequestProfile,
        )
    }

    val d = dash ?: return null
    val v = d.videos.firstOrNull { it.urls.isNotEmpty() } ?: return null
    val a = d.audios.firstOrNull { it.urls.isNotEmpty() } ?: return null
    val vUrls = v.urls
    val aUrls = a.urls
    return Playable.Dash(
        videoUrl = vUrls.first(),
        audioUrl = aUrls.first(),
        videoUrlCandidates = vUrls,
        audioUrlCandidates = aUrls,
        videoMediaRequestProfile = v.mediaRequestProfile,
        audioMediaRequestProfile = a.mediaRequestProfile,
        videoTrackInfo = v.info.toDashTrackInfo(),
        audioTrackInfo = a.info.toDashTrackInfo(),
        qn = v.qn,
        codecid = v.codecid,
        audioId = a.id,
        audioKind = a.kind.toDashAudioKind(),
        isDolbyVision = v.isDolbyVision,
    )
}

private fun VideoTrackInfo.toDashTrackInfo(): DashTrackInfo =
    DashTrackInfo(
        mimeType = mimeType,
        codecs = codecs,
        bandwidth = bandwidth,
        width = width,
        height = height,
        frameRate = frameRate,
        segmentBase = segmentBase?.let { DashSegmentBase(it.initialization, it.indexRange) },
    )

private fun VideoAudioKind.toDashAudioKind(): DashAudioKind =
    when (this) {
        VideoAudioKind.DOLBY -> DashAudioKind.DOLBY
        VideoAudioKind.FLAC -> DashAudioKind.FLAC
        else -> DashAudioKind.NORMAL
    }
