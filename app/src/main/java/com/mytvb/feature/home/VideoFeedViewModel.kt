package com.mytvb.feature.home

import com.mytvb.model.video.VideoModel
import kotlinx.coroutines.flow.StateFlow

interface VideoFeedViewModel {
    val uiState: StateFlow<FeedUiState<VideoModel>>

    /** 初始加载是否已发起过（Activity recreate 后 ViewModel 保留时为 true，loadInitial 会 no-op）。 */
    val hasLoadedInitial: Boolean

    fun loadInitial()

    fun refresh()

    fun loadMore()

    fun consumeListChange()
}
