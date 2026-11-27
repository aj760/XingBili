package com.mytvb.model.common

import com.google.gson.annotations.SerializedName

/** x/click-interface/click/now 返回的服务器秒级时间戳。 */
data class ServerTimeModel(
    @SerializedName("now")
    val now: Long = 0L
)
