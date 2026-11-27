package com.mytvb.model.player

import com.google.gson.annotations.SerializedName

/**
 * 视频同时观看人数（在线人数）。
 *
 * 接口：GET x/player/online/total
 * 服务端已预格式化，如 total = "9.4万+"。
 */
data class OnlineTotalModel(
    @SerializedName("total")
    val total: String? = null,

    @SerializedName("count")
    val count: String? = null,

    @SerializedName("show_switch")
    val showSwitch: ShowSwitch? = null
) {

    /**
     * 按 UP 主展示开关计算应显示的人数文案。
     *
     * 优先所有终端总计人数，其次 web 端实时人数；开关关闭或数据为空时返回 null（不显示）。
     * 开关对象整体缺失时视为未限制（只看数据本身）。
     */
    fun displayText(): String? {
        val switch = showSwitch
        return when {
            (switch == null || switch.total) && !total.isNullOrBlank() -> total
            switch != null && switch.count && !count.isNullOrBlank() -> count
            else -> null
        }
    }

    data class ShowSwitch(
        @SerializedName("total")
        val total: Boolean = true,

        @SerializedName("count")
        val count: Boolean = true
    )
}
