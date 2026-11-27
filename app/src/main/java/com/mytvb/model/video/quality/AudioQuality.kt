package com.mytvb.model.video.quality

import android.content.Context
import androidx.annotation.StringRes
import com.mytvb.R

data class AudioQuality(
    val id: Int,
    val name: String,
    val bandwidth: Long = 0,
    val codecId: Int = 0,
    val baseUrl: String = "",
    val backupUrls: List<String>? = null,
    /** 显示名的字符串资源（0 表示无对应资源，展示时回退 [name]）。 */
    @StringRes val nameRes: Int = 0
) {
    /** 本地化显示名；无资源时回退 [name]（"192Kbps" 等语言无关名）。多余格式参数会被 String.format 忽略。 */
    fun displayName(context: Context): String =
        if (nameRes != 0) context.getString(nameRes, id) else name

    companion object {
        val AUDIO_192K = AudioQuality(30280, "192Kbps")
        val AUDIO_132K = AudioQuality(30232, "132Kbps")
        val AUDIO_64K = AudioQuality(30216, "64Kbps")
        val AUDIO_DOLBY = AudioQuality(30250, "杜比全景声", nameRes = R.string.setting_audio_dolby_atmos)
        val AUDIO_HIRES = AudioQuality(30251, "Hi-Res无损", nameRes = R.string.setting_audio_hi_res)

        fun fromId(id: Int): AudioQuality {
            return when (id) {
                30280 -> AUDIO_192K
                30232 -> AUDIO_132K
                30216 -> AUDIO_64K
                30250 -> AUDIO_DOLBY
                30251 -> AUDIO_HIRES
                else -> AudioQuality(id, "音轨 $id", nameRes = R.string.audio_track_unknown_format)
            }
        }
    }
}
