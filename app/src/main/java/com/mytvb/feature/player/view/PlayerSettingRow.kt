package com.mytvb.feature.player.view

internal sealed interface PlayerSettingRow {
    data class Header(
        val title: String,
        val subTitle: String? = null
    ) : PlayerSettingRow

    data class Item(
        val id: Int,
        // CharSequence 而非 String：字幕轨道名的 "AI" 标记用 Spannable 拼接（小号+次要色），其余调用点传 String 均兼容。
        val title: CharSequence,
        val value: String = "",
        val iconRes: Int? = null,
        val checked: Boolean = false,
        val showArrow: Boolean = true
    ) : PlayerSettingRow
}
