package com.mytvb.model.live

import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import java.io.Serializable

data class LiveRoomItem(
    @SerializedName("roomid")
    val roomId: Long = 0,
    @SerializedName("uid")
    val uid: Long = 0,
    @SerializedName("title")
    val title: String = "",
    @SerializedName("cover")
    val cover: String = "",
    @SerializedName("user_cover")
    val userCover: String = "",
    @SerializedName("keyframe")
    val keyframe: String = "",
    @SerializedName("face")
    val face: String = "",
    @SerializedName("uname")
    val uname: String = "",
    @SerializedName("online")
    val online: Int = 0,
    @SerializedName("area_name")
    val areaName: String = "",
    @SerializedName("area_v2_name")
    val areaV2Name: String = "",
    @SerializedName("parent_area_name")
    val parentAreaName: String = "",
    @SerializedName("area_v2_id")
    val areaV2Id: Int = 0,
    @SerializedName("parent_area_id")
    val parentAreaId: Int = 0,
    @SerializedName("live_status")
    val liveStatus: Int = 0,
    @SerializedName("live_time")
    val liveTime: String = "",
    @SerializedName("watched_show")
    val watchedShow: WatchedShowModel? = null
) : Serializable {

    data class WatchedShowModel(
        @SerializedName("num")
        val num: Int = 0,
        @SerializedName("text_small")
        val textSmall: String = "",
        @SerializedName("text_large")
        val textLarge: String = ""
    ) : Serializable

    companion object {
        fun fromJson(obj: JSONObject): LiveRoomItem {
            val wsObj = obj.optJSONObject("watched_show")
            val watchedShow = if (wsObj != null) WatchedShowModel(
                num = wsObj.optInt("num", 0),
                textSmall = wsObj.optString("text_small", ""),
                textLarge = wsObj.optString("text_large", "")
            ) else null
            return LiveRoomItem(
                // 关注列表接口(GetAttentionListInfo)用 room_id,首页模块用 roomid
                roomId = obj.optLong("roomid", obj.optLong("room_id", 0)),
                uid = obj.optLong("uid", 0),
                title = obj.optString("title", ""),
                cover = obj.optString("cover", "").ifEmpty { obj.optString("cover_from_user", "") },
                userCover = obj.optString("user_cover", ""),
                keyframe = obj.optString("keyframe", ""),
                face = obj.optString("face", ""),
                uname = obj.optString("uname", ""),
                online = obj.optInt("online", 0),
                areaName = obj.optString("area_name", ""),
                areaV2Name = obj.optString("area_v2_name", ""),
                parentAreaName = obj.optString("parent_area_name", ""),
                areaV2Id = obj.optInt("area_v2_id", 0),
                parentAreaId = obj.optInt("parent_area_id", 0),
                liveStatus = obj.optInt("live_status", 0),
                liveTime = obj.optString("live_time", ""),
                watchedShow = watchedShow
            )
        }
    }
}
