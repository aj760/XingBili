package com.mytvb.repository.cache

import com.mytvb.core.common.cache.OnDemandRequestCache
import com.mytvb.model.BaseResponse
import com.mytvb.model.video.detail.VideoDetailModel
import com.mytvb.network.session.RequestSessionScope
import com.mytvb.network.session.RequestSessionSnapshot

/** Shared by detail screen, card menu and both playback hosts. No player objects. */
object VideoMetadataCache {
    private data class Key(val session: RequestSessionSnapshot, val aid: Long?, val bvid: String?)
    private val details = OnDemandRequestCache<Key, BaseResponse<VideoDetailModel>>(
        maxEntries = 32,
        maxAgeMs = 15_000,
        canCache = { it.isSuccess && it.data != null }
    )

    suspend fun detail(
        aid: Long?,
        bvid: String?,
        load: suspend () -> BaseResponse<VideoDetailModel>
    ): BaseResponse<VideoDetailModel> {
        val session = RequestSessionScope.capture()
        val normalizedBvid = bvid?.trim()?.takeIf { it.isNotBlank() }
        val key = Key(session, if (normalizedBvid == null) aid else null, normalizedBvid)
        return details.getOrLoad(key) {
            load().also { RequestSessionScope.requireCurrent(session) }
        }.also { RequestSessionScope.requireCurrent(session) }
    }
}
