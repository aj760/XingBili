package com.mytvb.model.live

import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import java.io.Serializable

data class LiveListWrapper(
    @SerializedName("area_entrance_v2")
    val areaEntranceV2: LiveAreaEntranceWrapper? = null,
    @SerializedName("room_list")
    val roomList: List<LiveRoomWrapper>? = null,
    @SerializedName("recommend_room_list")
    val recommendRoomList: List<LiveRoomItem>? = null
) : Serializable {

    companion object {
        fun fromJson(data: JSONObject): LiveListWrapper {
            val areaObj = data.optJSONObject("area_entrance_v2")
            val areaEntrance = if (areaObj != null) {
                val areaList = areaObj.optJSONArray("list")?.let { arr ->
                    (0 until arr.length()).mapNotNull { idx ->
                        runCatching { LiveAreaCategoryParent.fromJson(arr.getJSONObject(idx)) }.getOrNull()
                    }
                }
                LiveAreaEntranceWrapper(list = areaList)
            } else null

            val roomList = data.optJSONArray("room_list")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    runCatching { LiveRoomWrapper.fromJson(arr.getJSONObject(idx)) }.getOrNull()
                }
            }

            val recommendList = data.optJSONArray("recommend_room_list")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    runCatching { LiveRoomItem.fromJson(arr.getJSONObject(idx)) }.getOrNull()
                }
            }

            return LiveListWrapper(
                areaEntranceV2 = areaEntrance,
                roomList = roomList,
                recommendRoomList = recommendList
            )
        }
    }
}
