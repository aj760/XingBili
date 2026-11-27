package com.mytvb.model.player

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * 视频章节/看点（x/player/wbi/v2 的 view_points）。
 * from/to 为秒（实测整数下发）；章节图为 imgUrl（vchapter 域名）。
 */
data class ViewPoint(
    @SerializedName("from")
    val from: Double = 0.0,
    @SerializedName("to")
    val to: Double = 0.0,
    @SerializedName("content")
    val content: String = "",
    @SerializedName("imgUrl")
    val img: String = ""
) : Serializable {
    val fromMs: Long get() = (from * 1000L).toLong()
    val toMs: Long get() = (to * 1000L).toLong()
}
