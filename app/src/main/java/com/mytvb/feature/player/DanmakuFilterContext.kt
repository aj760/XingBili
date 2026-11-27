package com.mytvb.feature.player

import com.mytvb.model.proto.DmSmartFilterConfigProto
import com.mytvb.model.proto.DmWebViewReplyProto
import com.mytvb.model.proto.DmRestrictPeriodProto
import com.mytvb.model.proto.DanmuWebPlayerConfigProto
import java.io.Serializable

data class DanmakuFilterContext(
    val smartFilterConfig: DmSmartFilterConfigProto = DmSmartFilterConfigProto(),
    val playerConfig: DanmuWebPlayerConfigProto = DanmuWebPlayerConfigProto(),
    val reportFilters: List<String> = emptyList(),
    val restrictPeriods: List<DmRestrictPeriodProto> = emptyList()
) : Serializable {
    companion object {
        val EMPTY = DanmakuFilterContext()

        fun fromView(view: DmWebViewReplyProto?): DanmakuFilterContext {
            if (view == null) return EMPTY
            return DanmakuFilterContext(
                smartFilterConfig = view.smartFilterConfig,
                playerConfig = view.playerConfig,
                reportFilters = view.reportFilters,
                restrictPeriods = view.restrictPeriods
            )
        }
    }
}
