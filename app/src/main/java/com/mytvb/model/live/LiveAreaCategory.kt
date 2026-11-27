package com.mytvb.model.live

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class LiveAreaCategory(
    @SerializedName("id")
    val id: Long = 0,
    @SerializedName("parent_id")
    val parentId: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("area_type")
    val areaType: Int = 0,
    @SerializedName("pic")
    val pic: String = "",
    @SerializedName("parent_name")
    val parentName: String = "",
    @SerializedName("title")
    val title: String = "",
    @SerializedName("link")
    val link: String = "",
    @SerializedName("area_v2_id")
    val areaV2Id: Long = 0,
    @SerializedName("area_v2_parent_id")
    val areaV2ParentId: Long = 0
) {
    companion object {
        fun fromJson(obj: JSONObject): LiveAreaCategory = LiveAreaCategory(
            id = obj.optLong("id", 0),
            parentId = obj.optLong("parent_id", 0),
            name = obj.optString("name", ""),
            areaType = obj.optInt("area_type", 0),
            pic = obj.optString("pic", ""),
            parentName = obj.optString("parent_name", ""),
            title = obj.optString("title", ""),
            link = obj.optString("link", ""),
            areaV2Id = obj.optLong("area_v2_id", 0),
            areaV2ParentId = obj.optLong("area_v2_parent_id", 0)
        )
    }
}
