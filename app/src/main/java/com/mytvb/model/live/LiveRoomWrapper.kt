package com.mytvb.model.live

import com.google.gson.annotations.SerializedName
import org.json.JSONObject
import java.io.Serializable

data class LiveRoomWrapper(
    @SerializedName("list")
    val list: List<LiveRoomItem>? = null,
    @SerializedName("module_info")
    val moduleInfo: LiveRoomModule? = null
) : Serializable {

    companion object {
        fun fromJson(obj: JSONObject): LiveRoomWrapper {
            val items = obj.optJSONArray("list")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    runCatching { LiveRoomItem.fromJson(arr.getJSONObject(idx)) }.getOrNull()
                }
            }
            val modObj = obj.optJSONObject("module_info")
            val module = if (modObj != null) LiveRoomModule(
                id = modObj.optInt("id", 0),
                title = modObj.optString("title", "")
            ) else null
            return LiveRoomWrapper(list = items, moduleInfo = module)
        }
    }
}
