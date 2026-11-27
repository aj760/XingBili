package com.mytvb.network.response

import com.google.gson.annotations.SerializedName
import com.mytvb.model.dm.DmMaskInfo
import com.mytvb.model.interaction.InteractionInfo
import com.mytvb.model.player.ViewPoint
import com.mytvb.model.subtitle.SubtitleWrapper
import java.io.Serializable

data class PlayerInfoDataWrapper(
    @SerializedName("interaction")
    val interaction: InteractionInfo? = null,
    @SerializedName("subtitle")
    val subtitle: SubtitleWrapper? = null,
    @SerializedName("dm_mask")
    val dmMask: DmMaskInfo? = null,
    @SerializedName("view_points")
    val viewPoints: List<ViewPoint>? = null
) : Serializable
