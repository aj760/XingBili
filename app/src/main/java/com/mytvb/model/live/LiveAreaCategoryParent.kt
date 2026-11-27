package com.mytvb.model.live

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class LiveAreaCategoryParent(
    @SerializedName("id")
    val id: Long = 0,
    @SerializedName("name")
    val name: String = "",
    @SerializedName(value = "list", alternate = ["area_list"])
    val areaList: List<LiveAreaCategory>? = null
) {
    companion object {
        fun fromJson(obj: JSONObject): LiveAreaCategoryParent {
            val areaArr = obj.optJSONArray("list") ?: obj.optJSONArray("area_list")
            val areas = areaArr?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    runCatching { LiveAreaCategory.fromJson(arr.getJSONObject(idx)) }.getOrNull()
                }
            }
            return LiveAreaCategoryParent(
                id = obj.optLong("id", 0),
                name = obj.optString("name", ""),
                areaList = areas
            )
        }
    }
}
