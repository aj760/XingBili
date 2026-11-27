package com.mytvb.feature.player

import android.content.Context
import com.mytvb.core.common.content.ContentFilter
import com.mytvb.model.video.VideoModel

/**
 * PlayerActivity 与 VideoPlayerFragment 共用的未成年人保护拦截判断。
 */
internal object PlayerContentGuards {

    fun isVideoBlocked(context: Context, video: VideoModel): Boolean {
        return ContentFilter.isVideoBlocked(
            context = context,
            typeName = video.typeName,
            title = video.title,
            teenageMode = video.teenageMode,
            desc = video.desc,
            authorName = video.authorName,
            aid = video.aid,
            bvid = video.bvid,
            coverUrl = video.coverUrl,
            typeId = video.typeId
        )
    }

    fun isEpisodeBlocked(context: Context, episode: VideoPlayerViewModel.PlayableEpisode): Boolean {
        return ContentFilter.isVideoBlocked(
            context = context,
            typeName = "",
            title = episode.title,
            aid = episode.aid,
            bvid = episode.bvid,
            coverUrl = episode.cover
        )
    }
}
