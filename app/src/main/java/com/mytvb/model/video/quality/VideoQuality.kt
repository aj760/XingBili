package com.mytvb.model.video.quality

import android.content.Context
import androidx.annotation.StringRes
import com.mytvb.R

data class VideoQuality(
    val id: Int,
    val name: String,
    val resolution: String = "",
    val codecId: Int = 0,
    val bandwidth: Long = 0,
    val baseUrl: String = "",
    val backupUrls: List<String>? = null,
    /** 显示名的字符串资源（0 表示无对应资源，展示时回退 [name]）。 */
    @StringRes val nameRes: Int = 0
) {
    /** 本地化显示名；无资源时回退 [name]（语言无关名）。多余格式参数会被 String.format 忽略。 */
    fun displayName(context: Context): String =
        if (nameRes != 0) context.getString(nameRes, id) else name

    companion object {
        val QUALITY_8K = VideoQuality(127, "8K 超高清", "4320P", nameRes = R.string.quality_8k)
        val QUALITY_DOLBY_VISION = VideoQuality(126, "杜比视界", "DolbyVision", nameRes = R.string.setting_quality_dolby_vision)
        val QUALITY_HDR_VIVID = VideoQuality(129, "HDR Vivid", "HDRVivid")
        val QUALITY_HDR = VideoQuality(125, "HDR 真彩", "HDR", nameRes = R.string.quality_hdr)
        val QUALITY_4K = VideoQuality(120, "4K 超高清", "2160P", nameRes = R.string.quality_4k)
        val QUALITY_1080P_60 = VideoQuality(116, "1080P 60帧", "1080P60", nameRes = R.string.quality_1080p_60)
        val QUALITY_1080P_PLUS = VideoQuality(112, "1080P 高码率", "1080P+", nameRes = R.string.quality_1080p_plus)
        val QUALITY_SUPER_RESOLUTION = VideoQuality(100, "智能修复", "SuperResolution", nameRes = R.string.setting_quality_ai_restore)
        val QUALITY_1080P = VideoQuality(80, "1080P 高清", "1080P", nameRes = R.string.quality_1080p)
        val QUALITY_720P_60 = VideoQuality(74, "720P 60帧", "720P60", nameRes = R.string.quality_720p_60)
        val QUALITY_720P = VideoQuality(64, "720P 准高清", "720P", nameRes = R.string.quality_720p)
        val QUALITY_480P = VideoQuality(32, "480P 标清", "480P", nameRes = R.string.quality_480p)
        val QUALITY_360P = VideoQuality(16, "360P 流畅", "360P", nameRes = R.string.quality_360p)
        val QUALITY_240P = VideoQuality(6, "240P 极速", "240P", nameRes = R.string.quality_240p)

        fun fromId(id: Int): VideoQuality {
            return when (id) {
                129 -> QUALITY_HDR_VIVID
                127 -> QUALITY_8K
                126 -> QUALITY_DOLBY_VISION
                125 -> QUALITY_HDR
                120 -> QUALITY_4K
                116 -> QUALITY_1080P_60
                112 -> QUALITY_1080P_PLUS
                100 -> QUALITY_SUPER_RESOLUTION
                80 -> QUALITY_1080P
                74 -> QUALITY_720P_60
                64 -> QUALITY_720P
                32 -> QUALITY_480P
                16 -> QUALITY_360P
                6 -> QUALITY_240P
                else -> VideoQuality(id, "画质 $id", nameRes = R.string.quality_unknown_format)
            }
        }
    }
}
