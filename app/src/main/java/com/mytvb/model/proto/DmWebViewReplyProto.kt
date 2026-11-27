package com.mytvb.model.proto

import java.io.Serializable

data class DmSmartFilterConfigProto(
    val cloudLevel: Int = 0,
    val cloudText: String = "",
    val cloudSwitch: Int = 0,
    val defaultLevel: Int = 0,
    val defaultEnabled: Boolean = false,
    val playerLevel: Int = 0,
    val playerEnabled: Boolean = false
) : Serializable {
    val resolvedLevel: Int
        get() = when {
            playerLevel > 0 -> playerLevel
            defaultLevel > 0 -> defaultLevel
            else -> cloudLevel
        }

    val resolvedEnabled: Boolean
        get() = when {
            playerLevel > 0 -> playerEnabled
            defaultLevel > 0 -> defaultEnabled
            cloudLevel > 0 -> cloudSwitch != 0
            else -> false
        }
}

data class DmWebViewReplyProto(
    val segmentDurationMs: Int = 0,
    val totalSegments: Int = 0,
    val totalCount: Long = 0L,
    val specialDanmakuUrls: List<String> = emptyList(),
    val smartFilterConfig: DmSmartFilterConfigProto = DmSmartFilterConfigProto(),
    val playerConfig: DanmuWebPlayerConfigProto = DanmuWebPlayerConfigProto(),
    val reportFilters: List<String> = emptyList(),
    val commandDms: List<CommandDmProto> = emptyList(),
    val restrictPeriods: List<DmRestrictPeriodProto> = emptyList()
) : Serializable

data class DanmuWebPlayerConfigProto(
    val dmSwitch: Boolean = true,
    val aiSwitch: Boolean = false,
    val aiLevel: Int = 0,
    val typeTop: Boolean = true,
    val typeScroll: Boolean = true,
    val typeBottom: Boolean = true,
    val typeColor: Boolean = true,
    val typeSpecial: Boolean = true,
    val preventShade: Boolean = false,
    val dmask: Boolean = false,
    val opacity: Float = 1f,
    val speedPlus: Float = 1f,
    val fontSize: Float = 1f,
    val fontFamily: String = "",
    val bold: Boolean = false,
    val fontBorder: Int = 0,
    val seniorModeSwitch: Boolean = true,
    val typeTopBottom: Boolean = true,
    val dmArea: Int = 0,
    val dmDensity: Int = 0
) : Serializable

data class CommandDmProto(
    val command: String = "",
    val text: String = "",
    val stimeMs: Long = 0L,
    val dmid: Long = 0L
) : Serializable

data class DmRestrictPeriodProto(
    val startMs: Long = 0L,
    val endMs: Long = 0L
) : Serializable
