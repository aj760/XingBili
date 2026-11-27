package com.mytvb.model.video

import com.google.gson.annotations.SerializedName
import com.mytvb.model.common.CursorModel
import com.mytvb.model.common.TabModel
import java.io.Serializable

data class HistoryListResponse(
    @SerializedName("cursor")
    val cursor: CursorModel? = null,
    @SerializedName("list")
    val list: List<HistoryVideoModel> = emptyList(),
    @SerializedName("tab")
    val tab: List<TabModel> = emptyList()
) : Serializable
