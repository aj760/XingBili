package com.mytvb.feature.player

import android.content.Context
import android.view.View
import com.mytvb.R
import com.mytvb.core.common.format.MediaFormatUtils
import com.mytvb.core.common.format.NumberUtils
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.common.time.TimeUtils
import com.mytvb.event.AppEventHub
import com.mytvb.model.video.detail.VideoDetailModel
import com.mytvb.model.video.detail.VideoView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * PlayerActivity 与 VideoPlayerFragment 逐字相同的播放器界面逻辑，单一来源。
 * 只收纯逻辑/参数传递；视图持有仍留在各自的宿主里。
 */
internal object PlayerScreenLogic {

    /**
     * 音乐区判定：tid 3（音乐）及其全部子分区。详情接口返回的分区名 tname 常为空，
     * 且音乐区视频携带的是子分区 id 而非音乐分区 id（3），因此以已知子分区 id
     * 集合为主，分区名含"音乐"作兜底（B 站新增子分区时仍能命中）。
     */
    private val MUSIC_ZONE_TIDS = setOf(
        3,   // 音乐
        28,  // 原创音乐
        29,  // 三次元音乐
        30,  // VOCALOID·UTAU
        31,  // 翻唱
        59,  // 演奏
        130, // 音乐综合
        193, // MV
        194  // 电音
    )

    fun isMusicZone(view: VideoView?): Boolean {
        val video = view ?: return false
        if (video.tid in MUSIC_ZONE_TIDS) return true
        return video.tname.contains("音乐")
    }

    fun postPlaybackProgressEvent(
        appEventHub: AppEventHub,
        latestView: VideoView?,
        sessionCoordinator: PlayerSessionCoordinator,
        positionMs: Long
    ) {
        val info = latestView ?: return
        val episodes = sessionCoordinator.getEpisodes()
        val selectedIndex = sessionCoordinator.getSelectedEpisodeIndex()
        if (episodes.isNotEmpty() && selectedIndex in episodes.indices) {
            val episode = episodes[selectedIndex]
            if (episode.epId > 0L) {
                appEventHub.dispatch(
                    AppEventHub.Event.EpisodePlaybackProgressUpdated(
                        episodeId = episode.epId,
                        progressMs = positionMs.coerceAtLeast(0L).plus(1L),
                        episodeIndex = episode.title
                    )
                )
                return
            }
        }
        val progressMs = positionMs.coerceAtLeast(0L).plus(1L)
        appEventHub.dispatch(
            AppEventHub.Event.PlaybackProgressUpdated(
                aid = info.aid,
                cid = info.cid,
                progressMs = progressMs
            )
        )
    }

    fun resolvePlaybackStartSeekPosition(
        playbackRequest: VideoPlayerViewModel.PlaybackRequest,
        currentPlayer: Player,
        tag: String
    ): Long {
        val requestedSeekMs = playbackRequest.seekPositionMs.coerceAtLeast(0L)
        if (!playbackRequest.reuseSameSource || requestedSeekMs <= 0L) {
            return requestedSeekMs
        }
        val durationMs = currentPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET }
            ?: return requestedSeekMs
        val resolution = PlaybackStartSeekResolver.resolve(
            requestedSeekMs = requestedSeekMs,
            durationMs = durationMs,
            reuseSameSource = true
        )
        if (resolution.nearEndReset) {
            AppLog.w(
                tag,
                "warm_reuse_seek_clamped reason=near_end requested=$requestedSeekMs duration=$durationMs"
            )
        }
        return resolution.positionMs
    }

    fun buildHeaderTitle(
        videoTitle: String,
        selectedEpisode: VideoPlayerViewModel.PlayableEpisode?
    ): String {
        val episodeTitle = selectedEpisode?.title?.trim().orEmpty()
        if (episodeTitle.isBlank() || episodeTitle == videoTitle) {
            return videoTitle
        }
        return "$episodeTitle ｜ $videoTitle"
    }

    fun buildHeaderMetaParts(context: Context, video: VideoView): List<String> {
        return buildList {
            video.owner?.name?.takeIf { it.isNotBlank() }?.let(::add)
            video.stat?.view?.takeIf { it > 0 }?.let { add(context.getString(R.string.player_view_count_play_format, NumberUtils.formatCount(it))) }
            if (video.pubDate > 0) {
                add(TimeUtils.formatTime(video.pubDate))
            }
        }
    }

    /** 返回 (hasOwner, hasVideoIdentity)，用于头像/操作按钮显隐。 */
    fun primaryActionFlags(view: VideoView?): Pair<Boolean, Boolean> {
        val hasOwner = view?.owner?.mid?.let { it > 0L } == true
        val hasVideoIdentity = (view?.aid ?: 0L) > 0L || !view?.bvid.isNullOrBlank()
        return hasOwner to hasVideoIdentity
    }

    fun syncChromeStateToCoordinator(
        coordinator: PlaybackUiCoordinator,
        showBottomProgressBar: Boolean,
        visibility: Int
    ) {
        coordinator.withState { coord ->
            coord.chromeState = when (visibility) {
                View.VISIBLE -> PlaybackUiCoordinator.ChromeState.Full
                View.GONE -> PlaybackUiCoordinator.ChromeState.Hidden
                else -> coord.chromeState
            }
            coord.bottomOccupant = when (visibility) {
                View.VISIBLE -> PlaybackUiCoordinator.BottomOccupant.FullChrome
                View.GONE -> if (showBottomProgressBar) PlaybackUiCoordinator.BottomOccupant.SlimTimeline else PlaybackUiCoordinator.BottomOccupant.None
                else -> coord.bottomOccupant
            }
            coord.hudState = when (visibility) {
                View.VISIBLE -> PlaybackUiCoordinator.HudState.Chrome
                View.GONE -> PlaybackUiCoordinator.HudState.Ambient
                else -> coord.hudState
            }
        }
    }

    fun shouldShowSlimTimeline(
        showBottomProgressBar: Boolean,
        controllerVisibility: Int,
        coordinator: PlaybackUiCoordinator
    ): Boolean {
        return showBottomProgressBar &&
                controllerVisibility == View.GONE &&
                coordinator.bottomOccupant == PlaybackUiCoordinator.BottomOccupant.SlimTimeline &&
                coordinator.seekState == PlaybackUiCoordinator.SeekState.None &&
                coordinator.panelState == PlaybackUiCoordinator.PanelState.None
    }

    fun shouldRefreshAmbientChrome(
        showBottomProgressBar: Boolean?,
        controllerVisibility: Int,
        coordinator: PlaybackUiCoordinator
    ): Boolean {
        if (showBottomProgressBar == null || controllerVisibility != View.GONE) {
            return false
        }
        return coordinator.seekState == PlaybackUiCoordinator.SeekState.None &&
                coordinator.panelState == PlaybackUiCoordinator.PanelState.None
    }
}
