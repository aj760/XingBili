package com.mytvb.model.series

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class BadgeInfoModel(
    @SerializedName("text")
    val text: String = "",
    @SerializedName("bg_color")
    val bgColor: String = "",
    @SerializedName("bg_color_night")
    val bgColorNight: String = ""
) : Serializable
