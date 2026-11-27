package com.mytvb.model.player

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class PlayInfoModel(
    @SerializedName("from")
    val from: String = "",
    
    @SerializedName("result")
    val result: String = "",
    
    @SerializedName("message")
    val message: String = "",
    
    @SerializedName("quality")
    val quality: Int = 0,
    
    @SerializedName("format")
    val format: String = "",
    
    @SerializedName("timelength")
    val timeLength: Long = 0,
    
    @SerializedName("accept_format")
    val acceptFormat: String = "",
    
    @SerializedName("accept_description")
    val acceptDescription: List<String>? = null,
    
    @SerializedName("accept_quality")
    val acceptQuality: List<Int>? = null,
    
    @SerializedName("video_codecid")
    val videoCodecId: Int = 0,
    
    @SerializedName("seek_param")
    val seekParam: String = "",
    
    @SerializedName("seek_type")
    val seekType: String = "",
    
    @SerializedName("dash")
    val dash: Dash? = null,
    
    @SerializedName("durl")
    val durl: List<Durl>? = null,
    
    @SerializedName("support_formats")
    val supportFormats: List<SupportFormat>? = null,
    
    @SerializedName("high_format")
    val highFormat: JsonElement? = null,
    
    @SerializedName("last_play_time")
    val lastPlayTime: Long = 0,
    
    @SerializedName("last_play_cid")
    val lastPlayCid: Long = 0,

    @SerializedName("v_voucher")
    val vVoucher: String = "",

    // 番剧/影视官方片头片尾打点（pgc playurl 下发）；UGC 接口无此字段。
    @SerializedName("clip_info_list")
    val clipInfoList: List<PgcClipInfo>? = null
)

/**
 * 官方片头片尾打点：clipType 为 CLIP_TYPE_OP（片头）/ CLIP_TYPE_ED（片尾），
 * start/end 为秒；toastText 为官方提示文案（"即将跳过片头"）。
 */
data class PgcClipInfo(
    @SerializedName("materialNo")
    val materialNo: Int = 0,

    @SerializedName("start")
    val start: Double = 0.0,

    @SerializedName("end")
    val end: Double = 0.0,

    @SerializedName("toastText")
    val toastText: String = "",

    @SerializedName("clipType")
    val clipType: String = ""
)

data class Dash(
    @SerializedName("duration")
    val duration: Long = 0,
    
    @SerializedName("minBufferTime")
    val minBufferTime: Double = 0.0,
    
    @SerializedName("min_buffer_time")
    val minBufferTimeAlt: Double = 0.0,
    
    @SerializedName("video")
    val video: List<DashVideo>? = null,
    
    @SerializedName("audio")
    val audio: List<DashAudio>? = null,
    
    @SerializedName("dolby")
    val dolby: Dolby? = null,
    
    @SerializedName("flac")
    val flac: Flac? = null
)

data class DashVideo(
    @SerializedName("id")
    val id: Int = 0,
    
    @SerializedName("baseUrl")
    val baseUrl: String = "",
    
    @SerializedName("base_url")
    val baseUrlAlt: String = "",
    
    @SerializedName("backupUrl")
    val backupUrl: List<String>? = null,
    
    @SerializedName("backup_url")
    val backupUrlAlt: List<String>? = null,
    
    @SerializedName("bandwidth")
    val bandwidth: Long = 0,
    
    @SerializedName("mimeType")
    val mimeType: String = "",
    
    @SerializedName("mime_type")
    val mimeTypeAlt: String = "",
    
    @SerializedName("codecs")
    val codecs: String = "",
    
    @SerializedName("codecid")
    val codecId: Int = 0,
    
    @SerializedName("width")
    val width: Int = 0,
    
    @SerializedName("height")
    val height: Int = 0,
    
    @SerializedName("frameRate")
    val frameRate: String = "",
    
    @SerializedName("frame_rate")
    val frameRateAlt: String = "",
    
    @SerializedName("sar")
    val sar: String = "",
    
    @SerializedName("startWithSap")
    val startWithSap: Int = 0,
    
    @SerializedName("start_with_sap")
    val startWithSapAlt: Int = 0,
    
    @SerializedName("segmentBase")
    val segmentBase: SegmentBase? = null,
    
    @SerializedName("segment_base")
    val segmentBaseAlt: SegmentBase? = null,
    
    @SerializedName("qualityType")
    val qualityType: Int = 0,
    
    @SerializedName("quality_type")
    val qualityTypeAlt: Int = 0
) {
    val realBaseUrl: String get() = baseUrl.ifEmpty { baseUrlAlt }
    val realBackupUrl: List<String>? get() = backupUrl ?: backupUrlAlt
    val realMimeType: String get() = mimeType.ifEmpty { mimeTypeAlt }
    val realFrameRate: String get() = frameRate.ifEmpty { frameRateAlt }
    val realStartWithSap: Int get() = if (startWithSap != 0) startWithSap else startWithSapAlt
    val realSegmentBase: SegmentBase? get() = segmentBase ?: segmentBaseAlt
    val realQualityType: Int get() = if (qualityType != 0) qualityType else qualityTypeAlt
}

data class DashAudio(
    @SerializedName("id")
    val id: Int = 0,
    
    @SerializedName("baseUrl")
    val baseUrl: String = "",
    
    @SerializedName("base_url")
    val baseUrlAlt: String = "",
    
    @SerializedName("backupUrl")
    val backupUrl: List<String>? = null,
    
    @SerializedName("backup_url")
    val backupUrlAlt: List<String>? = null,
    
    @SerializedName("bandwidth")
    val bandwidth: Long = 0,
    
    @SerializedName("mimeType")
    val mimeType: String = "",
    
    @SerializedName("mime_type")
    val mimeTypeAlt: String = "",
    
    @SerializedName("codecs")
    val codecs: String = "",
    
    @SerializedName("codecid")
    val codecId: Int = 0,
    
    @SerializedName("width")
    val width: Int = 0,
    
    @SerializedName("height")
    val height: Int = 0,
    
    @SerializedName("frameRate")
    val frameRate: String = "",
    
    @SerializedName("frame_rate")
    val frameRateAlt: String = "",
    
    @SerializedName("sar")
    val sar: String = "",
    
    @SerializedName("startWithSap")
    val startWithSap: Int = 0,
    
    @SerializedName("start_with_sap")
    val startWithSapAlt: Int = 0,
    
    @SerializedName("segmentBase")
    val segmentBase: SegmentBase? = null,
    
    @SerializedName("segment_base")
    val segmentBaseAlt: SegmentBase? = null
) {
    val realBaseUrl: String get() = baseUrl.ifEmpty { baseUrlAlt }
    val realBackupUrl: List<String>? get() = backupUrl ?: backupUrlAlt
    val realMimeType: String get() = mimeType.ifEmpty { mimeTypeAlt }
    val realSegmentBase: SegmentBase? get() = segmentBase ?: segmentBaseAlt
}

data class SegmentBase(
    @SerializedName("initialization")
    val initialization: String = "",
    
    @SerializedName("indexRange")
    val indexRange: String = "",
    
    @SerializedName("index_range")
    val indexRangeAlt: String = "",
    
    @SerializedName("range")
    val range: String = ""
) {
    val realIndexRange: String get() = indexRange.ifEmpty { indexRangeAlt }
}

data class Durl(
    @SerializedName("order")
    val order: Int = 0,
    
    @SerializedName("length")
    val length: Long = 0,
    
    @SerializedName("size")
    val size: Long = 0,
    
    @SerializedName("ahead")
    val ahead: String = "",
    
    @SerializedName("vhead")
    val vhead: String = "",
    
    @SerializedName("url")
    val url: String = "",
    
    @SerializedName("backupUrl")
    val backupUrl: List<String>? = null
)

data class SupportFormat(
    @SerializedName("quality")
    val quality: Int = 0,
    
    @SerializedName("format")
    val format: String = "",
    
    @SerializedName("new_description")
    val newDescription: String = "",
    
    @SerializedName("display_desc")
    val displayDesc: String = "",
    
    @SerializedName("superscript")
    val superscript: String = "",
    
    @SerializedName("codecs")
    val codecs: List<String>? = null
)

data class Dolby(
    @SerializedName("type")
    val type: Int = 0,
    
    @SerializedName("audio")
    val audio: List<DashAudio>? = null
)

data class Flac(
    @SerializedName("display")
    val display: Boolean = false,

    @SerializedName("audio")
    val audio: DashAudio? = null
)

data class PgcV2Result(
    @SerializedName("video_info")
    val videoInfo: PlayInfoModel? = null,

    @SerializedName("play_check")
    val playCheck: PgcPlayCheck? = null
)

data class PgcPlayCheck(
    /**
     * 播放权限判定：PLAY_PREVIEW = 试看（无权限/未登录，服务端只下发约 60s 的 durl 流）；
     * PLAY_OK = 完整播放。app 依赖它区分"正片播完"与"试看流播完"，避免把试看结束伪装成卡死。
     */
    @SerializedName("play_detail")
    val playDetail: String = ""
)
