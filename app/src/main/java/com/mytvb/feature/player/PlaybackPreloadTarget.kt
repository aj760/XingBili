package com.mytvb.feature.player

import com.mytvb.core.model.id.Aid
import com.mytvb.core.model.id.Bvid
import com.mytvb.core.model.id.Cid
import com.mytvb.core.model.id.EpId

data class PlaybackPreloadTarget(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long,
    val epId: Long? = null,
    val seasonId: Long? = null,
    val source: Source
) {
    enum class Source {
        NEXT_EPISODE,
        PLAY_QUEUE,
        RELATED_VIDEO,
        AUTOPLAY_COUNTDOWN,
        LIST_TOUCH,
        DOUYIN_MODE
    }

    val typedAid: Aid? get() = aid?.let(::Aid)
    val typedBvid: Bvid? get() = bvid?.let(::Bvid)
    val typedCid: Cid get() = Cid(cid)
    val typedEpId: EpId? get() = epId?.let(::EpId)
}
