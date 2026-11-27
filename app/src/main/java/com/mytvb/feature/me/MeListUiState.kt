package com.mytvb.feature.me

import com.mytvb.model.video.HistoryVideoModel
import com.mytvb.model.video.VideoModel

data class MeListUiState(
    val historyVideos: List<HistoryVideoModel> = emptyList(),
    val laterVideos: List<VideoModel> = emptyList()
)
