package com.mytvb.network.response

import com.google.gson.annotations.SerializedName
import com.mytvb.model.lane.LaneItemModel
import java.io.Serializable

data class GetAllSeriesWrapper(
    @SerializedName("list")
    val list: List<LaneItemModel> = emptyList()
) : Serializable
