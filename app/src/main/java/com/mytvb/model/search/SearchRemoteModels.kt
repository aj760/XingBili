package com.mytvb.model.search

import com.google.gson.annotations.SerializedName

data class SearchSuggestItem(
    @SerializedName("value")
    val value: String = "",
    @SerializedName("name")
    val name: String = ""
)
