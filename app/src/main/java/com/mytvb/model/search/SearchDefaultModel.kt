package com.mytvb.model.search

import com.google.gson.annotations.SerializedName

/** x/web-interface/wbi/search/default 返回的搜索框默认词。 */
data class SearchDefaultModel(
    @SerializedName("seid")
    val seid: String = "",
    @SerializedName("id")
    val id: Long = 0L,
    @SerializedName("type")
    val type: Int = 0,
    /** 展示文案（可能带样式修饰），做 hint 用这个。 */
    @SerializedName("show_name")
    val showName: String = "",
    /** 跳转搜索关键词。 */
    @SerializedName("name")
    val name: String = "",
    @SerializedName("goto_type")
    val gotoType: Int = 0,
    @SerializedName("goto_value")
    val gotoValue: String = "",
    @SerializedName("url")
    val url: String = ""
)
